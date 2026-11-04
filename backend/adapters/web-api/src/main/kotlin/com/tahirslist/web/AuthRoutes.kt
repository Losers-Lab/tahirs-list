package com.tahirslist.web

import com.tahirslist.application.account.AuthSession
import com.tahirslist.application.account.InvalidCredentialsException
import com.tahirslist.web.ratelimit.AuthRateLimitGateKey
import com.tahirslist.web.ratelimit.authRateLimitKey
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable

/**
 * The account contract shared by Log In (sc-40) and Refresh: the client gets
 * ONLY the short-lived access JWT plus token type / lifetime and the account's
 * id + role. The refresh token is deliberately absent from JSON — it travels
 * exclusively through the HttpOnly refresh cookie (sc-133).
 */
@Serializable
data class AuthResponse(
    val accessToken: String,
    val tokenType: String,
    val expiresIn: Long,
    val accountId: String,
    val role: String,
) {
    companion object {
        fun from(session: AuthSession): AuthResponse = AuthResponse(
            accessToken = session.tokens.accessToken,
            tokenType = session.tokens.tokenType,
            expiresIn = session.tokens.accessTokenExpiresInSeconds,
            accountId = session.account.id.toString(),
            role = session.account.role.name,
        )
    }
}

@Serializable
data class SignupRequest(val email: String, val password: String)

@Serializable
data class SignupResponse(val id: String, val email: String, val role: String)

/** Login and signup share the same credential shape and validation order. */
@Serializable
data class LoginRequest(val email: String, val password: String)

/**
 * Auth endpoints:
 *  - `POST /v1/auth/signup`  — create an account (409 taken / 422 weak password; rate limited);
 *  - `POST /v1/auth/login`   — issue an access token + refresh cookie (rate limited);
 *  - `POST /v1/auth/refresh` — rotate the cookie (no request body; rate limited);
 *  - `POST /v1/auth/logout`  — revoke the cookie server-side and clear it (always 204).
 *
 * All four are public; the JWT they mint guards every other route. The rate
 * limit is the same gate instance for all three mutations — the bucket key
 * includes the route, so signup cannot starve login (or vice versa) while
 * account creation can no longer be used as an unthrottled CPU sink (Argon2).
 */
fun Route.authRoutes(auth: AuthUseCases) {
    post("/v1/auth/signup") {
        if (!call.passesAuthRateLimit()) return@post
        val request = call.receiveJson<SignupRequest>()
        requireValid(validateCredentials(request.email, request.password))
        val account = auth.signup.execute(emailRaw = request.email, password = request.password)
        call.respond(
            HttpStatusCode.Created,
            SignupResponse(id = account.id.toString(), email = account.email.value, role = account.role.name),
        )
    }

    post("/v1/auth/login") {
        if (!call.passesAuthRateLimit()) return@post
        val request = call.receiveJson<LoginRequest>()
        requireValid(validateCredentials(request.email, request.password))
        val session = auth.login.execute(emailRaw = request.email, password = request.password)
        call.setRefreshCookie(auth.cookie, session.tokens.refreshToken)
        call.respond(HttpStatusCode.OK, AuthResponse.from(session))
    }

    post("/v1/auth/refresh") {
        if (!call.passesAuthRateLimit()) return@post
        // No request body: the token is presented via the HttpOnly cookie only.
        val refreshToken = call.request.cookies[REFRESH_COOKIE_NAME]
        if (refreshToken.isNullOrBlank()) {
            throw IllegalArgumentException("refreshToken cookie is required")
        }
        val session = try {
            auth.refresh.execute(refreshToken)
        } catch (e: InvalidCredentialsException) {
            // The presented cookie is dead (expired/rotated/unknown): clear it so
            // the browser stops resubmitting a token that can never succeed.
            call.clearRefreshCookie(auth.cookie)
            throw e
        }
        call.setRefreshCookie(auth.cookie, session.tokens.refreshToken)
        call.respond(HttpStatusCode.OK, AuthResponse.from(session))
    }

    post("/v1/auth/logout") {
        val refreshToken = call.request.cookies[REFRESH_COOKIE_NAME]
        if (!refreshToken.isNullOrBlank()) {
            auth.logout.execute(refreshToken)
        }
        call.clearRefreshCookie(auth.cookie)
        call.respond(HttpStatusCode.NoContent)
    }
}

/**
 * Counts this request against the auth budget for its route. Returns `false`
 * (and writes the generic 429 envelope) when the budget is exhausted, in which
 * case the caller must stop before touching the auth use case or the request
 * body.
 */
private suspend fun ApplicationCall.passesAuthRateLimit(): Boolean {
    val gate = application.attributes.getOrNull(AuthRateLimitGateKey) ?: return true
    if (gate.limiter.tryAcquire(authRateLimitKey(gate.trustProxy))) return true
    respondError(HttpStatusCode.TooManyRequests, ErrorCodes.RATE_LIMITED, ErrorMessages.RATE_LIMITED)
    return false
}

/**
 * Manual equivalent of the previous bean-validation rules, in the same field
 * order: blank email, malformed email, blank password. Messages are preserved
 * verbatim so clients see the same 400 detail.
 */
private fun validateCredentials(email: String, password: String): List<String> = buildList {
    if (email.isBlank()) {
        add("email is required")
    } else if (!looksLikeEmail(email)) {
        add("email must be a valid address")
    }
    if (password.isBlank()) {
        add("password is required")
    }
}

private fun looksLikeEmail(value: String): Boolean {
    val at = value.indexOf('@')
    return at > 0 &&
        at < value.lastIndex &&
        value.indexOf('@', startIndex = at + 1) == -1 &&
        !value.any { it.isWhitespace() }
}
