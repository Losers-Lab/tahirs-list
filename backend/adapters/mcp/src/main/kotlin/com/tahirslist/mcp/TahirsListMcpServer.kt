package com.tahirslist.mcp

import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities

/**
 * Builds the MCP [Server] for one stateless request: identity, tools-only
 * capability, and the two registered tools. A fresh instance per request keeps
 * the endpoint stateless (no shared session registry).
 */
class TahirsListMcpServer(
    private val useCases: McpUseCases,
    private val config: McpConfig,
    private val gate: McpCallGate = McpCallGate { true },
) {
    fun createServer(): Server {
        val search = SearchRestaurantsTool(useCases.search, config, gate)
        val detail = GetRestaurantTool(useCases.detail, config, gate)

        return Server(
            serverInfo = Implementation(name = SERVER_NAME, version = SERVER_VERSION),
            options = ServerOptions(
                capabilities = ServerCapabilities(
                    tools = ServerCapabilities.Tools(listChanged = false),
                ),
            ),
        ) {
            addTool(search.definition) { request -> search.call(request.params.arguments) }
            addTool(detail.definition) { request -> detail.call(request.params.arguments) }
        }
    }

    companion object {
        const val SERVER_NAME: String = "tahirs-list"

        /** Advertised implementation version; bumped with the application release. */
        const val SERVER_VERSION: String = "0.1.0"
    }
}
