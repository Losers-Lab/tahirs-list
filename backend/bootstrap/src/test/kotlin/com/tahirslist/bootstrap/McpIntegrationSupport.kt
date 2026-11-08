package com.tahirslist.bootstrap

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** JSON-RPC helpers shared by the MCP integration specs. */

internal const val MCP_INITIALIZE: String =
    """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"it","version":"1.0"}}}"""

internal fun mcpToolCall(name: String, arguments: String, id: Int = 2): String =
    """{"jsonrpc":"2.0","id":$id,"method":"tools/call","params":{"name":"$name","arguments":$arguments}}"""

internal suspend fun KtorPostgresTest.mcpCall(
    toolName: String,
    arguments: String,
    origin: String? = null,
): ApiResponse = post(
    path = "/mcp",
    jsonText = mcpToolCall(toolName, arguments),
    extraHeaders = origin?.let { mapOf("Origin" to it) } ?: emptyMap(),
)

internal fun ApiResponse.mcpResult(): JsonObject = json()["result"]!!.jsonObject

internal fun ApiResponse.mcpStructured(): JsonObject =
    mcpResult()["structuredContent"]!!.jsonObject

internal fun searchArguments(
    lat: Double = 43.6532,
    lng: Double = -79.3832,
    radiusMiles: Double = 5.0,
    extra: String = "",
): String = buildString {
    append("""{"center":{"lat":$lat,"lng":$lng},"radiusMiles":$radiusMiles""")
    if (extra.isNotBlank()) {
        append(',')
        append(extra)
    }
    append('}')
}
