package com.tahirslist.certifiers.hma

import com.tahirslist.certifiers.core.SourceParseException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

data class HmaParseResult(
    val outlets: List<HmaCertifiedOutlet>,
    val skipped: Int,
)

/**
 * Parses the WordPress REST payload for HMA's certified-restaurant child pages.
 *
 * The address, phone, and website live in Elementor icon-list markup. We anchor
 * on the Font Awesome icon class rather than on element order, so unrelated
 * Elementor edits do not shift fields.
 */
object HmaParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(body: String): HmaParseResult {
        val pages = try {
            json.decodeFromString<List<HmaPageDto>>(body)
        } catch (e: Exception) {
            throw SourceParseException("hma", "HMA WordPress API returned unparseable JSON", e)
        }

        val outlets = pages.mapNotNull { page ->
            val id = page.id ?: return@mapNotNull null
            val name = text(page.title?.rendered) ?: return@mapNotNull null
            val content = page.content?.rendered.orEmpty()
            val document = Jsoup.parse(content)

            HmaCertifiedOutlet(
                id = id,
                slug = page.slug?.trim().orEmpty(),
                name = name,
                url = page.link?.trim().orEmpty(),
                address = parseAddress(
                    iconValue(document, "fa-map-marker-alt")
                        ?: iconValue(document, "fa-map-marker"),
                ),
                phone = iconValue(document, "fa-phone-alt") ?: iconValue(document, "fa-phone"),
                website = document.selectFirst("i.fa-globe")
                    ?.closest("li")
                    ?.selectFirst("a[href]")
                    ?.attr("href")
                    ?.trim()
                    ?.ifBlank { null },
                certificationStatement = certificationStatement(document),
            )
        }

