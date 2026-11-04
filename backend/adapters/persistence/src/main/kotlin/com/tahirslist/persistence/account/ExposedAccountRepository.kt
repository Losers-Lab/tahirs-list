package com.tahirslist.persistence.account

import com.tahirslist.application.account.AccountRepository
import com.tahirslist.application.account.EmailAlreadyExistsException
import com.tahirslist.domain.account.Account
import com.tahirslist.domain.account.Email
import com.tahirslist.domain.account.Role
import com.tahirslist.persistence.db.Users
import com.tahirslist.persistence.db.bind
import com.tahirslist.persistence.db.queryRows
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import java.sql.ResultSet
import java.util.UUID
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid

/**
 * Exposed implementation of the [AccountRepository] port against the `users`
 * table (see V1__create_users.sql). Deliberately boring and explicit: SQL is
 * kept verbatim and the database owns id generation (`gen_random_uuid`), so a
 * saved account's id always overrides whatever the caller built.
 */
class ExposedAccountRepository(private val db: Database) : AccountRepository {

    override suspend fun findByEmail(email: Email): Account? = suspendTransaction(db) {
        queryRows(
            "SELECT id, email, password_hash, role FROM users WHERE email = ?",
            listOf(Users.email bind email.value),
        ) { it.toAccount() }.firstOrNull()
    }

    override suspend fun findById(id: Uuid): Account? = suspendTransaction(db) {
        queryRows(
            "SELECT id, email, password_hash, role FROM users WHERE id = ?",
            listOf(Users.id bind id),
        ) { it.toAccount() }.firstOrNull()
    }

    override suspend fun save(account: Account): Account {
        val id = try {
            suspendTransaction(db) {
                queryRows(
                    """
                    INSERT INTO users (email, password_hash, role)
                    VALUES (?, ?, ?)
                    RETURNING id
                    """.trimIndent(),
                    listOf(
                        Users.email bind account.email.value,
                        Users.passwordHash bind account.passwordHash,
                        Users.role bind account.role.name,
                    ),
                ) { it.getObject("id", UUID::class.java).toKotlinUuid() }
                    .firstOrNull()
                    ?: error("INSERT RETURNING id returned no row")
            }
        } catch (e: ExposedSQLException) {
            // sc-135 Gap 6 / Defect 4a: the application-level findByEmail check is
            // TOCTOU-racy — two concurrent signups can both pass it, then one INSERT
            // hits the `uk_users_email` unique constraint. The DB is the real
            // backstop; translate its signal into the domain exception so the web
            // layer maps it to 409 (email_already_exists) instead of a raw 500.
            // Any other integrity error keeps surfacing as the Exposed exception.
            if (e.getSQLState() != UNIQUE_VIOLATION) throw e
            throw EmailAlreadyExistsException(account.email)
        }

        return account.copy(id = id)
    }

    private fun ResultSet.toAccount(): Account = Account.fromStorage(
        id = getObject("id", UUID::class.java).toKotlinUuid(),
        email = Email(getString("email")),
        passwordHash = getString("password_hash"),
        role = Role.valueOf(getString("role")),
    )

    private companion object {
        /** PostgreSQL SQLSTATE for `unique_violation`. */
        const val UNIQUE_VIOLATION = "23505"
    }
}
