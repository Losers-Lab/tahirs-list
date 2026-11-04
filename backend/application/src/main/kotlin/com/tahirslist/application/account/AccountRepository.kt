package com.tahirslist.application.account

import com.tahirslist.domain.account.Account
import com.tahirslist.domain.account.Email
import kotlin.uuid.Uuid

/**
 * Persistence port (hexagonal "out" port) for accounts. Implemented by the
 * infrastructure/persistence adapter; the application layer depends only on this
 * contract.
 */
interface AccountRepository {
    suspend fun findByEmail(email: Email): Account?
    suspend fun findById(id: Uuid): Account?
    suspend fun save(account: Account): Account
}
