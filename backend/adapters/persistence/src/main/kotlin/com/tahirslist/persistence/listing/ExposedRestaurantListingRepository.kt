package com.tahirslist.persistence.listing

import com.tahirslist.application.listing.RestaurantListingRepository
import com.tahirslist.domain.restaurant.CrossContamination
import com.tahirslist.domain.restaurant.Cuisine
import com.tahirslist.domain.restaurant.HalalItem
import com.tahirslist.domain.restaurant.HalalScope
import com.tahirslist.domain.restaurant.LatLng
import com.tahirslist.domain.restaurant.Price
import com.tahirslist.domain.restaurant.Provenance
import com.tahirslist.domain.restaurant.Rating
import com.tahirslist.domain.restaurant.RestaurantListing
import com.tahirslist.domain.restaurant.VerificationStatus
import com.tahirslist.persistence.db.ListingSearch
import com.tahirslist.persistence.db.RestaurantHalalItems
import com.tahirslist.persistence.db.RestaurantListingCuisines
import com.tahirslist.persistence.db.RestaurantListings
import com.tahirslist.persistence.db.SqlArg
import com.tahirslist.persistence.db.bind
import com.tahirslist.persistence.db.doubleParam
import com.tahirslist.persistence.db.executeUpdate
import com.tahirslist.persistence.db.queryRows
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import java.sql.ResultSet
import java.util.UUID
import kotlin.time.toKotlinInstant
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid

/**
 * Exposed implementation of the [RestaurantListingRepository] port against the
 * `restaurant_listings` table (see V4__create_restaurant_listings.sql and
 * V18__sc_119_partial_halal.sql). The database owns id generation.
 *
 * A location point is written with `ST_SetSRID(ST_MakePoint(lng, lat), 4326)`
 * (ST_MakePoint takes x=longitude first) and cast to the `geography(Point,4326)`
 * column; it is read back by casting the geography to geometry so ST_X/ST_Y
 * yield longitude/latitude.
 *
 * [save] writes the denormalised `listing_search` projection in the SAME
 * transaction, so a newly-added listing is immediately searchable (sc-10) and
 * the two tables can never diverge on this write path.
 *
 * sc-119: the per-item halal scope is stored in the `restaurant_halal_items`
 * child table, and [mirrorIntoListingSearch] is the cross-contamination INDEX
 * GATE — only [CrossContamination.isIndexQualified] listings are present in
 * `listing_search`; non-qualified rows are removed from the mirror so the index
 * never holds PRESENT/UNCERTAIN listings.
 */
class ExposedRestaurantListingRepository(private val db: Database) : RestaurantListingRepository {

    override suspend fun save(listing: RestaurantListing): RestaurantListing = suspendTransaction(db) {
        val id = queryRows(
            """
            INSERT INTO restaurant_listings (name, address, city, province, postal, country, location, cuisine, is_hand_cut, is_delivery, owner_id, brand_id, provenance, venue_key, verification_status, price, rating, alcohol_served, halal_scope, cross_contamination)
            VALUES (
                ?, ?, ?, ?, ?, ?,
                ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography,
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
            )
            RETURNING id
            """.trimIndent(),
            saveArgs(listing),
        ) { it.getObject("id", UUID::class.java).toKotlinUuid() }
            .firstOrNull()
            ?: error("INSERT RETURNING id returned no row")

        val withId = listing.copy(id = id)
        // Multi-cuisine store (sc-44): the single Add-Listing cuisine becomes
        // one row, so user-added listings are immediately cuisine-filterable.
        withId.cuisine?.let { cuisine ->
            executeUpdate(
                """
                INSERT INTO restaurant_listing_cuisines (listing_id, cuisine)
                VALUES (?, ?)
                ON CONFLICT (listing_id, cuisine) DO NOTHING
                """.trimIndent(),
                listOf(
                    RestaurantListingCuisines.listingId bind id,
                    RestaurantListingCuisines.cuisine bind cuisine.value,
                ),
                StatementType.INSERT,
            )
        }
        // Per-item halal scope (sc-119), mirroring the multi-cuisine shape.
        replaceHalalItems(id, withId.halalItems)
        mirrorIntoListingSearch(withId)
        withId
    }

