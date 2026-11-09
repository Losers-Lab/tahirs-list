package com.tahirslist.bootstrap.listing

import com.tahirslist.application.listing.SearchSettings
import com.tahirslist.bootstrap.KtorPostgresTest
import com.tahirslist.domain.restaurant.CoordinatePrecision
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * SC-199 wiring proof (QA finding F5): the configured search settings reach the
 * running route, not just the Koin definition. Boots the real application with a
 * 3 dp precision and a non-default window, so a hardcoded default would fail.
 */
class ListingSearchWiringTest : KtorPostgresTest(
    searchSettings = SearchSettings(
        centrePrecision = CoordinatePrecision.THREE_DECIMALS,
        cacheMaxAgeSeconds = 30,
        sMaxAgeSeconds = 120,
        staleWhileRevalidateSeconds = 30,
    ),
) {

    init {
        test("the configured precision and window reach the search route (F5)") {
            val response = get("/v1/listings/search?center=43.682921,-79.418493&radius=5.0")

            response.status shouldBe 200
            response.header("Cache-Control") shouldBe
                "public, max-age=30, s-maxage=120, stale-while-revalidate=30"

            // Both fall in one 3 dp basin (43.683) but in different 4 dp basins
            // (43.6829 vs 43.6830), so equal bodies prove the 3 dp setting is live.
            val lowSide = get("/v1/listings/search?center=43.68294,-79.41849&radius=5.0")
            val highSide = get("/v1/listings/search?center=43.68295,-79.41849&radius=5.0")
            highSide.body shouldBe lowSide.body

            // A different 3 dp basin still answers differently.
            val otherBasin = get("/v1/listings/search?center=43.6900,-79.4200&radius=5.0")
            otherBasin.body shouldNotBe lowSide.body
        }
    }
}
