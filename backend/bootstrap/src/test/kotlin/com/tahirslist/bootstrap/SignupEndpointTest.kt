package com.tahirslist.bootstrap

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * End-to-end Create Account (sc-39) test against the full application graph:
 * Ktor route -> CreateAccount use case -> Argon2id hasher -> Exposed account
 * repository, with the real Flyway users migration applied on boot.
 */
class SignupEndpointTest : KtorPostgresTest() {

    init {
        test("POST /v1/auth/signup persists an account with a hashed password and USER role") {
            val resp = signup("alice@example.com")

            resp.status shouldBe 201
            val body = resp.json()
            body["email"].asString() shouldBe "alice@example.com"
            body["role"].asString() shouldBe "USER"
            body["id"].asString().isNotBlank() shouldBe true

            // Persisted: Argon2id hash present, plaintext never stored, default role.
            val row = dbRow(
                "SELECT password_hash, role FROM users WHERE email = ?",
                "alice@example.com",
            )
            row["password_hash"].toString() shouldContain "\$argon2id\$"
            row["password_hash"].toString() shouldNotContain TEST_PASSWORD
            row["role"] shouldBe "USER"
        }

        test("POST /v1/auth/signup rejects a duplicate email with 409 and persists only one row") {
            signup("bob@example.com").status shouldBe 201

            val resp = signup("bob@example.com", "another-password")

            resp.status shouldBe 409
            resp.json()["code"].asString() shouldBe "email_already_exists"
            dbCount("SELECT count(*) FROM users WHERE email = ?", "bob@example.com") shouldBe 1
        }

        test("POST /v1/auth/signup rejects a weak password with 422 and persists nothing") {
            val resp = signup("carol@example.com", "tiny")

            resp.status shouldBe 422
            resp.json()["code"].asString() shouldBe "weak_password"
            dbCount("SELECT count(*) FROM users WHERE email = ?", "carol@example.com") shouldBe 0
        }

        test("POST /v1/auth/signup accepts a password of exactly the 8-char minimum (sc-135 Gap 5 boundary)") {
            val resp = signup("debra@example.com", "pass1234")

            resp.status shouldBe 201
        }

        test("POST /v1/auth/signup rejects a password of exactly 7 chars with 422 and persists nothing (sc-135 Gap 5 boundary)") {
            val resp = signup("erin@example.com", "pass123")

            resp.status shouldBe 422
            dbCount("SELECT count(*) FROM users WHERE email = ?", "erin@example.com") shouldBe 0
        }

        test("concurrent duplicate signups never 500 and persist exactly one row (sc-135 Gap 6 / Defect 4a: unique-constraint race)") {
            val email = "race-http@example.com"
            val threads = 6
            val start = CountDownLatch(1)
            val done = CountDownLatch(threads)
            val statuses = ConcurrentLinkedQueue<Int>()

            repeat(threads) {
                Thread {
                    start.await()
                    try {
                        statuses.add(runBlocking { signup(email).status })
                    } finally {
                        done.countDown()
                    }
                }.start()
            }
            start.countDown()
            done.await(30, TimeUnit.SECONDS)

            // The unique DB constraint is the backstop: no 500, exactly one 201, rest 409.
            statuses.size shouldBe threads
            statuses.forEach { (it == 201 || it == 409) shouldBe true }
            statuses.count { it == 201 } shouldBe 1
            dbCount("SELECT count(*) FROM users WHERE email = ?", email) shouldBe 1
        }
    }
}
