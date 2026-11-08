package com.tahirslist.application.agent

import com.tahirslist.domain.restaurant.RestaurantListing
import com.tahirslist.domain.restaurant.VerificationStatus
import com.tahirslist.domain.verification.HalalCertificationReview
import com.tahirslist.domain.verification.VerificationOutcome
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/**
 * The certificate display facts shown for a verified listing.
 *
 * Eligibility is deliberately strict: the listing must be VERIFIED, the latest
 * approved review must carry an APPROVED decision, and that review must name the
 * archived certificate object. Otherwise there is nothing safe to display and
 * the detail omits the block entirely (never a URL that would 404).
 */
data class CertificateFacts(
    val certifier: String?,
    val reviewedOn: Instant,
    val expiresOn: LocalDate?,
    val certificateKey: String,
) {
    companion object {
        fun of(listing: RestaurantListing, latestApproved: HalalCertificationReview?): CertificateFacts? {
            if (listing.verificationStatus != VerificationStatus.VERIFIED) return null
            val review = latestApproved ?: return null
            val decision = review.decision ?: return null
            if (decision.outcome != VerificationOutcome.APPROVED) return null
            val key = review.certificateKey ?: return null
            return CertificateFacts(
                certifier = review.certifier,
                reviewedOn = decision.decidedAt,
                expiresOn = review.expiresOn,
                certificateKey = key,
            )
        }
    }
}
