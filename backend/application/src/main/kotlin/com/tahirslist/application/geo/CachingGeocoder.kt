package com.tahirslist.application.geo

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

private val log = KotlinLogging.logger("com.tahirslist.application.geo.CachingGeocoder")

/**
 * The cache decorator on [GeocoderPort] (D-1). Every consumer — the MCP search
 * and the ingestion controls — gets this binding, so a place that was resolved
 * once costs no provider call again until it goes stale.
 *
 * Contract, in order:
 *  1. normalize the query once and derive its hash ([NormalizedQuery]);
 *  2. ask the store for a row at or after `now − ttl` (a stale row is a miss);
 *  3. on a hit, return the stored place — the provider is never called;
 *  4. on a miss, call the wrapped provider; `null` is not cached;
 *  5. on a match, upsert the row, then return the provider's place.
 *
 * The cache is an optimization, never a dependency: a store read failure is
 * logged and treated as a miss, and a store write failure is logged while the
 * provider's result is still returned (D-7). Provider failures propagate
 * unchanged and write nothing (D-4).
 */
class CachingGeocoder(
    private val delegate: GeocoderPort,
    private val store: GeocodedAddressStore,
    private val clock: Clock = Clock.System,
    private val ttl: Duration = 90.days,
    private val provider: String = "photon",
) : GeocoderPort {

    override suspend fun geocode(address: String): GeocodedPlace? {
        val query = NormalizedQuery.of(address)
        val now = clock.now()

        val cached = try {
            store.findByQueryHash(query.hash, notBefore(now))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn(e) { "geocode cache read failed; treating the lookup as a miss" }
            null
        }
        if (cached != null) return cached.toPlace()

        val place = delegate.geocode(address) ?: return null

        try {
            store.save(
                GeocodedAddress(
                    queryHash = query.hash,
                    location = place.location,
                    displayName = place.displayName,
                    providerRef = place.providerRef,
                    provider = provider,
                    createdAt = now,
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn(e) { "geocode cache write failed; returning the provider result" }
        }
        return place
    }

    /** `ttl = Duration.INFINITE` (config `0`) means a row never goes stale. */
    private fun notBefore(now: Instant): Instant =
        if (ttl == Duration.INFINITE) Instant.DISTANT_PAST else now - ttl
}
