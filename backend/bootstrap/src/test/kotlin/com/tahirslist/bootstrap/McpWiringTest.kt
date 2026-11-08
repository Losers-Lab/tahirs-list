package com.tahirslist.bootstrap

import io.kotest.matchers.shouldBe

/** AC-6 wiring: enabled mounts a public POST endpoint; disabled is a plain 404. */
class McpEnabledWiringTest : KtorPostgresTest() {

    init {
        test("endpointIsPublicAndMounted") {
            post("/mcp", jsonText = MCP_INITIALIZE).status shouldBe 200
            // POST-only: no SSE stream and no session to delete.
            get("/mcp").status shouldBe 405
        }
    }
}

class McpDisabledWiringTest : KtorPostgresTest(mcpSettings = DEFAULT_MCP.copy(enabled = false)) {

    init {
        test("endpointIs404WhenDisabled") {
            post("/mcp", jsonText = MCP_INITIALIZE).status shouldBe 404
        }
    }
}