        if (pages.isNotEmpty() && outlets.isEmpty()) {
            throw SourceParseException("hma", "HMA returned pages but none were usable")
        }
        return HmaParseResult(outlets = outlets, skipped = pages.size - outlets.size)
    }

    /**
     * Splits `street, city, province, postal` without guessing missing parts.
     *
     * HMA writes the same parts in many shapes: a trailing `Canada`, a province
     * glued to the postal code (`ON L6R 1W4`), an abbreviation with a dot
     * (`Ont. L7L 0B6`), the postal before the city (`L4W4G1 Mississauga`) and
     * outright typos (`L6Z2WL`, `L4W 4GI`). Reading from the end and stripping
     * the parts it recognises keeps a country name or a broken postal code out
     * of the city field; when the shape cannot be read, the city stays null
     * rather than holding garbage.
     */
    internal fun parseAddress(raw: String?): HmaAddress {
        val parts = raw?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
        if (parts.isEmpty()) return HmaAddress(null, null, null, null)

        val rest = parts.toMutableList()
        var postal: String? = null
        var region: String? = null
        var city: String? = null

        var index = rest.lastIndex
        while (index >= 0) {
            var segment = rest[index]

            segment = COUNTRY_WORD.replace(segment, " ").trim(' ', ',', '.')
            if (segment.isEmpty()) {
                rest.removeAt(index)
                index--
                continue
            }

            val postalMatch = POSTAL_CODE.find(segment)
            if (postalMatch != null) {
                if (postal == null) postal = postalMatch.value
                segment = segment.removeRange(postalMatch.range).trim(' ', ',', '.')
            }

            val provinceMatch = PROVINCE_WORD.find(segment)
            if (provinceMatch != null) {
                if (region == null) region = provinceCode(provinceMatch.value)
                segment = segment.removeRange(provinceMatch.range).trim(' ', ',', '.')
            }

            if (segment.isEmpty()) {
                rest.removeAt(index)
                index--
                continue
            }

            // A broken postal code ("L6Z2WL") or a unit ("Unit 7A") is not a
            // city; skip it and keep reading towards the street.
            if (looksLikePostalOrUnit(segment)) {
                rest.removeAt(index)
                index--
                continue
            }

            if (isCity(segment)) {
                city = segment
                rest.removeAt(index)
                break
            }

            // The street itself, or something unreadable: keep it in the line.
            rest[index] = segment
            break
        }

        return HmaAddress(
            line = rest.joinToString(", ").ifBlank { null },
            city = city,
            region = region,
            postalCode = postal,
        )
    }

    private fun isCity(value: String): Boolean =
        value.length in 2..60 &&
            value.none { it.isDigit() } &&
            value.any { it.isLetter() } &&
            value.uppercase() !in PROVINCES &&
            !value.equals("Canada", ignoreCase = true)

    /** A short alphanumeric token that is not a valid postal code: a typo or a unit. */
    private fun looksLikePostalOrUnit(value: String): Boolean =
        value.length <= 8 &&
            value.any { it.isDigit() } &&
            value.any { it.isLetter() } &&
            value.count { it == ' ' } <= 1

    private fun provinceCode(value: String): String {
        val key = value.uppercase().replace(".", "").trim()
        return PROVINCES[key] ?: key
    }

    private fun iconValue(document: Document, iconClass: String): String? =
        document.selectFirst("i.$iconClass")
            ?.closest("li")
            ?.selectFirst(".elementor-icon-list-text")
            ?.text()
            ?.trim()
            ?.ifBlank { null }

    private fun certificationStatement(document: Document): String? =
        document.select("p, div.elementor-widget-text-editor")
            .map { it.text().trim() }
            .firstOrNull { it.contains("HMA Certified", ignoreCase = true) && it.length <= 300 }
            ?.ifBlank { null }

    private fun text(html: String?): String? =
        html?.takeIf { it.isNotBlank() }?.let { Jsoup.parse(it).text().trim() }?.ifBlank { null }

    private val POSTAL_CODE = Regex("(?i)(?<![A-Za-z0-9])[A-Z]\\d[A-Z][ -]?\\d[A-Z]\\d(?![A-Za-z0-9])")

    private val COUNTRY_WORD = Regex("(?i)(?<![A-Za-z])(Canada|CAN)(?![A-Za-z])")

    private val PROVINCE_WORD = Regex(
        "(?i)(?<![A-Za-z])(" +
            "Ontario|Ont\\.|ONT|ON|Quebec|Québec|Que\\.|QUE|QC|British Columbia|B\\.C\\.|BC|" +
            "Alberta|Alta\\.|ALTA|AB|Manitoba|Man\\.|MAN|MB|Saskatchewan|Sask\\.|SASK|SK|" +
            "Nova Scotia|N\\.S\\.|NS|New Brunswick|N\\.B\\.|NB|" +
            "Newfoundland and Labrador|Newfoundland|Nfld\\.|NL|" +
            "Prince Edward Island|P\\.E\\.I\\.|PE|Yukon|YT|" +
            "Northwest Territories|N\\.W\\.T\\.|NWT|NT|Nunavut|NU" +
            ")(?![A-Za-z])",
    )

    private val PROVINCES = mapOf(
        "ONTARIO" to "ON", "ONT" to "ON",
        "QUEBEC" to "QC", "QUÉBEC" to "QC", "QUE" to "QC", "QC" to "QC",
        "BRITISH COLUMBIA" to "BC", "BC" to "BC",
        "ALBERTA" to "AB", "ALTA" to "AB", "AB" to "AB",
        "MANITOBA" to "MB", "MAN" to "MB", "MB" to "MB",
        "SASKATCHEWAN" to "SK", "SASK" to "SK", "SK" to "SK",
        "NOVA SCOTIA" to "NS", "NS" to "NS",
        "NEW BRUNSWICK" to "NB", "NB" to "NB",
        "NEWFOUNDLAND AND LABRADOR" to "NL", "NEWFOUNDLAND" to "NL", "NFLD" to "NL", "NL" to "NL",
        "PRINCE EDWARD ISLAND" to "PE", "PEI" to "PE", "PE" to "PE",
        "YUKON" to "YT", "YT" to "YT",
        "NORTHWEST TERRITORIES" to "NT", "NWT" to "NT", "NT" to "NT",
        "NUNAVUT" to "NU", "NU" to "NU",
    )

    @Serializable
    internal data class HmaPageDto(
        val id: Long? = null,
        val slug: String? = null,
        val link: String? = null,
        val title: HmaRendered? = null,
        val content: HmaRendered? = null,
    )

    @Serializable
    internal data class HmaRendered(
        @SerialName("rendered") val rendered: String? = null,
    )
}
