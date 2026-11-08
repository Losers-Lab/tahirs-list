package com.tahirslist.application.geo

import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

/**
 * The cache-key policy for SC-198 (docs/features/SC-198/01-domain-model.md, D-3).
 *
 * Normalization is deliberately narrow and stable: Unicode NFKC, trim, collapse
 * every whitespace run to one space, then lowercase with [Locale.ROOT]. The hash
 * is the lowercase hex SHA-256 of the normalized UTF-8 bytes and becomes the
 * `geocoded_addresses` primary key.
 *
 * A change here re-keys every row, so [NormalizedQueryTest] pins a known vector:
 * an accidental change breaks the build instead of silently splitting the cache.
 */
class NormalizedQuery private constructor(
    val text: String,
    val hash: String,
) {
    companion object {
        fun of(raw: String): NormalizedQuery {
            val normalized = normalize(raw)
            return NormalizedQuery(normalized, sha256(normalized))
        }

        private fun normalize(raw: String): String =
            Normalizer.normalize(raw, Normalizer.Form.NFKC)
                .trim()
                .replace(Regex("\\s+"), " ")
                .lowercase(Locale.ROOT)

        private fun sha256(value: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(value.toByteArray(Charsets.UTF_8))
                .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xFF) }
    }
}
