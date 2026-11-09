package com.tahirslist.application.listing

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import com.tahirslist.domain.restaurant.CoordinatePrecision
import com.tahirslist.domain.restaurant.LatLng
import io.kotest.matchers.shouldBe
import java.math.BigDecimal

class ListingSearchRequestParserTest : FunSpec({

    test("centre snaps the search centre before the query (SC-199 UC-01.3)") {
        ListingSearchRequestParser.centre(
            LatLng(40.71283741, -74.00601293),
            CoordinatePrecision.FOUR_DECIMALS,
        ) shouldBe LatLng(40.7128, -74.006)
    }

    test("centre leaves the radius untouched (SC-199 INV-7)") {
        ListingSearchRequestParser.radius(2.123456789) shouldBe 2.123456789
    }

    test("matchesRestSemantics") {
        ListingSearchRequestParser.cuisines(listOf("  Thai ", "thai", "PAKISTANI")) shouldContainExactly
            listOf("thai", "pakistani")
        ListingSearchRequestParser.cuisines(null).shouldBeEmpty()
        ListingSearchRequestParser.cuisineLogic(null) shouldBe CuisineLogic.OR
        ListingSearchRequestParser.cuisineLogic("and") shouldBe CuisineLogic.AND
        ListingSearchRequestParser.price(null, "minPrice").shouldBeNull()
        ListingSearchRequestParser.price(12.5, "minPrice") shouldBe BigDecimal.valueOf(12.5)
        ListingSearchRequestParser.rating(null).shouldBeNull()
        ListingSearchRequestParser.rating(4.5) shouldBe BigDecimal.valueOf(4.5)
    }

    test("rejectsInvalidFilterValues") {
        shouldThrow<IllegalArgumentException> { ListingSearchRequestParser.cuisines(listOf("  ")) }
        shouldThrow<IllegalArgumentException> {
            ListingSearchRequestParser.cuisines(listOf("x".repeat(65)))
        }
        shouldThrow<IllegalArgumentException> { ListingSearchRequestParser.cuisineLogic("XOR") }
        shouldThrow<IllegalArgumentException> { ListingSearchRequestParser.price(-1.0, "minPrice") }
        shouldThrow<IllegalArgumentException> { ListingSearchRequestParser.rating(-0.1) }
        shouldThrow<IllegalArgumentException> { ListingSearchRequestParser.rating(5.1) }
        shouldThrow<IllegalArgumentException> {
            ListingSearchRequestParser.requirePriceRange(BigDecimal.TEN, BigDecimal.ONE)
        }
    }

    test("radiusIsPositiveAndRequired") {
        ListingSearchRequestParser.radius(2.5) shouldBe 2.5
        shouldThrow<IllegalArgumentException> { ListingSearchRequestParser.radius(null) }
        shouldThrow<IllegalArgumentException> { ListingSearchRequestParser.radius(0.0) }
        shouldThrow<IllegalArgumentException> { ListingSearchRequestParser.radius(-1.0) }
    }

    test("agentPageBoundsAreStrict") {
        ListingSearchRequestParser.limit(null) shouldBe 20
        ListingSearchRequestParser.limit(1) shouldBe 1
        ListingSearchRequestParser.limit(100) shouldBe 100
        shouldThrow<IllegalArgumentException> { ListingSearchRequestParser.limit(0) }
        shouldThrow<IllegalArgumentException> { ListingSearchRequestParser.limit(101) }

        ListingSearchRequestParser.offset(null) shouldBe 0
        ListingSearchRequestParser.offset(5) shouldBe 5
        shouldThrow<IllegalArgumentException> { ListingSearchRequestParser.offset(-1) }
    }

    test("placeIsTrimmedCappedAndNonBlank") {
        ListingSearchRequestParser.place("  Toronto  ") shouldBe "Toronto"
        shouldThrow<IllegalArgumentException> { ListingSearchRequestParser.place("   ") }
        shouldThrow<IllegalArgumentException> { ListingSearchRequestParser.place(null) }
        shouldThrow<IllegalArgumentException> {
            ListingSearchRequestParser.place("x".repeat(257))
        }
    }
})
