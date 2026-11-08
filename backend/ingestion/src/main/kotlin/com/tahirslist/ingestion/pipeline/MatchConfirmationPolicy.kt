package com.tahirslist.ingestion.pipeline

import com.tahirslist.application.geo.GeocodedPlace

/** What a directory row claims, as the match policy needs it. */
data class MatchQuery(
    val name: String,
    val city: String?,
    val region: String?,
    val country: String?,
)

/** Whether a geocoder result may be accepted as this venue's location. */
sealed interface MatchDecision {
    data class Confirmed(val place: GeocodedPlace) : MatchDecision
    data class Rejected(val reason: String, val detail: String) : MatchDecision
}

/**
 * Confirms that a Photon hit is plausibly the venue the directory named.
 *
 * Photon returns the *best* hit, not necessarily the right one, so a hit is
 * accepted only when the resolved label shares a meaningful name token with the
 * directory row or names the expected city. Directory coordinates are never
 * used as the accepted location (BR-3); this check is what replaces them.
 */
class MatchConfirmationPolicy {

    fun confirm(query: MatchQuery, place: GeocodedPlace): MatchDecision {
        val label = normalize(place.displayName)
        if (label.isBlank()) {
            return MatchDecision.Rejected("AMBIGUOUS_GEOCODER_MATCH", "geocoder returned a blank label")
        }
        val labelTokens = label.split(' ').filter { it.isNotBlank() }

        val cityHit = query.city?.let { containsPhrase(labelTokens, normalize(it)) } ?: false
        val nameTokens = normalize(query.name).split(' ').filter { it.length >= 3 && it !in GENERIC }
        // Whole-token match: "ham" must not match "Birmingham".
        val nameHit = nameTokens.any { it in labelTokens }

        if (nameHit || cityHit) return MatchDecision.Confirmed(place)

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
            else -> MatchDecision.Rejected(
                "AMBIGUOUS_GEOCODER_MATCH",
                "geocoder matched '${place.displayName}', which does not share a name or city with '${query.name}'",
            )
        }
    }

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
    }
}
