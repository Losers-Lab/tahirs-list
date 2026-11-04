package com.tahirslist.bootstrap

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldMatch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.long
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * End-to-end Log In + Refresh (sc-40) test against the full application graph,
 * for the sc-133 cookie contract: the access JWT is returned in the JSON body
 * (the frontend keeps it memory-only), but the refresh token is delivered ONLY
 * as an HttpOnly; Secure; SameSite=Lax cookie scoped to the auth routes — it
 * must never appear in the JSON body. Refresh presents the cookie (no request
 * body) and rotates it via a fresh Set-Cookie.
 */
class LoginEndpointTest : KtorPostgresTest() {

    private suspend fun loginRequest(email: String, password: String) =
        post("/v1/auth/login", json = linkedMapOf("email" to email, "password" to password))

    private suspend fun refresh(refreshToken: String) =
        post("/v1/auth/refresh", cookies = mapOf("refresh_token" to refreshToken))

    init {
        test("login returns the access token in the body and the refresh token ONLY as an HttpOnly Secure SameSite=Lax cookie (never in JSON)") {
            signup("gina@example.com").status shouldBe 201
            val resp = loginRequest("gina@example.com", TEST_PASSWORD)
            resp.status shouldBe 200

            val tokens = resp.json()
            // Access JWT stays in the JSON body (frontend holds it memory-only).
            tokens["accessToken"].asString().isNotBlank() shouldBe true
            tokens["tokenType"].asString() shouldBe "Bearer"
            (tokens["expiresIn"] as JsonPrimitive).long shouldBe 900L
            tokens["accountId"].asString().isNotBlank() shouldBe true
            tokens["role"].asString() shouldBe "USER"

            // The refresh token must NOT be in the JSON body (JS-readable storage contract removed).
            tokens.containsKey("refreshToken") shouldBe false

            // The access token is an RS256 JWT whose payload embeds sub + role.
            val payload = decodeJwtPayload(tokens["accessToken"].asString())
            payload shouldContain "\"role\":\"USER\""
            payload shouldContain "\"sub\""

            // The refresh token is delivered by Set-Cookie with the hardened attributes.
            val cookie = resp.refreshCookie()
            cookie.value.isNotBlank() shouldBe true
            cookie.httpOnly shouldBe true
            cookie.secure shouldBe true
            cookie.sameSite shouldBe "Lax"
            cookie.path shouldBe "/v1/auth"
            cookie.maxAgeSeconds shouldBe 30L * 24 * 60 * 60

            // The refresh cookie persists only a SHA-256 hash of the raw token.
            val storedHash = dbString(
                "SELECT token_hash FROM refresh_tokens WHERE token_hash = ?",
                sha256Hex(cookie.value),
            ) ?: error("expected the refresh token hash to be persisted")
            storedHash shouldMatch "[0-9a-f]{64}"
            storedHash shouldNotBe cookie.value
        }

        test("wrong password is rejected with a generic 401 invalid_credentials") {
            signup("helen@example.com").status shouldBe 201

            val resp = loginRequest("helen@example.com", "wrong-password")

            resp.status shouldBe 401
            resp.json()["code"].asString() shouldBe "invalid_credentials"
            resp.json()["message"].asString() shouldBe "Invalid email or password."
        }

        test("unknown email is rejected with the SAME generic 401 (no user enumeration)") {
            val resp = loginRequest("nobody@example.com", "whatever")

            resp.status shouldBe 401
            resp.json()["code"].asString() shouldBe "invalid_credentials"
            resp.json()["message"].asString() shouldBe "Invalid email or password."
        }

        test("missing login fields return 400") {
            val resp = loginRequest("", "")

            resp.status shouldBe 400
            resp.json()["code"].asString() shouldBe "invalid_input"
        }

        test("refresh rotates within a family: old cookie consumed, new cookie progressively rotates") {
            val session = signupAndLogin("iris@example.com")
            val oldRefresh = session.refreshCookie.value

            val first = refresh(oldRefresh)
            first.status shouldBe 200
            val rotated = first.json()
            rotated["accessToken"].asString().isNotBlank() shouldBe true
            rotated.containsKey("refreshToken") shouldBe false
            rotated["role"].asString() shouldBe "USER"

            val newCookie = first.refreshCookie()
            newCookie.value.isNotBlank() shouldBe true
            newCookie.value shouldNotBe oldRefresh
            newCookie.httpOnly shouldBe true
            newCookie.secure shouldBe true
            newCookie.sameSite shouldBe "Lax"

            // The new token is still valid and rotates again (no replay involved).
            val second = refresh(newCookie.value)
            second.status shouldBe 200
            second.json()["accessToken"].asString().isNotBlank() shouldBe true
        }

        test("reuse of an already-consumed refresh token revokes the ENTIRE family (sc-136)") {
            val session = signupAndLogin("yara@example.com")
            val oldRefresh = session.refreshCookie.value

            // First refresh consumes the login token and issues its family child.
            val first = refresh(oldRefresh)
            first.status shouldBe 200
            val newRefresh = first.refreshCookie().value

            // Repeating the OLD (now consumed) token is a reuse signal → 401.
            val replay = refresh(oldRefresh)
            replay.status shouldBe 401

            // sc-136: reuse tears down the whole family, so the fresh child token
            // (same family) has been revoked along with it.
            val childAfterReuse = refresh(newRefresh)
            childAfterReuse.status shouldBe 401
            childAfterReuse.json()["code"].asString() shouldBe "invalid_credentials"
        }

        test("refresh with an unknown/garbage refresh cookie is rejected with 401") {
            val resp = refresh("not-a-real-refresh-token")

            resp.status shouldBe 401
            resp.json()["code"].asString() shouldBe "invalid_credentials"
        }

        test("concurrent refresh with the same cookie yields exactly one 200, one 401, and at most one live row") {
            val session = signupAndLogin("nora@example.com")
            val shared = session.refreshCookie.value

            // Two threads fire the SAME refresh cookie at the same time, so both
            // may read the live row before either consumes it — there is no
            // guaranteed ordering between them. Exactly one may win the rotation.
            val start = CyclicBarrier(2)
            val pool = Executors.newFixedThreadPool(2)
            val statuses = try {
                (1..2).map {
                    pool.submit(Callable { start.await(); runBlocking { refresh(shared).status } })
                }.map { it.get(30, TimeUnit.SECONDS) }
            } finally {
                pool.shutdownNow()
            }

            // The atomic consume-and-rotate gate guarantees exactly ONE winner.
            statuses.count { it == 200 } shouldBe 1
            // The loser is rejected — either cleanly (the conditional consume
            // reported the token already claimed) or via sc-136 reuse detection.
            statuses.count { it == 401 } shouldBe 1

            // The core rotation invariant: NEVER more than one live token row can
            // survive for this account — a second live token must never be minted
            // from a single-use refresh token. (Under sc-136, a loser that reaches
            // the reuse path additionally tears down the whole family, which can
            // reduce live rows to ZERO — that is the theft response, not a second
            // token. The hard guarantee is the upper bound of one live row.)
            val liveRows = dbRows(
                "SELECT token_hash FROM refresh_tokens WHERE account_id = ? AND consumed_at IS NULL",
                UUID.fromString(session.accountId),
            )
            (liveRows.size <= 1) shouldBe true
        }

        test("refresh with an expired refresh cookie is rejected with 401 and revoked (negative path)") {
            val session = signupAndLogin("jules@example.com")
            val expired = session.refreshCookie.value

            // Manually back-date the stored row to the past to simulate expiry.
            dbUpdate(
                "UPDATE refresh_tokens SET expires_at = now() - interval '1 second' WHERE token_hash = ?",
                sha256Hex(expired),
            )

            val resp = refresh(expired)
            resp.status shouldBe 401
            resp.json()["code"].asString() shouldBe "invalid_credentials"

            // Expired handling revokes the row so it cannot be replayed.
            dbRows("SELECT 1 FROM refresh_tokens WHERE token_hash = ?", sha256Hex(expired))
                .isEmpty() shouldBe true
        }

        test("refresh without a refresh cookie returns 400") {
            val resp = post("/v1/auth/refresh")

            resp.status shouldBe 400
            resp.json()["code"].asString() shouldBe "invalid_input"
        }
    }

    private fun decodeJwtPayload(jwt: String): String {
        val payload = jwt.split(".")[1]
        val padded = payload + "=".repeat((4 - payload.length % 4) % 4)
        return String(java.util.Base64.getUrlDecoder().decode(padded))
    }
}
