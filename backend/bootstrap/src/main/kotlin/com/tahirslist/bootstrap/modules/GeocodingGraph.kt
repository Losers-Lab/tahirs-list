package com.tahirslist.bootstrap.modules

import com.tahirslist.application.geo.CachingGeocoder
import com.tahirslist.application.geo.CheckGeocoderHealth
import com.tahirslist.application.geo.GeocodedAddressStore
import com.tahirslist.application.geo.GeocoderPort
import com.tahirslist.bootstrap.config.AppConfig
import com.tahirslist.geocoding.PhotonGeocoder
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/** Koin qualifier for the uncached provider binding (D-2). */
object GeocodingQualifiers {
    const val UPSTREAM = "geocoder-upstream"
}

/**
 * The geocoding composition decisions in one testable place (SC-198, D-1/D-2).
 *
 * `AppModules` binds exactly these: the default [GeocoderPort] is the caching
 * decorator, the upstream binding is the raw provider, and the ingestion
 * pre-flight receives the upstream one so a warm cache can never report a
 * healthy geocoder while Photon is down. [GeocodingWiringTest] proves each.
 */
internal object GeocodingGraph {

    fun upstream(config: AppConfig): GeocoderPort =
        PhotonGeocoder(baseUrl = config.mcp.geocoderBaseUrl)

    /** `0` or negative means a row never goes stale (D-6). */
    fun ttl(days: Int): Duration = if (days <= 0) Duration.INFINITE else days.days

    fun cached(
        upstream: GeocoderPort,
        store: GeocodedAddressStore,
        ttl: Duration,
    ): CachingGeocoder = CachingGeocoder(delegate = upstream, store = store, ttl = ttl)

    fun healthProbe(upstream: GeocoderPort): CheckGeocoderHealth =
        CheckGeocoderHealth(geocoder = upstream)
}
