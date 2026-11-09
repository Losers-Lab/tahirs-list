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
})
