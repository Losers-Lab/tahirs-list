package com.tahirslist.application.photo

import com.tahirslist.domain.restaurant.CoverPhoto
import com.tahirslist.domain.restaurant.RestaurantListing
import kotlin.uuid.Uuid

/**
 * The photo side of a listing, as a port (SC-231, UC-03).
 *
 * One photo per listing, so this is a map of listing → photo rather than a list.
 * The write is idempotent: saving a photo again replaces the credit row, which is
 * how an expired Places cache is refreshed without a second row.
 */
interface CoverPhotoRepository {
    suspend fun find(listingId: Uuid): CoverPhoto?

    suspend fun save(listingId: Uuid, photo: CoverPhoto)
}

/**
 * Offers photos for one listing (SC-231, UC-03).
 *
 * Two questions, not one, because the precedence is a rule: every source may
 * answer the free one, but only the Places adapter has a Places answer. A source
 * that has nothing returns an empty list / null — it never invents a photo.
 *
 * The precedence itself lives in [AttachCoverPhoto]: free candidates first, then
 * Places. A source does not know, and must not decide, whether it wins.
 */
interface PhotoCandidateSource {
    /** Free-licensed photos this source can offer, best first. */
    suspend fun freeCandidates(listing: RestaurantListing): List<PhotoCandidate> = emptyList()

    /**
     * The Places photo for this listing, or null.
     *
     * @param placeId a place id already on file, so a refresh does not pay for a
     *        second search.
     */
    suspend fun placesCandidate(listing: RestaurantListing, placeId: String? = null): PhotoCandidate? = null
}
