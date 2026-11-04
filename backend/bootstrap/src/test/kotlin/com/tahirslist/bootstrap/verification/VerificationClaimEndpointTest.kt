package com.tahirslist.bootstrap.verification

import com.tahirslist.bootstrap.KtorPostgresTest
import com.tahirslist.bootstrap.asString
import com.tahirslist.bootstrap.dbCount
import com.tahirslist.bootstrap.dbRow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.util.UUID

/**
 * Owner-claim HTTP surface (sc-46): POST /v1/listings/{id}/claim.
 *
 * End-to-end against the full application graph (route -> ClaimListing ->
 * RequestVerification(DeferToHuman) -> Exposed review repository -> real Flyway
 * V13/V14). Covers: 201 happy path persisting an AI_SUGGESTED review,
 * unauthenticated 401, the owner-only guard (403), 404 for an unknown listing,
 * and 400 for a blank proof.
 *
 * No hosted verifier is configured in tests, so the safe DeferToHumanProvider is
 * active — every claim is suggested NEEDS_REVIEW and the review sits in
 * AI_SUGGESTED awaiting the human (sc-73).
 */
class VerificationClaimEndpointTest : KtorPostgresTest() {

    private val certBytes = byteArrayOf(1, 2, 3, 4)

    private suspend fun reviewCount(): Int =
        dbCount("SELECT count(*) FROM halal_certification_reviews")

    private suspend fun claim(
        listingId: String,
        proof: String,
        imageBytes: ByteArray = certBytes,
        bearer: String?,
        aiConsent: String = "true",
    ) = post(
        "/v1/listings/$listingId/claim",
        token = bearer,
        multipart = multipart {
            field("proof", proof)
            field("aiConsent", aiConsent)
            file("certImage", imageBytes, "image/jpeg", "cert.jpg")
        },
    )

    init {
        test("owner claims a listing: 201, drives review to AI_SUGGESTED, persists it") {
            val session = signupAndLogin("claim-happy@example.com")
            val listingId = createListing(session.accessToken, session.accountId)

            val resp = claim(listingId, " I own this; license TAH-2024-118. ", bearer = session.accessToken)

            resp.status shouldBe 201
            val body = resp.json()
            val reviewId = body["reviewId"].asString()
            UUID.fromString(reviewId)
            body["listingId"].asString() shouldBe listingId
            body["state"].asString() shouldBe "AI_SUGGESTED"
            body["suggestedVerdict"].asString() shouldBe "NEEDS_REVIEW"

            val row = dbRow(
                """
                SELECT state, suggestion_verdict, submitted_by, listing_id, ai_consent_at
                FROM halal_certification_reviews WHERE id = ?
                """.trimIndent(),
                UUID.fromString(reviewId),
            )
            row["state"] shouldBe "AI_SUGGESTED"
            row["suggestion_verdict"] shouldBe "NEEDS_REVIEW"
            row["submitted_by"].toString() shouldBe session.accountId
            row["listing_id"].toString() shouldBe listingId
            // sc-120: consent is recorded with the verification request
            row["ai_consent_at"] shouldNotBe null
        }

        test("a claim without explicit AI-analysis consent returns 400 and stores nothing") {
            val session = signupAndLogin("claim-noconsent@example.com")
            val listingId = createListing(session.accessToken, session.accountId)
            val before = reviewCount()

            val resp = claim(listingId, "license", bearer = session.accessToken, aiConsent = "false")

            resp.status shouldBe 400
            resp.json()["code"].asString() shouldBe "invalid_input"
            reviewCount() shouldBe before
        }

        test("a claim never auto-verifies the listing") {
            val session = signupAndLogin("claim-safe@example.com")
            val listingId = createListing(session.accessToken, session.accountId)

            val resp = claim(listingId, "license", bearer = session.accessToken)

            resp.status shouldBe 201
            resp.json()["state"].asString() shouldBe "AI_SUGGESTED"
            dbRow(
                "SELECT verification_status FROM restaurant_listings WHERE id = ?",
                UUID.fromString(listingId),
            )["verification_status"] shouldBe "UNVERIFIED"
        }

        test("unauthenticated call returns a generic 401") {
            val resp = claim(UUID.randomUUID().toString(), "license", bearer = null)

            resp.status shouldBe 401
            resp.json()["code"].asString() shouldBe "unauthorized"
        }

        test("a non-owner claiming a listing returns 403 not_listing_owner") {
            val owner = signupAndLogin("claim-owner@example.com")
            val listingId = createListing(owner.accessToken, owner.accountId)
            val intruder = signupAndLogin("claim-intruder@example.com")

            val resp = claim(listingId, "I own this", bearer = intruder.accessToken)

            resp.status shouldBe 403
            resp.json()["code"].asString() shouldBe "not_listing_owner"
        }

        test("claiming an unknown listing returns 404 listing_not_found") {
            val session = signupAndLogin("claim-404@example.com")

            val resp = claim(UUID.randomUUID().toString(), "license", bearer = session.accessToken)

            resp.status shouldBe 404
            resp.json()["code"].asString() shouldBe "listing_not_found"
        }

        test("blank proof returns 400 invalid_input") {
            val session = signupAndLogin("claim-blank@example.com")
            val listingId = createListing(session.accessToken, session.accountId)

            val resp = claim(listingId, "   ", bearer = session.accessToken)

            resp.status shouldBe 400
            resp.json()["code"].asString() shouldBe "invalid_input"
        }
    }
}
