package com.tahirslist.bootstrap

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/**
 * sc-134 security headers + error containment at the HTTP edge, re-pinned on the
 * Ktor stack.
 *
 * Security headers (finding #8): nosniff, frame-deny, the CSP — and now HSTS —
 * are applied to every response by the web plugin pipeline (including responses
 * the deny guard rejects), and the uniform cache policy is `no-store`.
 *
 * HSTS is asserted here because the app is only ever reachable over TLS (the
 * Cloudflare tunnel terminates it), so pinning browsers to HTTPS for a year is
 * safe to do unconditionally; the plain-HTTP in-process test transport simply
 * carries the header like any other.
 *
 * Error containment (finding #6): the manual credential validation that replaced
 * bean validation must still answer 400 with the generic `invalid_input`
 * envelope *and* the per-field detail — never a 500, never a raw exception.
 */
class SecurityHeadersTest : KtorPostgresTest() {

    init {
        test("health internals are not exposed: the liveness payload carries only the status") {
            // Spring's actuator used to gate this with management.endpoint.health
            // .show-details=never. Ktor has no component-health surface at all:
            // the only health payload is `{"status":"ok"}`.
            val resp = get("/v1/health")

            resp.status shouldBe 200
            resp.json().keys shouldBe setOf("status")
        }

        test("public /v1/health response carries baseline edge security headers") {
            val resp = get("/v1/health")

            resp.status shouldBe 200
            resp.header("X-Content-Type-Options") shouldBe "nosniff"
            // API serves no embedded pages: deny frames outright, never 'self'.
            resp.header("Content-Security-Policy")!! shouldContain "frame-ancestors 'none'"
            // TLS is terminated by the tunnel; HSTS pins the browser to it.
            resp.header("Strict-Transport-Security") shouldBe "max-age=31536000; includeSubDomains"
        }

        test("responses default to a no-store cache policy") {
            val resp = get("/v1/health")

            resp.header("Cache-Control") shouldBe "no-store"
        }

        test("frame embedding is denied on the public route") {
            val resp = get("/v1/health")

            val frameOptions = resp.header("X-Frame-Options").orEmpty()
            val csp = resp.header("Content-Security-Policy").orEmpty()
            (frameOptions.isNotBlank() || csp.contains("frame-ancestors 'none'")) shouldBe true
        }

        test("security headers are present even on a rejected (401) request") {
            val resp = get("/v1/me")

            resp.status shouldBe 401
            resp.header("X-Content-Type-Options") shouldBe "nosniff"
            resp.header("Content-Security-Policy")!! shouldContain "frame-ancestors 'none'"
            resp.header("Strict-Transport-Security") shouldBe "max-age=31536000; includeSubDomains"
            resp.header("Cache-Control") shouldBe "no-store"
        }

        test("empty-field login stays 400 invalid_input with field detail, not a 500") {
            // Empty-string email/password fails the server-side credential rules.
            val resp = post(
                "/v1/auth/login",
                json = linkedMapOf("email" to "", "password" to ""),
            )

            resp.status shouldBe 400
            resp.body shouldContain "\"code\":\"invalid_input\""
            resp.body shouldContain "email is required"
            resp.body shouldContain "password is required"
        }

        test("signup empty-password validation stays 400 invalid_input, not a 500") {
            val resp = post(
                "/v1/auth/signup",
                json = linkedMapOf("email" to "x@example.com", "password" to ""),
            )

            resp.status shouldBe 400
            resp.body shouldContain "\"code\":\"invalid_input\""
            resp.body shouldContain "password is required"
        }
    }
}
