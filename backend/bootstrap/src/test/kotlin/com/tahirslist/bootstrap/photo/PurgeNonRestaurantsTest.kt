package com.tahirslist.bootstrap.photo

import com.tahirslist.domain.restaurant.Cuisine
import com.tahirslist.domain.restaurant.LatLng
import com.tahirslist.domain.restaurant.RestaurantListing
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlin.uuid.Uuid

/**
 * SC-231 D-12: the one-off purge selects by the gate's own rule, and only those
 * rows. A hand-written name list would rot; a rule that also catches a restaurant
 * would be worse than leaving the meat markets in place.
 */
class PurgeNonRestaurantsTest : FunSpec({

    fun listing(name: String) = RestaurantListing.new(
        name = name,
        address = "1 Test St",
        location = LatLng(43.7, -79.4),
        cuisine = Cuisine("middle eastern"),
        ownerId = Uuid.random(),
    )

    test("the three meat markets in the catalogue today are selected") {
        val names = listOf(
            "Jamaica Live Poultry Meat Market [Butcher Dept. Only]",
            "Hamza Meat Shop [Butcher Dept. ONLY]",
            "Alladin Halal Meat & Grocery [Butcher Dept. ONLY]",
        )

        PurgeSelection.select(names.map(::listing)) shouldHaveSize 3
    }

    test("restaurants, bakeries and markets with a kitchen are left alone") {
        val names = listOf(
            "Madina Market (Kitchen)",
            "Mannan Bakery and Grill",
            "Mannan Sweets & Restaurant",
            "Firin Bakery",
            "Mannan Bakery",
            "Mannan Bakery Factory",
            "Corner Coffee",
            "Paramount Fine Foods",
        )

        PurgeSelection.select(names.map(::listing)).shouldBeEmpty()
    }

    test("a dry run changes nothing — the selection is a pure read") {
        val rows = listOf(listing("Hamza Meat Shop"), listing("Noor Grill"))

        val first = PurgeSelection.select(rows)
        val second = PurgeSelection.select(rows)

        first.map { it.listing.name } shouldBe second.map { it.listing.name }
        first.single().reason.contains("butcher") shouldBe true
    }
})
