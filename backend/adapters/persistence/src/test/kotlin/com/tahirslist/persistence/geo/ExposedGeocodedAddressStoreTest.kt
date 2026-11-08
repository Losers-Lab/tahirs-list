package com.tahirslist.persistence.geo

import com.tahirslist.application.geo.GeocodedAddress
import com.tahirslist.application.geo.GeocodedAddressStore
import com.tahirslist.domain.restaurant.LatLng
import com.tahirslist.persistence.PostgisTestDatabase
import com.tahirslist.persistence.count
import com.tahirslist.persistence.rows
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.time.Instant

/**
 * WI-03: the store behind `GeocoderPort` — round trip, the 90-day read window,
 * and an upsert that refreshes a stale row without adding a second one.
 */
class ExposedGeocodedAddressStoreTest : FunSpec({

    val postgis = PostgisTestDatabase()
    lateinit var store: GeocodedAddressStore

    beforeSpec {
        postgis.start()
        store = ExposedGeocodedAddressStore(postgis.database)
    }
    afterSpec { postgis.stop() }

    fun entry(
        hash: String = "c".repeat(64),
        createdAt: Instant = Instant.parse("2026-11-01T12:00:00Z"),
    ) = GeocodedAddress(
        queryHash = hash,
        location = LatLng(43.6532, -79.3832),
        displayName = "Toronto",
        providerRef = "N/123",
        provider = "photon",
        createdAt = createdAt,
    )

    test("saveThenFindRoundTrips") {
        runTest {
            val saved = entry()
            store.save(saved)

            postgis.count("SELECT count(*) FROM geocoded_addresses") shouldBe 1
            val storedHash = postgis.rows(
                "SELECT normalized_query_hash FROM geocoded_addresses",
            ).single()["normalized_query_hash"] as String
            storedHash.trim() shouldBe saved.queryHash

            store.findByQueryHash(saved.queryHash, Instant.parse("2026-10-01T00:00:00Z")) shouldBe saved
        }
    }

    test("oldRowIsNotServed") {
        runTest {
            val stale = entry(hash = "d".repeat(64), createdAt = Instant.parse("2026-07-01T00:00:00Z"))
            store.save(stale)

            store.findByQueryHash(stale.queryHash, Instant.parse("2026-11-01T00:00:00Z")) shouldBe null
        }
    }

    test("permanentCutoffIsInRangeAndServesOldRows") {
        runTest {
            val old = entry(hash = "f".repeat(64), createdAt = Instant.parse("2000-01-01T00:00:00Z"))
            store.save(old)

            store.findByQueryHash(old.queryHash, Instant.fromEpochSeconds(0)) shouldBe old
        }
    }

    test("saveRefreshesStaleRow") {
        runTest {
            val hash = "e".repeat(64)
            store.save(entry(hash = hash, createdAt = Instant.parse("2026-07-01T00:00:00Z")))

            val fresh = entry(hash = hash).copy(
                location = LatLng(40.7128, -74.0060),
                displayName = "New York",
                createdAt = Instant.parse("2026-11-01T12:00:00Z"),
            )
            store.save(fresh)

            store.findByQueryHash(hash, Instant.parse("2026-10-01T00:00:00Z")) shouldBe fresh
            postgis.count(
                "SELECT count(*) FROM geocoded_addresses WHERE normalized_query_hash = ?",
                hash,
            ) shouldBe 1
        }
    }
})
