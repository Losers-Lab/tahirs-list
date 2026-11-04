package com.tahirslist.domain.account

import kotlin.uuid.Uuid

/**
 * A registered account. Holds the Argon2id password hash — never a plaintext
 * password. Sign-up assigns the default [Role.USER]; other roles are set when
 * an account is promoted or restored from storage.
 */
data class Account(
    val id: Uuid,
    val email: Email,
    val passwordHash: String,
    val role: Role,
) {

    companion object {
        /** Create a brand-new account (default role = USER). */
        fun new(email: Email, passwordHash: String): Account =
            Account(id = Uuid.random(), email = email, passwordHash = passwordHash, role = Role.DEFAULT)

        /** Reconstitute an account that was previously persisted (any role). */
        fun fromStorage(id: Uuid, email: Email, passwordHash: String, role: Role): Account =
            Account(id = id, email = email, passwordHash = passwordHash, role = role)
    }
}