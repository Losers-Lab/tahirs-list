package com.tahirslist.application.agent

import com.tahirslist.application.geo.GeocodingException
import com.tahirslist.application.geo.GeocoderPort
import com.tahirslist.application.listing.ListingSearchFilters
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class SearchListingsForAgentTest : FunSpec({

    test("geocodesPlaceThenSearches") {
        val geocoder = RecordingGeocoder(place(lat = 45.0, lng = -75.0))
        val query = RecordingSearchQuery()
        val search = SearchListingsForAgent(query, geocoder)

        val result = search.execute(
            AgentSearchRequest(
                location = SearchLocation.Place("  Ottawa  "),
                radiusMiles = 10.0,
                limit = 5,
            ),
        )

        geocoder.queried shouldBe "  Ottawa  "
        query.center shouldBe com.tahirslist.domain.restaurant.LatLng(45.0, -75.0)
        query.radiusMiles shouldBe 10.0
        query.limit shouldBe 5
        result.count shouldBe 1
    }

    test("coordinatesSkipGeocoding") {
        val geocoder = RecordingGeocoder(place())
        val query = RecordingSearchQuery()
        val search = SearchListingsForAgent(query, geocoder)

        search.execute(
            AgentSearchRequest(
                location = SearchLocation.Coordinates(com.tahirslist.domain.restaurant.LatLng(1.0, 2.0)),
                radiusMiles = 3.0,
            ),
        )

        geocoder.queried.shouldBeNull()
        query.center shouldBe com.tahirslist.domain.restaurant.LatLng(1.0, 2.0)
    }

    test("placeNotResolvedIsActionable") {
        val query = RecordingSearchQuery()
        val search = SearchListingsForAgent(query, RecordingGeocoder(null))

        val failure = shouldThrow<PlaceNotResolvedException> {
            search.execute(
                AgentSearchRequest(SearchLocation.Place("Nowhere"), radiusMiles = 1.0),
            )
        }

        failure.message shouldBe
            "No place matched that text. Try a nearby city, or supply coordinates."
        query.center.shouldBeNull()
    }

    test("geocoderOutageIsDistinctAndActionable") {
        val query = RecordingSearchQuery()
        val outage = object : GeocoderPort {
            override suspend fun geocode(address: String): com.tahirslist.application.geo.GeocodedPlace? =
                throw GeocodingException("provider down")
        }
        val search = SearchListingsForAgent(query, outage)

        shouldThrow<GeocodingException> {
            search.execute(AgentSearchRequest(SearchLocation.Place("Toronto"), radiusMiles = 1.0))
        }
        query.center.shouldBeNull()
    }

    test("computesNextOffsetOnlyWhenPageFull") {
        val full = RecordingSearchQuery(List(20) { sampleResult(name = "R$it") })
        val partial = RecordingSearchQuery(listOf(sampleResult()))
        val request = AgentSearchRequest(
            SearchLocation.Coordinates(com.tahirslist.domain.restaurant.LatLng(1.0, 2.0)),
            radiusMiles = 5.0,
            offset = 40,
            limit = 20,
        )

        SearchListingsForAgent(full, RecordingGeocoder(null)).execute(request).nextOffset shouldBe 60
        SearchListingsForAgent(partial, RecordingGeocoder(null)).execute(request).nextOffset.shouldBeNull()
    }

    test("emptyResultIsSuccessWithCountZero") {
        val result = SearchListingsForAgent(
            RecordingSearchQuery(emptyList()),
            RecordingGeocoder(null),
        ).execute(
            AgentSearchRequest(SearchLocation.Coordinates(com.tahirslist.domain.restaurant.LatLng(1.0, 2.0)), 1.0),
        )

        result.results shouldHaveSize 0
        result.count shouldBe 0
    }

    test("filtersArePassedThroughUnchanged") {
        val recording = RecordingFiltersQuery()
        val filters = ListingSearchFilters(handCutOnly = true, deliveryOnly = true)
        SearchListingsForAgent(recording, RecordingGeocoder(null)).execute(
            AgentSearchRequest(
                location = SearchLocation.Coordinates(com.tahirslist.domain.restaurant.LatLng(1.0, 2.0)),
                radiusMiles = 2.0,
                filters = filters,
            ),
        )
        recording.filters shouldBe filters
    }

    test("notFoundExceptionIsAnExceptionType") {
        AgentListingNotFoundException().shouldBeInstanceOf<Exception>()
    }
})

private class RecordingFiltersQuery : com.tahirslist.application.listing.ListingSearchQuery {
    var filters: ListingSearchFilters? = null

    override suspend fun searchNearby(
        center: com.tahirslist.domain.restaurant.LatLng,
        radiusMiles: Double,
        filters: ListingSearchFilters,
        offset: Int,
        limit: Int,
    ): List<com.tahirslist.application.listing.ListingSearchResult> {
        this.filters = filters
        return emptyList()
    }
}
