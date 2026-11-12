package com.tahirslist.bootstrap.photo

import com.tahirslist.bootstrap.SharedPostgres
import com.tahirslist.domain.restaurant.Cuisine
import com.tahirslist.domain.restaurant.LatLng
import com.tahirslist.domain.restaurant.Provenance
import com.tahirslist.domain.restaurant.RestaurantListing
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlin.uuid.Uuid
import kotlinx.coroutines.runBlocking

/**
 * SC-231 D-12: the one-off purge selects by the gate's own rule, and only those
 * rows. A hand-written name list would rot; a rule that also catches a restaurant
 * would be worse than leaving the meat markets in place.
 */
class PurgeNonRestaurantsTest : FunSpec({

    /** An ingested row, like the ones the gate rejects today: no owner, a stamp. */
    fun listing(name: String) = RestaurantListing.imported(
        name = name,
        address = "1 Test St",
        location = LatLng(43.7, -79.4),
        cuisine = Cuisine("middle eastern"),
        provenance = Provenance("certifier-hms"),
        venueKey = "0000000000000000000000000000000000000000000000000000000000000000",
    )

    test("the three meat markets in the catalogue today are selected") {
        val names = listOf(
            "Jamaica Live Poultry Meat Market [Butcher Dept. Only]",
            "Hamza Meat Shop [Butcher Dept. ONLY]",
            "Alladin Halal Meat & Grocery [Butcher Dept. ONLY]",
        )

        PurgeSelection.select(names.map(::listing)) shouldHaveSize 3
    }

    test("restaurants, bakeries and markets with a kitchen are left alone") {
        val names = listOf(
            "Madina Market (Kitchen)",
            "Mannan Bakery and Grill",
            "Mannan Sweets & Restaurant",
            "Firin Bakery",
            "Mannan Bakery",
            "Mannan Bakery Factory",
            "Corner Coffee",
            "Paramount Fine Foods",
        )

        PurgeSelection.select(names.map(::listing)).shouldBeEmpty()
    }

    test("a dry run changes nothing — the selection is a pure read") {
        val rows = listOf(listing("Hamza Meat Shop"), listing("Noor Grill"))

        val first = PurgeSelection.select(rows)
        val second = PurgeSelection.select(rows)

        first.map { it.listing.name } shouldBe second.map { it.listing.name }
        first.single().reason.contains("butcher") shouldBe true
    }

    test("an owner's listing and a seed row are never selected, whatever the name") {
        val ownerOwned = listing("Poultry King").copy(ownerId = Uuid.random())
        val seed = listing("Hamza Meat Shop").copy(provenance = null)
        val ingested = listing("Hamza Meat Shop")

        PurgeSelection.select(listOf(ownerOwned, seed, ingested)).map { it.listing.name } shouldBe
            listOf("Hamza Meat Shop")
    }

    /**
     * The CLI must be able to delete a real row: `external_certification_claims`
     * is RESTRICT and `halal_certification_reviews` is NO ACTION, so both have to
     * go before the listing. This inserts one of each and proves the deletion.
     */
    test("deleting a listing removes its claim and review first, in one transaction") {
        runBlocking {
            SharedPostgres.migrateOnce()
            val dataSource = SharedPostgres.dataSource
            val listings = com.tahirslist.persistence.listing.ExposedRestaurantListingRepository(SharedPostgres.database)
            val accounts = com.tahirslist.persistence.account.ExposedAccountRepository(SharedPostgres.database)

            val owner = accounts.save(
                com.tahirslist.domain.account.Account.new(
                    com.tahirslist.domain.account.Email("purge-${Uuid.random()}@example.com"),
                    "argon2id\$h",
                ),
            ).id
            val saved = listings.save(
                RestaurantListing.new(
                    name = "Delete Probe Meat Market",
                    address = "1 Probe Rd",
                    location = LatLng(43.7, -79.4),
                    cuisine = Cuisine("middle eastern"),
                    ownerId = owner,
                ),
            )

            val probe = java.util.UUID.fromString(saved.id.toString())
            val connection: java.sql.Connection = dataSource.connection
            try {
                connection.prepareStatement(
                    """
                    INSERT INTO external_certification_claims
                        (listing_id, source, source_tier, source_record_ref, source_url, certifier, hand_cut_declaration)
                    VALUES (?, 'hms', 1, ?, 'https://hmsusa.org/x', 'Halal Monitoring Services (HMS)', 'UNSPECIFIED')
                    """.trimIndent(),
                ).use { statement ->
                    statement.setObject(1, probe)
                    statement.setObject(2, java.util.UUID.randomUUID().toString())
                    statement.executeUpdate()
                }
                connection.prepareStatement(
                    """
                    INSERT INTO halal_certification_reviews (listing_id, submitted_by, state)
                    VALUES (?, ?, 'SUBMITTED')
                    """.trimIndent(),
                ).use { statement ->
                    statement.setObject(1, probe)
                    statement.setObject(2, java.util.UUID.fromString(owner.toString()))
                    statement.executeUpdate()
                }
            } finally {
                connection.close()
            }

            deleteListing(dataSource, saved.id.toString())

            listOf(
                "SELECT count(*) FROM restaurant_listings WHERE id = ?",
                "SELECT count(*) FROM external_certification_claims WHERE listing_id = ?",
                "SELECT count(*) FROM halal_certification_reviews WHERE listing_id = ?",
            ).forEach { sql -> countFor(dataSource, sql, probe) shouldBe 0 }
        }
    }
})

/** Row count for one listing-scoped query, on the test's own pool. */
private fun countFor(dataSource: javax.sql.DataSource, sql: String, listingId: java.util.UUID): Int {
    val connection: java.sql.Connection = dataSource.connection
    try {
        connection.prepareStatement(sql).use { statement ->
            statement.setObject(1, listingId)
            statement.executeQuery().use { rs -> return if (rs.next()) rs.getInt(1) else 0 }
        }
    } finally {
        connection.close()
    }
}
