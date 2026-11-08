package com.tahirslist.ingestion.pipeline

import com.tahirslist.application.ingestion.IngestReject
import com.tahirslist.application.ingestion.RejectReason
import com.tahirslist.certifiers.core.HandCutDeclaration
import com.tahirslist.domain.restaurant.CrossContamination
import com.tahirslist.ingestion.DirectoryStandard
import com.tahirslist.ingestion.SourceListing
import com.tahirslist.ingestion.SourceRef
import com.tahirslist.ingestion.SourceTier
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/** One curated row plus the evidence the curator recorded with it. */
data class CuratedListing(
    val listing: SourceListing,
    val crossContamination: CrossContamination,
    val accessedOn: String,
)

data class CuratedRead(
    val listings: List<CuratedListing>,
    val rejects: List<IngestReject>,
)

/** Reads the hand-curated, cited manual batch (D-13: no Zabihah fetch, ever). */
interface CuratedBatchSource {
    fun read(path: Path): CuratedRead
}

/**
 * The manual batch file:
 *
 * ```json
 * {
 *   "batch": "zabihah-sample-2026-11",
 *   "accessDate": "2026-11-08",
 *   "rows": [
 *     {"name": "...", "address": "...", "city": "...", "region": "ON",
 *      "country": "CA", "postal": "M5B 1N8", "url": "https://...",
 *      "crossContamination": "UNCERTAIN"}
 *   ]
 * }
 * ```
 *
 * Validation is per row: an invalid row becomes `INVALID_MANUAL_ENTRY` and the
 * rest of the batch proceeds (UC-02 E1). Nothing here fetches Zabihah; the
 * curator reads the listing and cites the URL.
 */
class JsonCuratedBatchSource(
    private val json: Json = Json { ignoreUnknownKeys = true },
) : CuratedBatchSource {

    override fun read(path: Path): CuratedRead {
        val file = json.decodeFromString<CuratedBatchFile>(Files.readString(path, StandardCharsets.UTF_8))
        require(file.rows.isNotEmpty()) { "the curated batch has no rows" }

        val listings = mutableListOf<CuratedListing>()
        val rejects = mutableListOf<IngestReject>()

        file.rows.forEachIndexed { index, row ->
            val problem = validate(row)
            if (problem != null) {
                rejects += IngestReject(
                    reason = RejectReason.INVALID_MANUAL_ENTRY,
                    detail = "row ${index + 1}: $problem",
                    source = SOURCE,
                    name = row.name,
                )
                return@forEachIndexed
            }
            val crossContamination = row.crossContamination
                ?.let { runCatching { CrossContamination.valueOf(it) }.getOrNull() }
                ?: CrossContamination.DEFAULT
            listings += CuratedListing(
                listing = SourceListing(
                    source = SourceRef(
                        certifierId = SOURCE,
                        recordId = recordId(row.url),
                        url = row.url,
                    ),
                    standard = DirectoryStandard(
                        tier = SourceTier.TIER_3,
                        certifier = "Zabihah (curated)",
                        handCut = HandCutDeclaration.UNSPECIFIED,
                    ),
                    name = row.name.trim(),
                    addressLines = listOf(row.address.trim()),
                    city = row.city.trim(),
                    region = row.region.trim(),
                    postalCode = row.postal?.trim(),
                    country = row.country.trim(),
                    latitude = null,
                    longitude = null,
                    phone = null,
                    website = row.url,
                    certificateRef = null,
                    certificationStatus = "Curated citation",
                    expiresOn = null,
                    providerPlaceId = null,
                    category = "Restaurant",
                    warnings = listOf("curated Tier-3 row: citation required, no automatic verification"),
                ),
                crossContamination = crossContamination,
                accessedOn = file.accessDate,
            )
        }
        return CuratedRead(listings, rejects)
    }

    private fun validate(row: CuratedRow): String? = when {
        row.name.isBlank() -> "name is required"
        row.address.isBlank() -> "address is required"
        row.city.isBlank() -> "city is required"
        row.region.isBlank() -> "region is required"
        row.country.isBlank() -> "country is required"
        !row.url.startsWith("https://") || row.url.length <= "https://".length ->
            "url must be an https citation"
        row.crossContamination != null &&
            runCatching { CrossContamination.valueOf(row.crossContamination) }.isFailure ->
            "crossContamination must be one of ${CrossContamination.entries.joinToString()}"
        else -> null
    }

    private fun recordId(url: String): String =
        "z-" + MessageDigest.getInstance("SHA-256")
            .digest(url.trim().toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(16)

    private companion object {
        const val SOURCE = "manual-zabihah"
    }
}

@Serializable
private data class CuratedBatchFile(
    val batch: String,
    val accessDate: String,
    val rows: List<CuratedRow>,
)

@Serializable
private data class CuratedRow(
    val name: String = "",
    val address: String = "",
    val city: String = "",
    val region: String = "",
    val country: String = "",
    val postal: String? = null,
    val url: String = "",
    val crossContamination: String? = null,
)
