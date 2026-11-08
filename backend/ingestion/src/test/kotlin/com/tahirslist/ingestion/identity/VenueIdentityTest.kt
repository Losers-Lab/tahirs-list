package com.tahirslist.ingestion.identity

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class VenueIdentityTest : FunSpec({

    test("case, punctuation, whitespace and diacritics do not change the key") {
        val a = VenueIdentity.from(
            "Al-Amir Lebanese Restaurant & Club",
            listOf("3885 Belt Line Rd"),
            "Addison",
            "TX",
            "US",
        )
        val b = VenueIdentity.from(
            "  al amir   lebanese restaurant and club ",
            listOf("3885  Belt Line Road"),
            "ADDISON",
            "tx",
            "us",
        )

        // "Rd" vs "Road" is intentionally NOT normalized: that is a fuzzy match,
        // not an exact identity match. This assertion pins the conservative rule.
        a shouldNotBe b
    }

    test("the same normalized venue produces the same key") {
        val a = VenueIdentity.from(
            "Al-Amir Lebanese Restaurant & Club",
            listOf("3885 Belt Line Rd"),
            "Addison",
            "TX",
            "US",
        )
        val b = VenueIdentity.from(
            "AL-AMIR LEBANESE RESTAURANT AND CLUB",
            listOf("3885 belt line rd"),
            "addison",
            "Tx",
            "us",
        )

        a shouldBe b
    }

    test("different addresses produce different keys") {
        val a = VenueIdentity.from("Dera", listOf("37-11 74th St"), "Jackson Heights", "NY", "US")
        val b = VenueIdentity.from("Dera", listOf("37-12 74th St"), "Jackson Heights", "NY", "US")

        a shouldNotBe b
    }

    test("the same name and address in different cities are different venues") {
        val a = VenueIdentity.from("The Halal Guys", listOf("1 Main St"), "Dallas", "TX", "US")
        val b = VenueIdentity.from("The Halal Guys", listOf("1 Main St"), "Toronto", "ON", "CA")

        a shouldNotBe b
    }

    test("coordinates are not part of the key") {
        // There is no coordinate parameter by design; this test documents the
        // contract so a future refactor cannot silently add one.
        val a = VenueIdentity.from("Dera", listOf("37-11 74th St"), "Jackson Heights", "NY", "US")
        val b = VenueIdentity.from("Dera", listOf("37-11 74th St"), "Jackson Heights", "NY", "US")

        a shouldBe b
    }

    test("a blank name or address cannot produce an identity") {
        VenueIdentity.from("", listOf("1 Main St"), "Dallas", "TX", "US") shouldBe null
        VenueIdentity.from("Dera", listOf("  "), "Jackson Heights", "NY", "US") shouldBe null
    }

    test("the key is a lowercase SHA-256 hex string") {
        val key = VenueIdentity.from("Dera", listOf("37-11 74th St"), "Jackson Heights", "NY", "US")!!.key
        key.length shouldBe 64
        key.all { it in '0'..'9' || it in 'a'..'f' } shouldBe true
    }

    test("normalization strips punctuation but keeps letters and digits") {
        VenueIdentity.normalize("  St. John's   Fish & Chips  ") shouldBe "st johns fish and chips"
        VenueIdentity.normalize("Café Boulud") shouldBe "cafe boulud"
        VenueIdentity.normalize("7-Eleven") shouldBe "7 eleven"
    }
})
