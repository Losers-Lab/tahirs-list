package com.tahirslist.application.geo

import kotlin.time.Instant

/**
 * Outbound port for the geocode cache (D-1). `CachingGeocoder` is the only
 * caller; the persistence adapter owns the SQL and the short transactions.
 *
 * [findByQueryHash] must treat a row older than [notBefore] as absent — lazy
 * expiry, no scheduler (D-6). [save] is an upsert: one row per hash, refreshed
 * in place when the row is stale.
 */
interface GeocodedAddressStore {

    suspend fun findByQueryHash(hash: String, notBefore: Instant): GeocodedAddress?

    suspend fun save(entry: GeocodedAddress)
}
