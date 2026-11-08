package com.tahirslist.application.geo

import com.tahirslist.domain.restaurant.LatLng
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

private val NOW: Instant = Instant.parse("2026-11-08T12:00:00Z")

private val TORONTO = GeocodedPlace(LatLng(43.6532, -79.3832), "Toronto", "N/1")
private val NEW_YORK = GeocodedPlace(LatLng(40.7128, -74.0060), "New York", "N/2")

private class FakeProvider(
    private val answer: suspend (String) -> GeocodedPlace?,
) : GeocoderPort {
    var calls = 0
    val addresses = mutableListOf<String>()

    override suspend fun geocode(address: String): GeocodedPlace? {
        calls += 1
        addresses += address
        return answer(address)
    }
}

private class FakeStore(
    private val entries: MutableMap<String, GeocodedAddress> = mutableMapOf(),
    private val failRead: Boolean = false,
    private val failWrite: Boolean = false,
) : GeocodedAddressStore {
    val saved = mutableListOf<GeocodedAddress>()
    var lastNotBefore: Instant? = null

    override suspend fun findByQueryHash(hash: String, notBefore: Instant): GeocodedAddress? {
        lastNotBefore = notBefore
        if (failRead) error("database down")
        val entry = entries[hash] ?: return null
        return if (entry.createdAt >= notBefore) entry else null
    }

    override suspend fun save(entry: GeocodedAddress) {
        if (failWrite) error("database down")
        saved += entry
        entries[entry.queryHash] = entry
    }

    fun seed(entry: GeocodedAddress) {
        entries[entry.queryHash] = entry
    }
}

private class FixedClock(private val instant: Instant) : Clock {
    override fun now(): Instant = instant
}

private fun cached(provider: GeocoderPort, store: GeocodedAddressStore) =
    CachingGeocoder(
        delegate = provider,
        store = store,
        clock = FixedClock(NOW),
        ttl = 90.days,
    )

private fun row(
    place: GeocodedPlace = TORONTO,
    createdAt: Instant = NOW,
): GeocodedAddress = GeocodedAddress(
    queryHash = NormalizedQuery.of("Main St").hash,
    location = place.location,
    displayName = place.displayName,
    providerRef = place.providerRef,
    provider = "photon",
    createdAt = createdAt,
)

class CachingGeocoderTest : FunSpec({

    test("missThenHitCallsProviderOnce") {
        runTest {
            val provider = FakeProvider { TORONTO }
            val store = FakeStore()
            val geocoder = cached(provider, store)

            geocoder.geocode("Main St") shouldBe TORONTO
            geocoder.geocode("  MAIN   ST  ") shouldBe TORONTO

            provider.calls shouldBe 1
            store.saved.size shouldBe 1
            store.saved.single().queryHash shouldBe NormalizedQuery.of("main st").hash
            store.lastNotBefore shouldBe NOW - 90.days
        }
    }

    test("hitReturnsStoredPlaceWithoutProviderCall") {
        runTest {
            val provider = FakeProvider { error("provider must not be called") }
            val store = FakeStore().apply { seed(row()) }

            cached(provider, store).geocode(" main st") shouldBe TORONTO
            provider.calls shouldBe 0
        }
    }

    test("noMatchIsNotCached") {
        runTest {
            val provider = FakeProvider { null }
            val store = FakeStore()

            cached(provider, store).geocode("Nowhere Rd") shouldBe null
            provider.calls shouldBe 1
            store.saved shouldBe emptyList()
        }
    }

    test("providerFailureLeavesNoCacheRow") {
        runTest {
            val provider = FakeProvider { throw GeocodingException("Photon timed out") }
            val store = FakeStore()

            shouldThrow<GeocodingException> { cached(provider, store).geocode("Main St") }
            store.saved shouldBe emptyList()
        }
    }

    test("expiredRowIsRefetchedAndRefreshed") {
        runTest {
            val provider = FakeProvider { NEW_YORK }
            val store = FakeStore().apply { seed(row(createdAt = NOW - 91.days)) }

            cached(provider, store).geocode("Main St") shouldBe NEW_YORK
            provider.calls shouldBe 1
            store.saved.single().displayName shouldBe "New York"
            store.saved.single().createdAt shouldBe NOW
        }
    }

    test("lookupAndWriteUseSameKey") {
        runTest {
            val provider = FakeProvider { TORONTO }
            val store = FakeStore()
            val geocoder = cached(provider, store)

            geocoder.geocode("Main St")
            geocoder.geocode("  MAIN   ST  ")

            provider.calls shouldBe 1
            store.saved.single().queryHash shouldBe NormalizedQuery.of("main st").hash
        }
    }

    test("permanentTtlKeepsVeryOldRowsFresh") {
        runTest {
            val provider = FakeProvider { error("provider must not be called") }
            val store = FakeStore().apply {
                seed(row(createdAt = Instant.parse("2000-01-01T00:00:00Z")))
            }
            val geocoder = CachingGeocoder(
                delegate = provider,
                store = store,
                clock = FixedClock(NOW),
                ttl = Duration.INFINITE,
            )

            geocoder.geocode("Main St") shouldBe TORONTO
            provider.calls shouldBe 0
            store.lastNotBefore shouldBe Instant.fromEpochSeconds(0)
        }
    }

    test("readFailureFallsBackToProvider") {
        runTest {
            val provider = FakeProvider { TORONTO }
            val store = FakeStore(failRead = true)

            cached(provider, store).geocode("Main St") shouldBe TORONTO
            provider.calls shouldBe 1
        }
    }

    test("writeFailureStillReturnsPlace") {
        runTest {
            val provider = FakeProvider { TORONTO }
            val store = FakeStore(failWrite = true)

            cached(provider, store).geocode("Main St") shouldBe TORONTO
        }
    }
})
