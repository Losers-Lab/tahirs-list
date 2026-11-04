package com.tahirslist.web

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import kotlin.time.Duration.Companion.minutes

/**
 * sc-134 error handling (security finding #6): a generic global handler must
 * NEVER echo a raw exception message, and client-facing validation detail must
 * still reach the client as 400 `invalid_input`. Also pins the deny-by-default
 * 401 envelope, the unknown-route 404 envelope, and the baseline security
 * headers installed at the edge.
 */
class ErrorMappingTest : FunSpec({

    test("an unexpected exception returns generic internal_error and never leaks its raw message") {
        testApplication {
            application {
                configureWebPlugins(testJson(), testJwtConfig(), testRateLimitConfig())
                routing {
                    get("/v1/listings/boom") {
                        throw IllegalStateException("Database password 'hunter2-secret' rejected")
                    }
                }
            }

            val response = client.get("/v1/listings/boom")
            val body = response.bodyAsText()

            response.status shouldBe HttpStatusCode.InternalServerError
            body shouldContain "\"code\":\"internal_error\""
            body shouldContain "An unexpected server error occurred."
            body shouldNotContain "hunter2-secret"
            body shouldNotContain "IllegalStateException"
        }
    }

    test("field-level invalid input is answered 400 invalid_input with the safe detail") {
        testApplication {
            application {
                configureWebPlugins(testJson(), testJwtConfig(), testRateLimitConfig())
                routing {
                    get("/v1/listings/invalid") {
                        throw InvalidInputException("email is required; password is required")
                    }
                }
            }

            val response = client.get("/v1/listings/invalid")
            val body = response.bodyAsText()

            response.status shouldBe HttpStatusCode.BadRequest
            body shouldContain "\"code\":\"invalid_input\""
            body shouldContain "email is required"
            body shouldContain "password is required"
        }
    }

    test("a bare IllegalArgumentException is answered generically, without its raw message") {
        testApplication {
            application {
                configureWebPlugins(testJson(), testJwtConfig(), testRateLimitConfig())
                routing {
                    get("/v1/listings/raw") {
                        throw IllegalArgumentException("refreshToken cookie is required")
                    }
                }
            }

            val response = client.get("/v1/listings/raw")
            val body = response.bodyAsText()

            response.status shouldBe HttpStatusCode.BadRequest
            body shouldContain "\"code\":\"invalid_input\""
            body shouldContain "Invalid input."
            body shouldNotContain "refreshToken cookie is required"
        }
    }

    test("the public health route carries the baseline edge security headers and no-store cache policy") {
        testApplication {
            application {
                configureWebPlugins(testJson(), testJwtConfig(), testRateLimitConfig())
                routing { healthRoutes() }
            }

            val response = client.get("/v1/health")

            response.status shouldBe HttpStatusCode.OK
            response.headers[HttpHeaders.ContentType]?.substringBefore(";") shouldBe "application/json"
            response.headers["X-Content-Type-Options"] shouldBe "nosniff"
            response.headers["X-Frame-Options"] shouldBe "DENY"
            response.headers["Content-Security-Policy"] shouldContain "frame-ancestors 'none'"
            response.headers["Strict-Transport-Security"] shouldBe "max-age=31536000; includeSubDomains"
            response.headers[HttpHeaders.CacheControl] shouldBe "no-store"
            response.bodyAsText() shouldBe """{"status":"ok"}"""
        }
    }

    test("an unauthenticated protected route returns the generic 401 envelope") {
        testApplication {
            application {
                configureWebPlugins(testJson(), testJwtConfig(), testRateLimitConfig())
                routing { meRoutes() }
            }

            val response = client.get("/v1/me")
            val body = response.bodyAsText()

            response.status shouldBe HttpStatusCode.Unauthorized
            body shouldContain "\"code\":\"unauthorized\""
            body shouldContain "Authentication required."
        }
    }

    test("an unknown public route returns the 404 not_found envelope, not a 500") {
        testApplication {
            application {
                configureWebPlugins(testJson(), testJwtConfig(), testRateLimitConfig())
                routing { healthRoutes() }
            }

            val response = client.get("/v1/listings/unknown")
            val body = response.bodyAsText()

            response.status shouldBe HttpStatusCode.NotFound
            body shouldContain "\"code\":\"not_found\""
        }
    }
})

private fun testJson(): Json = Json {
    encodeDefaults = true
    explicitNulls = true
}

private val testPublicKey: RSAPublicKey by lazy {
    val generator = KeyPairGenerator.getInstance("RSA")
    generator.initialize(2048)
    generator.generateKeyPair().public as RSAPublicKey
}

private fun testJwtConfig(): JwtConfig = JwtConfig(issuer = "tahirs-list", publicKey = testPublicKey)

private fun testRateLimitConfig(): RateLimitConfig = RateLimitConfig(
    capacity = 20,
    refillTokens = 20,
    refillWindow = 1.minutes,
    trustProxy = false,
    maxKeys = 1_000,
)
