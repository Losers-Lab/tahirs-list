package com.tahirslist.persistence.listing

import com.tahirslist.application.listing.CuisineLogic
import com.tahirslist.application.listing.ListingSearchFilters
import com.tahirslist.application.listing.ListingSearchQuery
import com.tahirslist.application.listing.ListingSearchResult
import com.tahirslist.domain.restaurant.Cuisine
import com.tahirslist.domain.restaurant.HalalScope
import com.tahirslist.domain.restaurant.LatLng
import com.tahirslist.domain.restaurant.Rating
import com.tahirslist.domain.restaurant.VerificationStatus
import com.tahirslist.persistence.db.ListingSearch
import com.tahirslist.persistence.db.SqlArg
import com.tahirslist.persistence.db.bind
import com.tahirslist.persistence.db.doubleParam
import com.tahirslist.persistence.db.intParam
import com.tahirslist.persistence.db.queryRows
import com.tahirslist.persistence.db.stringParam
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import java.sql.ResultSet
import java.util.UUID
import kotlin.uuid.toKotlinUuid

/** One statute mile in metres — ST_DistanceSphere returns metres. */
private const val METRES_PER_MILE = 1609.344

/**
 * Exposed implementation of [ListingSearchQuery] against the denormalised
 * `listing_search` table (see V8__create_listing_search.sql, extended by
 * V18__sc_119_partial_halal.sql).
 *
 * Exactly the ratified sc-10 contract in one query: `ST_DWithin` (geography,
 * metres) filters to the radius, `ST_DistanceSphere` (geometry, metres) orders
 * ascending, offset/limit do the paging. Because the stored column is
 * `geography(Point,4326)`, the centre is built as a geography for the filter
 * and cast to geometry for the sphere distance; the returned distance is
 * converted metres -> statute miles (/1609.344).
 *
 * sc-119:
 *  - CROSS-CONTAMINATION INDEX GATE: the query always constrains
 *    `cross_contamination = 'NO_CROSS_CONTAMINATION'` as defence-in-depth (the
 *    mirror already only indexes qualified rows). PRESENT/UNCERTAIN listings can
 *    never appear in search.
 *  - The hand-cut filter ([ListingSearchFilters.handCutOnly]) maps to
 *    `is_hand_cut = true` when set; absent = any (sc-42, no machine-cut).
 *
 * radius <= 0 would return nothing meaningfully and is a caller error; the
 * controller guards it (IllegalArgumentException -> 400).
 */
class ExposedListingSearchQuery(private val db: Database) : ListingSearchQuery {

