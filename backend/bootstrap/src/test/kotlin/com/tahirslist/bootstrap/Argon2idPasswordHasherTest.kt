package com.tahirslist.bootstrap

import com.tahirslist.application.account.PasswordHasher
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

/**
 * Argon2id hashing/verification, including the accepted-parameter bounds that
 * keep a tampered stored hash from dictating the work factor of a login.
 *
 * The frozen PHC vector in "verifies a frozen PHC vector with explicit
 * parameters" is password "s3cr3t-password", ASCII salt "frozen-vector-16",
 * m=16384, t=2, p=1, 32-byte tag. It was generated once with an independent
 * Argon2id implementation (Node's `crypto.argon2Sync`, itself checked against
 * the RFC 9106 Argon2id test vector) using this class's parameters. It pins the
 * PHC *format* and parameter parsing so a refactor cannot silently stop
 * accepting existing stored hashes; it is NOT a Spring-generated vector.
 */
class Argon2idPasswordHasherTest : FunSpec({

    val hasher: PasswordHasher = Argon2idPasswordHasher()

    test("produced hash is not the plaintext password") {
        val hash = hasher.hash("s3cr3t-password")
        hash shouldNotBe "s3cr3t-password"
    }

    test("hash verifies against the original password") {
        val hash = hasher.hash("s3cr3t-password")
        hasher.verify("s3cr3t-password", hash) shouldBe true
    }

    test("hash does not verify against a different password") {
        val hash = hasher.hash("s3cr3t-password")
        hasher.verify("wrong-password", hash) shouldBe false
    }

    test("the same password produces a different hash each time (salted)") {
        val hash1 = hasher.hash("s3cr3t-password")
        val hash2 = hasher.hash("s3cr3t-password")
        hash1 shouldNotBe hash2
    }

    // Frozen PHC vector from the class KDoc: the format pin, not a
    // Spring-generated hash.
    test("verifies a frozen PHC vector with explicit parameters") {
        val storedHash = "\$argon2id\$v=19\$m=16384,t=2,p=1\$ZnJvemVuLXZlY3Rvci0xNg\$" +
            "ETEZLccn9ym20I1yCKvg3VCNQGaBHB98PuhLTsnELek"

        hasher.verify("s3cr3t-password", storedHash) shouldBe true
    }

    test("rejects a frozen PHC vector when the password is wrong") {
        val storedHash = "\$argon2id\$v=19\$m=16384,t=2,p=1\$ZnJvemVuLXZlY3Rvci0xNg\$" +
            "ETEZLccn9ym20I1yCKvg3VCNQGaBHB98PuhLTsnELek"

        hasher.verify("not-the-password", storedHash) shouldBe false
    }

    test("rejects stored parameters outside the accepted bounds quickly and without throwing") {
        val salt = "ZnJvemVuLXZlY3Rvci0xNg" // the 16-byte frozen salt
        val hash = "ETEZLccn9ym20I1yCKvg3VCNQGaBHB98PuhLTsnELek" // 32-byte tag
        val shortSalt = "YWJjZA" // 4 bytes
        val shortHash = "MTIzNDU2Nzg" // 8 bytes

        val cases = mapOf(
            "memory above the 64 MiB cap" to "\$argon2id\$v=19\$m=1073741824,t=2,p=1\$$salt\$$hash",
            "memory below the 8 MiB floor" to "\$argon2id\$v=19\$m=1024,t=2,p=1\$$salt\$$hash",
            "zero iterations" to "\$argon2id\$v=19\$m=16384,t=0,p=1\$$salt\$$hash",
            "iterations above the 10-pass cap" to "\$argon2id\$v=19\$m=16384,t=11,p=1\$$salt\$$hash",
            "parallelism above the 4-lane cap" to "\$argon2id\$v=19\$m=16384,t=2,p=99\$$salt\$$hash",
            "salt shorter than 8 bytes" to "\$argon2id\$v=19\$m=16384,t=2,p=1\$$shortSalt\$$hash",
            "hash shorter than 16 bytes" to "\$argon2id\$v=19\$m=16384,t=2,p=1\$$salt\$$shortHash",
        )

        for ((label, storedHash) in cases) {
            withClue(label) {
                // The rejected path must not run Argon2 at all: fast, no OOM,
                // no exception escaping verify(). 2s is a very loose ceiling
                // compared with ~50ms for one real Argon2id run.
                val elapsed = measureTime {
                    hasher.verify("s3cr3t-password", storedHash) shouldBe false
                }
                (elapsed < 2.seconds) shouldBe true
            }
        }
    }
})
