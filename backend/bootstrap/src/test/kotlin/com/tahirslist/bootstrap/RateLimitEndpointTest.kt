package com.tahirslist.bootstrap

import com.tahirslist.bootstrap.config.RateLimitSettings
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank

/**
 * sc-136 rate limiting (end-to-end) on the Ktor stack. A deliberately tiny,
 * per-spec token bucket (`capacity=2`) is passed through [AppConfig] so the 429s
 * are asserted deterministically against the real HTTP stack — proving the gate
 * short-circuits before the auth use case runs and returns the generic,
 * non-informative envelope. Budgets are per in-process client IP + route.
 *
 * X-Forwarded-For trust is opted in here (the production default is
 * remoteAddr-only) because these tests simulate distinct client IPs; the
 * default path is covered by the web-api unit tests.
 */
class RateLimitEndpointTest : KtorPostgresTest(
    rateLimit = RateLimitSettings(
        capacity = 2,
        refillTokens = 2,
        refillWindowSeconds = 60,
        trustProxy = true,
        maxKeys = 1_000,
    ),
) {

    private suspend fun loginRequest(email: String, password: String) =
        post("/v1/auth/login", json = linkedMapOf("email" to email, "password" to password))

    private suspend fun refresh(refreshToken: String, forwardedFor: String? = null) =
        post(
            "/v1/auth/refresh",
            cookies = mapOf("refresh_token" to refreshToken),
            extraHeaders = forwardedFor?.let { mapOf("X-Forwarded-For" to it) } ?: emptyMap(),
        )

    init {
        test("login rate-limit kicks in after the burst capacity is exhausted (429, generic envelope)") {
            // The first two login attempts fill the tiny (capacity=2) bucket.
            repeat(2) {
                loginRequest("cap@example.com", "wrong").status shouldBe 401
            }

            // The third attempt from the same client+route is rate-limited.
            val throttled = loginRequest("cap@example.com", "wrong")
            throttled.status shouldBe 429
            throttled.code() shouldBe "rate_limited"
            throttled.message().orEmpty().shouldNotBeBlank()
        }

        test("refresh rate-limit kicks in after its own burst capacity (separate route bucket)") {
            // The refresh route has its own bucket, so login starvation (above)
            // does not bleed into it: these two refresh attempts pass the gate
            // (401 from auth, not 429 from the limiter).
            repeat(2) {
                refresh("never-issued-token").status shouldBe 401
            }

            val throttled = refresh("never-issued-token")
            throttled.status shouldBe 429
            throttled.code() shouldBe "rate_limited"
        }

        test("signup rate-limit kicks in after its own burst capacity (429, generic envelope)") {
            // Signup keys its own bucket (the route is part of the key), so the
            // login exhaustion above does not pre-throttle it: the first two
            // account creations succeed...
            repeat(2) { index ->
                post(
                    "/v1/auth/signup",
                    json = linkedMapOf(
                        "email" to "cap-signup-$index@example.com",
                        "password" to TEST_PASSWORD,
                    ),
                ).status shouldBe 201
            }

            // ...and the third from the same client+route is refused before the
            // account is created, so Argon2 is no longer an unthrottled sink.
            val throttled = post(
                "/v1/auth/signup",
                json = linkedMapOf("email" to "cap-signup-3@example.com", "password" to TEST_PASSWORD),
            )
            throttled.status shouldBe 429
            throttled.code() shouldBe "rate_limited"
            throttled.message().orEmpty().shouldNotBeBlank()
        }

        test("a different client IP is not throttled by another's exhaustion") {
            val first = refresh("tok", forwardedFor = "203.0.113.7")
            val second = refresh("tok", forwardedFor = "203.0.113.8")

            // 401 (auth) for both — neither was rate-limited despite the cap.
            first.status shouldBe 401
            second.status shouldBe 401
        }
    }
}
