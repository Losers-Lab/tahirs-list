package com.tahirslist.mcp

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication

class McpOriginTest : FunSpec({

    val config = testMcpConfig(allowedOrigins = setOf("http://localhost:6274"))

    test("allowsRequestsWithoutOrigin") {
        testApplication {
            application { installTestMcp(config = config) }

            client.mcpPost(INITIALIZE_REQUEST).status shouldBe HttpStatusCode.OK
        }
    }

    test("allowsListedOrigin") {
        testApplication {
            application { installTestMcp(config = config) }

            client.mcpPost(INITIALIZE_REQUEST, origin = "http://localhost:6274").status shouldBe
                HttpStatusCode.OK
        }
    }

    test("postToAllowedOriginCarriesCorsHeader") {
        testApplication {
            application { installTestMcp(config = config) }

            val response = client.mcpPost(INITIALIZE_REQUEST, origin = "http://localhost:6274")

            response.headers[HttpHeaders.AccessControlAllowOrigin] shouldBe "http://localhost:6274"
            response.headers[HttpHeaders.Vary] shouldContain "Origin"
        }
    }

    test("preflightForListedOriginIsAllowed") {
        testApplication {
            application { installTestMcp(config = config) }

            val response = client.mcpOptions(origin = "http://localhost:6274")

            response.status shouldBe HttpStatusCode.NoContent
            response.headers[HttpHeaders.AccessControlAllowOrigin] shouldBe "http://localhost:6274"
            response.headers[HttpHeaders.AccessControlAllowMethods] shouldContain "POST"
        }
    }

    test("preflightForDisallowedOriginIsForbidden") {
        testApplication {
            application { installTestMcp(config = config) }

            client.mcpOptions(origin = "http://evil.example").status shouldBe
                HttpStatusCode.Forbidden
        }
    }

    test("rejectsDisallowedOriginWith403") {
        testApplication {
            application { installTestMcp(config = config) }

            client.mcpPost(INITIALIZE_REQUEST, origin = "http://evil.example").status shouldBe
                HttpStatusCode.Forbidden
        }
    }

    test("emptyAllowListRejectsAnyPresentOrigin") {
        testApplication {
            application { installTestMcp(config = testMcpConfig(allowedOrigins = emptySet())) }

            client.mcpPost(INITIALIZE_REQUEST, origin = "http://localhost:6274").status shouldBe
                HttpStatusCode.Forbidden
        }
    }
})
