package com.tahirslist.application.geo

import com.tahirslist.domain.restaurant.LatLng

/**
 * The result of resolving an address to a coordinate via [GeocoderPort]. The
 * provider-agnostic shape an adapter maps its response into, regardless of which
 * provider is behind the port.
 *
 * @param location the WGS84 point (stored as PostGIS `geography(Point,4326)`).
 * @param displayName a human-readable label for the resolved place.
 * @param providerRef the provider's identifier for this place (e.g. `N/123456`
 *        for an OSM node), used for dedupe/reference — may be null if the
 *        provider returns none.
 * @param name the provider's own **name** for this place (an OSM POI name),
 *        or null when the hit is an address only. Name evidence is read from
 *        this field, never from [displayName], so a street cannot pass for a
 *        venue name.
 */
data class GeocodedPlace(
    val location: LatLng,
    val displayName: String,
    val providerRef: String?,
    val name: String? = null,
)