    override suspend fun searchNearby(
        center: LatLng,
        radiusMiles: Double,
        filters: ListingSearchFilters,
        offset: Int,
        limit: Int,
    ): List<ListingSearchResult> {
        val radiusMeters = radiusMiles * METRES_PER_MILE
        // Non-positive radius cannot match anything; the DB-boundary test pins that
        // a 0 radius yields an empty result rather than an error.
        if (radiusMeters <= 0.0) return emptyList()

        // sc-42: hand-cut is an EXTRA on/off boolean filter. When off (default)
        // no predicate is added so every listing matches regardless of status;
        // when on, a simple IS TRUE predicate (ignores NULL = unknown, which
        // search treats as not-hand-cut — same null semantics as price/rating).
        val handCutClause = if (filters.handCutOnly) "AND is_hand_cut" else ""

        // sc-184: delivery-only is the same EXTRA on/off boolean filter as
        // hand-cut (sc-42). When off (default) no predicate is added so every
        // listing matches regardless of delivery status; when on, a simple
        // IS TRUE predicate (ignores NULL = unknown, which search treats as
        // no-delivery — pickup is the implicit baseline default).
        val deliveryClause = if (filters.deliveryOnly) "AND is_delivery" else ""

        // sc-44: cuisine AND/OR over the multi-cuisine join table.
        //   OR (default): the listing has ANY selected cuisine -> EXISTS.
        //   AND: the listing has ALL selected cuisines -> COUNT(DISTINCT matched) == selected count.
        // A listing with no cuisine rows (NULL-cuisine seed) never matches either
        // clause, so cuisine filters exclude NULL-cuisine listings (V6 contract).
        // Values are normalized at the edge; we normalize here too so direct
        // callers and repeated builds get the same idempotent result.
        val minPrice = filters.minPrice
        val maxPrice = filters.maxPrice
        val normalizedCuisines = filters.cuisines.map { it.trim().lowercase() }.distinct()
        val cuisineClause = if (normalizedCuisines.isEmpty()) {
            ""
        } else {
            val placeholders = normalizedCuisines.joinToString(", ") { "?" }
            if (filters.cuisineLogic == CuisineLogic.AND) {
                "AND (SELECT COUNT(DISTINCT rc.cuisine) FROM restaurant_listing_cuisines rc " +
                    "WHERE rc.listing_id = listing_search.id AND rc.cuisine IN ($placeholders)) = ${normalizedCuisines.size}"
            } else {
                "AND EXISTS (SELECT 1 FROM restaurant_listing_cuisines rc " +
                    "WHERE rc.listing_id = listing_search.id AND rc.cuisine IN ($placeholders))"
            }
        }

        // sc-43: price range against the denormalised price mirror. A NULL-price
        // row never satisfies >= / <= and is therefore excluded from a price filter,
        // the same null semantics cuisine filters already use (V6).
        val priceClause = buildString {
            if (filters.minPrice != null) append("AND listing_search.price >= ? ")
            if (filters.maxPrice != null) append("AND listing_search.price <= ? ")
        }.trimEnd()

        // sc-45: minimum rating against the denormalised rating mirror. A
        // NULL-rating row never satisfies >= and is therefore excluded from a
        // rating filter, the same null semantics price (V9) and cuisine (V6)
        // already use.
        val minRating = filters.minRating
        val ratingClause = if (minRating != null) "AND listing_search.rating >= ?" else ""

        val sql = """
            SELECT
                id,
                name,
                address,
                city,
                province,
                postal,
                country,
                ST_Y(location::geometry) AS lat,
                ST_X(location::geometry) AS lng,
                cuisine,
                is_hand_cut,
                halal_scope,
                is_delivery,
                verification_status,
                rating,
                image_version,
                ST_DistanceSphere(
                    location::geometry,
                    ST_SetSRID(ST_MakePoint(?, ?), 4326)
                ) / $METRES_PER_MILE AS distance_miles
            FROM listing_search
            WHERE ST_DWithin(
                location,
                ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography,
                ?
            )
            AND cross_contamination = 'NO_CROSS_CONTAMINATION'
            $handCutClause
            $deliveryClause
            $cuisineClause
            $priceClause
            $ratingClause
            ORDER BY ST_DistanceSphere(
                location::geometry,
                ST_SetSRID(ST_MakePoint(?, ?), 4326)
            )
            LIMIT ? OFFSET ?
        """.trimIndent()

        val args: List<SqlArg> = buildList {
            // distance/order centre (x=lng, y=lat)
            add(doubleParam(center.lng))
            add(doubleParam(center.lat))
            // filter centre
            add(doubleParam(center.lng))
            add(doubleParam(center.lat))
            add(doubleParam(radiusMeters))
            normalizedCuisines.forEach { add(stringParam(it)) }
            if (minPrice != null) add(ListingSearch.price bind minPrice)
            if (maxPrice != null) add(ListingSearch.price bind maxPrice)
            if (minRating != null) add(ListingSearch.rating bind minRating)
            add(doubleParam(center.lng))
            add(doubleParam(center.lat))
            add(intParam(limit))
            add(intParam(offset))
        }

        return suspendTransaction(db) {
            queryRows(sql, args) { rs -> toSearchResult(rs) }
        }
    }

    private fun toSearchResult(rs: ResultSet): ListingSearchResult = ListingSearchResult(
        id = rs.getObject("id", UUID::class.java).toKotlinUuid(),
        name = rs.getString("name"),
        address = rs.getString("address"),
        city = rs.getString("city"),
        province = rs.getString("province"),
        postal = rs.getString("postal"),
        country = rs.getString("country"),
        location = LatLng(lat = rs.getDouble("lat"), lng = rs.getDouble("lng")),
        cuisine = rs.getString("cuisine")?.let { Cuisine(it) },
        isHandCut = rs.getObject("is_hand_cut", java.lang.Boolean::class.java) as Boolean?,
        halalScope = HalalScope.valueOf(rs.getString("halal_scope")),
        isDelivery = rs.getObject("is_delivery", java.lang.Boolean::class.java) as Boolean?,
        verificationStatus = VerificationStatus.valueOf(rs.getString("verification_status")),
        rating = rs.getBigDecimal("rating")?.let { Rating(it) },
        distanceMiles = rs.getDouble("distance_miles"),
        imageVersion = rs.getLong("image_version"),
    )
}
