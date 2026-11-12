package com.tahirslist.application.listing

import com.tahirslist.application.ingestion.BatchLoadResult
import com.tahirslist.application.ingestion.ClaimReattachment
import com.tahirslist.application.ingestion.ListingMerge
import com.tahirslist.application.ingestion.ListingWrite
import com.tahirslist.domain.restaurant.RestaurantListing
import com.tahirslist.domain.restaurant.VerificationStatus
import kotlin.uuid.Uuid

/**
 * Persistence port (hexagonal "out" port) for restaurant listings. Implemented by
 * the persistence adapter; the application layer depends only on this contract.
 */
interface RestaurantListingRepository {
    suspend fun save(listing: RestaurantListing): RestaurantListing
    suspend fun findById(id: Uuid): RestaurantListing?

    /**
     * SC-205: stamp a new hero-photo version on the listing and return it.
     *
     * The version is monotonic (`GREATEST(image_version + 1, nowMillis)`), so a
     * successful upload always changes the public image URL even if the clock
     * goes backwards. It is the commit point of an image write: callers stamp it
     * only after every object variant is stored.
     *
     * @return the new version, or `null` when no listing has [id] (the row
     *         vanished between the load and the write).
     */
    suspend fun bumpImageVersion(id: Uuid): Long?

    /**
     * Full replace of a listing's editable content fields (sc-23/47/48 owner
     * listing edit), persisted atomically with its search mirror and child
     * stores. Identity and governance fields (id, owner, verification status,
     * createdAt, price, rating) are written unchanged — the caller decides what
     * changed; this method persists the aggregate as given.
     *
     * @return the reloaded updated listing, or null if no listing has that id.
     */
    suspend fun update(listing: RestaurantListing): RestaurantListing?

    /**
     * Promote/change a listing's [VerificationStatus]. sc-73 uses this to promote
     * a listing to VERIFIED once the Verification Committee approves its review
     * (the listing itself was never auto-promoted by the claim). The source table
     * and the `listing_search` read mirror are kept in sync by the adapter.
     *
     * @return the updated listing, or null if no listing has that id.
     */
    suspend fun updateVerificationStatus(id: Uuid, status: VerificationStatus): RestaurantListing?

    /**
     * All listings, for the minimal sc-157 browse/search read path. Full
     * filtered search (cuisine AND/OR, price, rating, distance) is a later
     * story; this is the unfiltered seed surface documented in
     * docs/design/sc-157-image-variants.md.
     */
    suspend fun findAll(): List<RestaurantListing>

    /**
     * The listing that already owns the claim `(source, sourceRecordRef)`, if
     * any. This is the first and strongest identity match for a replayed batch
     * (D-18): the same source record must never attach to a second listing,
     * even when its coordinates drifted.
     */
    suspend fun findClaim(source: String, sourceRecordRef: String): RestaurantListing?

    /** The listing with this deterministic venue key, if any (D-18, second match). */
    suspend fun findByVenueKey(venueKey: String): RestaurantListing?

    /**
     * Listings at the same normalised (6 decimal place) location. This is a
     * report-only fallback: a hit here is a suspected duplicate that the load
     * holds for a curator, never an identity match (D-18).
     */
    suspend fun findByNormalizedLocation(lat: Double, lng: Double): List<RestaurantListing>

    /**
     * Apply a reviewed ingestion batch in one transaction: brands, listings,
     * child rows, claims and the search mirror commit together or not at all
     * (BR-13). A claim that already points at a different listing aborts the
     * batch (BR-21) rather than silently re-pointing the audit trail.
     *
     * [merges] and [reattachments] carry the effects of curator decisions
     * (same-venue, attach-to). They are applied before the writes, in the same
     * transaction; each is re-validated against the current rows first, so a
     * stale plan fails the whole batch instead of half-applying it.
     */
    suspend fun applyBatch(
        writes: List<ListingWrite>,
        merges: List<ListingMerge> = emptyList(),
        reattachments: List<ClaimReattachment> = emptyList(),
    ): BatchLoadResult
}
