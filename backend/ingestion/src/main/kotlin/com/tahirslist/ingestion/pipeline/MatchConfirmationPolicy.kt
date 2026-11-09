package com.tahirslist.ingestion.pipeline

import com.tahirslist.application.geo.GeocodedPlace
import com.tahirslist.domain.restaurant.RestaurantListing
import com.tahirslist.ingestion.LocalityNames

/** What a directory row claims, as the match policy needs it. */
data class MatchQuery(
    val name: String,
    val city: String?,
    val region: String?,
    val country: String?,
    /** Cleaned street line, house number included (see [AddressText]). */
    val street: String? = null,
    /** The leading house number, when the row has one. */
    val houseNumber: String? = null,
    /** Distinctive street words after suffix expansion ("dallas", "parkway"). */
    val streetTokens: List<String> = emptyList(),
    val postalCode: String? = null,
)

/** Whether a geocoder result may be accepted as this venue's location. */
sealed interface MatchDecision {
    data class Confirmed(val place: GeocodedPlace) : MatchDecision
    data class Rejected(val reason: String, val detail: String) : MatchDecision
}

/**
 * Confirms that a Photon hit is plausibly the venue the directory named.
 *
 * Photon returns the *best* hit, not necessarily the right one. A hit is
 * accepted only when the resolved label carries evidence for this row:
 *
 *  1. **the name** — a distinctive trade-name token appears in the label; or
 *  2. **the address** — the label carries the row's house number *and* a
 *     distinctive street token, and the city or postal code agrees.
 *
 * A shared city alone is **not** evidence: Photon answers a long query with any
 * POI in that city (a "Cloud Naan" query in Richardson once returned "Muslim
 * Legal Fund of America"). The same rule rejects a street-level hit for a house
 * number that Photon cannot pin, because a street centroid can be kilometres
 * from the venue.
 *
 * Directory coordinates are never used as the accepted location (BR-3); this
 * check is what replaces them.
 */
class MatchConfirmationPolicy {

    fun confirm(query: MatchQuery, place: GeocodedPlace): MatchDecision {
        val label = normalize(place.displayName)
        if (label.isBlank()) {
            return MatchDecision.Rejected("AMBIGUOUS_GEOCODER_MATCH", "geocoder returned a blank label")
        }
        val labelTokens = label.split(' ').filter { it.isNotBlank() }
        // House numbers keep their hyphen ("258-03"), so match against a
        // hyphen-preserving view of the label.
        val labelled = place.displayName.lowercase()
            .replace(Regex("""[^a-z0-9-]+"""), " ")
            .trim()
            .split(' ')
            .filter { it.isNotBlank() }

        val cityHit = query.city?.let { containsPhrase(labelTokens, normalize(it)) } ?: false
        val postalHit = query.postalCode?.let { containsPostal(labelled, it) } ?: false
        // Name evidence compares the row's name with the label's name slot — the
        // first part before the adapter's ", " join. The label also carries the
        // street, city, state and country, and a row called "Texas King" must
        // not match the state "Texas".
        val labelNameTokens = normalize(place.displayName.substringBefore(", "))
            .split(' ')
            .filter { it.isNotBlank() }
        val nameTokens = normalize(query.name).split(' ').filter { it.length >= 3 && it !in GENERIC }
        // A name token that is also the city, region, postal code or street says
        // nothing about identity: "Dallas Grill" must not match every Dallas POI.
        val placeTokens = buildSet {
            listOfNotNull(query.city, query.region, query.country, query.postalCode)
                .forEach { addAll(normalize(it).split(' ')) }
            // A code excludes its full name too: "NY" must also block "New York".
            query.region?.let { LocalityNames.usStateName(it) }
                ?.let { addAll(normalize(it).split(' ')) }
            query.country?.let { LocalityNames.country(it, RestaurantListing.COUNTRY_MAX_LENGTH) }
                ?.let { addAll(normalize(it).split(' ')) }
            addAll(query.streetTokens)
        }
        // Whole-token match: "ham" must not match "Birmingham".
        val nameHit = nameTokens.any { it in labelNameTokens && it !in placeTokens }

        if (nameHit) return MatchDecision.Confirmed(place)

        // Address evidence replaces the old city-only acceptance.
        val houseHit = query.houseNumber?.let { houseNumberHit(labelled, it) } ?: false
        val streetHit = query.streetTokens.any { token ->
            labelTokens.any { label -> streetTokenHit(token, label) }
        }
        val localityHit = (query.city == null && query.postalCode == null) || cityHit || postalHit
        if (houseHit && streetHit && localityHit) return MatchDecision.Confirmed(place)

        val addressReason = when {
            query.houseNumber != null && streetHit && !houseHit ->
                "geocoder matched a street but not house ${query.houseNumber}"
            query.houseNumber != null && houseHit && !localityHit ->
                "geocoder matched ${query.houseNumber} but not ${query.city ?: query.postalCode}"
            else -> null
        }

        val regionHint = query.region?.let { normalize(it) }
        val countryHint = query.country?.let { normalize(it) }
        val regionPresent = regionHint?.let { containsPhrase(labelTokens, it) } ?: false
        val countryPresent = countryHint?.let { containsPhrase(labelTokens, it) } ?: true

        return when {
            regionHint != null && countryHint != null && !regionPresent && !countryPresent ->
                MatchDecision.Rejected(
                    "WRONG_REGION",
                    "geocoder matched '${place.displayName}' outside ${query.city}, ${query.region}, ${query.country}",
                )
            addressReason != null -> MatchDecision.Rejected(
                "AMBIGUOUS_GEOCODER_MATCH",
                "geocoder matched '${place.displayName}', but $addressReason",
            )
            else -> MatchDecision.Rejected(
                "AMBIGUOUS_GEOCODER_MATCH",
                "geocoder matched '${place.displayName}', which shares no name or address with '${query.name}'",
            )
        }
    }

