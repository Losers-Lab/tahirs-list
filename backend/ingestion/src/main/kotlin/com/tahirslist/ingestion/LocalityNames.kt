package com.tahirslist.ingestion

/**
 * The short, canonical names the catalogue stores for places.
 *
 * Sources publish the same place in different shapes — HMS writes
 * "United States Of America"/"New York", HFSAA writes "United States"/"NY" —
 * and region is part of the venue key, so the same venue seen in two
 * directories would never merge. The translators and the match confirmation
 * share these maps instead of each keeping a private list.
 */
object LocalityNames {

    /** A US state or territory name (or code) to its two-letter code, else the input. */
    fun usStateCode(value: String?): String? {
        val trimmed = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        US_STATES[trimmed.lowercase().replace(".", "").trim()]?.let { return it }
        // Already a code ("ny")? Keep the catalogue's uppercase form.
        val code = trimmed.uppercase()
        return if (US_STATE_NAMES.containsKey(code)) code else trimmed
    }

    /** A two-letter US state code to its full name, or null when it is not one. */
    fun usStateName(value: String?): String? {
        val code = value?.trim()?.uppercase()?.takeIf { it.isNotEmpty() } ?: return null
        return US_STATE_NAMES[code]
    }

    /** A short country name for the catalogue field, or null when it does not fit. */
    fun country(value: String?, maxLength: Int): String? {
        val trimmed = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return COUNTRY_ALIASES[trimmed.lowercase().replace(".", "").trim()]
            ?: trimmed.takeIf { it.length <= maxLength }
    }

    private val COUNTRY_ALIASES = mapOf(
        "united states of america" to "United States",
        "united states" to "United States",
        "usa" to "United States",
        "us" to "United States",
        "canada" to "Canada",
        "can" to "Canada",
    )

    private val US_STATES = mapOf(
        "alabama" to "AL", "alaska" to "AK", "arizona" to "AZ", "arkansas" to "AR",
        "california" to "CA", "colorado" to "CO", "connecticut" to "CT", "delaware" to "DE",
        "district of columbia" to "DC", "florida" to "FL", "georgia" to "GA", "hawaii" to "HI",
        "idaho" to "ID", "illinois" to "IL", "indiana" to "IN", "iowa" to "IA",
        "kansas" to "KS", "kentucky" to "KY", "louisiana" to "LA", "maine" to "ME",
        "maryland" to "MD", "massachusetts" to "MA", "michigan" to "MI", "minnesota" to "MN",
        "mississippi" to "MS", "missouri" to "MO", "montana" to "MT", "nebraska" to "NE",
        "nevada" to "NV", "new hampshire" to "NH", "new jersey" to "NJ", "new mexico" to "NM",
        "new york" to "NY", "north carolina" to "NC", "north dakota" to "ND", "ohio" to "OH",
        "oklahoma" to "OK", "oregon" to "OR", "pennsylvania" to "PA", "rhode island" to "RI",
        "south carolina" to "SC", "south dakota" to "SD", "tennessee" to "TN", "texas" to "TX",
        "utah" to "UT", "vermont" to "VT", "virginia" to "VA", "washington" to "WA",
        "west virginia" to "WV", "wisconsin" to "WI", "wyoming" to "WY",
        "puerto rico" to "PR", "guam" to "GU", "virgin islands" to "VI",
        "american samoa" to "AS", "northern mariana islands" to "MP",
    )

    private val US_STATE_NAMES = US_STATES.entries.associate { (name, code) ->
        code to name.split(' ').joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
    }
}
