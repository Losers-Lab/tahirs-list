package com.tahirslist.mcp

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

class McpEndpointTest : FunSpec({

    test("toolsCallOverStreamableHttp") {
        testApplication {
            application { installTestMcp() }

            val response = client.mcpPost(
                toolCallRequest(
                    name = "search_restaurants",
                    arguments = """{"center":{"lat":1,"lng":2},"radiusMiles":5}""",
                ),
            )
            val body = response.jsonBody()

            response.status shouldBe HttpStatusCode.OK
            body.structuredContent()["count"]!!.jsonPrimitive.int shouldBe 1
            body.structuredContent()["results"]!!.jsonArray shouldHaveSize 1
        }
    }

    test("statelessConcurrentCallsSucceed") {
        testApplication {
            application { installTestMcp() }

            val statuses = coroutineScope {
                (1..10).map {
                    async { client.mcpPost(INITIALIZE_REQUEST).status }
                }.awaitAll()
            }

            statuses.forEach { it shouldBe HttpStatusCode.OK }
        }
    }

    test("getRestaurantToolOverHttp") {
        testApplication {
            application { installTestMcp() }

            val response = client.mcpPost(
                toolCallRequest(
                    name = "get_restaurant",
                    arguments = """{"id":"11111111-2222-3333-4444-555555555555"}""",
                ),
            )
            val payload = response.jsonBody().structuredContent()

            response.status shouldBe HttpStatusCode.OK
            payload["name"]!!.jsonPrimitive.content shouldBe "Halal Grill"
        }
    }
})
