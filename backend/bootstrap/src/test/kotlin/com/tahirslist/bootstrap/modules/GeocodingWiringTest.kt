package com.tahirslist.bootstrap.modules

import com.tahirslist.application.geo.CheckGeocoderHealth
import com.tahirslist.application.geo.GeocodedAddress
import com.tahirslist.application.geo.GeocodedAddressStore
import com.tahirslist.application.geo.GeocodedPlace
import com.tahirslist.application.geo.GeocoderPort
import com.tahirslist.domain.restaurant.LatLng
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

private class CountingProvider : GeocoderPort {
    var calls = 0

    override suspend fun geocode(address: String): GeocodedPlace? {
        calls += 1
        return GeocodedPlace(LatLng(40.7128, -74.0060), "New York", "N/1")
    }
}

private class AlwaysHitStore : GeocodedAddressStore {
    override suspend fun findByQueryHash(hash: String, notBefore: Instant): GeocodedAddress =
        GeocodedAddress(
            queryHash = hash,
            location = LatLng(0.0, 0.0),
            displayName = "cached",
            providerRef = null,
            provider = "photon",
            createdAt = Instant.parse("2026-11-01T00:00:00Z"),
        )

    override suspend fun save(entry: GeocodedAddress) = Unit
}

/**
 * D-2 / INV-7: the pre-flight holds the upstream port, every consumer holds the
 * cached decorator, and the config days map to the cache window.
 */
class GeocodingWiringTest : FunSpec({

    test("healthProbeBypassesCache") {
        runTest {
            val upstream = CountingProvider()
            val health = GeocodingGraph.healthProbe(upstream)

            health.geocoder shouldBeSameInstanceAs upstream

            health.execute().shouldBeInstanceOf<com.tahirslist.application.geo.GeocoderHealth.Available>()
            upstream.calls shouldBe 1
        }
    }

    test("consumersReceiveTheCachedPort") {
        runTest {
            val upstream = CountingProvider()
            val cached = GeocodingGraph.cached(upstream, AlwaysHitStore(), 90.days)

            cached.shouldBeInstanceOf<com.tahirslist.application.geo.CachingGeocoder>()
            cached.geocode("Main St")?.displayName shouldBe "cached"
            upstream.calls shouldBe 0
        }
    }

    test("ttl days map to the cache window, zero is permanent") {
        GeocodingGraph.ttl(90) shouldBe 90.days
        GeocodingGraph.ttl(0) shouldBe Duration.INFINITE
    }

    test("the probe uses the documented default address") {
        CheckGeocoderHealth(CountingProvider()).probeAddress shouldBe CheckGeocoderHealth.DEFAULT_PROBE_ADDRESS
    }
})
