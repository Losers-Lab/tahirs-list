package com.tahirslist.application.geo

import kotlin.coroutines.cancellation.CancellationException

/** The outcome of the pre-flight geocoder check. */
sealed interface GeocoderHealth {
    data class Available(val place: GeocodedPlace) : GeocoderHealth
    data class Unavailable(val reason: String) : GeocoderHealth
}

/**
 * One cheap pre-flight query before an ingestion run fetches any directory.
 *
 * A run can pull tens of megabytes from three certifiers before it ever needs a
 * coordinate. If the geocoder is down, that work is wasted and the run cannot
 * produce a single acceptable location. This check runs first; `RunSampleIngestion`
 * aborts before the first directory request when it returns [GeocoderHealth.Unavailable].
 *
 * The probe is a normal [GeocoderPort] call with a stable, well-known address, so
 * it exercises the same provider, credentials, and adapter path as the real run.
 */
class CheckGeocoderHealth(
    private val geocoder: GeocoderPort,
    private val probeAddress: String = DEFAULT_PROBE_ADDRESS,
) {
    suspend fun execute(): GeocoderHealth = try {
        val place = geocoder.geocode(probeAddress)
        if (place == null) {
            GeocoderHealth.Unavailable("pre-flight query returned no match for '$probeAddress'")
        } else {
            GeocoderHealth.Available(place)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: GeocodingException) {
        GeocoderHealth.Unavailable(e.message ?: "geocoder unavailable during pre-flight")
    }

    companion object {
        const val DEFAULT_PROBE_ADDRESS: String = "New York, USA"
    }
}
