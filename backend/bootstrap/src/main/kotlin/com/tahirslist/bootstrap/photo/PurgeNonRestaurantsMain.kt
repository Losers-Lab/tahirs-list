package com.tahirslist.bootstrap.photo

import com.tahirslist.bootstrap.config.loadConfig
import com.tahirslist.domain.restaurant.AdmissionDecision
import com.tahirslist.domain.restaurant.RestaurantListing
import com.tahirslist.ingestion.enrich.AdmissionPolicy
import com.tahirslist.persistence.db.DatabaseFactory
import com.tahirslist.persistence.db.Migrations
import com.tahirslist.persistence.listing.ExposedRestaurantListingRepository
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.runBlocking

/**
 * D-12 (SC-231): the one-off removal of catalogue rows that the admission gate
 * would never admit — the meat markets HMS files under *Restaurants*.
 *
 * The selection is the same rule the gate uses (a shop word with no premises
 * word), not a hand-written name list: a list would silently stop matching the
 * moment a name changed, and would remove whatever someone typed into it.
 *
 * Two guards, both deliberate:
 *
 *  - **no `--execute` without `--backup`**: the file must exist and be non-empty,
 *    so a dump was actually taken before a destructive run;
 *  - **dry run is the default**: it prints exactly what would go and changes
 *    nothing.
 *
 * Child rows (claims, cuisines, halal items, favourites, reviews, cover photo,
 * search mirror) are removed by the schema's `ON DELETE CASCADE`.
 */
fun main(args: Array<String>) = runBlocking {
    val options = PurgeOptions.parse(args)
    val execute = options.flag("execute")
    val backup = options["backup"]

    val config = loadConfig()
    Migrations.migrate(
        jdbcUrl = config.database.jdbcUrl,
        user = config.database.user,
        password = config.database.password,
    )
    val dataSource = DatabaseFactory.dataSource(
        jdbcUrl = config.database.jdbcUrl,
        user = config.database.user,
        password = config.database.password,
        maxPoolSize = 2,
    )

    try {
        val database = DatabaseFactory.connect(dataSource)
        val listings = ExposedRestaurantListingRepository(database)
        val all = listings.findAll()
        val doomed = PurgeSelection.select(all)

        println("catalogue: ${all.size} listing(s)")
        println("to purge: ${doomed.size}")
        doomed.forEach { row ->
            println("  - ${row.listing.name} (${row.listing.id}) — ${row.reason}")
        }

        if (doomed.isEmpty()) {
            println("Nothing to purge.")
            return@runBlocking
        }
        if (!execute) {
            println("Dry run — pass --execute --backup=<dump file> to remove these rows.")
            return@runBlocking
        }

        if (backup == null) {
            error("refusing --execute without --backup=<dump file>: take a dump first")
        }
        val dump = Path.of(backup)
        if (!Files.isRegularFile(dump) || Files.size(dump) == 0L) {
            error("refusing --execute: backup '$backup' does not exist or is empty")
        }

        doomed.forEach { row ->
            deleteListing(dataSource, row.listing.id.toString())
            println("purged ${row.listing.name} (${row.listing.id})")
        }
        println("Purged ${doomed.size} row(s) after backup: $backup")
    } finally {
        dataSource.close()
    }
}

/** One row the gate refuses, with the evidence that decided it. */
data class PurgeCandidate(val listing: RestaurantListing, val reason: String)

/**
 * Remove one listing and its search-mirror row in a single transaction. Every
 * child table (claims, cuisines, halal items, favourites, reviews, the cover
 * photo) cascades from `restaurant_listings`.
 */
private fun deleteListing(dataSource: javax.sql.DataSource, id: String) {
    dataSource.connection.use { connection ->
        connection.autoCommit = false
        try {
            connection.prepareStatement("DELETE FROM listing_search WHERE id = ?").use { statement ->
                statement.setObject(1, java.util.UUID.fromString(id))
                statement.executeUpdate()
            }
            connection.prepareStatement("DELETE FROM restaurant_listings WHERE id = ?").use { statement ->
                statement.setObject(1, java.util.UUID.fromString(id))
                statement.executeUpdate()
            }
            connection.commit()
        } catch (e: Exception) {
            connection.rollback()
            throw e
        } finally {
            connection.autoCommit = true
        }
    }
}

/**
 * The selection rule, kept pure so it can be tested without a database.
 *
 * The shop-word rule is category-independent by construction, which is what makes
 * it the right rule here: the source filed these rows under *Restaurants*, and
 * trusting that field is exactly what let them in.
 */
internal object PurgeSelection {

    fun select(listings: List<RestaurantListing>, policy: AdmissionPolicy = AdmissionPolicy()): List<PurgeCandidate> =
        listings.mapNotNull { listing ->
            val decision = policy.decide(category = "", name = listing.name)
            if (decision is AdmissionDecision.Excluded) {
                PurgeCandidate(listing, "${decision.venueClass.name.lowercase()}: ${decision.reason}")
            } else {
                null
            }
        }
}

/** Tiny `--key=value` reader, matching the other CLIs. */
private class PurgeOptions(private val values: Map<String, String>) {
    operator fun get(key: String): String? = values[key]?.takeIf { it.isNotBlank() && it != "true" }

    fun flag(key: String): Boolean = values[key] == "true"

    companion object {
        fun parse(args: Array<String>): PurgeOptions = PurgeOptions(
            args.filter { it.startsWith("--") }.associate { arg ->
                val body = arg.removePrefix("--")
                val name = body.substringBefore('=')
                val value = if ('=' in body) body.substringAfter('=') else "true"
                name to value
            },
        )
    }
}
