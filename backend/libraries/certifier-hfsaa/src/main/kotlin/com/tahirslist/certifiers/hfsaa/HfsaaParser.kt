package com.tahirslist.certifiers.hfsaa

import com.tahirslist.certifiers.core.SourceParseException
import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Parsed entries plus an honest count of rows that could not be used. */
data class HfsaaParseResult(
    val entries: List<HfsaaEntry>,
    val skipped: Int,
)

/**
 * Parses the array returned by HFSAA's public locator RPC. Unknown fields are
 * ignored so the platform can add columns without breaking the library.
 */
object HfsaaParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    fun parse(body: String): HfsaaParseResult {
        val rows = try {
            json.decodeFromString<List<HfsaaEntryDto>>(body)
        } catch (e: Exception) {
            throw SourceParseException("hfsaa", "HFSAA locator returned unparseable JSON", e)
        }

        val entries = rows.mapNotNull { dto ->
            val id = dto.id?.trim()?.ifBlank { null } ?: return@mapNotNull null
            val name = dto.name?.trim()?.ifBlank { null } ?: return@mapNotNull null
            HfsaaEntry(
                id = id,
                type = HfsaaEntryType.fromLabel(dto.type),
                name = name,
                certificateId = dto.certificateId?.trim()?.ifBlank { null },
                certificationStatus = dto.certificationStatus?.trim()?.ifBlank { null },
                chapter = dto.chapterName?.trim()?.ifBlank { null },
                cuisine = dto.cuisineType?.trim()?.ifBlank { null },
                address = HfsaaAddress(
                    line = dto.address?.trim()?.ifBlank { null },
                    city = dto.city?.trim()?.ifBlank { null },
                    state = dto.state?.trim()?.ifBlank { null },
                    postalCode = dto.zipCode?.trim()?.ifBlank { null },
                ),
                location = HfsaaLocation.of(dto.latitude, dto.longitude),
                phone = dto.storefrontNumber?.trim()?.ifBlank { null },
                websiteUrl = dto.websiteUrl?.trim()?.ifBlank { null },
                deliveryUrl = dto.deliveryUrl?.trim()?.ifBlank { null },
                businessHours = dto.businessHours?.trim()?.ifBlank { null },
                googlePlaceId = dto.googlePlaceId?.trim()?.ifBlank { null },
                createdAt = dto.createdAt?.let(::parseInstant),
                updatedAt = dto.updatedAt?.let(::parseInstant),
            )
        }

        if (rows.isNotEmpty() && entries.isEmpty()) {
            throw SourceParseException("hfsaa", "HFSAA locator returned rows but none were usable")
        }
        return HfsaaParseResult(entries = entries, skipped = rows.size - entries.size)
    }

    private fun parseInstant(value: String): Instant? = runCatching { Instant.parse(value) }.getOrNull()

    @Serializable
    private data class HfsaaEntryDto(
        val id: String? = null,
        val type: String? = null,
        val name: String? = null,
        @SerialName("certificate_id") val certificateId: String? = null,
        @SerialName("certification_status") val certificationStatus: String? = null,
        @SerialName("chapter_name") val chapterName: String? = null,
        @SerialName("cuisine_type") val cuisineType: String? = null,
        val address: String? = null,
        val city: String? = null,
        val state: String? = null,
        @SerialName("zip_code") val zipCode: String? = null,
        val latitude: Double? = null,
        val longitude: Double? = null,
        @SerialName("storefront_number") val storefrontNumber: String? = null,
        @SerialName("website_url") val websiteUrl: String? = null,
        @SerialName("delivery_url") val deliveryUrl: String? = null,
        @SerialName("business_hours") val businessHours: String? = null,
        @SerialName("google_place_id") val googlePlaceId: String? = null,
        @SerialName("created_at") val createdAt: String? = null,
        @SerialName("updated_at") val updatedAt: String? = null,
    )
}
