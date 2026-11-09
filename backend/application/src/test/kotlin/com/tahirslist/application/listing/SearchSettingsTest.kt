package com.tahirslist.application.listing

import com.tahirslist.domain.restaurant.CoordinatePrecision
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class SearchSettingsTest : FunSpec({

    test("defaults are 4 dp, max-age 60, s-maxage 300, stale-while-revalidate 60 (SC-199 D-1, D-2)") {
        val settings = SearchSettings()
        settings.centrePrecision shouldBe CoordinatePrecision.FOUR_DECIMALS
        settings.cacheMaxAgeSeconds shouldBe 60
        settings.sMaxAgeSeconds shouldBe 300
        settings.staleWhileRevalidateSeconds shouldBe 60
    }

    test("the advertised cache policy names every directive") {
        SearchSettings().cacheControl() shouldBe
            "public, max-age=60, s-maxage=300, stale-while-revalidate=60"
    }

    test("a window whose s-maxage plus stale-while-revalidate exceeds 600 s is rejected (INV-10)") {
        shouldThrow<IllegalArgumentException> {
            SearchSettings(sMaxAgeSeconds = 500, staleWhileRevalidateSeconds = 101)
        }
    }

    test("a window of exactly 600 s is accepted (INV-10 boundary)") {
        SearchSettings(sMaxAgeSeconds = 540, staleWhileRevalidateSeconds = 60).sMaxAgeSeconds shouldBe 540
    }

    test("negative ages are rejected") {
        shouldThrow<IllegalArgumentException> { SearchSettings(cacheMaxAgeSeconds = -1) }
        shouldThrow<IllegalArgumentException> { SearchSettings(sMaxAgeSeconds = -1) }
        shouldThrow<IllegalArgumentException> { SearchSettings(staleWhileRevalidateSeconds = -1) }
    }
})
