package com.tahirslist.geocoding

import com.tahirslist.application.geo.GeocodedPlace
import com.tahirslist.application.geo.GeocoderPort
import com.tahirslist.application.geo.GeocodingException
import com.tahirslist.domain.restaurant.LatLng
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

/**
 * [GeocoderPort] adapter for **Photon** (photon.komoot.io) — the $0, no-key,
 * OSM-backed default geocoder (docs/reviews/sc-138-external-services.md §2.1).
 *
 * ## Public demo-server policy (SC-227)
 *
 * komoot publishes **no numeric limit**. Its terms say the API may be used while
 * requests "stay in a reasonable limit", that "extensive usage will be throttled
 * or completely banned", and that availability is not guaranteed. The public
 * server is one small instance; the project's own answer to volume is
 * self-hosting. The community convention (e.g. the CRAN `photon` package) is
 * **1 request/second**, and the operator has publicly asked heavy clients to
 * slow down even at that rate.
 *
 * So this adapter is deliberately conservative:
 *  - one request in flight at a time ([Mutex]), spaced by [minInterval]
 *    (default 1 s) — no bursts, regardless of how many callers ask at once;
 *  - asks for one result (`limit=1`), because only the first feature is used;
 *  - retries only transient outcomes (HTTP 429/500/502/503/504, timeouts,
 *    connect/read failures), honouring a bounded `Retry-After`, else
 *    exponential backoff 1s → 2s → 4s, capped by [maxAttempts];
 *  - never retries permanent client errors (400/401/403/404) or malformed
 *    success bodies;
 *  - propagates structured cancellation unchanged.
 *
 * Retry exhaustion is still a hard failure: ingestion must abort before writing
 * a completed run rather than invent or drop a location.
 *
 * Server-side only. The transport is a **Ktor client (CIO)**; the GeoJSON body
 * is read as a JSON tree rather than bound to DTOs, so a non-array `features`
 * member or a first feature without geometry is "no match" (null), while only
 * unparseable JSON / an empty body are provider failures.
 */
