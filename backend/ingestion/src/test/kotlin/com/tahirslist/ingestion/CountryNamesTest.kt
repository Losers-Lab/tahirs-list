package com.tahirslist.ingestion

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * The HMS directory publishes `United States Of America`; the catalogue column is
 * 16 characters (V20), so the load refused 17 valid rows. The mapping is aliases
 * only — an unknown country is never truncated into a different one.
 */
class CountryNamesTest : FunSpec({

    test("the HMS country name fits the catalogue column") {
        val canonical = CountryNames.canonical("United States Of America")
        canonical shouldBe "United States"
        check(canonical != null && canonical.length <= CountryNames.MAX_LENGTH)
    }

    test("already-short values are normalised, not rewritten") {
        CountryNames.canonical("Canada") shouldBe "Canada"
        CountryNames.canonical("US") shouldBe "United States"
        CountryNames.canonical("  canada  ") shouldBe "Canada"
    }

    test("an unknown country is left alone rather than truncated") {
        CountryNames.canonical("Republic of Somewhere Long") shouldBe "Republic of Somewhere Long"
        CountryNames.canonical("") shouldBe null
        CountryNames.canonical(null) shouldBe null
    }
})
