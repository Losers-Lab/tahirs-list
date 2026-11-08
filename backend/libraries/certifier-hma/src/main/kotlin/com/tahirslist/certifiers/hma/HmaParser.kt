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

    /** Splits `street, city, province, postal` without guessing missing parts. */
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

        val combined = rest.lastOrNull()?.let { TRAILING_REGION_POSTAL.matchEntire(it) }
            ?.takeIf { PROVINCE.matches(it.groupValues[1]) }
        if (combined != null) {
            region = combined.groupValues[1]
            postal = combined.groupValues[2]
            rest.removeAt(rest.lastIndex)
        } else {
            if (rest.isNotEmpty() && POSTAL_CODE.matches(rest.last())) {
                postal = rest.removeAt(rest.lastIndex)
            }
            if (rest.isNotEmpty() && PROVINCE.matches(rest.last())) {
                region = rest.removeAt(rest.lastIndex)
            }
        }
        if (rest.isNotEmpty()) {
            city = rest.removeAt(rest.lastIndex)
        }

        return HmaAddress(
            line = rest.joinToString(", ").ifBlank { null },
            city = city,
            region = region,
            postalCode = postal,
        )
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

    private val POSTAL_CODE = Regex("(?i)^[A-Z]\\d[A-Z][ -]?\\d[A-Z]\\d$")

    private val TRAILING_REGION_POSTAL = Regex("(?i)^(.+?)\\s+([A-Z]\\d[A-Z][ -]?\\d[A-Z]\\d)$")

    private val PROVINCE = Regex(
        "(?i)^(ON|Ontario|QC|Quebec|BC|British Columbia|AB|Alberta|MB|Manitoba|SK|Saskatchewan|" +
            "NS|Nova Scotia|NB|New Brunswick|NL|Newfoundland and Labrador|PE|Prince Edward Island|" +
            "YT|Yukon|NT|Northwest Territories|NU|Nunavut)$",
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