    /**
     * Denormalised read-model mirror (V8): the search surface reads ONLY
     * `listing_search`. Keeping it in the same transaction as the source write
     * guarantees a listing is either in both tables or neither — never only the
     * source. Partial-failure of either INSERT rolls the whole save back.
     *
     * sc-119 cross-contamination INDEX GATE: a listing is present in the mirror
     * ONLY when [CrossContamination.NO_CROSS_CONTAMINATION]. PRESENT/UNCERTAIN
     * listings are deleted from the index (removed if previously present).
     */
    private fun JdbcTransaction.mirrorIntoListingSearch(listing: RestaurantListing) {
        if (listing.crossContamination.isIndexQualified()) {
            executeUpdate(
                """
                INSERT INTO listing_search (id, name, address, city, province, postal, country, location, cuisine, is_hand_cut, is_delivery, verification_status, price, rating, alcohol_served, halal_scope, cross_contamination)
                VALUES (?, ?, ?, ?, ?, ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    name = EXCLUDED.name,
                    address = EXCLUDED.address,
                    city = EXCLUDED.city,
                    province = EXCLUDED.province,
                    postal = EXCLUDED.postal,
                    country = EXCLUDED.country,
                    location = EXCLUDED.location,
                    cuisine = EXCLUDED.cuisine,
                    is_hand_cut = EXCLUDED.is_hand_cut,
                    is_delivery = EXCLUDED.is_delivery,
                    verification_status = EXCLUDED.verification_status,
                    price = EXCLUDED.price,
                    rating = EXCLUDED.rating,
                    alcohol_served = EXCLUDED.alcohol_served,
                    halal_scope = EXCLUDED.halal_scope,
                    cross_contamination = EXCLUDED.cross_contamination
                """.trimIndent(),
                listOf(
                    ListingSearch.id bind listing.id,
                    ListingSearch.name bind listing.name,
                    ListingSearch.address bind listing.address,
                    ListingSearch.city bind listing.city,
                    ListingSearch.province bind listing.province,
                    ListingSearch.postal bind listing.postal,
                    ListingSearch.country bind listing.country,
                    doubleParam(listing.location.lng),
                    doubleParam(listing.location.lat),
                    ListingSearch.cuisine bind listing.cuisine?.value,
                    ListingSearch.isHandCut bind listing.isHandCut,
                    ListingSearch.isDelivery bind listing.isDelivery,
                    ListingSearch.verificationStatus bind listing.verificationStatus.name,
                    ListingSearch.price bind listing.price?.value,
                    ListingSearch.rating bind listing.rating?.value,
                    ListingSearch.alcoholServed bind listing.alcoholServed,
                    ListingSearch.halalScope bind listing.halalScope.name,
                    ListingSearch.crossContamination bind listing.crossContamination.name,
                ),
                StatementType.INSERT,
            )
        } else {
            // Index gate: remove non-qualified listings from the search index.
            executeUpdate(
                "DELETE FROM listing_search WHERE id = ?",
                listOf(ListingSearch.id bind listing.id),
                StatementType.DELETE,
            )
        }
    }

    private fun JdbcTransaction.replaceHalalItems(listingId: Uuid, items: Set<HalalItem>) {
        executeUpdate(
            "DELETE FROM restaurant_halal_items WHERE listing_id = ?",
            listOf(RestaurantHalalItems.listingId bind listingId),
            StatementType.DELETE,
        )
        items.forEach {
            executeUpdate(
                "INSERT INTO restaurant_halal_items (listing_id, name, is_halal) VALUES (?, ?, ?)",
                listOf(
                    RestaurantHalalItems.listingId bind listingId,
                    RestaurantHalalItems.name bind it.name,
                    RestaurantHalalItems.isHalal bind it.isHalal,
                ),
                StatementType.INSERT,
            )
        }
    }

