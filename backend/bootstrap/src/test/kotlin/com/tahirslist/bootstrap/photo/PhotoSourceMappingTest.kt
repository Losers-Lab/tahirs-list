package com.tahirslist.bootstrap.photo

import com.tahirslist.domain.restaurant.PhotoLicence
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

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
})
