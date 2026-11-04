package com.tahirslist.persistence.account

import com.tahirslist.application.account.RefreshTokenStore
import com.tahirslist.application.account.StoredRefreshToken
import com.tahirslist.persistence.db.RefreshTokens
import com.tahirslist.persistence.db.bind
import com.tahirslist.persistence.db.executeUpdate
import com.tahirslist.persistence.db.queryRows
import com.tahirslist.persistence.db.toSqlTimestamp
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import java.security.MessageDigest
import java.sql.ResultSet
import java.util.UUID
import kotlin.time.Instant
import kotlin.time.toKotlinInstant
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid

/**
 * Exposed implementation of the [RefreshTokenStore] port against the
 * `refresh_tokens` table (see V2__create_refresh_tokens.sql + V3: family ids and
 * a soft-consumed marker for reuse detection). Only the SHA-256 hash of each
 * token is persisted — the plaintext token is never stored, so a database leak
 * of this table cannot be replayed.
 *
 * Lifecycle (sc-136): a live token has `consumed_at IS NULL`. Rotation soft-
 * consumes the old token (sets `consumed_at`) and inserts the new one in the
 * SAME `family_id`, so a *present-but-consumed* token remains discoverable — the
 * application treats its reappearance as reuse and revokes the whole family.
 */
class ExposedRefreshTokenStore(private val db: Database) : RefreshTokenStore {

    override suspend fun store(token: String, accountId: Uuid, familyId: Uuid, expiresAt: Instant) {
        suspendTransaction(db) {
            executeUpdate(
                """
                INSERT INTO refresh_tokens (token_hash, account_id, family_id, expires_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (token_hash) DO UPDATE
                    SET account_id = EXCLUDED.account_id,
                        family_id = EXCLUDED.family_id,
                        expires_at = EXCLUDED.expires_at
                """.trimIndent(),
                listOf(
                    RefreshTokens.tokenHash bind sha256(token),
                    RefreshTokens.accountId bind accountId,
                    RefreshTokens.familyId bind familyId,
                    RefreshTokens.expiresAt bind expiresAt.toSqlTimestamp(),
                ),
                StatementType.INSERT,
            )
        }
    }

    override suspend fun findByToken(token: String): StoredRefreshToken? = suspendTransaction(db) {
        queryRows(
            "SELECT account_id, family_id, expires_at FROM refresh_tokens WHERE token_hash = ? AND consumed_at IS NULL",
            listOf(RefreshTokens.tokenHash bind sha256(token)),
        ) { it.toStoredRefreshToken() }.firstOrNull()
    }

    override suspend fun findConsumedByToken(token: String): StoredRefreshToken? = suspendTransaction(db) {
        queryRows(
            "SELECT account_id, family_id, expires_at FROM refresh_tokens WHERE token_hash = ? AND consumed_at IS NOT NULL",
            listOf(RefreshTokens.tokenHash bind sha256(token)),
        ) { it.toStoredRefreshToken() }.firstOrNull()
    }

    override suspend fun revoke(token: String) {
        suspendTransaction(db) {
            executeUpdate(
                "DELETE FROM refresh_tokens WHERE token_hash = ?",
                listOf(RefreshTokens.tokenHash bind sha256(token)),
                StatementType.DELETE,
            )
        }
    }

    override suspend fun revokeFamily(familyId: Uuid) {
        suspendTransaction(db) {
            executeUpdate(
                "DELETE FROM refresh_tokens WHERE family_id = ?",
                listOf(RefreshTokens.familyId bind familyId),
                StatementType.DELETE,
            )
        }
    }

    override suspend fun consumeAndRotate(
        presentedToken: String,
        newToken: String,
        accountId: Uuid,
        familyId: Uuid,
        expiresAt: Instant,
    ): Boolean = suspendTransaction(db) {
        // The conditional soft-consume is the atomic claim on the token. Its
        // affected-row count is the gate: exactly one concurrent caller can flip
        // a live row to consumed (Postgres locks the row until this tx commits,
        // so a concurrent consume blocks, then re-evaluates and sees 0 rows after
        // the winner commits). If we did NOT own the row (0 updated), we must not
        // insert a replacement — that is what stops a second live token. On a
        // successful consume the INSERT happens in the same transaction, so the
        // rotate is atomic (an INSERT failure rolls the consume back too).
        val consumed = executeUpdate(
            "UPDATE refresh_tokens SET consumed_at = now() WHERE token_hash = ? AND consumed_at IS NULL",
            listOf(RefreshTokens.tokenHash bind sha256(presentedToken)),
        )
        if (consumed != 1) {
            // Losing branch: no row was written, so committing an empty
            // transaction is harmless.
            false
        } else {
            executeUpdate(
                "INSERT INTO refresh_tokens (token_hash, account_id, family_id, expires_at) VALUES (?, ?, ?, ?)",
                listOf(
                    RefreshTokens.tokenHash bind sha256(newToken),
                    RefreshTokens.accountId bind accountId,
                    RefreshTokens.familyId bind familyId,
                    RefreshTokens.expiresAt bind expiresAt.toSqlTimestamp(),
                ),
                StatementType.INSERT,
            )
            true
        }
    }

    private fun ResultSet.toStoredRefreshToken(): StoredRefreshToken = StoredRefreshToken(
        accountId = getObject("account_id", UUID::class.java).toKotlinUuid(),
        familyId = getObject("family_id", UUID::class.java).toKotlinUuid(),
        expiresAt = getTimestamp("expires_at").toInstant().toKotlinInstant(),
    )

    private fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
