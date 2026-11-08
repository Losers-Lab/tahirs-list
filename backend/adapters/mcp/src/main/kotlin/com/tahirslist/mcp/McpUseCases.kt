package com.tahirslist.mcp

import com.tahirslist.application.agent.GetListingForAgent
import com.tahirslist.application.agent.SearchListingsForAgent

/** The two use cases the MCP tools bind to. */
data class McpUseCases(
    val search: SearchListingsForAgent,
    val detail: GetListingForAgent,
)
