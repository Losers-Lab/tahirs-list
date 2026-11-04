package com.tahirslist.bootstrap

import io.kotest.matchers.shouldBe

/**
 * End-to-end Log Out (sc-133 cookie contract, building on sc-132) test against
 * the full application graph. Logout reads the refresh token from the HttpOnly
 * cookie (it no longer accepts a request body), revokes it server-side, and
 * clears the cookie. Proves a later refresh with the revoked cookie is rejected
 * 401, and that logout stays idempotent — an unknown or absent cookie still
 * returns 204 and never leaks token validity.
 */
class LogoutEndpointTest : KtorPostgresTest() {

    private suspend fun logout(refreshToken: String?) = post(
        "/v1/auth/logout",
        cookies = refreshToken?.let { mapOf("refresh_token" to it) } ?: emptyMap(),
    )

    private suspend fun refresh(refreshToken: String) =
        post("/v1/auth/refresh", cookies = mapOf("refresh_token" to refreshToken))

    /** True if a row for this refresh token's SHA-256 hash still exists in storage. */
    private suspend fun refreshHashExists(token: String): Boolean =
        dbRows("SELECT 1 FROM refresh_tokens WHERE token_hash = ?", sha256Hex(token)).isNotEmpty()

    init {
        test("logout reads the refresh cookie, revokes it (row gone), and a later refresh with it is rejected 401") {
            val session = signupAndLogin("kim@example.com")
            val refreshToken = session.refreshCookie.value
            refreshHashExists(refreshToken) shouldBe true

            val logoutResp = logout(refreshToken)
            logoutResp.status shouldBe 204

            // The server clears the refresh cookie so the client session is ended.
            val clearCookie = logoutResp.refreshCookie()
            clearCookie.maxAgeSeconds shouldBe 0L

            // The row is gone from storage (token can no longer be resolved).
            refreshHashExists(refreshToken) shouldBe false

            // Refreshing with the revoked cookie must fail closed.
            val replay = refresh(refreshToken)
            replay.status shouldBe 401
            replay.json()["code"].asString() shouldBe "invalid_credentials"
        }

        test("logout is idempotent: an unknown/never-issued refresh cookie still returns 204") {
            logout("never-issued-refresh-token").status shouldBe 204
        }

        test("logout is idempotent: calling it twice with the same cookie succeeds both times") {
            val session = signupAndLogin("leon@example.com")
            val refreshToken = session.refreshCookie.value

            logout(refreshToken).status shouldBe 204
            logout(refreshToken).status shouldBe 204
        }

        test("logout without a refresh cookie still returns 204 (idempotent, no body required)") {
            logout(null).status shouldBe 204
        }
    }
}
