package com.tahirslist.persistence.listing

import com.tahirslist.application.ingestion.BatchLoadResult
import com.tahirslist.application.ingestion.BatchPlanException
import com.tahirslist.application.ingestion.ClaimConflictException
import com.tahirslist.application.ingestion.ClaimReattachment
import com.tahirslist.application.ingestion.IngestionCandidate
import com.tahirslist.application.ingestion.ListingMerge
import com.tahirslist.application.ingestion.ListingWrite
import com.tahirslist.application.ingestion.WriteTarget
import com.tahirslist.application.listing.RestaurantListingRepository
import com.tahirslist.domain.restaurant.CertificationClaim
import com.tahirslist.domain.restaurant.CertificationTier
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
import com.tahirslist.persistence.db.Brands
import com.tahirslist.persistence.db.ExternalCertificationClaims
import com.tahirslist.persistence.db.Favorites
import com.tahirslist.persistence.db.HalalCertificationReviews
import com.tahirslist.persistence.db.ListingSearch
import com.tahirslist.persistence.db.RestaurantHalalItems
import com.tahirslist.persistence.db.RestaurantListingCuisines
import com.tahirslist.persistence.db.RestaurantListings
import com.tahirslist.persistence.db.SqlArg
import com.tahirslist.persistence.db.bind
import com.tahirslist.persistence.db.doubleParam
import com.tahirslist.persistence.db.executeUpdate
import com.tahirslist.persistence.db.queryRows
import com.tahirslist.persistence.db.toSqlTimestamp
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import java.sql.ResultSet
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.toKotlinInstant
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid
import kotlinx.datetime.toJavaLocalDate

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
        loadById(id)
    }

    override suspend fun findClaim(source: String, sourceRecordRef: String): RestaurantListing? =
        suspendTransaction(db) {
            queryRows(
                """
                SELECT $LISTING_COLUMNS
                FROM restaurant_listings
                WHERE id = (
                    SELECT listing_id
                    FROM external_certification_claims
                    WHERE source = ? AND source_record_ref = ?
                    ORDER BY created_at
                    LIMIT 1
                )
                """.trimIndent(),
                listOf(
                    ExternalCertificationClaims.claimSource bind source,
                    ExternalCertificationClaims.sourceRecordRef bind sourceRecordRef,
                ),
            ) { toListing(it) }.firstOrNull()?.let { withHalalItems(it) }
        }

    override suspend fun findByVenueKey(venueKey: String): RestaurantListing? = suspendTransaction(db) {
        queryRows(
            """
            SELECT $LISTING_COLUMNS
            FROM restaurant_listings
            WHERE venue_key = ?
            """.trimIndent(),
            listOf(RestaurantListings.venueKey bind venueKey),
        ) { toListing(it) }.firstOrNull()?.let { withHalalItems(it) }
    }

    override suspend fun findByNormalizedLocation(lat: Double, lng: Double): List<RestaurantListing> =
        suspendTransaction(db) {
            queryRows(
                """
                SELECT $LISTING_COLUMNS
                FROM restaurant_listings
                WHERE location_lat_6 = round(?::numeric, 6)
                  AND location_lng_6 = round(?::numeric, 6)
                  -- Matches uq_restaurant_listings_research_location's predicate so
                  -- the lookup uses the partial index instead of a seq scan.
                  AND provenance IS NOT NULL
                ORDER BY created_at, name
                """.trimIndent(),
                listOf(doubleParam(lat), doubleParam(lng)),
            ) { toListing(it) }.map { withHalalItems(it) }
        }

    /**
     * One transaction for the whole reviewed batch (BR-13): brands, listings,
     * claims and the search mirror. A claim conflict or any SQL failure rolls
     * the entire batch back, so a load never leaves a partial catalogue.
     */
    override suspend fun applyBatch(
        writes: List<ListingWrite>,
        merges: List<ListingMerge>,
        reattachments: List<ClaimReattachment>,
    ): BatchLoadResult = suspendTransaction(db) {
        var inserted = 0
        var upgraded = 0
        var claimsWritten = 0

        // Curator decisions first: a re-pointed claim and a merged duplicate
        // change which listing a write lands on, so they precede the writes and
        // are re-validated against the current rows.
        reattachments.forEach { reattachClaim(it) }
        merges.forEach { mergeListings(it) }

        for (write in writes) {
            val candidate = write.candidate
            val brandId = resolveOrCreateBrand(candidate.name)
            val listingId = when (val target = write.target) {
                is WriteTarget.Insert -> {
                    inserted++
                    insertImported(candidate, brandId)
                }
                is WriteTarget.Update -> {
                    // Fail on a claim conflict *before* touching the listing row:
                    // the update would otherwise surface a location/venue unique
                    // violation first and hide the real cause (BR-21).
                    assertClaimsBelongTo(target.listingId, candidate)
                    upgraded++
                    updateImported(target.listingId, candidate, brandId)
                }
            }
            for (claim in candidate.claims) {
                writeClaim(listingId, claim)
                claimsWritten++
            }
            recomputeCertifierTrust(
                listingId,
                // A curated Tier-3 row's cross-contamination is explicit and
                // must persist; a Tier-1 row derives it from its claim.
                manualCrossContamination = candidate.crossContamination
                    .takeIf { candidate.tier != CertificationTier.TIER_1 },
            )
            loadById(listingId)?.let { mirrorIntoListingSearch(it) }
        }

        BatchLoadResult(
            inserted = inserted,
            upgraded = upgraded,
            merged = merges.size,
            claimsWritten = claimsWritten,
        )
    }

    /**
     * `attach-to`: move one claim to the listing the curator named. The owner is
     * re-read so a claim that moved since the preview fails the batch instead of
     * re-pointing someone else's evidence.
     */
    private fun JdbcTransaction.reattachClaim(reattachment: ClaimReattachment) {
        val owner = claimOwner(reattachment.source, reattachment.sourceRecordRef)
            ?: throw BatchPlanException(
            "claim ${reattachment.source}::${reattachment.sourceRecordRef} no longer exists; " +
                "re-run the preview and decide again",
        )
        if (owner != reattachment.fromListingId) {
            throw BatchPlanException(
                "claim ${reattachment.source}::${reattachment.sourceRecordRef} now belongs to $owner, " +
                    "not ${reattachment.fromListingId}; re-run the preview and decide again",
            )
        }
        if (loadById(reattachment.toListingId) == null) {
            throw BatchPlanException("listing ${reattachment.toListingId} does not exist; cannot attach the claim")
        }
        executeUpdate(
            """
            UPDATE external_certification_claims SET listing_id = ?
            WHERE source = ? AND source_record_ref = ?
            """.trimIndent(),
            listOf(
                ExternalCertificationClaims.listingId bind reattachment.toListingId,
                ExternalCertificationClaims.claimSource bind reattachment.source,
                ExternalCertificationClaims.sourceRecordRef bind reattachment.sourceRecordRef,
            ),
        )

        // The claim left this listing: if it was its only Tier-1 evidence, the
        // source row must lose the trust it conferred and leave the index.
        recomputeCertifierTrust(reattachment.fromListingId)
        loadById(reattachment.fromListingId)?.let { mirrorIntoListingSearch(it) }
    }

    /**
     * `same-venue`: fold [ListingMerge.retireId] into [ListingMerge.keepId] in
     * the same transaction as the load.
     *
     * Claims, cuisines, halal items, favourites and verification reviews move to
     * the keeper (duplicates are dropped by the child primary keys), an owner is
     * carried over only when the keeper has none, the retired row leaves the
     * search mirror, and the row itself is deleted. Nothing here is automatic:
     * it only runs for a decision a curator recorded.
     */
    private fun JdbcTransaction.mergeListings(merge: ListingMerge) {
        val keep = loadById(merge.keepId)
            ?: throw BatchPlanException("merge keeper ${merge.keepId} does not exist")
        val retire = loadById(merge.retireId)
            ?: throw BatchPlanException("merge duplicate ${merge.retireId} does not exist")

        if (keep.ownerId == null && retire.ownerId != null) {
            executeUpdate(
                "UPDATE restaurant_listings SET owner_id = ? WHERE id = ?",
                listOf(RestaurantListings.ownerId bind retire.ownerId, RestaurantListings.id bind keep.id),
            )
        }

        executeUpdate(
            """
            INSERT INTO restaurant_listing_cuisines (listing_id, cuisine)
            SELECT ?, cuisine FROM restaurant_listing_cuisines WHERE listing_id = ?
            ON CONFLICT (listing_id, cuisine) DO NOTHING
            """.trimIndent(),
            listOf(
                RestaurantListingCuisines.listingId bind keep.id,
                RestaurantListingCuisines.listingId bind retire.id,
            ),
        )
        executeUpdate(
            "DELETE FROM restaurant_listing_cuisines WHERE listing_id = ?",
            listOf(RestaurantListingCuisines.listingId bind retire.id),
            StatementType.DELETE,
        )

        executeUpdate(
            """
            INSERT INTO restaurant_halal_items (listing_id, name, is_halal)
            SELECT ?, name, is_halal FROM restaurant_halal_items WHERE listing_id = ?
            ON CONFLICT (listing_id, name) DO NOTHING
            """.trimIndent(),
            listOf(
                RestaurantHalalItems.listingId bind keep.id,
                RestaurantHalalItems.listingId bind retire.id,
            ),
        )
        executeUpdate(
            "DELETE FROM restaurant_halal_items WHERE listing_id = ?",
            listOf(RestaurantHalalItems.listingId bind retire.id),
            StatementType.DELETE,
        )

        // A favourite of the duplicate is a favourite of the keeper; the pair
        // primary key drops the ones the user already had.
        executeUpdate(
            """
            INSERT INTO favorites (user_id, listing_id, created_at)
            SELECT user_id, ?, created_at FROM favorites WHERE listing_id = ?
            ON CONFLICT DO NOTHING
            """.trimIndent(),
            listOf(Favorites.listingId bind keep.id, Favorites.listingId bind retire.id),
        )
        executeUpdate(
            "DELETE FROM favorites WHERE listing_id = ?",
            listOf(Favorites.listingId bind retire.id),
            StatementType.DELETE,
        )

        // Reviews are evidence about the venue, not about the row.
        executeUpdate(
            "UPDATE halal_certification_reviews SET listing_id = ? WHERE listing_id = ?",
            listOf(HalalCertificationReviews.listingId bind keep.id, HalalCertificationReviews.listingId bind retire.id),
        )

        executeUpdate(
            "UPDATE external_certification_claims SET listing_id = ? WHERE listing_id = ?",
            listOf(
                ExternalCertificationClaims.listingId bind keep.id,
                ExternalCertificationClaims.listingId bind retire.id,
            ),
        )

        // The keeper now owns the union of both claim sets: recompute so a
        // verified row that was folded in cannot lose its trust (round 2, B4).
        recomputeCertifierTrust(keep.id)
        loadById(keep.id)?.let { mirrorIntoListingSearch(it) }

        executeUpdate(
            "DELETE FROM listing_search WHERE id = ?",
            listOf(ListingSearch.id bind retire.id),
            StatementType.DELETE,
        )
        executeUpdate(
            "DELETE FROM restaurant_listings WHERE id = ?",
            listOf(RestaurantListings.id bind retire.id),
            StatementType.DELETE,
        )
    }

    private fun JdbcTransaction.loadById(id: Uuid): RestaurantListing? =
        queryRows(
            """
            SELECT $LISTING_COLUMNS
            FROM restaurant_listings
            WHERE id = ?
            """.trimIndent(),
            listOf(RestaurantListings.id bind id),
        ) { rs -> toListing(rs) }.firstOrNull()?.let { withHalalItems(it) }

    /**
     * Updates only the identity/address content ingestion owns. Trust columns
     * (status, basis, hand-cut, provenance, cross-contamination) are NOT written
     * here: after the claims are written, [recomputeCertifierTrust] derives them
     * from the resulting claim set. That is what lets a certifier's own
     * withdrawal of hand-cut take effect, and stops a Tier-3 row from silently
     * overriding a verified venue (D-10).
     */
    private fun JdbcTransaction.updateImported(
        id: Uuid,
        candidate: IngestionCandidate,
        brandId: Uuid,
    ): Uuid {
        val rows = executeUpdate(
            """
            UPDATE restaurant_listings SET
                name = ?, address = ?, city = ?, province = ?, postal = ?, country = ?,
                location = ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography,
                brand_id = ?, venue_key = ?
            WHERE id = ?
            """.trimIndent(),
            listOf(
                RestaurantListings.name bind candidate.name,
                RestaurantListings.address bind candidate.address,
                RestaurantListings.city bind candidate.city,
                RestaurantListings.province bind candidate.region,
                RestaurantListings.postal bind candidate.postal,
                RestaurantListings.country bind candidate.country,
                doubleParam(candidate.location.lng),
                doubleParam(candidate.location.lat),
                RestaurantListings.brandId bind brandId,
                RestaurantListings.venueKey bind candidate.venueKey,
                RestaurantListings.id bind id,
            ),
        )
        check(rows == 1) { "listing $id vanished between preview and apply" }
        return id
    }

    /**
     * Trust is a projection of the claim set, not an accumulator.
     *
     * - A Tier-1 claim wins: `VERIFIED` + `CERTIFIER_REGISTRY`, hand-cut follows
     *   *that claim's current declaration* (TRUE only for HAND_CUT, FALSE for
     *   declared-not and for silence), certifier provenance, index-qualified.
     * - With no Tier-1 claim, only trust this method previously derived
     *   (`verification_basis = CERTIFIER_REGISTRY`) is withdrawn. A committee
     *   verification (basis NULL) and an owner's hand-cut value are left alone:
     *   an ingestion batch never demotes them (D-10).
     * - A merge moved the retired row's claims here, so the keeper is recomputed
     *   from the union and cannot lose a verified outcome (review round 2).
     */
    private fun JdbcTransaction.recomputeCertifierTrust(
        listingId: Uuid,
        /** The explicit curated value of a Tier-3 candidate in this batch, if any. */
        manualCrossContamination: CrossContamination? = null,
    ) {
        val current = queryRows(
            """
            SELECT verification_basis, provenance, cross_contamination
            FROM restaurant_listings WHERE id = ?
            """.trimIndent(),
            listOf(RestaurantListings.id bind listingId),
        ) { rs ->
            Triple(
                rs.getString("verification_basis"),
                rs.getString("provenance")?.let { Provenance(it) },
                CrossContamination.valueOf(rs.getString("cross_contamination")),
            )
        }.firstOrNull() ?: return

        val winner = queryRows(
            """
            SELECT source, source_tier, source_record_ref, hand_cut_declaration
            FROM external_certification_claims
            WHERE listing_id = ?
            """.trimIndent(),
            listOf(ExternalCertificationClaims.listingId bind listingId),
        ) { rs ->
            ClaimEvidence(
                source = rs.getString("source"),
                tier = rs.getShort("source_tier").toInt(),
                recordRef = rs.getString("source_record_ref"),
                declaration = rs.getString("hand_cut_declaration"),
            )
        }.minWithOrNull(
            compareBy<ClaimEvidence> { it.tier }
                .thenBy { IngestionCandidate.sourcePriority(it.source) }
                .thenBy { it.recordRef },
        )

        if (winner != null && winner.tier == 1) {
            val handCut = winner.declaration == "HAND_CUT"
            // An unknown future certifier is not mislabelled as HMS: keep the
            // row's existing provenance when the source has no stamp.
            val provenance = runCatching { IngestionCandidate.provenanceFor(winner.source) }
                .getOrNull() ?: current.second
            executeUpdate(
                """
                UPDATE restaurant_listings SET
                    verification_status = 'VERIFIED',
                    verification_basis = 'CERTIFIER_REGISTRY',
                    is_hand_cut = ?,
                    provenance = ?,
                    cross_contamination = 'NO_CROSS_CONTAMINATION'
                WHERE id = ?
                """.trimIndent(),
                listOf(
                    RestaurantListings.isHandCut bind handCut,
                    RestaurantListings.provenance bind provenance?.value,
                    RestaurantListings.id bind listingId,
                ),
            )
            return
        }

        val withdrawing = current.first == "CERTIFIER_REGISTRY"
        if (withdrawing) {
            // The certifier evidence was withdrawn or moved away: withdraw only
            // what the certifier conferred. Committee trust has basis NULL.
            executeUpdate(
                """
                UPDATE restaurant_listings SET
                    verification_status = 'UNVERIFIED',
                    verification_basis = NULL,
                    is_hand_cut = NULL
                WHERE id = ?
                """.trimIndent(),
                listOf(RestaurantListings.id bind listingId),
            )
        }

        // Cross-contamination is evidence about the venue. With no Tier-1 claim
        // the curated row's explicit value is authoritative; a withdrawal with
        // no new statement falls back to UNCERTAIN (and leaves the index).
        val cross = when {
            !withdrawing && manualCrossContamination == null -> return
            else -> manualCrossContamination ?: CrossContamination.DEFAULT
        }
        executeUpdate(
            "UPDATE restaurant_listings SET cross_contamination = ? WHERE id = ?",
            listOf(
                RestaurantListings.crossContamination bind cross.name,
                RestaurantListings.id bind listingId,
            ),
        )
    }

    private data class ClaimEvidence(
        val source: String,
        val tier: Int,
        val recordRef: String,
        val declaration: String,
    )

    /** D-15: inserts go through the domain factory, so its trust guard is on the write path. */
    private fun JdbcTransaction.insertImported(
        candidate: IngestionCandidate,
        brandId: Uuid,
    ): Uuid {
        val listing = RestaurantListing.imported(
            name = candidate.name,
            address = candidate.address,
            city = candidate.city,
            province = candidate.region,
            postal = candidate.postal,
            country = candidate.country,
            location = candidate.location,
            cuisine = null,
            isHandCut = candidate.isHandCut,
            isDelivery = null,
            price = null,
            rating = null,
            ownerId = null,
            brandId = brandId,
            provenance = candidate.provenance,
            venueKey = candidate.venueKey,
            verificationStatus = candidate.verificationStatus,
            verificationBasis = candidate.verificationBasis,
            createdAt = Clock.System.now(),
            crossContamination = candidate.crossContamination,
        )
        return queryRows(
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
        ) { it.getObject("id", UUID::class.java).toKotlinUuid() }.firstOrNull()
            ?: error("INSERT RETURNING id returned no row")
    }

    private fun JdbcTransaction.resolveOrCreateBrand(name: String): Uuid {
        val brandName = name.trim()
        // Case-insensitive, exactly like ExposedBrandRepository: "The Halal
        // Guys" and "the halal guys" are one brand, not two rows.
        findBrand(brandName)?.let { return it }
        executeUpdate(
            "INSERT INTO brands (name) VALUES (?) ON CONFLICT (name) DO NOTHING",
            listOf(Brands.name bind brandName),
            StatementType.INSERT,
        )
        return findBrand(brandName)
            ?: error("brand '$brandName' could not be created or found")
    }

    private fun JdbcTransaction.findBrand(name: String): Uuid? =
        queryRows(
            "SELECT id FROM brands WHERE lower(name) = lower(?) ORDER BY created_at LIMIT 1",
            listOf(Brands.name bind name),
        ) { it.getObject("id", UUID::class.java).toKotlinUuid() }.firstOrNull()

    private fun JdbcTransaction.assertClaimsBelongTo(listingId: Uuid, candidate: IngestionCandidate) {
        for (claim in candidate.claims) {
            val owner = claimOwner(claim)
            if (owner != null && owner != listingId) {
                throw ClaimConflictException(claim.key, owner, listingId)
            }
        }
    }

    private fun JdbcTransaction.claimOwner(claim: CertificationClaim): Uuid? =
        claimOwner(claim.source, claim.sourceRecordRef)

    private fun JdbcTransaction.claimOwner(source: String, sourceRecordRef: String): Uuid? =
        queryRows(
            "SELECT listing_id FROM external_certification_claims WHERE source = ? AND source_record_ref = ?",
            listOf(
                ExternalCertificationClaims.claimSource bind source,
                ExternalCertificationClaims.sourceRecordRef bind sourceRecordRef,
            ),
        ) { it.getObject("listing_id", UUID::class.java).toKotlinUuid() }.firstOrNull()

    private fun JdbcTransaction.writeClaim(listingId: Uuid, claim: CertificationClaim) {
        val existing = claimOwner(claim)

        if (existing != null && existing != listingId) {
            throw ClaimConflictException(claim.key, existing, listingId)
        }

        val now = Clock.System.now()
        if (existing == null) {
            executeUpdate(
                """
                INSERT INTO external_certification_claims
                    (listing_id, source, source_tier, source_record_ref, source_url, certifier,
                     expires_on, hand_cut_declaration, first_seen, last_seen)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                listOf(
                    ExternalCertificationClaims.listingId bind listingId,
                    ExternalCertificationClaims.claimSource bind claim.source,
                    ExternalCertificationClaims.sourceTier bind claim.tier.rank.toShort(),
                    ExternalCertificationClaims.sourceRecordRef bind claim.sourceRecordRef,
                    ExternalCertificationClaims.sourceUrl bind claim.sourceUrl,
                    ExternalCertificationClaims.certifier bind claim.certifier,
                    ExternalCertificationClaims.expiresOn bind claim.expiresOn?.toJavaLocalDate(),
                    ExternalCertificationClaims.handCutDeclaration bind claim.declaredHandCut.name,
                    ExternalCertificationClaims.firstSeen bind (claim.firstSeen ?: now).toSqlTimestamp(),
                    ExternalCertificationClaims.lastSeen bind (claim.lastSeen ?: now).toSqlTimestamp(),
                ),
                StatementType.INSERT,
            )
        } else {
            executeUpdate(
                """
                UPDATE external_certification_claims SET
                    source_tier = ?, source_url = ?, certifier = ?, expires_on = ?,
                    hand_cut_declaration = ?, last_seen = ?
                WHERE listing_id = ? AND source = ? AND source_record_ref = ?
                """.trimIndent(),
                listOf(
                    ExternalCertificationClaims.sourceTier bind claim.tier.rank.toShort(),
                    ExternalCertificationClaims.sourceUrl bind claim.sourceUrl,
                    ExternalCertificationClaims.certifier bind claim.certifier,
                    ExternalCertificationClaims.expiresOn bind claim.expiresOn?.toJavaLocalDate(),
                    ExternalCertificationClaims.handCutDeclaration bind claim.declaredHandCut.name,
                    ExternalCertificationClaims.lastSeen bind (claim.lastSeen ?: now).toSqlTimestamp(),
                    ExternalCertificationClaims.listingId bind listingId,
                    ExternalCertificationClaims.claimSource bind claim.source,
                    ExternalCertificationClaims.sourceRecordRef bind claim.sourceRecordRef,
                ),
            )
        }
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
            // A committee decision is not certifier-derived: basis NULL keeps
            // the ingestion recompute from ever demoting it (D-10).
            val updated = executeUpdate(
                "UPDATE restaurant_listings SET verification_status = ?, verification_basis = NULL WHERE id = ?",
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

    private companion object {
        /** The read shape shared by every lookup, so all read paths agree. */
        val LISTING_COLUMNS = """
            id, name, address, city, province, postal, country,
            ST_Y(location::geometry) AS lat, ST_X(location::geometry) AS lng,
            cuisine, is_hand_cut, is_delivery, price, rating, alcohol_served,
            halal_scope, cross_contamination, owner_id, brand_id, provenance, venue_key,
            verification_status, created_at
        """.trimIndent()
    }
}
