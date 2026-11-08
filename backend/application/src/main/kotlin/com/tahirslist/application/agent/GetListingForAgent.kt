package com.tahirslist.application.agent

import com.tahirslist.application.listing.RestaurantListingRepository
import com.tahirslist.application.verification.HalalCertificationReviewRepository
import com.tahirslist.domain.restaurant.VerificationStatus
import kotlin.uuid.Uuid

/**
 * UC-02 for the agent channel: load one listing and derive its certificate facts
 * from the latest approved review. Two read-only port calls, no writes.
 *
 * The review lookup is skipped for unverified listings, and a missing review is
 * not an error: the detail simply has no certificate block.
 */
class GetListingForAgent(
    private val listings: RestaurantListingRepository,
    private val reviews: HalalCertificationReviewRepository,
) {
    suspend fun execute(id: Uuid): AgentListingDetail {
        val listing = listings.findById(id) ?: throw AgentListingNotFoundException()
        val latestApproved = if (listing.verificationStatus == VerificationStatus.VERIFIED) {
            reviews.findLatestApprovedByListing(id)
        } else {
            null
        }
        return AgentListingDetail(
            listing = listing,
            certificate = CertificateFacts.of(listing, latestApproved),
        )
    }
}
