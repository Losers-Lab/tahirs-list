package com.tahirslist.mcp

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
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

    test("malformedJsonIsSanitized") {
        testApplication {
            application { installTestMcp() }

            val response = client.mcpPost("""{"jsonrpc":"2.0","id":1,"method":""")
            val body = response.bodyAsText()

            response.status shouldBe HttpStatusCode.BadRequest
            body shouldContain "Parse error"
            body shouldNotContain "Exception"
            body shouldNotContain "kotlin."
            body shouldNotContain "at io.modelcontextprotocol"
        }
    }

    test("unknownToolReturnsAnErrorResult") {
        testApplication {
            application { installTestMcp() }

            val response = client.mcpPost(
                toolCallRequest(name = "not_a_tool", arguments = "{}"),
            )
            val result = response.jsonBody().result()

            response.status shouldBe HttpStatusCode.OK
            result["isError"]!!.jsonPrimitive.booleanOrNull shouldBe true
            response.bodyAsText() shouldNotContain "Exception"
            response.bodyAsText() shouldNotContain "kotlin."
        }
    }

    test("missingArgumentsIsAnActionableToolError") {
        testApplication {
            application { installTestMcp() }

            val result = client.mcpPost(
                toolCallRequest(name = "search_restaurants", arguments = "{}"),
            ).jsonBody().result()

            result["isError"]!!.jsonPrimitive.booleanOrNull shouldBe true
            result["content"]!!.jsonArray.first().jsonObject["text"]!!
                .jsonPrimitive.content shouldBe "Provide exactly one of place or center."
        }
    }
})
