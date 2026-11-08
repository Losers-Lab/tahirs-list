package com.tahirslist.mcp

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class McpDiscoveryTest : FunSpec({

    test("initializeWithoutAuthSucceeds") {
        testApplication {
            application { installTestMcp() }

            val response = client.mcpPost(INITIALIZE_REQUEST)
            val body = response.jsonBody()

            if (response.status != HttpStatusCode.OK) {
                error("status=${response.status} body=${response.bodyAsText()}")
            }
            body.result()["serverInfo"]!!.jsonObject["name"]!!.jsonPrimitive.content shouldBe "tahirs-list"
            body.result()["protocolVersion"]!!.jsonPrimitive.content shouldBe "2025-06-18"
        }
    }

    test("listsExactlyTwoTools") {
        testApplication {
            application { installTestMcp() }

            val response = client.mcpPost(toolsListRequest())
            val body = response.jsonBody()

            response.status shouldBe HttpStatusCode.OK
            body.toolNames() shouldContainExactlyInAnyOrder listOf("search_restaurants", "get_restaurant")
            // MCP serialization must not emit explicit nulls for optional fields.
            kotlinx.serialization.json.Json.parseToJsonElement(response.bodyAsText())
                .toString().contains(":null") shouldBe false
        }
    }

    test("advertisesInputAndOutputSchemas") {
        testApplication {
            application { installTestMcp() }

            val tools = client.mcpPost(toolsListRequest()).jsonBody()
                .result()["tools"]!!.jsonArray.map { it.jsonObject }

            tools.forEach { tool ->
                tool["inputSchema"]!!.jsonObject["type"]!!.jsonPrimitive.content shouldBe "object"
                tool["outputSchema"]!!.jsonObject["type"]!!.jsonPrimitive.content shouldBe "object"
            }

            // The search card schema must describe every field the mapper emits,
            // including the address parts and the genuinely nullable tri-state.
            val search = tools.first { it["name"]!!.jsonPrimitive.content == "search_restaurants" }
            val items = search["outputSchema"]!!.jsonObject["properties"]!!.jsonObject["results"]!!
                .jsonObject["items"]!!.jsonObject["properties"]!!.jsonObject

            items.keys shouldContainAll listOf(
                "id", "name", "address", "city", "province", "postal", "country",
                "lat", "lng", "distanceMiles", "cuisine", "isHandCut", "halalScope",
                "isDelivery", "verificationStatus", "rating", "websiteUrl",
            )
            items["isHandCut"]!!.jsonObject["type"]!!.jsonArray.map { it.jsonPrimitive.content } shouldContain "null"
            items["rating"]!!.jsonObject["type"]!!.jsonArray.map { it.jsonPrimitive.content } shouldContain "null"
            // Address parts are frequently absent; the schema must not promise them.
            listOf("city", "province", "postal", "country", "cuisine", "isDelivery").forEach { name ->
                items[name]!!.jsonObject["type"]!!.jsonArray.map { it.jsonPrimitive.content } shouldContain "null"
            }

            val detail = tools.first { it["name"]!!.jsonPrimitive.content == "get_restaurant" }
            val detailProperties = detail["outputSchema"]!!.jsonObject["properties"]!!.jsonObject
            listOf("city", "province", "postal", "country", "cuisine", "isHandCut", "isDelivery").forEach { name ->
                detailProperties[name]!!.jsonObject["type"]!!.jsonArray.map { it.jsonPrimitive.content } shouldContain "null"
            }
        }
    }
})
