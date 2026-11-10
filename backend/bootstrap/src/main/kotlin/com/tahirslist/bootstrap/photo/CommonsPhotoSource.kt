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
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Wikimedia Commons as a free-photo source (SC-231, UC-03 step 5b).
 *
 * The search is by name, and a name search is a guess, so the rule is: the file
 * title must name the venue ([VenueMatch.namesAgree]) or the candidate is not
 * offered at all. That is why most listings get nothing here — an honest gap is
 * better than a photo of a different restaurant.
 *
 * OSM's `image` / `wikimedia_commons` tags would be a stronger source, but the
 * matched feature's tags reach us only with SC-227, so this source stands alone
 * for now. Nothing here pretends otherwise.
 */
class CommonsPhotoSource(
    private val userAgent: String = "TahirsList/0.1 (cover photo pass; https://github.com/Losers-Lab/tahirs-list)",
    timeout: Duration = 20.seconds,
    private val api: String = "https://commons.wikimedia.org/w/api.php",
) : PhotoCandidateSource {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client = HttpClient(CIO) {
        followRedirects = true
        install(HttpTimeout) {
            requestTimeoutMillis = timeout.inWholeMilliseconds
            connectTimeoutMillis = timeout.inWholeMilliseconds
        }
    }

    override suspend fun freeCandidates(listing: RestaurantListing): List<PhotoCandidate> {
        val query = listing.name.replace("\"", " ") + " filetype:bitmap"
        val body = try {
            val response = client.get(api) {
                url {
                    parameters.append("action", "query")
                    parameters.append("format", "json")
                    parameters.append("generator", "search")
                    parameters.append("gsrsearch", query)
                    parameters.append("gsrnamespace", "6")
                    parameters.append("gsrlimit", "5")
                    parameters.append("prop", "imageinfo")
                    parameters.append("iiprop", "url|extmetadata")
                    parameters.append("iiurlwidth", "1600")
                }
                header(HttpHeaders.UserAgent, userAgent)
            }
            if (!response.status.isSuccess()) return emptyList()
            response.bodyAsText()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return emptyList()
        }

        val pages = runCatching {
            json.parseToJsonElement(body).jsonObject["query"]?.jsonObject
                ?.get("pages")?.jsonObject
                ?.values?.map { it.jsonObject }
                .orEmpty()
        }.getOrDefault(emptyList())

        return pages.mapNotNull { page -> toCandidate(listing, page) }
    }

    private fun toCandidate(listing: RestaurantListing, page: JsonObject): PhotoCandidate? {
        val title = page["title"]?.jsonPrimitive?.contentOrNullSafe() ?: return null
        if (!VenueMatch.namesAgree(listing.name, VenueMatch.fileTitle(title))) return null

        val info = page["imageinfo"]?.jsonArray?.firstOrNull()?.jsonObject ?: return null
        val meta = info["extmetadata"]?.jsonObject

        val licenceText = meta?.get("LicenseShortName")?.jsonObject?.get("value")
            ?.jsonPrimitive?.contentOrNullSafe()
        val licence = CommonsLicence.parse(licenceText)
        val author = meta?.get("Artist")?.jsonObject?.get("value")
            ?.jsonPrimitive?.contentOrNullSafe()
            ?.let(::stripHtml)
            ?.takeIf { it.isNotBlank() }

        val fetchUrl = info["thumburl"]?.jsonPrimitive?.contentOrNullSafe()
            ?: info["url"]?.jsonPrimitive?.contentOrNullSafe()
        val pageUrl = info["descriptionurl"]?.jsonPrimitive?.contentOrNullSafe()

        val credit = listOfNotNull(author, "Wikimedia Commons", licenceText).joinToString(", ")
        return PhotoCandidate(
            sourceKind = PhotoSourceKind.WIKIMEDIA,
            fetchUrl = fetchUrl,
            // A Commons photo always carries a licence or the candidate is refused
            // by AttachCoverPhoto; the credit is the human-readable form.
            attribution = credit.ifBlank { "Wikimedia Commons" },
            licence = licence,
            author = author,
            sourceUrl = pageUrl,
            reason = when {
                licence == null -> "licence '${licenceText ?: "unknown"}' is not accepted"
                licence.requiresAttribution && author.isNullOrBlank() -> "attribution missing"
                fetchUrl == null -> "no image url"
                else -> null
            },
        )
    }

    private fun stripHtml(raw: String): String =
        raw.replace(Regex("<[^>]+>"), " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun kotlinx.serialization.json.JsonPrimitive.contentOrNullSafe(): String? =
        runCatching { content }.getOrNull()?.takeIf { it.isNotBlank() }
}
