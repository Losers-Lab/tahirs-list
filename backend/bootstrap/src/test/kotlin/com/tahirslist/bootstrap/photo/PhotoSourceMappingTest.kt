package com.tahirslist.bootstrap.photo

import com.tahirslist.domain.restaurant.PhotoLicence
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/**
 * SC-231 UC-03: the source-side rules that must hold before a photo is even
 * offered — the venue match and the licence table.
 *
 * These are pure, so they are tested without a network. The HTTP shape of each
 * adapter is exercised by the live pass, not by CI: there is no fixture server,
 * and inventing one would only test the fixture.
 */
class PhotoSourceMappingTest : FunSpec({

    test("a whole-name phrase agrees, a stray word does not") {
        VenueMatch.namesAgree("Noor Grill", "Noor Grill & Pizza") shouldBe true
        VenueMatch.namesAgree("Firin Bakery", "Firin Bakery") shouldBe true
        VenueMatch.namesAgree("Noor Grill", "Noor") shouldBe false
        VenueMatch.namesAgree("Noor", "Noor Halal Pizza") shouldBe false
    }

    test("a Commons file title is stripped before matching") {
        VenueMatch.fileTitle("File:Firin Bakery, Toronto.jpg") shouldBe "Firin Bakery, Toronto"
        VenueMatch.namesAgree("Firin Bakery", VenueMatch.fileTitle("File:Firin Bakery.jpg")) shouldBe true
    }

    test("the address must corroborate the name") {
        VenueMatch.addressAgrees("253 Yonge St", "M5B 1N8", "253 Yonge Street, Toronto, ON M5B 1N8") shouldBe true
        VenueMatch.addressAgrees("253 Yonge St", null, "253 Yonge Street, Toronto") shouldBe true
        VenueMatch.addressAgrees("253 Yonge St", "M5B 1N8", "1 Queen Street West, Toronto") shouldBe false
    }

    test("CC variants map to the licences we accept") {
        CommonsLicence.parse("CC0") shouldBe PhotoLicence.CC0
        CommonsLicence.parse("Public domain") shouldBe PhotoLicence.PUBLIC_DOMAIN
        CommonsLicence.parse("CC BY 4.0") shouldBe PhotoLicence.CC_BY
        CommonsLicence.parse("CC BY-SA 3.0") shouldBe PhotoLicence.CC_BY_SA
    }

    test("a licence we cannot state is refused") {
        CommonsLicence.parse("All rights reserved") shouldBe null
        CommonsLicence.parse(null) shouldBe null
        CommonsLicence.parse("") shouldBe null
    }

    // ---- SC-231: a name match is not a place match -------------------------

    fun listingAt(city: String?, lat: Double, lng: Double) = com.tahirslist.domain.restaurant.RestaurantListing.new(
        name = "Ali Baba Mediterranean",
        address = "2103 N Central Expwy",
        city = city,
        location = com.tahirslist.domain.restaurant.LatLng(lat, lng),
        cuisine = com.tahirslist.domain.restaurant.Cuisine("middle eastern"),
        ownerId = kotlin.uuid.Uuid.random(),
    )

    test("coordinates near the listing confirm the file") {
        val listing = listingAt("Richardson", 32.978694, -96.715556)

        CommonsPlace.refusal(listing, 32.9790, -96.7160, "") shouldBe null
    }

    test("a file photographed in another country is refused, with the distance") {
        val listing = listingAt("Richardson", 32.978694, -96.715556)

        val refusal = CommonsPlace.refusal(listing, 52.4409, -1.9394, "Restaurants in Birmingham")
        refusal.orEmpty() shouldContain "km from this listing"
    }

    test("without coordinates, the file must name the listing's city") {
        val listing = listingAt("Toronto", 43.6714, -79.3246)

        CommonsPlace.refusal(listing, null, null, "Restaurants in Toronto|Kebabs in Canada") shouldBe null
        CommonsPlace.refusal(listing, null, null, "2005 in Mississauga|People of Canada").orEmpty()
            .shouldContain("does not name Toronto")
    }

    test("a two-letter region is never used as place evidence") {
        // "ON" is inside "London" — a text match that would confirm the wrong place.
        val listing = listingAt("Toronto", 43.6714, -79.3246)

        CommonsPlace.refusal(listing, null, null, "London, ON").orEmpty() shouldContain "Toronto"
    }

    test("the great-circle distance is the real one") {
        // Toronto -> Birmingham is about 6,000 km.
        val km = CommonsPlace.distanceKm(43.6714, -79.3246, 52.4409, -1.9394)
        (km > 5000) shouldBe true
    }
})
