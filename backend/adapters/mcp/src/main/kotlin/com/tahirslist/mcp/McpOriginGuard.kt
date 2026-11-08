package com.tahirslist.mcp

/**
 * Origin policy for the public MCP endpoint.
 *
 * Non-browser agents send no `Origin` and must always work. A browser client
 * (MCP Inspector, a web connector) must name an origin from the configured
 * allow-list; anything else is refused with 403 before any tool work. Matching is
 * exact — scheme, host and port all count — because the SDK helper's own check
 * compares hostnames only, which is too weak for this contract.
 */
class McpOriginGuard(allowedOrigins: Set<String>) {

    private val allowed: Set<String> = allowedOrigins
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toSet()

    fun allows(origin: String?): Boolean = origin == null || origin in allowed
}
