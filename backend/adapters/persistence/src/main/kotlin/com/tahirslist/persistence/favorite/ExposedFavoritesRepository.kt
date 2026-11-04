package com.tahirslist.persistence.favorite

import com.tahirslist.application.favorite.FavoritesRepository
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
import com.tahirslist.persistence.db.Favorites
import com.tahirslist.persistence.db.RestaurantHalalItems
import com.tahirslist.persistence.db.bind
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
 * Exposed implementation of the [FavoritesRepository] port against the
 * `favorites` table (see V12__create_favorites.sql). Deliberately boring and
 * explicit.
 *
 * [add] uses `ON CONFLICT DO NOTHING`: the composite PK (user_id, listing_id)
 * makes the idempotent POST contract structural, so a duplicate favourite is a
 * no-op rather than an error.
 *
 * [findFavoriteListings] joins favorites → restaurant_listings so the read
 * returns full listing rows (the browse-card shape) in one query — no N+1 for the
 * listings themselves. Per-item halal scope is still loaded per row from the
 * child table, as before (sc-119).
 */
class ExposedFavoritesRepository(private val db: Database) : FavoritesRepository {

    override suspend fun add(userId: Uuid, listingId: Uuid) {
        suspendTransaction(db) {
            executeUpdate(
                """
                INSERT INTO favorites (user_id, listing_id)
                VALUES (?, ?)
                ON CONFLICT (user_id, listing_id) DO NOTHING
                """.trimIndent(),
                listOf(Favorites.userId bind userId, Favorites.listingId bind listingId),
                StatementType.INSERT,
            )
        }
    }

    override suspend fun remove(userId: Uuid, listingId: Uuid) {
        suspendTransaction(db) {
            executeUpdate(
                "DELETE FROM favorites WHERE user_id = ? AND listing_id = ?",
                listOf(Favorites.userId bind userId, Favorites.listingId bind listingId),
                StatementType.DELETE,
            )
        }
    }

    override suspend fun findFavoriteListings(userId: Uuid): List<RestaurantListing> = suspendTransaction(db) {
        queryRows(
            """
            SELECT
                l.id,
                l.name,
                l.address,
                ST_Y(l.location::geometry) AS lat,
                ST_X(l.location::geometry) AS lng,
                l.cuisine,
                l.is_hand_cut,
                l.price,
                l.rating,
                l.alcohol_served,
                l.halal_scope,
                l.cross_contamination,
                l.owner_id,
                l.brand_id,
                l.provenance,
                l.verification_status,
                l.created_at
            FROM favorites f
            JOIN restaurant_listings l ON l.id = f.listing_id
            WHERE f.user_id = ?
            ORDER BY f.created_at DESC, l.name
            """.trimIndent(),
            listOf(Favorites.userId bind userId),
        ) { rs -> toListing(rs) }.map { withHalalItems(it) }
    }

    private fun JdbcTransaction.toListing(rs: ResultSet): RestaurantListing = RestaurantListing.fromStorage(
        id = rs.getObject("id", UUID::class.java).toKotlinUuid(),
        name = rs.getString("name"),
        address = rs.getString("address"),
        location = LatLng(lat = rs.getDouble("lat"), lng = rs.getDouble("lng")),
        cuisine = rs.getString("cuisine")?.let { Cuisine(it) },
        isHandCut = rs.getObject("is_hand_cut", java.lang.Boolean::class.java) as Boolean?,
        price = rs.getBigDecimal("price")?.let { Price(it) },
        rating = rs.getBigDecimal("rating")?.let { Rating(it) },
        halalScope = HalalScope.valueOf(rs.getString("halal_scope")),
        crossContamination = CrossContamination.valueOf(rs.getString("cross_contamination")),
        ownerId = rs.getObject("owner_id", UUID::class.java)?.toKotlinUuid(),
        brandId = rs.getObject("brand_id", UUID::class.java)?.toKotlinUuid(),
        provenance = rs.getString("provenance")?.let { Provenance(it) },
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
