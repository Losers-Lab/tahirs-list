package com.tahirslist.bootstrap.modules

import com.tahirslist.application.geo.CachingGeocoder
import com.tahirslist.application.geo.CheckGeocoderHealth
import com.tahirslist.application.geo.GeocodedAddressStore
import com.tahirslist.application.geo.GeocoderPort
import com.tahirslist.bootstrap.config.AppConfig
import com.tahirslist.geocoding.PhotonGeocoder
import com.tahirslist.persistence.geo.ExposedGeocodedAddressStore
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** Koin qualifier for the uncached provider binding (D-2). */
object GeocodingQualifiers {
    const val UPSTREAM = "geocoder-upstream"
}

/**
 * The geocoding composition decisions in one testable place (SC-198, D-1/D-2).
 *
 * The default [GeocoderPort] is the caching decorator, the named upstream
 * binding is the raw provider, and the ingestion pre-flight receives the
 * upstream one so a warm cache can never report a healthy geocoder while the
 * provider is down.
 *
 * [upstreamFactory] and [storeOverride] on [geocodingModule] exist so
 * `GeocodingWiringTest` can start this exact module with fakes; production
 * always uses the defaults.
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

/**
 * The Koin module `appModules` installs. Pulled out (and parameterised) so the
 * wiring itself is testable — `GeocodingWiringTest` starts this module, not a
 * copy of its bindings.
 */
internal fun geocodingModule(
    config: AppConfig,
    upstreamFactory: () -> GeocoderPort = { GeocodingGraph.upstream(config) },
    storeOverride: GeocodedAddressStore? = null,
): Module = module {
    if (storeOverride != null) {
        single<GeocodedAddressStore> { storeOverride }
    } else {
        single<GeocodedAddressStore> { ExposedGeocodedAddressStore(get()) }
    }
    single<GeocoderPort>(named(GeocodingQualifiers.UPSTREAM)) { upstreamFactory() }
    single<GeocoderPort> {
        GeocodingGraph.cached(
            upstream = get(named(GeocodingQualifiers.UPSTREAM)),
            store = get(),
            ttl = GeocodingGraph.ttl(config.geocodeCache.ttlDays),
        )
    }
    single { GeocodingGraph.healthProbe(get(named(GeocodingQualifiers.UPSTREAM))) }
}
