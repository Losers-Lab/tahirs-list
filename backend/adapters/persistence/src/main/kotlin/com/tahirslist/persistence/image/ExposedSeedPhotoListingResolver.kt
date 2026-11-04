package com.tahirslist.persistence.image

import com.tahirslist.application.image.SeedHeroPhoto
import com.tahirslist.application.image.SeedPhotoListingResolver
import com.tahirslist.application.image.SeedPhotoResolution
import com.tahirslist.persistence.db.RestaurantListings
import com.tahirslist.persistence.db.bind
import com.tahirslist.persistence.db.queryRows
import com.tahirslist.persistence.db.stringParam
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import java.util.UUID
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid

/**
 * Resolves a [SeedHeroPhoto] to its seeded `restaurant_listings` row.
 *
 * Scoped to seed provenance (`research-seed / photon-geocode` — the only rows
 * the manifest covers). Matching is unambiguous only:
 *  - normalized-name equality first, then normalized-address equality;
 *    "normalized" = lower-case + trimmed (both sides), so a manifest casing or
 *    whitespace variant of a seeded name still matches;
 *  - a row is resolved only if EXACTLY ONE listing matches; multiple candidates
 *    ("The Halal Guys" has 3 locations) or zero -> [SeedPhotoResolution.Unresolved].
 *
 * This deliberately avoids fuzzy name guessing: attaching a hero photo to the
 * wrong restaurant is worse than skipping it and reporting it
 * (docs/design/sc-157-image-variants.md §"Manifest → listing resolution").
 */
class ExposedSeedPhotoListingResolver(private val db: Database) : SeedPhotoListingResolver {

    override suspend fun resolve(photo: SeedHeroPhoto): SeedPhotoResolution = suspendTransaction(db) {
        if (!photo.name.isBlank()) {
            matchUnique("name", photo.name.trim())
                ?.let { return@suspendTransaction SeedPhotoResolution.Resolved(it) }
        }
        // Name not unique/exact: fall back to normalized address when the row
        // carries a street we can key on (brief-approx neighborhood refs won't).
        val address = photo.addressGiven
        if (!address.isNullOrBlank()) {
            matchUnique("address", address.trim())
                ?.let { return@suspendTransaction SeedPhotoResolution.Resolved(it) }
        }
        SeedPhotoResolution.Unresolved("no unique seed listing for '${photo.name}'")
    }

    /** Returns the single matching listing id, or null when 0 or >1 match. */
    private fun JdbcTransaction.matchUnique(column: String, value: String): Uuid? {
        val ids = queryRows(
            """
            SELECT id FROM restaurant_listings
            WHERE provenance = ? AND lower(btrim($column)) = lower(?)
            """.trimIndent(),
            listOf(RestaurantListings.provenance bind SEED_PROVENANCE, stringParam(value)),
        ) { rs -> rs.getObject("id", UUID::class.java).toKotlinUuid() }
        return if (ids.size == 1) ids.single() else null
    }

    private companion object {
        const val SEED_PROVENANCE = "research-seed / photon-geocode"
    }
}
