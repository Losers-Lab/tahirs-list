package com.tahirslist.application.geo

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch

/**
 * AC-c / AC-d: one key per place regardless of case or whitespace, and a key
 * that cannot drift between calls or processes.
 */
class NormalizedQueryTest : FunSpec({

    test("ignores case and surrounding whitespace (AC-c)") {
        NormalizedQuery.of("Main St").hash shouldBe NormalizedQuery.of(" main st").hash
        NormalizedQuery.of("MAIN ST").hash shouldBe NormalizedQuery.of("  main st  ").hash
    }

    test("collapses internal whitespace") {
        NormalizedQuery.of("3885   Belt   Line   Rd").text shouldBe "3885 belt line rd"
    }

    test("folds compatibility forms before hashing (NFKC)") {
        NormalizedQuery.of("Ｍａｉｎ　Ｓｔ").hash shouldBe NormalizedQuery.of("Main St").hash
    }

    test("hash is stable across calls (AC-d)") {
        NormalizedQuery.of("123 Yonge St, Toronto").hash shouldBe
            NormalizedQuery.of("123 Yonge St, Toronto").hash
    }

    test("pins the algorithm with a known vector") {
        NormalizedQuery.of("Main St").hash shouldBe
            "44e24a9d002a923dc64679433313f11b9ce3c5cabed13f8983872fd99f87d504"
    }

    test("hash is lowercase hex of 64 characters") {
        NormalizedQuery.of("Somewhere").hash shouldMatch Regex("^[0-9a-f]{64}$")
    }
})
