package com.tahirslist.persistence.account

import com.tahirslist.application.account.AccountRepository
import com.tahirslist.application.account.EmailAlreadyExistsException
import com.tahirslist.domain.account.Account
import com.tahirslist.domain.account.Email
import com.tahirslist.domain.account.Role
import com.tahirslist.persistence.PostgisTestDatabase
import com.tahirslist.persistence.count
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.test.runTest
import kotlin.uuid.Uuid

/**
 * Persistence adapter test: proves the Flyway migration creates the users table
 * and that [ExposedAccountRepository] round-trips an account via the
 * application-layer [AccountRepository] port against a real PostGIS container.
 */
class ExposedAccountRepositoryTest : FunSpec() {

    private val postgis = PostgisTestDatabase()
    private lateinit var repository: AccountRepository

    init {
        beforeSpec {
            postgis.start()
            repository = ExposedAccountRepository(postgis.database)
        }
        afterSpec { postgis.stop() }

        test("V1 migration creates the users table") {
            runTest {
                postgis.count("SELECT count(*) FROM information_schema.tables WHERE table_name = 'users'") shouldBe 1
            }
        }

        test("saved account is found back by its canonical email") {
            runTest {
                val account = Account.new(email = Email("User@Example.COM"), passwordHash = "argon2id\$stored-hash")
                val saved = repository.save(account)

                saved.email.value shouldBe "user@example.com"
                saved.passwordHash shouldBe "argon2id\$stored-hash"
                saved.role shouldBe Role.USER
                saved.id shouldNotBe account.id // DB generates the id, not the caller

                repository.findByEmail(Email("user@example.com"))!!.id shouldBe saved.id
            }
        }

        test("findByEmail returns null for an unknown email") {
            runTest {
                repository.findByEmail(Email("nobody@example.com")) shouldBe null
            }
        }

        test("persisted account restores the exact role and hash") {
            runTest {
                val account = Account.fromStorage(
                    id = Uuid.random(),
                    email = Email("owner@example.com"),
                    passwordHash = "argon2id\$owner-hash",
                    role = Role.RESTAURANT_OWNER,
                )
                repository.save(account)

                val found = repository.findByEmail(Email("OWNER@example.com"))
                found shouldNotBe null
                found!!.role shouldBe Role.RESTAURANT_OWNER
                found.passwordHash shouldBe "argon2id\$owner-hash"
            }
        }

        test("V1 migration enforces a unique constraint on users.email") {
            runTest {
                val uniqueIndexCount = postgis.count(
                    """
                    SELECT count(*)
                    FROM pg_index
                    WHERE indisunique = true
                      AND indisprimary = false
                      AND indrelid = 'users'::regclass
                    """.trimIndent(),
                )
                uniqueIndexCount shouldBe 1
            }
        }

        test("a second save of the same email surfaces EmailAlreadyExistsException (sc-135 Gap 6: unique-constraint race backstop, not a 500)") {
            runTest {
                val first = Account.new(email = Email("race@example.com"), passwordHash = "argon2id\$one")
                repository.save(first)

                // Simulate the lost TOCTOU race: a concurrent signup that already passed
                // the application-layer findByEmail check now hits the DB unique constraint.
                val racer = Account.new(email = Email("race@example.com"), passwordHash = "argon2id\$two")
                val ex = shouldThrow<EmailAlreadyExistsException> { repository.save(racer) }

                ex.email.value shouldBe "race@example.com"

                // Exactly one row survives; the unique constraint is the backstop.
                repository.findByEmail(Email("race@example.com")) shouldNotBe null
                repository.findByEmail(Email("race@example.com"))!!.passwordHash shouldBe "argon2id\$one"
            }
        }
    }
}
