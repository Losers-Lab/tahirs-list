package com.tahirslist.ingestion

/**
 * The catalogue stores a short country name (`VARCHAR(16)`, V20), and the HMS
 * directory publishes `United States Of America`. Without this mapping the load
 * refuses 17 perfectly good rows with "Listing country must be 16 characters or
 * fewer" — a source-format defect that surfaces as a domain error.
 *
 * Only aliases are mapped, never truncated: a country this table does not know
 * stays as published, so a genuinely new value still fails loudly instead of
 * being silently mangled.
 */
internal object CountryNames {

    const val MAX_LENGTH: Int = 16

    fun canonical(raw: String?): String? {
        val trimmed = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return when (trimmed.lowercase().replace(Regex("\\s+"), " ")) {
            "united states of america", "united states", "usa", "us" -> "United States"
            "canada", "ca" -> "Canada"
            else -> trimmed
        }
    }
}
