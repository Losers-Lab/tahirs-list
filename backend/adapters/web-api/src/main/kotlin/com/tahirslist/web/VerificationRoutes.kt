package com.tahirslist.web

import com.tahirslist.domain.account.Role
import com.tahirslist.domain.verification.HalalCertificationReview
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/** The review created by an owner claim (sc-46). */
@Serializable
data class ClaimResponse(
    val reviewId: String,
    val listingId: String,
    val submittedBy: String,
    val state: String,
    val suggestedVerdict: String?,
) {
    companion object {
        fun from(review: HalalCertificationReview): ClaimResponse = ClaimResponse(
            reviewId = review.id.toString(),
            listingId = review.listingId.toString(),
            submittedBy = review.submittedBy.toString(),
            state = review.state.name,
            suggestedVerdict = review.suggestion?.verdict?.name,
        )
    }
}

/**
 * The approve/deny body (both fields optional on the wire; a denial requires a
 * reason, enforced by the use case / route). `expiresOn` is an ISO-8601 date
 * transcribed from the certificate at approval.
 */
@Serializable
data class DecideRequest(
    val reason: String? = null,
    val certifier: String? = null,
    val expiresOn: String? = null,
)

/** The Verification Committee's view of a certification review (sc-73). */
@Serializable
data class ReviewResponse(
    val reviewId: String,
    val listingId: String,
    val submittedBy: String,
    val state: String,
    val suggestedVerdict: String?,
    val suggestionConfidence: Double?,
    val suggestionReasoning: String?,
    val decisionOutcome: String?,
    val decisionReason: String?,
    val decidedBy: String?,
) {
    companion object {
        fun from(review: HalalCertificationReview): ReviewResponse = ReviewResponse(
            reviewId = review.id.toString(),
            listingId = review.listingId.toString(),
            submittedBy = review.submittedBy.toString(),
            state = review.state.name,
            suggestedVerdict = review.suggestion?.verdict?.name,
            suggestionConfidence = review.suggestion?.confidence,
            suggestionReasoning = review.suggestion?.reasoning,
            decisionOutcome = review.decision?.outcome?.name,
            decisionReason = review.decision?.reason,
            decidedBy = review.decision?.decidedBy?.toString(),
        )
    }
}

/**
 * Verification vertical:
 *  - `POST /v1/listings/{listingId}/claim` — owner submits proof + certification
 *    image and explicit AI-analysis consent; the review lands in AI_SUGGESTED
 *    (never auto-approved);
 *  - `GET  /v1/verification-committee/reviews` — the pending workqueue;
 *  - `POST /v1/verification-committee/reviews/{reviewId}/approve` — promote the
 *    listing to VERIFIED (the only path);
 *  - `POST /v1/verification-committee/reviews/{reviewId}/deny` — record a denial.
 *
 * All are authenticated; the committee routes additionally require the
 * VERIFICATION_COMMITTEE role (non-committee → 403 `forbidden`).
 */
fun Route.verificationRoutes(verification: VerificationUseCases) {

    authenticate(JWT_AUTH_PROVIDER) {

        post("/v1/listings/{id}/claim") {
            val listingId = call.pathUuid("id")
            val claimerId = call.requireAccountId()
            val form = call.receiveForm()
            val proof = form.requiredField("proof", ErrorMessages.PROOF_PART_REQUIRED)
            val consentRaw = form.requiredField("aiConsent", ErrorMessages.CONSENT_PART_REQUIRED)
            val certificate = form.requiredFile("certImage", ErrorMessages.CERTIFICATION_PART_REQUIRED)

            val review = verification.claim.execute(
                listingId = listingId,
                claimerId = claimerId,
                proof = proof,
                aiConsentGiven = consentRaw.trim().equals("true", ignoreCase = true),
                contentType = certificate.contentType,
                imageBytes = certificate.bytes,
            )
            call.respond(HttpStatusCode.Created, ClaimResponse.from(review))
        }

        get("/v1/verification-committee/reviews") {
            call.requireCommittee()
            val pending = verification.committee.listPending().map { ReviewResponse.from(it) }
            call.respond(HttpStatusCode.OK, pending)
        }

        post("/v1/verification-committee/reviews/{reviewId}/approve") {
            call.requireCommittee()
            val reviewId = call.pathUuid("reviewId")
            // The body is optional: an empty body means "no reason / no cert facts".
            val request = call.receiveOptionalJson<DecideRequest>()
            val decided = verification.committee.approve(
                reviewId = reviewId,
                decidedBy = call.requireAccountId(),
                reason = request?.reason,
                certifier = request?.certifier,
                expiresOn = request?.expiresOn?.let(::parseExpiry),
            )
            call.respond(HttpStatusCode.OK, ReviewResponse.from(decided))
        }

        post("/v1/verification-committee/reviews/{reviewId}/deny") {
            call.requireCommittee()
            val reviewId = call.pathUuid("reviewId")
            val request = call.receiveOptionalJson<DecideRequest>()
            // A denial without a reason is rejected (the use case enforces non-blank).
            val reason = request?.reason ?: throw IllegalArgumentException("A denial reason is required.")
            val decided = verification.committee.deny(
                reviewId = reviewId,
                decidedBy = call.requireAccountId(),
                reason = reason,
            )
            call.respond(HttpStatusCode.OK, ReviewResponse.from(decided))
        }
    }
}

/**
 * Parses the certificate expiry date. A malformed value is an
 * [IllegalArgumentException] (→ 400 `invalid_input`), never a 500.
 */
private fun parseExpiry(raw: String): LocalDate =
    try {
        LocalDate.parse(raw.trim())
    } catch (ignored: IllegalArgumentException) {
        throw IllegalArgumentException("expiresOn must be an ISO-8601 date (yyyy-MM-dd).")
    }

/** Committee-only guard: any other authenticated role gets 403 `forbidden`. */
private fun ApplicationCall.requireCommittee() {
    val principal = jwtPrincipal() ?: throw ForbiddenException()
    if (principal.role != Role.VERIFICATION_COMMITTEE.name) throw ForbiddenException()
}
