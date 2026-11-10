package com.tahirslist.bootstrap.photo

import com.tahirslist.domain.restaurant.RestaurantListing
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Is this Commons file actually of *this* place? (SC-231, UC-03 step 5b.)
 *
 * A file title that names the venue is not enough: "Ali Baba Mediterranean Cafe"
 * is in Birmingham while the listing is in Texas, and "Moon Restaurant" is in
 * Hong Kong while the listing is in Queens. A name-only rule would have put both
 * storefronts on the wrong business — a misrepresentation on a halal directory,
 * not a cosmetic bug.
 *
 * Two ways to confirm, both of them evidence:
 *
 *  1. **Coordinates** — the file carries GPS and it is within [NEARBY_KM] of the
 *     listing. This is the strong one.
 *  2. **Place text** — no GPS, but the file's Commons categories or description
 *     name the listing's city. Weaker, and it never accepts a 2-letter region
 *     code ("ON" is inside "London").
 *
 * Anything else is refused with the reason, and the row reports it. No photo is
 * better than the wrong one.
 */
internal object CommonsPlace {

    /** A storefront or dish photo should be at the venue; 2 km absorbs geocoding slop. */
    const val NEARBY_KM: Double = 2.0

    /** The files that failed are worth naming in the report. */
    fun refusal(
        listing: RestaurantListing,
        gpsLat: Double?,
        gpsLng: Double?,
        metadataText: String,
    ): String? {
        if (gpsLat != null && gpsLng != null) {
            val km = distanceKm(listing.location.lat, listing.location.lng, gpsLat, gpsLng)
            return if (km <= NEARBY_KM) null else "the file is ${"%.0f".format(km)} km from this listing"
        }

        val city = listing.city
            ?.substringBefore('(')
            ?.trim()
            ?.takeIf { it.length >= 4 }
            ?: return "the file has no coordinates and the listing has no city to check"
        val haystack = metadataText.lowercase()
        return if (city.lowercase() in haystack) {
            null
        } else {
            "the file has no coordinates and does not name $city"
        }
    }

    /** Great-circle distance in kilometres. */
    fun distanceKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val earthKm = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        return 2 * earthKm * asin(sqrt(a))
    }
}
