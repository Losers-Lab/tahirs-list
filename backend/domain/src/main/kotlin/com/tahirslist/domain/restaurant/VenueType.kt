package com.tahirslist.domain.restaurant

/**
 * What kind of restaurant a stored listing is (SC-231).
 *
 * The catalogue holds restaurants only (L-1), so this vocabulary is deliberately
 * about *which kind of restaurant*, not about what the place might otherwise be.
 * A butcher, a grocery or a distributor is recognised by the ingestion gate as a
 * [VenueClass] and never reaches a listing, so it has no member here.
 *
 * A bakery is a restaurant by owner decision (L-3).
 */
enum class VenueType {
    RESTAURANT,
    CAFE,
    FAST_FOOD,
    BAKERY,
}
