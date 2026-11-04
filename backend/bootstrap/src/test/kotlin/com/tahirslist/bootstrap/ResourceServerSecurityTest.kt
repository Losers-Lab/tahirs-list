package com.tahirslist.bootstrap

import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.http.HttpMethod

/**
 * sc-131 deny-by-default resource server: RS256 + iss + exp + role-claim
 * verification at the HTTP edge. The application boots with a fixed test RSA
 * key (read from the `test-jwt-rsa-private.pem` test resource) so the test can
 * mint adversarial tokens with the matching private key, and every non-permitted
 * route requires a valid token.
 *
 * Negative paths (mandatory per docs/qa/auth-qa-assessment-and-testing-conventions.md,
 * Omar gate #8): expired, tampered, wrong-issuer, missing-role, and malformed
 * tokens must all yield a generic 401.
 */
class ResourceServerSecurityTest : KtorPostgresTest() {

    private suspend fun authenticatedGet(path: String, jwt: String) = get(path, token = jwt)

    init {
        test("an unauthenticated request to a protected route returns a generic 401") {
            val resp = get("/v1/me")

            resp.status shouldBe 401
            resp.json()["code"].asString() shouldBe "unauthorized"
        }

        test("a valid RS256 token with correct iss/exp/role is accepted") {
            val jwt = TestJwt.mint(role = "USER", sub = "2f3c1f9a-3f4b-4f6f-8bf7-2f8b1d3a9f5e", expSecondsAhead = 3600)

            val resp = authenticatedGet("/v1/me", jwt)

            resp.status shouldBe 200
            resp.json()["subjectId"].asString() shouldBe "2f3c1f9a-3f4b-4f6f-8bf7-2f8b1d3a9f5e"
            resp.json()["role"].asString() shouldBe "USER"
        }

        test("an expired token is rejected with a generic 401") {
            val jwt = TestJwt.mint(role = "USER", sub = "2f3c1f9a-3f4b-4f6f-8bf7-2f8b1d3a9f5e", expSecondsAhead = -60)

            val resp = authenticatedGet("/v1/me", jwt)

            resp.status shouldBe 401
            resp.json()["code"].asString() shouldBe "unauthorized"
        }

        test("a tampered token is rejected with a generic 401") {
            val jwt = TestJwt.mint(role = "USER", sub = "2f3c1f9a-3f4b-4f6f-8bf7-2f8b1d3a9f5e", expSecondsAhead = 3600)

            val resp = authenticatedGet("/v1/me", TestJwt.tamper(jwt))

            resp.status shouldBe 401
            resp.json()["code"].asString() shouldBe "unauthorized"
        }

        test("a token with the wrong issuer is rejected with a generic 401") {
            val jwt = TestJwt.mint(
                role = "USER",
                sub = "2f3c1f9a-3f4b-4f6f-8bf7-2f8b1d3a9f5e",
                issuer = "evil-issuer",
                expSecondsAhead = 3600,
            )

            val resp = authenticatedGet("/v1/me", jwt)

            resp.status shouldBe 401
            resp.json()["code"].asString() shouldBe "unauthorized"
        }

        test("a token missing the role claim is rejected with a generic 401") {
            val jwt = TestJwt.mint(role = null, sub = "2f3c1f9a-3f4b-4f6f-8bf7-2f8b1d3a9f5e", expSecondsAhead = 3600)

            val resp = authenticatedGet("/v1/me", jwt)

            resp.status shouldBe 401
            resp.json()["code"].asString() shouldBe "unauthorized"
        }

        test("a malformed / non-JWT token is rejected with a generic 401") {
            val resp = authenticatedGet("/v1/me", "not-a-jwt")

            resp.status shouldBe 401
            resp.json()["code"].asString() shouldBe "unauthorized"
        }

        test("public routes remain reachable without a token") {
            get("/v1/health").status shouldBe 200
        }

        test("an unknown path is denied by default (401), not exposed as a 404") {
            // Deny-by-default is structural: even unregistered paths require a
            // token, so route enumeration cannot distinguish "missing" from
            // "protected" without credentials. A valid token then gets 404.
            val anonymous = get("/v1/this-does-not-exist")
            anonymous.status shouldBe 401
            anonymous.json()["code"].asString() shouldBe "unauthorized"

            val jwt = TestJwt.mint(role = "USER", sub = "2f3c1f9a-3f4b-4f6f-8bf7-2f8b1d3a9f5e", expSecondsAhead = 3600)
            val authenticated = authenticatedGet("/v1/this-does-not-exist", jwt)
            authenticated.status shouldBe 404
            authenticated.json()["code"].asString() shouldBe "not_found"
        }

        test("a wrong method on a known protected path is a 404 envelope once authenticated (no Spring 405)") {
            val jwt = TestJwt.mint(role = "USER", sub = "2f3c1f9a-3f4b-4f6f-8bf7-2f8b1d3a9f5e", expSecondsAhead = 3600)

            // DELETE /v1/me has no handler: unauthenticated -> 401 (deny guard),
            // authenticated -> 404 not_found (no route matched).
            delete("/v1/me").status shouldBe 401

            val authenticated = call(HttpMethod.Delete, "/v1/me", token = jwt)
            authenticated.status shouldBe 404
            authenticated.json()["code"].asString() shouldBe "not_found"
        }

        test("a wrong method on a known public path is a 404 envelope") {
            // GET /v1/listings/{id}/image/{unknown-sub-resource} stays public, so
            // the unmatched route is answered by the status-pages 404 envelope.
            val resp = get("/v1/listings/2f3c1f9a-3f4b-4f6f-8bf7-2f8b1d3a9f5e/unknown")

            resp.status shouldBe 404
            resp.json()["code"].asString() shouldBe "not_found"
        }

        test("path-normalisation variants of a protected route cannot bypass the guard") {
            // The guard compares the RAW request path (no dot-segment folding) and
            // Ktor decodes per segment, so these must all stay closed: a 401 when
            // the guard sees a protected target, or a 404 when the router cannot
            // match it — never a 200.
            val variants = listOf(
                "/v1/me/",
                "/v1/me;x",
                "/V1/ME",
                "//v1/me",
                "/v1/listings/../v1/me",
                "/v1/listings/%2e%2e/v1/me",
                "/v1%2Fme",
                "/v1/listings/..%2F..%2Fv1%2Fme",
            )

            variants.forEach { path ->
                val resp = get(path)

                resp.status shouldBeIn setOf(400, 401, 404)
                resp.status shouldNotBe 200
            }
        }
    }
}
