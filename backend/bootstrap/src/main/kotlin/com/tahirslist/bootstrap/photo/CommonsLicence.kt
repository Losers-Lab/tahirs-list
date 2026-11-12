package com.tahirslist.bootstrap.photo

import com.tahirslist.domain.restaurant.PhotoLicence

/**
 * Commons publishes the licence as free text (`CC BY-SA 4.0`, `Public domain`,
 * `CC0`). Anything this table does not name is refused, because a photo whose
 * terms we cannot state is a photo we cannot show (INV-12).
 */
internal object CommonsLicence {

    fun parse(shortName: String?): PhotoLicence? {
        val raw = shortName?.trim()?.lowercase() ?: return null
        if (raw.isEmpty()) return null
        return when {
            raw.startsWith("cc0") || raw.contains("cc0") -> PhotoLicence.CC0
            raw.contains("public domain") -> PhotoLicence.PUBLIC_DOMAIN
            // Share-alike must be checked before plain CC BY.
            raw.contains("by-sa") || raw.contains("by sa") -> PhotoLicence.CC_BY_SA
            raw.contains("by") -> PhotoLicence.CC_BY
            else -> null
        }
    }
}
