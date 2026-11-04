package com.tahirslist.geocoding

import com.tahirslist.application.geo.GeocodedPlace
import com.tahirslist.application.geo.GeocoderPort
import com.tahirslist.application.geo.GeocodingException
import com.tahirslist.domain.restaurant.LatLng
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Duration
import kotlin.coroutines.cancellation.CancellationException
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
 * Server-side only. The transport is a **Ktor client (CIO)** with the response
 * decoded through kotlinx.serialization's JSON tree, so no blocking JDK client
 * or Jackson is involved. The adapter is cheap to configure
 * (constructor-injected [baseUrl] + [timeout], or an injected [HttpClient]) so
 * swapping to the premium Google adapter later is config, not code — see the
 * GeocoderPort KDoc for the swap contract. The public service is best-effort
 * (no SLA); production self-hosting Photon is a later scaling decision, not this
 * task. OSM data is ODbL — attribution/share-alike on derived fields is an open
 * founder decision, flagged not decided here.
 *
 * The GeoJSON body is read as a [JsonElement] tree rather than bound to DTOs on
 * purpose: the port's contract (pinned by tests) treats a non-array `features`
 * member or a first feature without geometry as "no match" (null), while only
 * unparseable JSON / an empty body are provider failures.
 */
class PhotonGeocoder(
    private val baseUrl: String = "https://photon.komoot.io/api/",
    private val timeout: Duration = Duration.ofSeconds(5),
    httpClient: HttpClient? = null,
) : GeocoderPort {

    /** Owned client (when not injected); NO follow-redirects-by-default surprises, plain GET only. */
    private val client: HttpClient = httpClient ?: HttpClient(CIO) {
        // Status handling is explicit below: only HTTP 200 counts as success.
        expectSuccess = false
        // Match the previous JDK client's default (never follow redirects):
        // a redirect is a non-200 provider response, not a transparent success.
        followRedirects = false
        install(HttpTimeout) {
            requestTimeoutMillis = timeout.toMillis()
            connectTimeoutMillis = timeout.toMillis()
        }
    }

    override suspend fun geocode(address: String): GeocodedPlace? {
        val encoded = URLEncoder.encode(address, StandardCharsets.UTF_8)
        val url = "${baseUrl.trimEnd('/')}/?q=$encoded"

        val (status, body) = try {
            val response = client.get(url)
            response.status to response.bodyAsText()
        } catch (e: IOException) {
            // Covers connect failures (java.net.ConnectException), Ktor's
            // request/connect timeouts, and body-read failures.
            throw GeocodingException("Photon geocoder request failed for address: $address", e)
        } catch (e: CancellationException) {
            // Caller cancelled: preserve structured concurrency, do not mask it
            // as a provider outage.
            throw e
        }

        if (status != HttpStatusCode.OK) {
            throw GeocodingException("Photon geocoder returned HTTP ${status.value} for address: $address")
        }

        return parse(body, address)
    }

    /** Maps Photon's GeoJSON FeatureCollection to a [GeocodedPlace] (or null for no match). */
    private fun parse(body: String, address: String): GeocodedPlace? {
        // An empty body is a provider failure, not a silent "no match".
        if (body.isBlank()) {
            throw GeocodingException("Photon geocoder returned an empty response for address: $address")
        }

        val root: JsonElement = try {
            Json.parseToJsonElement(body)
        } catch (e: SerializationException) {
            throw GeocodingException("Photon geocoder returned an unparseable response for address: $address", e)
        }

        // A JSON value that is not a FeatureCollection object has no features.
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
        )
    }

    /** Best-effort human label: the OSM name, else a "street, city" fallback. */
    private fun displayName(props: JsonObject?): String {
        val name = text(props, "name")
        if (!name.isNullOrBlank()) return name
        return listOfNotNull(text(props, "street"), text(props, "city"))
            .filter { it.isNotBlank() }
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
}
