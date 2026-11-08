package com.tahirslist.bootstrap

import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * AC-1 / AC-6 at the real composition root: the MCP endpoint needs no JWT, and a
 * tool call reaches PostGIS and returns data.
 */
class McpPublicAccessTest : KtorPostgresTest() {

    init {
        test("initializeAndToolsCallNeedNoJwt") {
            val initialize = post("/mcp", jsonText = MCP_INITIALIZE)
            initialize.status shouldBe 200
            initialize.json()["result"]!!.jsonObject["serverInfo"]!!.jsonObject["name"]!!
                .jsonPrimitive.content shouldBe "tahirs-list"

            val search = mcpCall("search_restaurants", searchArguments())
            search.status shouldBe 200
            search.mcpResult()["isError"]?.jsonPrimitive?.booleanOrNull shouldBe false
            search.mcpStructured()["results"]!!.jsonArray.shouldNotBeEmpty()
        }
    }
}
