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

    fun normalize(value: String): String = value.lowercase()
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

    /** The name with a Commons `File:` prefix and extension removed. */
    fun fileTitle(raw: String): String = raw
        .removePrefix("File:")
        .substringBeforeLast('.', missingDelimiterValue = raw)

    fun namesAgree(listingName: String, candidateName: String): Boolean {
        val a = normalize(listingName)
        val b = normalize(candidateName)
        if (a.isEmpty() || b.isEmpty()) return false
        if (a == b) return true
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

        postal?.let { code ->
            val compact = normalize(code).replace(" ", "")
            if (compact.length >= 3) return compact in candidate
        }

        val words = normalize(listingAddress)
            .split(' ')
            .filter { it.length >= 4 && it.any(Char::isLetter) }
        return words.any { it in candidate }
    }
}
