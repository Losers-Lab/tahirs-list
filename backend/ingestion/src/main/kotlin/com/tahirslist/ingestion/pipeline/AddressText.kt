package com.tahirslist.ingestion.pipeline

/**
 * The address a geocoder query is built from and a match is confirmed against.
 *
 * Directory rows arrive with a free-text street line that mixes house number,
 * unit/suite noise and postal codes. Photon matches **every** query token, so an
 * unparsed "suite c200" (or "#135") makes an otherwise resolvable address
 * return nothing. [AddressText] strips that noise, expands the street suffixes
 * Photon does not expand itself ("Rd" is not "Road" in its index), and exposes
 * the house number and street tokens the confirmation rule needs.
 *
 * @param street the cleaned street line, house number included, suffixes expanded.
 * @param houseNumber the leading house number ("18111", "258-03"), or null.
 * @param streetTokens distinctive street words after expansion ("dallas",
 *        "parkway"), with the number and generic suffixes removed.
 */
data class GeocodingAddress(
    val street: String?,
    val houseNumber: String?,
    val streetTokens: List<String>,
    val city: String?,
    val region: String?,
    val postalCode: String?,
) {
    /** The place part of a query: street, city, region and postal code. */
    fun placeQuery(): String? {
        val parts = listOfNotNull(street, city, region, postalCode)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        return if (parts.isEmpty()) null else parts.joinToString(", ")
    }
}

/** Parses directory address text into the pieces a geocoder needs. */
object AddressText {

    /**
     * @param lines the directory's street lines (often one line that also
     *        carries the unit, postal code and country).
     */
    fun parse(
        lines: List<String>,
        city: String? = null,
        region: String? = null,
        postalCode: String? = null,
    ): GeocodingAddress {
        val segments = lines
            .flatMap { it.split(',') }
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        var postal = postalCode?.trim()?.takeIf { it.isNotEmpty() }
        val kept = mutableListOf<String>()
        for (segment in segments) {
            if (isCountryOnly(segment)) continue
            // A unit segment ("Suite 12345") may contain five digits; never read
            // those as a ZIP. Units inside a street are cut by stripUnit below.
            if (startsWithUnit(segment)) continue
            val found = postalIn(segment)
            if (found != null) {
                if (postal == null) postal = normalizePostal(found.value)
                val without = segment.replace(found.value, "").trim().trim(',').trim()
                if (without.isEmpty() || isLocality(without, city, region)) continue
                stripUnit(without).takeIf { it.isNotEmpty() }?.let { kept += it }
            } else if (isLocality(segment, city, region)) {
                continue
            } else {
                stripUnit(segment).takeIf { it.isNotEmpty() }?.let { kept += it }
            }
        }

        // A directory sometimes writes the unit as its own leading segment;
        // prefer the segment that starts with a house number, else the first.
        val streetRaw = kept.firstOrNull { HOUSE_NUMBER.containsMatchIn(it) } ?: kept.firstOrNull()
        val street = streetRaw?.let(::expandStreet)?.takeIf { it.isNotBlank() }
        val houseNumber = streetRaw?.let { HOUSE_NUMBER.find(it)?.groupValues?.get(1) }

        val tokens = street
            ?.let(::expandStreet)
            ?.split(' ')
            ?.map { fold(it).lowercase() }
            ?.map { it.trim('.', ',') }
            ?.filter { it.isNotEmpty() }
            ?.filterNot { it in GENERIC_STREET_WORDS }
            ?.filterNot { it in SUFFIX_ABBREVIATIONS }   // a suffix the expansion left in place
            ?.filterNot { it in DIRECTIONAL_ABBREVIATIONS }
            // Keep numeric street names ("40 Street"); only the house number goes.
            ?.filterNot { houseNumber != null && it == houseNumber.lowercase() }
            ?.filter { it.length > 1 }
            ?.distinct()
            .orEmpty()

        return GeocodingAddress(
            street = street,
            houseNumber = houseNumber,
            streetTokens = tokens,
            city = city?.trim()?.takeIf { it.isNotEmpty() },
            region = region?.trim()?.takeIf { it.isNotEmpty() },
            postalCode = postal,
        )
    }

    /** True when [text] is only a country name, which Photon does not need. */
    private fun isCountryOnly(text: String): Boolean =
        text.trim().lowercase().trim('.') in COUNTRY_ONLY

    /**
     * True when the segment only repeats the city/region we pass separately
     * (e.g. a line that ends "Dallas, TX").
     */
    private fun isLocality(segment: String, city: String?, region: String?): Boolean {
        val normalized = normalize(segment)
        if (normalized.isEmpty()) return true
        return listOfNotNull(city, region).any { normalize(it) == normalized }
    }

    /** True when the segment opens with a unit marker ("Suite 500", "Unit 7A"). */
    private fun startsWithUnit(segment: String): Boolean = LEADING_UNIT.containsMatchIn(segment)

