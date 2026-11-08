package com.tahirslist.application.agent

import com.tahirslist.application.geo.GeocodedPlace
import com.tahirslist.application.geo.GeocoderPort
import com.tahirslist.application.listing.ListingSearchQuery
import com.tahirslist.domain.restaurant.LatLng

/**
 * UC-01 for the agent channel: resolve the requested place (if any) to a search
 * centre, then run the existing public search port. Read-only; no transaction is
 * opened here — the persistence adapter owns its own read-only transaction.
 *
 * Geocoder semantics come straight from [GeocoderPort]: null = "no match"
 * ([PlaceNotResolvedException]); a thrown
 * [com.tahirslist.application.geo.GeocodingException] = provider unavailable and
 * propagates so the adapter can give the caller the distinct, actionable advice.
 */
class SearchListingsForAgent(
    private val search: ListingSearchQuery,
    private val geocoder: GeocoderPort,
) {
    suspend fun execute(request: AgentSearchRequest): AgentSearchResult {
        val center: LatLng = when (val location = request.location) {
            is SearchLocation.Coordinates -> location.center
            is SearchLocation.Place -> geocoder.geocode(location.text)?.location
                ?: throw PlaceNotResolvedException()
        }
        val results = search.searchNearby(
            center = center,
            radiusMiles = request.radiusMiles,
            filters = request.filters,
            offset = request.offset,
            limit = request.limit,
        )
        return AgentSearchResult.of(request, results)
    }
}