class PhotonGeocoder(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val timeout: Duration = 5.seconds,
    httpClient: HttpClient? = null,
    /** Minimum spacing between requests to the same host. Public demo: 1 s. */
    private val minInterval: Duration = 1.seconds,
    /** Total attempts (the first request included) before giving up. */
    private val maxAttempts: Int = 4,
    /** A `Retry-After` above this is clamped, so one header cannot stall a run. */
    private val maxRetryAfter: Duration = 60.seconds,
    /** Injected in tests so pacing/backoff cost no real time. */
    private val sleeper: suspend (Duration) -> Unit = { delay(it) },
    /** Monotonic milliseconds; injected in tests alongside [sleeper]. */
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000 },
) : GeocoderPort {

    init {
        require(minInterval >= Duration.ZERO) { "minInterval must not be negative" }
        require(maxAttempts >= 1) { "maxAttempts must be at least 1" }
        require(maxRetryAfter > Duration.ZERO) { "maxRetryAfter must be positive" }
    }

    /** Owned client (when not injected); plain GET only, explicit status handling. */
    private val client: HttpClient = httpClient ?: HttpClient(CIO) {
        expectSuccess = false
        followRedirects = false
        install(HttpTimeout) {
            requestTimeoutMillis = timeout.inWholeMilliseconds
            connectTimeoutMillis = timeout.inWholeMilliseconds
        }
    }

    private val pace = Mutex()

    @Volatile
    private var lastRequestAt: Long? = null

    override suspend fun geocode(address: String): GeocodedPlace? {
        var attemptNumber = 1
        while (true) {
            val outcome = paced { attempt(address) }
            when (outcome) {
                is Attempt.Success -> return outcome.place

                is Attempt.Fatal -> throw GeocodingException(
                    failureMessage(outcome.reason, address),
                    outcome.cause,
                )

                is Attempt.Retryable -> {
                    if (attemptNumber >= maxAttempts) {
                        throw GeocodingException(
                            failureMessage(
                                "${outcome.reason} after $attemptNumber attempt(s)",
                                address,
                            ),
                            outcome.cause,
                        )
                    }
                    val wait = outcome.retryAfter?.coerceAtMost(maxRetryAfter) ?: backoff(attemptNumber)
                    sleeper(wait)
                    attemptNumber++
                }
            }
        }
    }

    private suspend fun <T> paced(block: suspend () -> T): T = pace.withLock {
        val previous = lastRequestAt
        if (previous != null) {
            val remaining = minInterval.inWholeMilliseconds - (nowMillis() - previous)
            if (remaining > 0) sleeper(remaining.milliseconds)
        }
        try {
            block()
        } finally {
            lastRequestAt = nowMillis()
        }
    }

    private suspend fun attempt(address: String): Attempt {
        val encoded = URLEncoder.encode(address, StandardCharsets.UTF_8)
        // Only the first feature is used, so do not ask the demo server for more.
        val url = "${baseUrl.trimEnd('/')}/?q=$encoded&limit=1"

        val response = try {
            client.get(url) {
                header(HttpHeaders.UserAgent, USER_AGENT)
            }
        } catch (e: IOException) {
            return Attempt.Retryable("request failed", null, e)
        } catch (e: CancellationException) {
            throw e
        }

        val status = response.status
        val body = try {
            response.bodyAsText()
        } catch (e: IOException) {
            return Attempt.Retryable("response read failed", null, e)
        } catch (e: CancellationException) {
            throw e
        }

        if (status == HttpStatusCode.OK) {
            return try {
                Attempt.Success(parse(body, address))
            } catch (e: GeocodingException) {
                // parse() already names the provider and address; do not prefix twice.
                Attempt.Fatal(e.message ?: "returned a malformed response", e)
            }
        }

        val reason = "returned HTTP ${status.value}"
        return if (status.value in TRANSIENT_STATUSES) {
            Attempt.Retryable(reason, retryAfter(response.headers[HttpHeaders.RetryAfter]))
        } else {
            Attempt.Fatal(reason)
        }
    }

    /** Transient provider/transport outcomes only; a 4xx stays fatal. */
    private fun retryAfter(header: String?): Duration? =
        header?.trim()?.toLongOrNull()?.takeIf { it > 0 }?.seconds

    private fun backoff(attemptNumber: Int): Duration =
        (1L shl (attemptNumber - 1).coerceAtMost(6)).seconds

    /**
     * One clear sentence. A reason that already names the provider is used as-is,
     * so a parse failure is not wrapped as "Photon geocoder Photon geocoder …".
     */
    private fun failureMessage(reason: String, address: String): String =
        if (reason.startsWith("Photon geocoder")) reason else "Photon geocoder $reason for address: $address"

    /** Maps Photon's GeoJSON FeatureCollection to a [GeocodedPlace] (or null for no match). */
    private fun parse(body: String, address: String): GeocodedPlace? {
        if (body.isBlank()) {
            throw GeocodingException("Photon geocoder returned an empty response for address: $address")
        }

        val root: JsonElement = try {
            Json.parseToJsonElement(body)
        } catch (e: SerializationException) {
            throw GeocodingException("Photon geocoder returned an unparseable response for address: $address", e)
        }

        val features = (root as? JsonObject)?.get("features") as? JsonArray ?: return null
        val feature = features.firstOrNull() as? JsonObject ?: return null

        val coordinates = ((feature["geometry"] as? JsonObject)?.get("coordinates")) as? JsonArray ?: return null
        if (coordinates.size < 2) return null

        // GeoJSON coordinates are [longitude, latitude].
        val lng = coordinates[0].asDoubleOrZero()
        val lat = coordinates[1].asDoubleOrZero()
        val location = try {
            LatLng(lat = lat, lng = lng)
        } catch (e: IllegalArgumentException) {
            throw GeocodingException("Photon geocoder returned invalid coordinates [$lat, $lng] for address: $address", e)
        }

        val props = feature["properties"] as? JsonObject
        return GeocodedPlace(
            location = location,
            displayName = displayName(props),
            providerRef = providerRef(props),
            name = venueName(props),
        )
    }

    /**
     * The provider's name is **venue evidence** only for a POI. Photon also
     * names streets, localities and administrative areas, and the match policy
     * must not accept "Elm Street" as a restaurant name. `osm_key` is the only
     * reliable discriminator in the response; without it, or for a road/place
     * feature, the hit is an address, not a venue.
     */
    private fun venueName(props: JsonObject?): String? {
        val key = text(props, "osm_key")?.lowercase()?.trim() ?: return null
        if (key !in VENUE_OSM_KEYS) return null
        return text(props, "name")?.takeIf { it.isNotBlank() }
    }

    /**
     * A label that carries every part the match policy can check: the OSM name,
     * the house number and street, and the postal code and city. Photon answers
     * a business-name query with the POI and an address query with a house; the
     * caller needs both the name and the address evidence to confirm either.
     */
    private fun displayName(props: JsonObject?): String {
        val name = text(props, "name")
        val street = listOfNotNull(
            text(props, "housenumber"),
            text(props, "street"),
        ).filter { it.isNotBlank() }.joinToString(" ").ifBlank { null }
        val locality = listOfNotNull(
            text(props, "postcode"),
            text(props, "city"),
        ).filter { it.isNotBlank() }.joinToString(" ").ifBlank { null }

        return listOfNotNull(name, street, locality, text(props, "state"), text(props, "country"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .joinToString(", ")
    }

    /** A stable provider reference (e.g. "N/123456" for OSM node 123456) if present. */
    private fun providerRef(props: JsonObject?): String? {
        val osmType = text(props, "osm_type")
        val osmId = text(props, "osm_id")
        return if (!osmType.isNullOrBlank() && !osmId.isNullOrBlank()) "$osmType/$osmId" else null
    }

    private fun text(props: JsonObject?, field: String): String? =
        (props?.get(field) as? JsonPrimitive)?.contentOrNull

    /** Jackson-compatible tolerance: non-numeric/missing coordinate members read as 0.0. */
    private fun JsonElement?.asDoubleOrZero(): Double =
        (this as? JsonPrimitive)?.doubleOrNull ?: 0.0

    private sealed interface Attempt {
        data class Success(val place: GeocodedPlace?) : Attempt
        data class Retryable(val reason: String, val retryAfter: Duration?, val cause: Throwable? = null) : Attempt
        data class Fatal(val reason: String, val cause: Throwable? = null) : Attempt
    }

    private companion object {
        const val DEFAULT_BASE_URL = "https://photon.komoot.io/api/"

        /** Identifies us to the demo server and points at the project; polite, not required. */
        const val USER_AGENT =
            "TahirsListBot/0.1 (+https://github.com/Losers-Lab/tahirs-list; data-curation)"

        /** Retry only what a later attempt can plausibly fix. */
        val TRANSIENT_STATUSES = setOf(408, 429, 500, 502, 503, 504)

        /**
         * OSM keys that name a *venue*. Roads (`highway`), places (`place`),
         * boundaries and waterways carry names too, but those are addresses or
         * areas, not businesses.
         */
        val VENUE_OSM_KEYS = setOf(
            "amenity", "shop", "tourism", "leisure", "craft", "office", "healthcare",
        )
    }
}
