package com.tahirslist.application.agent

import com.tahirslist.application.listing.ListingSearchFilters

/**
 * A validated search request as the MCP channel sees it. Validation/normalisation
 * happens at the edge ([com.tahirslist.application.listing.ListingSearchRequestParser]);
 * this type carries only values that already satisfy the public contract.
 *
 * [limit] is 1..100 (default 20) and [offset] is >= 0 for the agent surface.
 */
data class AgentSearchRequest(
    val location: SearchLocation,
    val radiusMiles: Double,
    val filters: ListingSearchFilters = ListingSearchFilters(),
    val offset: Int = 0,
    val limit: Int = 20,
)
