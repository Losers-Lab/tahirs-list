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
            val actual = argon2id(plainPassword, salt, memory, iterations, parallelism, expected.size)
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
    }
}
