package com.tahirslist.bootstrap.photo

import com.tahirslist.application.photo.PhotoCandidate
import com.tahirslist.application.photo.PhotoCandidateSource
import com.tahirslist.domain.restaurant.PhotoSourceKind
import com.tahirslist.domain.restaurant.RestaurantListing
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Google Places as the last photo source (SC-231, L-4).
 *
 * Two rules shape this adapter:
 *
 *  - **it never becomes a permanent copy.** The bytes are fetched through a media
 *    URL and stored as a cache with an expiry chosen by [AttachCoverPhoto]; the
 *    place id is kept so a refresh does not pay for a second search.
 *  - **a near-miss is refused, not taken.** The returned place must agree on the
 *    venue name *and* corroborate the address; otherwise the candidate carries the
 *    reason and no photo is fetched.
 *
 * The API key is held in memory and appears only inside the transient fetch URL,
 * which is never persisted. It is never logged.
 */
class GooglePlacesPhotoSource(
    private val apiKey: String,
    timeout: Duration = 20.seconds,
    private val baseUrl: String = "https://places.googleapis.com/v1",
) : PhotoCandidateSource {

    init {
        require(apiKey.isNotBlank()) { "A Places source needs an API key." }
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client = HttpClient(CIO) {
        followRedirects = true
        install(HttpTimeout) {
            requestTimeoutMillis = timeout.inWholeMilliseconds
            connectTimeoutMillis = timeout.inWholeMilliseconds
        }
    }

    override suspend fun placesCandidate(listing: RestaurantListing, placeId: String?): PhotoCandidate? {
        val place = if (placeId != null) {
            details(placeId)
        } else {
            search(listing)
        }
        return place?.let { toCandidate(listing, it) }
    }

    private suspend fun search(listing: RestaurantListing): JsonObject? {
        val query = listOfNotNull(listing.name, listing.address, listing.city).joinToString(", ")
        val payload = buildJsonObject {
            put("textQuery", query)
            put("maxResultCount", 1)
            put("languageCode", "en")
        }
        return try {
            val response = client.post("$baseUrl/places:searchText") {
                contentType(ContentType.Application.Json)
                header("X-Goog-Api-Key", apiKey)
                header(
                    "X-Goog-FieldMask",
                    "places.id,places.displayName,places.formattedAddress,places.photos",
                )
                setBody(payload.toString())
            }
            if (!response.status.isSuccess()) return null
            json.parseToJsonElement(response.bodyAsText()).jsonObject["places"]
                ?.jsonArray?.firstOrNull()?.jsonObject
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun details(placeId: String): JsonObject? = try {
        val response = client.get("$baseUrl/places/$placeId") {
            url {
                parameters.append("fields", "id,displayName,formattedAddress,photos")
                parameters.append("languageCode", "en")
            }
            header("X-Goog-Api-Key", apiKey)
        }
        if (!response.status.isSuccess()) return null
        json.parseToJsonElement(response.bodyAsText()).jsonObject
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private fun toCandidate(listing: RestaurantListing, place: JsonObject): PhotoCandidate? {
        val foundName = place["displayName"]?.jsonObject?.get("text")?.jsonPrimitive?.content
            ?: return null
        val foundAddress = place["formattedAddress"]?.jsonPrimitive?.content.orEmpty()
        val photos = place["photos"]?.jsonArray ?: JsonArray(emptyList())

        val mismatch = when {
            !VenueMatch.namesAgree(listing.name, foundName) ->
                "near-miss: '$foundName' does not name '${listing.name}'"
            !VenueMatch.addressAgrees(listing.address, listing.postal, foundAddress) ->
                "near-miss: '$foundAddress' does not corroborate '${listing.address}'"
            photos.isEmpty() -> "the place has no photos"
            else -> null
        }

        if (mismatch != null) {
            return PhotoCandidate(
                sourceKind = PhotoSourceKind.GOOGLE_PLACES,
                attribution = "Google Maps",
                reason = mismatch,
            )
        }

        val resolvedPlaceId = place["id"]?.jsonPrimitive?.content ?: return null
        val photo = photos.first().jsonObject
        val photoName = photo["name"]?.jsonPrimitive?.content ?: return null
        val author = photo["authorAttributions"]?.jsonArray
            ?.firstOrNull()?.jsonObject?.get("displayName")?.jsonPrimitive?.content

        return PhotoCandidate(
            sourceKind = PhotoSourceKind.GOOGLE_PLACES,
            fetchUrl = "$baseUrl/$photoName/media?maxWidthPx=1600&key=$apiKey",
            attribution = listOfNotNull("Google Maps", author).joinToString(", "),
            author = author,
            // The place link is the credit a reader can follow; the media URL with
            // the key in it is never stored.
            sourceUrl = "https://www.google.com/maps/place/?q=place_id:$resolvedPlaceId",
            placeId = resolvedPlaceId,
            photoName = photoName,
        )
    }
}
