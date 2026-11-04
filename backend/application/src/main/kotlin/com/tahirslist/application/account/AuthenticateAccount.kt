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
 *
 * The unknown-email path still runs a full Argon2id verification (against
 * [DUMMY_PASSWORD_HASH]) so the two failures also cost the same wall-clock time:
 * an immediately-rejected unknown email would otherwise be a user-enumeration
 * timing oracle next to a slow wrong-password check.
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
        // Unknown emails reuse the pre-computed dummy hash, so this is one real
        // Argon2id run either way — no hashing on the request path beyond the
        // one verification the caller always pays for.
        val storedHash = account?.passwordHash ?: DUMMY_PASSWORD_HASH
        val verified = withContext(Dispatchers.Default) { hasher.verify(password, storedHash) }
        // `account == null` is belt-and-braces: the dummy hash is of an unknown
        // password, so verify() cannot succeed against it, but an account must
        // exist before any session is issued regardless of the verify result.
        if (account == null || !verified) {
            // Identical signal for "no such user" and "wrong password".
            throw InvalidCredentialsException()
        }

        val tokens = tokenIssuer.issue(account)
        refreshTokenStore.store(
            token = tokens.refreshToken,
            accountId = account.id,
            familyId = Uuid.random(),
            expiresAt = clock.now() + SessionLifetimes.REFRESH_LIFETIME,
        )
        return AuthSession(account, tokens)
    }

    internal companion object {
        /**
         * Pre-computed Argon2id PHC hash (`m=16384,t=2,p=1`, 16-byte salt,
         * 32-byte tag — the same parameters as the production hasher) of a
         * random password that was generated once and thrown away. Because it
         * is a constant, the constant-work path never hashes anything extra per
         * request: `verify` just runs the normal Argon2id over the submitted
         * password and always mismatches. Regenerate if the hasher parameters
         * change, or the dummy run stops costing the same as a real one.
         *
         * `internal` (not private) so the login timing test can pin the exact
         * hash the unknown-email path verifies against.
         */
        const val DUMMY_PASSWORD_HASH =
            "\$argon2id\$v=19\$m=16384,t=2,p=1\$vEtOh3V9OcKwWG43hDXcJg\$" +
                "G+8wZFhsS0YDyEijan0pD8+gTC4ibpXX8G8jTsO7h4w"
    }
}
