package com.tahirslist.application.listing

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
}