    /**
     * A house number matches its token exactly. Queens-style hyphenated numbers
     * ("258-03") are interpolated per building, so the same block ("258-") with
     * a nearby suffix is accepted; a different block is not.
     */
    private fun houseNumberHit(labelTokens: List<String>, houseNumber: String): Boolean {
        val wanted = compact(houseNumber)
        if (wanted.isEmpty()) return false
        if (wanted in labelTokens) return true
        val wantedBlock = hyphenBlock(wanted) ?: return false
        return labelTokens.any { token ->
            val block = hyphenBlock(compact(token)) ?: return@any false
            block.first == wantedBlock.first && kotlin.math.abs(block.second - wantedBlock.second) <= 10
        }
    }

    /** ("258-03") -> ("258", 3); null when the value is not hyphenated. */
    private fun hyphenBlock(value: String): Pair<String, Int>? {
        val parts = value.split('-')
        if (parts.size != 2) return null
        val number = parts[1].takeWhile { it.isDigit() }.toIntOrNull() ?: return null
        return parts[0] to number
    }

    /** A postal code as its own token, or two adjacent tokens ("M5V 2T6"). */
    private fun containsPostal(tokens: List<String>, postal: String): Boolean {
        val wanted = compact(postal)
        if (wanted.isEmpty()) return false
        if (tokens.any { compact(it) == wanted }) return true
        return tokens.zipWithNext().any { (first, second) -> compact(first + second) == wanted }
    }

    /**
     * A street word matches itself, and a numeric street name also matches its
     * ordinal form: the directory writes "274 West 40 Street", Photon's label
     * says "274 West 40th Street".
     */
    private fun streetTokenHit(token: String, label: String): Boolean {
        if (token == label) return true
        if (token.isEmpty() || token.any { !it.isDigit() }) return false
        val digits = label.takeWhile { it.isDigit() }
        if (digits != token) return false
        return label.drop(digits.length) in ORDINAL_SUFFIXES
    }

    private fun compact(value: String): String =
        value.lowercase().replace(Regex("""[^a-z0-9-]"""), "")

    private fun normalize(value: String): String =
        value.lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()

    /** A contiguous run of whole tokens, so "york" matches "new york" but not "yorkshire". */
    private fun containsPhrase(labelTokens: List<String>, phrase: String): Boolean {
        val needle = phrase.split(' ').filter { it.isNotBlank() }
        if (needle.isEmpty() || needle.size > labelTokens.size) return false
        return (0..labelTokens.size - needle.size).any { start ->
            labelTokens.subList(start, start + needle.size) == needle
        }
    }

    private companion object {
        /**
         * Tokens that say nothing about identity; matching on them would accept
         * any restaurant in the city.
         */
        val GENERIC = setOf(
            "the", "and", "restaurant", "restaurants", "grill", "grills", "cafe",
            "kitchen", "food", "foods", "house", "halal", "bar", "bakery",
            "market", "express", "shop", "store", "inc", "ltd",
        )

        val ORDINAL_SUFFIXES = setOf("st", "nd", "rd", "th")
    }
}
