package com.tahirslist.ingestion.pipeline

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * The query Photon receives is only as good as this parse. A live four-metro
 * run failed 191 rows because the raw street line still carried "suite c200"
 * and "Rd" was never expanded, so these cases are taken from that run.
 */
class AddressTextTest : FunSpec({

    test("strips a suite so Photon sees only the street") {
        val address = AddressText.parse(
            listOf("18111 Dallas Pkwy suite c200"),
            city = "Dallas",
            region = "Texas",
        )

        address.street shouldBe "18111 Dallas Parkway"
        address.houseNumber shouldBe "18111"
        address.streetTokens shouldBe listOf("dallas")
        address.placeQuery() shouldBe "18111 Dallas Parkway, Dallas, Texas"
    }

    test("strips a hash unit and expands an abbreviated suffix") {
        val address = AddressText.parse(
            listOf("13340 Audelia Rd #135"),
            city = "Dallas",
            region = "TX",
        )

        address.street shouldBe "13340 Audelia Road"
        address.houseNumber shouldBe "13340"
        address.streetTokens shouldBe listOf("audelia")
    }

    test("lifts a postal code and drops repeated city/region/country segments") {
        val address = AddressText.parse(
            listOf("258-03 Hillside Ave., Queens, NY 11004, USA"),
            city = "Queens",
            region = "NY",
        )

        address.street shouldBe "258-03 Hillside Avenue"
        address.houseNumber shouldBe "258-03"
        address.streetTokens shouldBe listOf("hillside")
        address.postalCode shouldBe "11004"
        address.placeQuery() shouldBe "258-03 Hillside Avenue, Queens, NY, 11004"
    }

    test("keeps a Canadian postal code readable") {
        val address = AddressText.parse(
            listOf("123 Main St", "Toronto, ON M5V 2T6"),
            city = "Toronto",
            region = "ON",
        )

        address.street shouldBe "123 Main Street"
        address.postalCode shouldBe "M5V 2T6"
    }

    test("expands a leading directional but not a street named Saint") {
        AddressText.parse(listOf("3611 N Main St"), city = "Plano").street shouldBe
            "3611 North Main Street"
        AddressText.parse(listOf("12 St Marks Ave"), city = "New York").street shouldBe
            "12 St Marks Avenue"
    }

    test("expands a suffix followed by a direction, and keeps only distinctive tokens") {
        val address = AddressText.parse(listOf("1365 Gerrard St E"), city = "Toronto")

        address.street shouldBe "1365 Gerrard Street East"
        // "st"/"e" alone must never satisfy the address check.
        address.streetTokens shouldBe listOf("gerrard")
    }

    test("a numeric street name is kept, only the house number is dropped") {
        val address = AddressText.parse(listOf("274 West 40 Street"), city = "New York")

        address.houseNumber shouldBe "274"
        address.streetTokens shouldBe listOf("40")
    }

    test("a line with only a locality does not become the street") {
        val address = AddressText.parse(listOf("Dallas, TX"), city = "Dallas", region = "TX")

        address.street shouldBe null
        address.placeQuery() shouldBe "Dallas, TX"
    }

    test("a suite that is its own segment is dropped, not used as the street") {
        val address = AddressText.parse(
            listOf("Suite 500", "253 Yonge St"),
            city = "Toronto",
            region = "ON",
        )

        address.street shouldBe "253 Yonge Street"
        address.houseNumber shouldBe "253"
    }

    test("five digits in a unit are not read as a postal code") {
        val address = AddressText.parse(
            listOf("Suite 12345", "1 Main St"),
            city = "Dallas",
            region = "TX",
        )

        address.postalCode shouldBe null
        address.street shouldBe "1 Main Street"
    }

    test("a street named Ste. Anne is not mistaken for a unit") {
        val address = AddressText.parse(listOf("Ste. Anne Avenue"), city = "Winnipeg")

        address.street shouldBe "Ste. Anne Avenue"
        address.houseNumber shouldBe null
    }

    test("a house-numbered Ste. Anne street keeps its street, not just the number") {
        // The unit marker must not cut at " Ste" when the word after it is a
        // street name, not a unit value.
        val numbered = AddressText.parse(listOf("123 Ste. Anne Avenue"), city = "Winnipeg")
        numbered.street shouldBe "123 Ste. Anne Avenue"
        numbered.houseNumber shouldBe "123"
        // The saint prefix carries no identity; "anne" is what tells the street
        // apart. Without this, any Ste-* street confirms at the same number.
        numbered.streetTokens shouldBe listOf("anne")

        val hyphenated = AddressText.parse(listOf("1500 Ste-Catherine Street West"), city = "Montreal")
        hyphenated.street shouldBe "1500 Ste-Catherine Street West"
        hyphenated.houseNumber shouldBe "1500"
    }

    test("a real unit is still cut when a Ste. street precedes it") {
        val address = AddressText.parse(listOf("123 Ste. Anne Avenue Suite 500"), city = "Winnipeg")

        address.street shouldBe "123 Ste. Anne Avenue"
        address.houseNumber shouldBe "123"
    }

    test("a letter-only unit is still cut") {
        AddressText.parse(listOf("1200 Main St Suite B"), city = "Dallas")
            .street shouldBe "1200 Main Street"
    }

    test("a letter-prefixed unit value is still cut") {
        AddressText.parse(listOf("1200 Main St Suite BC200"), city = "Dallas")
            .street shouldBe "1200 Main Street"
        AddressText.parse(listOf("1200 Main St Unit AB12"), city = "Dallas")
            .street shouldBe "1200 Main Street"
    }

    test("a saint-prefixed street keeps only its distinguishing word") {
        AddressText.parse(listOf("1500 Ste-Catherine Street West"), city = "Montreal")
            .streetTokens shouldBe listOf("catherine")
        AddressText.parse(listOf("12 St. Marks Place"), city = "New York")
            .streetTokens shouldBe listOf("marks")
    }
})