    override suspend fun findById(id: Uuid): RestaurantListing? = suspendTransaction(db) {
        queryRows(
            """
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
                is_delivery,
                price,
                rating,
                alcohol_served,
                halal_scope,
                cross_contamination,
                owner_id,
                brand_id,
                provenance,
                venue_key,
                verification_status,
                created_at
            FROM restaurant_listings
            WHERE id = ?
            """.trimIndent(),
            listOf(RestaurantListings.id bind id),
        ) { rs -> toListing(rs) }.firstOrNull()?.let { withHalalItems(it) }
    }

    override suspend fun update(listing: RestaurantListing): RestaurantListing? = suspendTransaction(db) {
        val rows = executeUpdate(
            """
            UPDATE restaurant_listings SET
                name = ?, address = ?,
                location = ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography,
                cuisine = ?, is_hand_cut = ?, is_delivery = ?,
                price = ?, rating = ?, alcohol_served = ?,
                halal_scope = ?, cross_contamination = ?
            WHERE id = ?
            """.trimIndent(),
            listOf(
                RestaurantListings.name bind listing.name,
                RestaurantListings.address bind listing.address,
                doubleParam(listing.location.lng),
                doubleParam(listing.location.lat),
                RestaurantListings.cuisine bind listing.cuisine?.value,
                RestaurantListings.isHandCut bind listing.isHandCut,
                RestaurantListings.isDelivery bind listing.isDelivery,
                RestaurantListings.price bind listing.price?.value,
                RestaurantListings.rating bind listing.rating?.value,
                RestaurantListings.alcoholServed bind listing.alcoholServed,
                RestaurantListings.halalScope bind listing.halalScope.name,
                RestaurantListings.crossContamination bind listing.crossContamination.name,
                RestaurantListings.id bind listing.id,
            ),
        )
        if (rows == 0) return@suspendTransaction null

        // Rewrite the child stores + search mirror in the SAME transaction so
        // an edit is atomically consistent across source/mirror/child tables.
        replaceCuisines(listing.id, listing.cuisine)
        replaceHalalItems(listing.id, listing.halalItems)
        mirrorIntoListingSearch(listing)
        findById(listing.id)
    }

    /** Full replace of a listing's (single) cuisine in the multi-cuisine store (sc-44). */
    private fun JdbcTransaction.replaceCuisines(listingId: Uuid, cuisine: Cuisine?) {
        executeUpdate(
            "DELETE FROM restaurant_listing_cuisines WHERE listing_id = ?",
            listOf(RestaurantListingCuisines.listingId bind listingId),
            StatementType.DELETE,
        )
        cuisine?.let {
            executeUpdate(
                "INSERT INTO restaurant_listing_cuisines (listing_id, cuisine) VALUES (?, ?)",
                listOf(
                    RestaurantListingCuisines.listingId bind listingId,
                    RestaurantListingCuisines.cuisine bind it.value,
                ),
                StatementType.INSERT,
            )
        }
    }

    override suspend fun updateVerificationStatus(id: Uuid, status: VerificationStatus): RestaurantListing? {
        // Update the source row; no-op path returns 0 rows. The search mirror is
        // kept in sync in the SAME transaction so a promotion is atomically
        // visible to the public read surface (sc-73 → sc-49 verified display).
        val rows = suspendTransaction(db) {
            val updated = executeUpdate(
                "UPDATE restaurant_listings SET verification_status = ? WHERE id = ?",
                listOf(
                    RestaurantListings.verificationStatus bind status.name,
                    RestaurantListings.id bind id,
                ),
            )
            if (updated > 0) {
                executeUpdate(
                    "UPDATE listing_search SET verification_status = ? WHERE id = ?",
                    listOf(
                        ListingSearch.verificationStatus bind status.name,
                        ListingSearch.id bind id,
                    ),
                )
            }
            updated
        }
        // The reload happens after the write transaction commits, exactly as the
        // previous adapter did.
        return if (rows > 0) findById(id) else null
    }

