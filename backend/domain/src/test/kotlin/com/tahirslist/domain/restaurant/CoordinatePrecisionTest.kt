package com.tahirslist.domain.restaurant

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe

class CoordinatePrecisionTest : FunSpec({

    val fixes = listOf(
        LatLng(40.71283741, -74.00601293),
        LatLng(-33.86785, 151.20732),
        LatLng(0.0, 0.0),
        LatLng(89.9999999, 179.9999999),
    )

    test("snap keeps the configured number of decimals") {
        CoordinatePrecision.FOUR_DECIMALS.snap(LatLng(40.71283741, -74.00601293)) shouldBe
            LatLng(40.7128, -74.0060)
        CoordinatePrecision.THREE_DECIMALS.snap(LatLng(40.71283741, -74.00601293)) shouldBe
            LatLng(40.713, -74.006)
    }

    test("snapping is idempotent (INV-1)") {
        fixes.forEach { fix ->
            CoordinatePrecision.FOUR_DECIMALS.snap(CoordinatePrecision.FOUR_DECIMALS.snap(fix)) shouldBe
                CoordinatePrecision.FOUR_DECIMALS.snap(fix)
        }
    }

    test("two centres in one rounding basin snap to the same centre (INV-2)") {
        CoordinatePrecision.FOUR_DECIMALS.snap(LatLng(40.71283741, -74.00601293)) shouldBe
            CoordinatePrecision.FOUR_DECIMALS.snap(LatLng(40.71284999, -74.00603001))
    }

    test("snapping is deterministic (INV-6)") {
        fixes.forEach { fix ->
            CoordinatePrecision.FOUR_DECIMALS.snap(fix) shouldBe CoordinatePrecision.FOUR_DECIMALS.snap(fix)
        }
    }

    test("the snapped shift never exceeds the advertised maximum (INV-3)") {
        fixes.forEach { fix ->
            val snapped = CoordinatePrecision.FOUR_DECIMALS.snap(fix)
            haversineMetres(fix, snapped) shouldBeLessThanOrEqual CoordinatePrecision.FOUR_DECIMALS.maxShiftMetres
        }
    }

    test("the advertised maximum is the half cell diagonal, about 7.9 m at 4 dp") {
        (CoordinatePrecision.FOUR_DECIMALS.maxShiftMetres in 7.8..8.0) shouldBe true
        (CoordinatePrecision.THREE_DECIMALS.maxShiftMetres in 78.0..79.0) shouldBe true
    }
})

private fun haversineMetres(a: LatLng, b: LatLng): Double {
    val r = 6_371_008.8
    val dLat = Math.toRadians(b.lat - a.lat)
    val dLng = Math.toRadians(b.lng - a.lng)
    val h = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
        Math.cos(Math.toRadians(a.lat)) * Math.cos(Math.toRadians(b.lat)) *
        Math.sin(dLng / 2) * Math.sin(dLng / 2)
    return 2 * r * Math.asin(Math.sqrt(h))
}
