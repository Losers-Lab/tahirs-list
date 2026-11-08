package com.tahirslist.application.listing

import com.tahirslist.domain.restaurant.LatLng
import com.tahirslist.domain.restaurant.Price
import com.tahirslist.domain.restaurant.Rating
import java.math.BigDecimal

/**
 * Pure validation/normalisation shared by the REST search edge and the MCP
 * search tool, so both surfaces accept exactly the same filter vocabulary.
 *
 * Functions throw [IllegalArgumentException] with a caller-facing reason; each
 * edge maps that to its own envelope (HTTP 400 vs an `isError` tool result).
 * Page bounds differ by surface: REST coerces, the agent surface rejects, so
 * [limit] / [offset] here implement the strict agent contract while the REST
 * route keeps its historical coerce behaviour.
 */
object ListingSearchRequestParser {

    const val DEFAULT_LIMIT: Int = 20
    const val MAX_LIMIT: Int = 100
    const val MAX_CUISINE_LENGTH: Int = 64
    const val MAX_PLACE_LENGTH: Int = 256

    private val RATING_MAX: Double = Rating.MAX.toDouble()

    /** Radius is required and strictly positive. */
    fun radius(value: Double?): Double {
        requireNotNull(value) { "radiusMiles is required" }
        require(value > 0.0) { "radiusMiles must be positive" }
        return value
    }

    /** Agent page size: 1..100, default 20. */
    fun limit(value: Int?): Int {
        if (value == null) return DEFAULT_LIMIT
        require(value in 1..MAX_LIMIT) { "limit must be between 1 and $MAX_LIMIT" }
        return value
    }

    /** Agent offset: >= 0, default 0. */
    fun offset(value: Int?): Int {
        if (value == null) return 0
        require(value >= 0) { "offset must not be negative" }
        return value
    }

    /** Free-text place: trimmed, non-blank, at most [MAX_PLACE_LENGTH] characters. */
    fun place(value: String?): String {
        requireNotNull(value) { "place must not be blank" }
        val trimmed = value.trim()
        require(trimmed.isNotEmpty()) { "place must not be blank" }
        require(trimmed.length <= MAX_PLACE_LENGTH) {
            "place must be $MAX_PLACE_LENGTH characters or fewer"
        }
        return trimmed
    }

    /** A coordinate pair, with [LatLng] enforcing the WGS84 ranges. */
    fun coordinates(lat: Double, lng: Double): LatLng = LatLng(lat = lat, lng = lng)

    /** Boolean filters are plain on/off flags; absent means false. */
    fun flag(value: Boolean?): Boolean = value ?: false

    /**
     * Cuisines are trimmed + lowercased to match storage and de-duplicated;
     * blank or over-long values are caller errors.
     */
    fun cuisines(values: List<String>?): List<String> {
        if (values.isNullOrEmpty()) return emptyList()
        return values.map { raw ->
            val value = raw.trim().lowercase()
            require(value.isNotBlank()) { "cuisine must not be blank" }
            require(value.length <= MAX_CUISINE_LENGTH) {
                "cuisine must be $MAX_CUISINE_LENGTH characters or fewer"
            }
            value
        }.distinct()
    }

    /** Missing / blank / `OR` is the PRD default; `AND` requires every cuisine. */
    fun cuisineLogic(raw: String?): CuisineLogic = when (raw?.trim()?.uppercase()) {
        null, "", "OR" -> CuisineLogic.OR
        "AND" -> CuisineLogic.AND
        else -> throw IllegalArgumentException("cuisineLogic must be AND|OR")
    }

    /** A price bound: absent = unbounded; negative or above [Price.MAX] is a caller error. */
    fun price(raw: Double?, name: String): BigDecimal? {
        if (raw == null) return null
        require(raw >= 0.0) { "$name must not be negative" }
        val value = BigDecimal.valueOf(raw)
        require(value <= Price.MAX) { "$name must be ${Price.MAX} or fewer" }
        return value
    }

    fun requirePriceRange(minPrice: BigDecimal?, maxPrice: BigDecimal?) {
        require(minPrice == null || maxPrice == null || minPrice <= maxPrice) {
            "minPrice must not exceed maxPrice"
        }
    }

    /** Minimum rating on the closed 0..5 scale (sc-45). */
    fun rating(raw: Double?): BigDecimal? {
        if (raw == null) return null
        require(raw >= 0.0) { "minRating must not be negative" }
        require(raw <= RATING_MAX) { "minRating must be 5.0 or fewer" }
        return BigDecimal.valueOf(raw)
    }
}