    override suspend fun findAll(): List<RestaurantListing> = suspendTransaction(db) {
        queryRows(
            """
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
                is_delivery,
                price,
                rating,
                alcohol_served,
                halal_scope,
                cross_contamination,
                owner_id,
                brand_id,
                provenance,
                venue_key,
                verification_status,
                created_at
            FROM restaurant_listings
            ORDER BY created_at, name
            """.trimIndent(),
        ) { rs -> toListing(rs) }.map { withHalalItems(it) }
    }

    /**
     * Bind order matches the INSERT column list above (name → cross_contamination),
     * with the PostGIS point taking (lng, lat).
     */
    private fun saveArgs(listing: RestaurantListing): List<SqlArg> = listOf(
        RestaurantListings.name bind listing.name,
        RestaurantListings.address bind listing.address,
        RestaurantListings.city bind listing.city,
        RestaurantListings.province bind listing.province,
        RestaurantListings.postal bind listing.postal,
        RestaurantListings.country bind listing.country,
        doubleParam(listing.location.lng), // ST_MakePoint(x = longitude, y = latitude)
        doubleParam(listing.location.lat),
        RestaurantListings.cuisine bind listing.cuisine?.value,
        RestaurantListings.isHandCut bind listing.isHandCut,
        RestaurantListings.isDelivery bind listing.isDelivery,
        RestaurantListings.ownerId bind listing.ownerId,
        RestaurantListings.brandId bind listing.brandId,
        RestaurantListings.provenance bind listing.provenance?.value,
        RestaurantListings.venueKey bind listing.venueKey,
        RestaurantListings.verificationStatus bind listing.verificationStatus.name,
        RestaurantListings.price bind listing.price?.value,
        RestaurantListings.rating bind listing.rating?.value,
        RestaurantListings.alcoholServed bind listing.alcoholServed,
        RestaurantListings.halalScope bind listing.halalScope.name,
        RestaurantListings.crossContamination bind listing.crossContamination.name,
    )

    private fun JdbcTransaction.toListing(rs: ResultSet): RestaurantListing = RestaurantListing.fromStorage(
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
        isDelivery = rs.getObject("is_delivery", java.lang.Boolean::class.java) as Boolean?,
        price = rs.getBigDecimal("price")?.let { Price(it) },
        rating = rs.getBigDecimal("rating")?.let { Rating(it) },
        alcoholServed = rs.getBoolean("alcohol_served"),
        halalScope = HalalScope.valueOf(rs.getString("halal_scope")),
        crossContamination = CrossContamination.valueOf(rs.getString("cross_contamination")),
        ownerId = rs.getObject("owner_id", UUID::class.java)?.toKotlinUuid(),
        brandId = rs.getObject("brand_id", UUID::class.java)?.toKotlinUuid(),
        provenance = rs.getString("provenance")?.let { Provenance(it) },
        venueKey = rs.getString("venue_key"),
        verificationStatus = VerificationStatus.valueOf(rs.getString("verification_status")),
        createdAt = rs.getTimestamp("created_at").toInstant().toKotlinInstant(),
    )

    /** Load the per-item halal scope child rows (sc-119). */
    private fun JdbcTransaction.withHalalItems(listing: RestaurantListing): RestaurantListing {
        val items = queryRows(
            """
            SELECT name, is_halal
            FROM restaurant_halal_items
            WHERE listing_id = ?
            ORDER BY name
            """.trimIndent(),
            listOf(RestaurantHalalItems.listingId bind listing.id),
        ) { HalalItem(name = it.getString("name"), isHalal = it.getBoolean("is_halal")) }
        return if (items.isEmpty()) listing else listing.copy(halalItems = items.toSet())
    }
}
