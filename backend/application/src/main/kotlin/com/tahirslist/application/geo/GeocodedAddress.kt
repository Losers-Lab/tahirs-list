package com.tahirslist.application.geo

import com.tahirslist.domain.restaurant.LatLng
import kotlin.time.Instant

/**
 * One row of the geocode cache (docs/features/SC-198/01-domain-model.md).
 *
 * `createdAt` is both first-sighting and the freshness stamp: the store only
 * returns rows at or after its `notBefore` cutoff, and the next successful
 * lookup rewrites a stale row. There is no behaviour beyond [toPlace], so a hit
 * is indistinguishable from a fresh provider result.
 */
data class GeocodedAddress(
    val queryHash: String,
    val location: LatLng,
    val displayName: String,
    val providerRef: String?,
    val provider: String,
    val createdAt: Instant,
) {
    fun toPlace(): GeocodedPlace = GeocodedPlace(
        location = location,
        displayName = displayName,
        providerRef = providerRef,
    )
}
