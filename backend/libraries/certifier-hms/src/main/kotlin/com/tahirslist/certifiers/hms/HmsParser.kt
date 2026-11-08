package com.tahirslist.certifiers.hms

import com.tahirslist.certifiers.core.SourceParseException
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * Parses the HMS certified-listing page.
 *
 * HMS server-renders every record twice: as `div.MagicListing[data-*]` (the
 * table) and as an escaped JSON array in `input#Map_Hidden` (the map, with
 * coordinates, phone, and expiry). The two are joined on the record id. The
 * parser is deliberately tolerant: a missing map entry still yields a record
 * with an address, just without coordinates.
 */
object HmsParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(html: String): List<HmsEstablishment> {
        val document = Jsoup.parse(html)
        val mapRecords = mapRecords(document)
        val seen = LinkedHashMap<String, HmsEstablishment>()

        document.select("div.MagicListing").forEach { element ->
            val id = element.attr("data-id").trim()
            if (id.isEmpty()) return@forEach

            val map = mapRecords[id]
            val name = element.attr("data-name").ifBlank { map?.name.orEmpty() }.trim()
            if (name.isEmpty()) return@forEach

            val line = element.attr("data-address").ifBlank { map?.address.orEmpty() }.trim()
            val state = element.attr("data-state-name").ifBlank { map?.stateName.orEmpty() }.trim()
            val city = element.attr("data-city-name").ifBlank { map?.cityName.orEmpty() }.trim()
            val statusLabel = element.attr("data-status-name").ifBlank { map?.statusName.orEmpty() }.trim()

            seen.putIfAbsent(
                id,
                HmsEstablishment(
                    id = id,
                    name = name,
                    category = HmsCategory.fromLabel(
                        element.attr("data-category").ifBlank { map?.category.orEmpty() },
                    ),
                    address = HmsAddress(
                        line = line.ifBlank { null },
                        city = city.ifBlank { deriveCity(line, state) },
                        county = element.attr("data-county-name").trim().ifBlank { null },
                        state = state.ifBlank { null },
                        country = element.attr("data-country-name").trim().ifBlank { null },
                    ),
                    phone = map?.phone?.trim()?.ifBlank { null },
                    location = map?.let { HmsLocation.of(it.latitude, it.longitude) },
                    status = HmsCertificationStatus.fromLabel(statusLabel),
                    statusLabel = statusLabel.ifBlank { null },
                    expiresOn = map?.expires?.let(::parseDate),
                    listedOn = parseDate(element.attr("data-created-datetime")),
                    featureIds = element.attr("data-feature-ids")
                        .split(',')
                        .map { it.trim() }
                        .filter { it.isNotEmpty() },
                ),
            )
        }

        if (seen.isEmpty()) {
            throw SourceParseException(
                source = "hms",
                message = "HMS listing contained no parsable records (markup may have changed)",
            )
        }
        return seen.values.toList()
    }

    private fun mapRecords(document: Document): Map<String, MapRecord> {
        val raw = document.selectFirst("input#Map_Hidden")?.attr("value")?.trim()
        if (raw.isNullOrEmpty()) return emptyMap()

        val array = runCatching { json.parseToJsonElement(raw) as? JsonArray }.getOrNull()
            ?: return emptyMap()

        return array.mapNotNull { it as? JsonObject }
            .mapNotNull { obj ->
                val id = obj.text("ID") ?: return@mapNotNull null
                id to MapRecord(
                    name = obj.text("Name").orEmpty(),
                    category = obj.text("Category").orEmpty(),
                    address = obj.text("Address").orEmpty(),
                    phone = obj.text("Phone"),
                    latitude = obj.double("Latitude"),
                    longitude = obj.double("Longitude"),
                    expires = obj.text("Expires"),
                    statusName = obj.text("StatusName"),
                    cityName = obj.text("CityName").orEmpty(),
                    stateName = obj.text("StateName").orEmpty(),
                )
            }
            .toMap()
    }

    /**
     * When `data-city-name` is blank (HMS leaves it empty on some rows), recover
     * the city from the address line. Never invents one: returns null if the
     * segment looks like a state/ZIP rather than a city.
     */
    internal fun deriveCity(line: String, state: String): String? {
        val parts = line.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val candidate = when {
            // street, city, state/zip
            parts.size >= 3 -> parts[parts.size - 2]
            // city, state/zip — but only when the first segment is not a street
            parts.size == 2 && parts[0].none { it.isDigit() } -> parts[0]
            else -> return null
        }
        if (state.isNotBlank() && candidate.equals(state, ignoreCase = true)) return null
        if (candidate.matches(Regex("(?i)^[A-Z]{2}\\s+\\d{5}(-\\d{4})?$"))) return null
        return candidate.ifBlank { null }
    }

    private fun parseDate(value: String?): LocalDate? {
        val text = value?.trim().orEmpty()
        if (text.isEmpty()) return null
        return runCatching { LocalDateTime.parse(text).toLocalDate() }.getOrNull()
            ?: runCatching { LocalDate.parse(text) }.getOrNull()
    }

    private fun JsonObject.text(field: String): String? =
        (this[field] as? JsonPrimitive)?.contentOrNull?.trim()?.ifBlank { null }

    private fun JsonObject.double(field: String): Double? =
        (this[field] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()

    private data class MapRecord(
        val name: String,
        val category: String,
        val address: String,
        val phone: String?,
        val latitude: Double?,
        val longitude: Double?,
        val expires: String?,
        val statusName: String?,
        val cityName: String,
        val stateName: String,
    )
}
