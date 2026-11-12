package com.tahirslist.bootstrap.photo

/**
 * Does a source's answer name the same venue? (SC-231, UC-03 step 7a.)
 *
 * Deliberately strict: a near-miss is refused with a reason, never taken. Two
 * strings agree when one is the whole phrase of the other (`Noor Grill` vs
 * `Noor Grill & Pizza`), after case, punctuation and spacing are normalised.
 * Token-subset matching is NOT used: `Noor` must not match `Noor Halal Pizza`
 * just because one word appears.
 */
internal object VenueMatch {

    /** A single-word venue name must be at least this distinctive to prefix-match. */
    private const val SINGLE_WORD_PREFIX_MIN = 5

    fun normalize(value: String): String = value.lowercase()
        // Possessives: "Addy's" and "Addys" are the same name.
        .replace(Regex("['\u2019]"), "")
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
        .split(' ')
        // "Mannan Bakery & Restaurant" and "Mannan Bakery and Restaurant" are the
        // same words; "the" is noise in both directions.
        .filter { it.isNotEmpty() && it != "and" && it != "the" }
        .joinToString(" ")

    /** The name with a Commons `File:` prefix and extension removed. */
    fun fileTitle(raw: String): String = raw
        .removePrefix("File:")
        .substringBeforeLast('.', missingDelimiterValue = raw)

    fun namesAgree(listingName: String, candidateName: String): Boolean {
        val a = normalize(listingName)
        val b = normalize(candidateName)
        if (a.isEmpty() || b.isEmpty()) return false
        if (a == b) return true

        // A single distinctive word naming the start of the venue: "Sizzlings" for
        // "Sizzlings - Halal Hot Chicken…". Short generic words are not enough.
        if (!a.contains(' ') && a.length >= SINGLE_WORD_PREFIX_MIN && b.startsWith("$a ")) return true

        val shorter = if (a.length <= b.length) a else b
        val longer = if (a.length <= b.length) b else a
        // "Noor Grill" inside "Noor Grill & Pizza" — a multi-word phrase, never a
        // single stray word ("Noor" must not match "Noor Halal Pizza").
        return shorter.split(' ').size >= 2 && (" $shorter " in " $longer ")
    }

    /**
     * The address must corroborate the name: the postal code when the listing has
     * one, otherwise a meaningful word from the street (so "253 Yonge St"
     * corroborates "253 Yonge Street, Toronto").
     */
    fun addressAgrees(
        listingAddress: String,
        postal: String?,
        candidateAddress: String,
    ): Boolean {
        val candidate = normalize(candidateAddress).replace(" ", "")
        if (candidate.isEmpty()) return false

        // Postal OR street: a wrong postal must not veto an exact street match
        // ("287 King St W" against "287 King St W, Toronto, ON M5V 1J5").
        val compactPostal = postal
            ?.let { normalize(it).replace(" ", "") }
            ?.takeIf { it.length >= 3 }
        if (compactPostal != null && compactPostal in candidate) return true

        val words = normalize(listingAddress)
            .split(' ')
            .filter { it.length >= 4 && it.any(Char::isLetter) }
        return words.any { it in candidate }
    }
}
