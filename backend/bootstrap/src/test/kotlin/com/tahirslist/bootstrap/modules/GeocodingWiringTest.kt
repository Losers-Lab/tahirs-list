package com.tahirslist.bootstrap.modules

import com.tahirslist.application.geo.CachingGeocoder
import com.tahirslist.application.geo.CheckGeocoderHealth
import com.tahirslist.application.geo.GeocodedAddress
import com.tahirslist.application.geo.GeocodedAddressStore
import com.tahirslist.application.geo.GeocodedPlace
import com.tahirslist.application.geo.GeocoderHealth
import com.tahirslist.application.geo.GeocoderPort
import com.tahirslist.bootstrap.config.AppConfig
import com.tahirslist.bootstrap.config.CookieSettings
import com.tahirslist.bootstrap.config.DatabaseSettings
import com.tahirslist.bootstrap.config.GeocodeCacheSettings
import com.tahirslist.bootstrap.config.JwtSettings
import com.tahirslist.bootstrap.config.RateLimitSettings
import com.tahirslist.bootstrap.config.SeedIngestSettings
import com.tahirslist.domain.restaurant.LatLng
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.coroutines.test.runTest
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
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

private fun testConfig(ttlDays: Int = 90) = AppConfig(
    database = DatabaseSettings("jdbc:postgresql://localhost:5432/test", "test", "test"),
    jwt = JwtSettings(),
    rateLimit = RateLimitSettings(),
    cookie = CookieSettings(),
    storage = null,
    verification = null,
    seedIngest = SeedIngestSettings(),
    geocodeCache = GeocodeCacheSettings(ttlDays),
)

/**
 * D-2 / INV-7 against the real Koin module `appModules` installs (not a copy of
 * its bindings): the pre-flight holds the upstream provider while every consumer
 * holds the caching decorator.
 */
class GeocodingWiringTest : FunSpec({

    test("healthProbeBypassesCache") {
        val provider = CountingProvider()
        val koin = koinApplication {
            modules(
                geocodingModule(
                    config = testConfig(ttlDays = 0),
                    upstreamFactory = { provider },
                    storeOverride = AlwaysHitStore(),
                ),
            )
        }.koin

        val health = koin.get<CheckGeocoderHealth>()
        health.geocoder shouldBeSameInstanceAs provider

        runTest { health.execute().shouldBeInstanceOf<GeocoderHealth.Available>() }
        provider.calls shouldBe 1
    }

    test("consumersReceiveTheCachedPort") {
        val provider = CountingProvider()
        val koin = koinApplication {
            modules(
                geocodingModule(
                    config = testConfig(ttlDays = 0),
                    upstreamFactory = { provider },
                    storeOverride = AlwaysHitStore(),
                ),
            )
        }.koin

        koin.get<GeocoderPort>(named(GeocodingQualifiers.UPSTREAM)) shouldBeSameInstanceAs provider

        val cached = koin.get<GeocoderPort>()
        cached.shouldBeInstanceOf<CachingGeocoder>()
        runTest { cached.geocode("Main St")?.displayName shouldBe "cached" }
        provider.calls shouldBe 0
    }

    test("ttl days map to the cache window, zero is permanent") {
        GeocodingGraph.ttl(90) shouldBe 90.days
        GeocodingGraph.ttl(0) shouldBe Duration.INFINITE
    }
})
