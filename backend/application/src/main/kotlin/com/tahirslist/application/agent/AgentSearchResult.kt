package com.tahirslist.application.agent

import com.tahirslist.application.listing.ListingSearchResult

/**
 * One page of search hits for the agent surface.
 *
 * [count] is the number of hits on this page (the persistence port does not
 * return a total), and [nextOffset] is the offset to request next when the page
 * is full; null means this was the last page.
 */
data class AgentSearchResult(
    val results: List<ListingSearchResult>,
    val count: Int,
    val nextOffset: Int?,
) {
    companion object {
        fun of(request: AgentSearchRequest, results: List<ListingSearchResult>): AgentSearchResult =
            AgentSearchResult(
                results = results,
                count = results.size,
                nextOffset = if (results.size == request.limit) request.offset + results.size else null,
            )
    }
}
