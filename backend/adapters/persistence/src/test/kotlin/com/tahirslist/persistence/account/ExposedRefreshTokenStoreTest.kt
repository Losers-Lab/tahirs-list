package com.tahirslist.persistence.account

import com.tahirslist.application.account.AccountRepository
import com.tahirslist.application.account.RefreshTokenStore
import com.tahirslist.domain.account.Account
import com.tahirslist.domain.account.Email
import com.tahirslist.domain.account.Role
import com.tahirslist.persistence.PostgisTestDatabase
import com.tahirslist.persistence.count
import com.tahirslist.persistence.rows
import com.tahirslist.persistence.string
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldMatch
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Persistence adapter test for the rotating-hashed refresh-token store (sc-40,
 * hardened for sc-136). Proves the V2+V3 migrations create the necessary tables
 * and columns, that [ExposedRefreshTokenStore] stores only a SHA-256 hash of
 * each token, resolves live vs consumed records, keeps rotation within a token
 * family, and supports whole-family revocation on reuse.
 */
class ExposedRefreshTokenStoreTest : FunSpec() {

    private val postgis = PostgisTestDatabase()
    private lateinit var repository: AccountRepository
    private lateinit var refreshTokens: RefreshTokenStore

    /** Fixed clock-free instants: the store never validates expiry itself. */
    private val issuedAt = Instant.parse("2026-09-01T12:00:00Z")
    private val expiresIn30Days = issuedAt + 30.days
    private val expiresIn10Days = issuedAt + 10.days

