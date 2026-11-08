package com.tahirslist.application.geo

import com.tahirslist.domain.restaurant.LatLng
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.test.runTest

private class FakeGeocoder(
    private val result: suspend (String) -> GeocodedPlace?,
) : GeocoderPort {
    val queries = mutableListOf<String>()
    override suspend fun geocode(address: String): GeocodedPlace? {
        queries += address
        return result(address)
    }
}

class CheckGeocoderHealthTest : FunSpec({

    test("passes when the probe resolves") {
        runTest {
            val geocoder = FakeGeocoder {
                GeocodedPlace(LatLng(40.7128, -74.0060), "New York", "N/1")
            }

            val health = CheckGeocoderHealth(geocoder).execute()

            health.shouldBeInstanceOf<GeocoderHealth.Available>()
            geocoder.queries shouldBe listOf(CheckGeocoderHealth.DEFAULT_PROBE_ADDRESS)
        }
    }

    test("fails when the probe finds no match") {
        runTest {
            val health = CheckGeocoderHealth(FakeGeocoder { null }).execute()

            health.shouldBeInstanceOf<GeocoderHealth.Unavailable>()
            (health as GeocoderHealth.Unavailable).reason.contains("no match") shouldBe true
        }
    }

    test("fails when the provider throws") {
        runTest {
            val geocoder = FakeGeocoder { throw GeocodingException("Photon timed out") }

            val health = CheckGeocoderHealth(geocoder).execute()

            health.shouldBeInstanceOf<GeocoderHealth.Unavailable>()
            (health as GeocoderHealth.Unavailable).reason shouldBe "Photon timed out"
        }
    }

    test("does not swallow cancellation") {
        runTest {
            val geocoder = FakeGeocoder { throw CancellationException("caller cancelled") }

            shouldThrow<CancellationException> {
                CheckGeocoderHealth(geocoder).execute()
            }
        }
    }
})
