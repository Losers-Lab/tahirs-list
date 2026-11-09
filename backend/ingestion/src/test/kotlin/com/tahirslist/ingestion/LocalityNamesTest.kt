package com.tahirslist.ingestion

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class LocalityNamesTest : FunSpec({

    test("a long or dotted country name becomes the catalogue short form") {
        LocalityNames.country("United States Of America", 16) shouldBe "United States"
        LocalityNames.country("U.S.A.", 16) shouldBe "United States"
        LocalityNames.country("CAN", 16) shouldBe "Canada"
    }

    test("a country that does not fit is dropped, never truncated") {
        LocalityNames.country("United Arab Emirates", 16) shouldBe null
    }

    test("state names and codes normalise both ways") {
        LocalityNames.usStateCode("New York") shouldBe "NY"
        LocalityNames.usStateCode("ny") shouldBe "NY"
        LocalityNames.usStateCode("Ontario") shouldBe "Ontario"
        LocalityNames.usStateName("NY") shouldBe "New York"
        LocalityNames.usStateName("Ontario") shouldBe null
    }
})