    init {
        beforeSpec {
            postgis.start()
            repository = ExposedAccountRepository(postgis.database)
            refreshTokens = ExposedRefreshTokenStore(postgis.database)
        }
        afterSpec { postgis.stop() }

        test("V1+V2+V3 migrations create the users and refresh_tokens tables with family columns") {
            runTest {
                postgis.count("SELECT count(*) FROM information_schema.tables WHERE table_name = 'users'") shouldBe 1
                postgis.count("SELECT count(*) FROM information_schema.tables WHERE table_name = 'refresh_tokens'") shouldBe 1

                val familyCol = postgis.rows(
                    "SELECT column_name FROM information_schema.columns WHERE table_name = 'refresh_tokens' AND column_name = 'family_id'",
                )
                val consumedCol = postgis.rows(
                    "SELECT column_name FROM information_schema.columns WHERE table_name = 'refresh_tokens' AND column_name = 'consumed_at'",
                )
                familyCol.size shouldBe 1
                consumedCol.size shouldBe 1
            }
        }

        test("store persists a live token in the given family; findByToken resolves account, family, and expiry") {
            runTest {
                val account = repository.save(Account.new(Email("dave@example.com"), "argon2id\$stored-hash"))
                val family = Uuid.random()

                refreshTokens.store("opaque-refresh-token", account.id, family, expiresIn30Days)

                val found = refreshTokens.findByToken("opaque-refresh-token")
                found shouldNotBe null
                found!!.accountId shouldBe account.id
                found.familyId shouldBe family
                // Postgres stores TIMESTAMPTZ at microsecond precision; compare the
                // round-tripped expiry within a microsecond, not bit-for-bit.
                val precisionDiff = (found.expiresAt - expiresIn30Days).absoluteValue.inWholeNanoseconds
                (precisionDiff < 1000L) shouldBe true

                // The persisted column is a 64-char SHA-256 hex of the token, never the token itself.
                val storedHash = postgis.string(
                    "SELECT token_hash FROM refresh_tokens WHERE token_hash = ?",
                    sha256("opaque-refresh-token"),
                )!!
                storedHash shouldBe sha256("opaque-refresh-token")
                storedHash shouldNotBe "opaque-refresh-token"
                storedHash shouldMatch "[0-9a-f]{64}"
            }
        }

        test("findByToken returns null for an unknown token and for a consumed token") {
            runTest {
                refreshTokens.findByToken("never-issued-token").shouldBeNull()

                val account = repository.save(Account.new(Email("bob@example.com"), "argon2id\$stored-hash"))
                val family = Uuid.random()
                refreshTokens.store("live-token", account.id, family, expiresIn10Days)
                val live = refreshTokens.findByToken("live-token")
                live!!.familyId shouldBe family
                // After signing it out, it is no longer live.
                refreshTokens.revoke("live-token")
                refreshTokens.findByToken("live-token").shouldBeNull()
            }
        }

        test("revokeFamily removes every token in the family (live and consumed)") {
            runTest {
                val account = repository.save(Account.new(Email("carl@example.com"), "argon2id\$stored-hash"))
                val family = Uuid.random()
                refreshTokens.store("live-1", account.id, family, expiresIn10Days)
                refreshTokens.store("live-2", account.id, family, expiresIn10Days)

                refreshTokens.revokeFamily(family)

                refreshTokens.findByToken("live-1").shouldBeNull()
                refreshTokens.findByToken("live-2").shouldBeNull()
            }
        }

        test("revokeFamily leaves a different family's tokens untouched") {
            runTest {
                val account = repository.save(Account.new(Email("carol@example.com"), "argon2id\$stored-hash"))
                val doomed = Uuid.random()
                val unrelated = Uuid.random()
                refreshTokens.store("doomed-token", account.id, doomed, expiresIn10Days)
                refreshTokens.store("safe-token", account.id, unrelated, expiresIn10Days)

                refreshTokens.revokeFamily(doomed)

                refreshTokens.findByToken("doomed-token").shouldBeNull()
                refreshTokens.findByToken("safe-token") shouldNotBe null
            }
        }

        test("consumeAndRotate keeps the new token in the same family and records the old one as consumed") {
            runTest {
                val account = repository.save(Account.new(Email("erin@example.com"), "argon2id\$stored-hash"))
                val family = Uuid.random()
                refreshTokens.store("first-token", account.id, family, expiresIn10Days)

                val rotated = refreshTokens.consumeAndRotate(
                    presentedToken = "first-token",
                    newToken = "second-token",
                    accountId = account.id,
                    familyId = family,
                    expiresAt = expiresIn10Days,
                )

                rotated shouldBe true
                // The old token is no longer live, but a consumed record exists.
                refreshTokens.findByToken("first-token").shouldBeNull()
                val consumed = refreshTokens.findConsumedByToken("first-token")
                consumed shouldNotBe null
                consumed!!.familyId shouldBe family
                // The new token lives in the SAME family.
                val live = refreshTokens.findByToken("second-token")
                live shouldNotBe null
                live!!.familyId shouldBe family
            }
        }

        test("consumeAndRotate returns false when the presented token is already consumed (reuse race loser)") {
            runTest {
                val account = repository.save(Account.new(Email("frank@example.com"), "argon2id\$stored-hash"))
                val family = Uuid.random()
                refreshTokens.store("only-token", account.id, family, expiresIn10Days)

                // First caller wins the race: consumes "only-token" and mints its child.
                refreshTokens.consumeAndRotate("only-token", "winner-token", account.id, family, expiresIn10Days)
                // Second caller loses: presents the now-consumed token.
                val second = refreshTokens.consumeAndRotate("only-token", "loser-token-2", account.id, family, expiresIn10Days)

                second shouldBe false
                // The winner's replacement is live; the old token is a consumed record.
                refreshTokens.findByToken("winner-token") shouldNotBe null
                refreshTokens.findByToken("only-token").shouldBeNull()
                refreshTokens.findConsumedByToken("only-token") shouldNotBe null
                // The losing caller must not mint a live record.
                refreshTokens.findByToken("loser-token-2").shouldBeNull()
            }
        }

        test("consumeAndRotate returns false for an unknown token without inserting anything") {
            runTest {
                val account = repository.save(Account.new(Email("gina@example.com"), "argon2id\$stored-hash"))
                val other = refreshTokens.consumeAndRotate(
                    "never-issued", "wont-land", account.id, Uuid.random(), expiresIn10Days,
                )
                other shouldBe false
                refreshTokens.findByToken("wont-land").shouldBeNull()
            }
        }

        test("revoke invalidates the token so it can no longer resolve as live") {
            runTest {
                val account = repository.save(Account.new(Email("hani@example.com"), "argon2id\$stored-hash"))
                refreshTokens.store("to-revoke", account.id, Uuid.random(), expiresIn10Days)
                refreshTokens.findByToken("to-revoke") shouldNotBe null

                refreshTokens.revoke("to-revoke")

                refreshTokens.findByToken("to-revoke").shouldBeNull()
            }
        }

        test("findById round-trips a persisted account by its database id") {
            runTest {
                val saved = repository.save(
                    Account.fromStorage(
                        id = Uuid.random(),
                        email = Email("owner@example.com"),
                        passwordHash = "argon2id\$owner-hash",
                        role = Role.VERIFICATION_COMMITTEE,
                    ),
                )

                val found = repository.findById(saved.id)
                found shouldNotBe null
                found!!.id shouldBe saved.id
                found.email.value shouldBe "owner@example.com"
                found.role shouldBe Role.VERIFICATION_COMMITTEE
            }
        }
    }

    private fun sha256(input: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
