package com.tahirslist.application.account

import com.tahirslist.domain.account.Email
import kotlin.time.Clock
import kotlin.uuid.Uuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Log In (sc-40) use case. Looks up the account by its canonical email, verifies
 * the submitted password against the stored Argon2id hash, and on success issues
 * a short access token + a rotating refresh token (persisted hashed). Each
 * login mints its refresh token in a FRESH family (sc-136), so separate log-ins
 * are never joint by a shared family id. Any failure — unknown email, wrong
 * password — throws the same [InvalidCredentialsException] so the endpoint
 * cannot leak which field failed.
 */
class AuthenticateAccount(
    private val repository: AccountRepository,
    private val hasher: PasswordHasher,
    private val tokenIssuer: TokenIssuer,
    private val refreshTokenStore: RefreshTokenStore,
    private val clock: Clock = Clock.System,
) {

    suspend fun execute(emailRaw: String, password: String): AuthSession {
        val email = Email(emailRaw)

        val account = repository.findByEmail(email)
        // Argon2id verification is CPU-bound: keep it off the caller's thread.
        val verified = account?.let {
            withContext(Dispatchers.Default) { hasher.verify(password, it.passwordHash) }
        } ?: false
        if (!verified) {
            // Identical signal for "no such user" and "wrong password".
            throw InvalidCredentialsException()
        }
        requireNotNull(account)

        val tokens = tokenIssuer.issue(account)
        refreshTokenStore.store(
            token = tokens.refreshToken,
            accountId = account.id,
            familyId = Uuid.random(),
            expiresAt = clock.now() + SessionLifetimes.REFRESH_LIFETIME,
        )
        return AuthSession(account, tokens)
    }
}