    /** Cuts a unit/suite marker and everything after it from a street segment. */
    private fun stripUnit(segment: String): String {
        val cut = UNIT_MARKER.find(segment)?.range?.first ?: return segment.trim()
        return segment.substring(0, cut).trim().trim(',').trim()
    }

    /** Expands the suffix and directional abbreviations Photon's index does not. */
    private fun expandStreet(segment: String): String {
        val tokens = segment.split(' ').filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return segment
        val last = tokens.lastIndex
        val lastKey = tokens.last().trim('.', ',').lowercase()
        // "1365 Gerrard St E": the suffix is followed by a direction, not last.
        val suffixIndex = if (lastKey in DIRECTIONAL_ABBREVIATIONS) last - 1 else last
        return tokens.mapIndexed { index, raw ->
            val cleaned = raw.trim('.', ',')
            val key = cleaned.lowercase()
            when {
                index == suffixIndex && key in SUFFIX_ABBREVIATIONS -> SUFFIX_ABBREVIATIONS.getValue(key)
                (index == last && key in DIRECTIONAL_ABBREVIATIONS) ||
                    (index < 2 && key in DIRECTIONAL_ABBREVIATIONS) ->
                    DIRECTIONAL_ABBREVIATIONS.getValue(key)
                else -> raw
            }
        }.joinToString(" ")
    }

    /**
     * The postal code in one comma-separated segment, if there is one that is
     * not the leading house number. "18111 Dallas Pkwy" starts with five digits
     * but is a street, not a ZIP; "TX 75287" and a bare "75287" are postals.
     */
    private fun postalIn(segment: String): MatchResult? {
        val whole = POSTAL.matchEntire(segment.trim())
        if (whole != null) return whole
        val found = POSTAL.find(segment) ?: return null
        return if (found.range.first > 0) found else null
    }

    private fun normalizePostal(value: String): String =
        value.uppercase().replace(" ", "").let {
            // Keep US ZIP+4 readable; compact Canadian codes for matching.
            if (it.matches(Regex("""[A-Z]\d[A-Z]\d[A-Z]\d"""))) {
                "${it.substring(0, 3)} ${it.substring(3)}"
            } else {
                value.uppercase()
            }
        }

    private fun normalize(value: String): String =
        fold(value).lowercase().replace(Regex("""[^a-z0-9]+"""), " ").trim()

    /** Diacritics folded away, so "Québec" and "Quebec" compare equal. */
    private fun fold(value: String): String =
        java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFKD)
            .replace(Regex("""\p{M}+"""), "")

    private val HOUSE_NUMBER = Regex("""^\s*(\d+[A-Za-z]?(?:-\d+[A-Za-z]?)?)\b""")

    private val POSTAL = Regex(
        """\b(\d{5}(?:-\d{4})?|[A-Za-z]\d[A-Za-z]\s?\d[A-Za-z]\d)\b""",
    )

    private val UNIT_MARKER = Regex(
        """(?i)(\s+(suite|ste|unit|apt|apartment|floor|fl|bldg|building|room|rm)\b|\s*#)""",
    )

    /** A unit word at the very start of a segment, followed by a unit value. */
    private val LEADING_UNIT = Regex(
        """(?i)^\s*(suite|ste|unit|apt|apartment|floor|fl|bldg|building|room|rm)\b\.?\s*#?\s*(?=[A-Za-z]?\d)""",
    )

    private val COUNTRY_ONLY = setOf(
        "usa", "u s a", "us", "united states", "united states of america",
        "canada", "ca",
    )

    private val SUFFIX_ABBREVIATIONS = mapOf(
        "rd" to "Road",
        "st" to "Street",
        "ave" to "Avenue",
        "av" to "Avenue",
        "blvd" to "Boulevard",
        "dr" to "Drive",
        "ln" to "Lane",
        "pkwy" to "Parkway",
        "hwy" to "Highway",
        "ct" to "Court",
        "pl" to "Place",
        "cir" to "Circle",
        "ter" to "Terrace",
        "terr" to "Terrace",
        "sq" to "Square",
        "expy" to "Expressway",
        "tpke" to "Turnpike",
        "trl" to "Trail",
        "cres" to "Crescent",
    )

    private val DIRECTIONAL_ABBREVIATIONS = mapOf(
        "n" to "North",
        "s" to "South",
        "e" to "East",
        "w" to "West",
        "ne" to "Northeast",
        "nw" to "Northwest",
        "se" to "Southeast",
        "sw" to "Southwest",
    )

    /**
     * Words that carry no identity for a match: every street has a suffix, and
     * matching on it would confirm any address on any road.
     */
    private val GENERIC_STREET_WORDS = setOf(
        "road", "street", "avenue", "boulevard", "drive", "lane", "parkway",
        "highway", "court", "place", "circle", "terrace", "square",
        "expressway", "turnpike", "trail", "crescent",
        "north", "south", "east", "west",
        "northeast", "northwest", "southeast", "southwest",
        "the", "and", "of",
    )
}
