package com.tahirslist.domain.restaurant

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class LatLngRoundingTest : FunSpec({

    test("roundedTo rounds both components to the requested decimals") {
        LatLng(40.71283741, -74.00601293).roundedTo(4) shouldBe LatLng(40.7128, -74.006)
    }

    test("a value already at the precision is unchanged (INV-1 via A1)") {
        LatLng(40.7128, -74.006).roundedTo(4) shouldBe LatLng(40.7128, -74.006)
    }

    test("rounding near a pole stays inside WGS84 (INV-4)") {
        LatLng(89.99999, 0.0).roundedTo(4) shouldBe LatLng(90.0, 0.0)
    }

    test("rounding near the antimeridian stays inside WGS84 (INV-4)") {
        LatLng(0.0, -179.99999).roundedTo(4) shouldBe LatLng(0.0, -180.0)
    }

    test("the rounded result has at most N fractional digits (INV-5)") {
        val rounded = LatLng(40.71283741, -74.00601293).roundedTo(3)
        rounded.lat.toBigDecimal().scale() shouldBe 3
        rounded.lng.toBigDecimal().scale() shouldBe 3
    }

    test("a negative decimals count is rejected") {
        shouldThrow<IllegalArgumentException> { LatLng(1.0, 1.0).roundedTo(-1) }
    }
})
