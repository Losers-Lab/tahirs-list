package com.tahirslist.bootstrap

import com.tahirslist.application.account.PasswordHasher
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Argon2id password hasher backed by BouncyCastle directly (the Spring Security
 * Crypto wrapper is gone with the Spring migration).
 *
 * Parameters and the encoded format deliberately match
 * `Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()` — salt 16 bytes,
 * hash 32 bytes, parallelism 1, memory 16 MiB (16384 KiB), 2 iterations,
 * PHC string `$argon2id$v=19$m=16384,t=2,p=1$<salt>$<hash>` with unpadded
 * standard Base64. Existing stored hashes therefore keep verifying unchanged.
 *
 * [verify] additionally refuses stored parameters outside the sane ranges
 * (memory 8–64 MiB, 1–10 passes, 1–4 lanes, 16–64-byte hash, 8–64-byte salt):
 * a stored hash is untrusted data, and hashing with whatever work factor it
 * names would let a bad row exhaust the server.
 */
class Argon2idPasswordHasher : PasswordHasher {

    private val random = SecureRandom()

    override fun hash(plainPassword: String): String {
        val salt = ByteArray(SALT_LENGTH).also(random::nextBytes)
        val hash = argon2id(plainPassword, salt)
        val b64 = Base64.getEncoder().withoutPadding()
        return "\$argon2id\$v=$ARGON2_VERSION\$m=$MEMORY_KIB,t=$ITERATIONS,p=$PARALLELISM" +
            "\$${b64.encodeToString(salt)}\$${b64.encodeToString(hash)}"
    }

    override fun verify(plainPassword: String, storedHash: String): Boolean {
        return try {
            // "$argon2id$v=19$m=16384,t=2,p=1$<salt>$<hash>" -> 6 segments (leading empty)
            val segments = storedHash.split('$')
            if (segments.size != 6 || segments[0].isNotEmpty() || segments[1] != "argon2id") {
                return false
            }
            val params = segments[3]
            val memory = params.substringAfter("m=").substringBefore(',').toIntOrNull() ?: return false
            val iterations = params.substringAfter("t=").substringBefore(',').toIntOrNull() ?: return false
            val parallelism = params.substringAfter("p=").toIntOrNull() ?: return false
            val b64 = Base64.getDecoder()
            val salt = b64.decode(segments[4])
            val expected = b64.decode(segments[5])

            // A stored hash is data, not a command: never let it choose the work
            // factor. Without these bounds a corrupt/tampered row could ask for
            // gigabytes of memory or thousands of passes and take the process
            // down during a login attempt. Out-of-range means "this cannot be one
            // of our hashes" -> fail closed (false) before any Argon2 work.
            if (memory !in MIN_MEMORY_KIB..MAX_MEMORY_KIB) return false
            if (iterations !in MIN_ITERATIONS..MAX_ITERATIONS) return false
            if (parallelism !in MIN_PARALLELISM..MAX_PARALLELISM) return false
            if (salt.size !in MIN_SALT_LENGTH..MAX_SALT_LENGTH) return false
            if (expected.size !in MIN_HASH_LENGTH..MAX_HASH_LENGTH) return false

            val actual = argon2id(plainPassword, salt, memory, iterations, parallelism, expected.size)
            // Constant-time: the comparison itself must not leak how many
            // leading bytes matched.
            MessageDigest.isEqual(actual, expected)
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    private fun argon2id(
        password: String,
        salt: ByteArray,
        memoryKib: Int = MEMORY_KIB,
        iterations: Int = ITERATIONS,
        parallelism: Int = PARALLELISM,
        hashLength: Int = HASH_LENGTH,
    ): ByteArray {
        val parameters = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withIterations(iterations)
            .withMemoryAsKB(memoryKib)
            .withParallelism(parallelism)
            .withSalt(salt)
            .build()
        val generator = Argon2BytesGenerator().apply { init(parameters) }
        val output = ByteArray(hashLength)
        generator.generateBytes(password.toCharArray(), output)
        return output
    }

    private companion object {
        const val SALT_LENGTH = 16
        const val HASH_LENGTH = 32
        const val PARALLELISM = 1
        const val MEMORY_KIB = 16384
        const val ITERATIONS = 2
        const val ARGON2_VERSION = 19 // 0x13, Argon2 v1.3

        // Accepted range for parameters read out of a stored PHC string. The
        // lower bounds exclude degenerate hashes (t=0, tiny salt), the upper
        // bounds cap the cost of a single verification (~64 MiB, 10 passes,
        // 4 lanes) so no stored row can turn a login into a resource attack.
        const val MIN_MEMORY_KIB = 8_192
        const val MAX_MEMORY_KIB = 65_536
        const val MIN_ITERATIONS = 1
        const val MAX_ITERATIONS = 10
        const val MIN_PARALLELISM = 1
        const val MAX_PARALLELISM = 4
        const val MIN_HASH_LENGTH = 16
        const val MAX_HASH_LENGTH = 64
        const val MIN_SALT_LENGTH = 8
        const val MAX_SALT_LENGTH = 64
    }
}
