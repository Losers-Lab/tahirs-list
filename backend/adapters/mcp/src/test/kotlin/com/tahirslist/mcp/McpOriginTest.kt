package com.tahirslist.mcp

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
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
