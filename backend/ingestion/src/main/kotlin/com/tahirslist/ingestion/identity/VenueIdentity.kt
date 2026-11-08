package com.tahirslist.ingestion.identity

import java.security.MessageDigest
import java.text.Normalizer

/**
 * A deterministic venue identity used by the ingestion load.
 *
 * Coordinates are deliberately **not** part of the key: Photon can resolve the
 * same venue to slightly different coordinates on different runs, and a
 * location-only match would then insert a duplicate. The key is built from the
 * normalized name, street address, city, region and country instead.
 *
 * The value is a SHA-256 hex string so it fits one indexed column and does not
 * expose the full address in an index. Two venues with the same normalized
 * name + address + city/region are the same venue for ingestion purposes.
 *
 * This is a *conservative* key: it catches case, punctuation, whitespace and
 * diacritic differences, but it does not fuzzy-match "McDonald's" to
 * "McDonalds Plaza". A location-only near-match is reported as a suspected
 * duplicate, never silently merged.
 */
@JvmInline
value class VenueIdentity(val key: String) {

    init {
        require(key.length == 64 && key.all { it in '0'..'9' || it in 'a'..'f' }) {
            "venue identity must be a lowercase SHA-256 hex string"
        }
    }

    companion object {
        fun from(
            name: String,
            addressLines: List<String>,
            city: String?,
            region: String?,
            country: String?,
        ): VenueIdentity? {
            val normalizedName = normalize(name)
            val normalizedAddress = normalize(addressLines.joinToString(" "))
            if (normalizedName.isEmpty() || normalizedAddress.isEmpty()) return null

            val material = listOf(
                normalizedName,
                normalizedAddress,
                normalize(city.orEmpty()),
                normalize(region.orEmpty()),
                normalize(country.orEmpty()),
            ).joinToString("|")
            return VenueIdentity(sha256(material))
        }

        /**
         * Lowercase, strip diacritics and punctuation, collapse whitespace.
         * `&` becomes `and` so "Halal Guys & Grill" and "Halal Guys and Grill"
         * land on the same key.
         */
        internal fun normalize(value: String): String {
            val withoutMarks = Normalizer.normalize(value, Normalizer.Form.NFKD)
                .replace(Regex("\\p{M}+"), "")
            return withoutMarks
                .lowercase()
                .replace("&", " and ")
                .replace("'", "")
                .replace("’", "")
                .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
                .trim()
                .replace(Regex("\\s+"), " ")
        }

        private fun sha256(value: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(value.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
    }
}
