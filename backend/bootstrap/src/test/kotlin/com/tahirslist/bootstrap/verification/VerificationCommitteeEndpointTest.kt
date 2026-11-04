package com.tahirslist.bootstrap.verification

import com.tahirslist.bootstrap.KtorPostgresTest
import com.tahirslist.bootstrap.asString
import com.tahirslist.bootstrap.dbString
import com.tahirslist.bootstrap.dbUpdate
import com.tahirslist.bootstrap.stringOrNull
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.jsonObject
import java.util.UUID

/**
 * Verification Committee HTTP surface (sc-73): the human review loop.
 *
 * End-to-end against the full application graph (route -> VerificationCommittee
 * -> Exposed review + listing repositories -> real Flyway V13/V15/V16). Covers:
 *   - a VC member lists the pending (AI_SUGGESTED) workqueue;
 *   - approve promotes the listing to VERIFIED (source + read mirror);
 *   - deny records the reason and leaves the listing UNVERIFIED;
 *   - re-deciding an already-decided review is a 409 conflict;
 *   - RBAC: a non-committee (USER) caller is forbidden, and unauthenticated is 401.
 *
 * A VC token is obtained by promoting a just-signed-up account's role in the DB
 * and logging back in (the issuer reads the role from the stored account), which
 * exercises the real JWT role claim rather than hand-minting a token.
 */
class VerificationCommitteeEndpointTest : KtorPostgresTest() {

    private val certBytes = byteArrayOf(1, 2, 3, 4)

    private data class Session(val id: String, val bearer: String)

    private suspend fun session(email: String): Session {
        val signedUp = signupAndLogin(email)
        return Session(signedUp.accountId, signedUp.accessToken)
    }

    /** Promote the account to VERIFICATION_COMMITTEE in the DB, then mint a token. */
    private suspend fun signupPromoteLogin(email: String): Session {
        val initial = session(email)
        dbUpdate(
            "UPDATE users SET role = 'VERIFICATION_COMMITTEE' WHERE id = ?",
            UUID.fromString(initial.id),
        )
        val loginResponse = login(email)
        loginResponse.status shouldBe 200
        return Session(initial.id, loginResponse.json()["accessToken"].asString())
    }

    private suspend fun claimAndGetReviewId(listingId: String, bearer: String): String {
        val resp = post(
            "/v1/listings/$listingId/claim",
            token = bearer,
            multipart = multipart {
                field("proof", " I own this; license TAH-2024-118. ")
                field("aiConsent", "true")
                file("certImage", certBytes, "image/jpeg", "cert.jpg")
            },
        )
        resp.status shouldBe 201
        return resp.json()["reviewId"].asString()
    }

    private suspend fun reviews(bearer: String?) =
        get("/v1/verification-committee/reviews", token = bearer)

    /**
     * A listing created through the API that is index-qualified
     * (NO_CROSS_CONTAMINATION), so the approve path's `listing_search` mirror
     * assertions have a row to read — exactly what the previous spec pinned.
     */
    private suspend fun createIndexedListing(bearer: String, ownerId: String): String =
        createListing(
            bearer,
            ownerId,
            listingBody(overrides = mapOf("crossContamination" to "NO_CROSS_CONTAMINATION")),
        )

    init {
        test("a Verification Committee member lists pending AI_SUGGESTED reviews") {
            val owner = session("vc-list-owner@example.com")
            val listingId = createIndexedListing(owner.bearer, owner.id)
            val reviewId = claimAndGetReviewId(listingId, owner.bearer)
            val vc = signupPromoteLogin("vc-list-member@example.com")

            val resp = reviews(vc.bearer)

            resp.status shouldBe 200
            val entries = resp.jsonArray().map { it.jsonObject }
            entries.map { it["reviewId"].asString() } shouldContain reviewId
            val thisOne = entries.first { it["reviewId"].asString() == reviewId }
            thisOne["listingId"].asString() shouldBe listingId
            thisOne["state"].asString() shouldBe "AI_SUGGESTED"
            thisOne["suggestedVerdict"].asString() shouldBe "NEEDS_REVIEW"
            thisOne["decisionOutcome"].stringOrNull() shouldBe null
        }

        test("approve promotes the listing to VERIFIED in source and read mirror") {
            val owner = session("vc-approve-owner@example.com")
            val listingId = createIndexedListing(owner.bearer, owner.id)
            val reviewId = claimAndGetReviewId(listingId, owner.bearer)
            val vc = signupPromoteLogin("vc-approve-member@example.com")

            val resp = post(
                "/v1/verification-committee/reviews/$reviewId/approve",
                token = vc.bearer,
                json = linkedMapOf("reason" to "cert matches listing"),
            )

            resp.status shouldBe 200
            resp.json()["state"].asString() shouldBe "APPROVED"
            resp.json()["decisionOutcome"].asString() shouldBe "APPROVED"
            resp.json()["decisionReason"].asString() shouldBe "cert matches listing"

            dbString(
                "SELECT verification_status FROM restaurant_listings WHERE id = ?",
                UUID.fromString(listingId),
            ) shouldBe "VERIFIED"
            dbString(
                "SELECT verification_status FROM listing_search WHERE id = ?",
                UUID.fromString(listingId),
            ) shouldBe "VERIFIED"
        }

        test("deny records the reason and leaves the listing UNVERIFIED") {
            val owner = session("vc-deny-owner@example.com")
            val listingId = createIndexedListing(owner.bearer, owner.id)
            val reviewId = claimAndGetReviewId(listingId, owner.bearer)
            val vc = signupPromoteLogin("vc-deny-member@example.com")

            val resp = post(
                "/v1/verification-committee/reviews/$reviewId/deny",
                token = vc.bearer,
                json = linkedMapOf("reason" to "cert image unreadable"),
            )

            resp.status shouldBe 200
            resp.json()["state"].asString() shouldBe "DENIED"
            resp.json()["decisionOutcome"].asString() shouldBe "DENIED"
            resp.json()["decisionReason"].asString() shouldBe "cert image unreadable"

            dbString(
                "SELECT verification_status FROM restaurant_listings WHERE id = ?",
                UUID.fromString(listingId),
            ) shouldBe "UNVERIFIED"
        }

        test("approve records certifier + expiry and the public detail renders them from live data (sc-73 read surface)") {
            val owner = session("vc-certattr-owner@example.com")
            val listingId = createIndexedListing(owner.bearer, owner.id)
            val reviewId = claimAndGetReviewId(listingId, owner.bearer)
            val vc = signupPromoteLogin("vc-certattr-member@example.com")

            val approve = post(
                "/v1/verification-committee/reviews/$reviewId/approve",
                token = vc.bearer,
                json = linkedMapOf(
                    "reason" to "cert matches listing",
                    "certifier" to "HFSAA",
                    "expiresOn" to "2027-01-12",
                ),
            )
            approve.status shouldBe 200
            approve.json()["state"].asString() shouldBe "APPROVED"

            // The public detail drive renders the CertificatePanel from live data.
            val detail = get("/v1/listings/$listingId").json()
            detail["verificationStatus"].asString() shouldBe "VERIFIED"
            val certificate = detail["certificate"]!!.jsonObject
            certificate["certifier"].asString() shouldBe "HFSAA"
            certificate["reviewedOn"].asString().isNotBlank() shouldBe true
            certificate["expiresOn"].asString() shouldBe "2027-01-12"
            certificate["certificateUrl"].asString().contains("/v1/listings/$listingId/certificate") shouldBe true

            // The certificate image proxy serves the archived bytes (View certificate).
            val cert = get("/v1/listings/$listingId/certificate")
            cert.status shouldBe 200
            cert.bytes.contentEquals(certBytes) shouldBe true
        }

        test("an unverified listing carries no certificate in the detail payload") {
            val owner = session("vc-nocert-owner@example.com")
            val listingId = createIndexedListing(owner.bearer, owner.id)

            val detail = get("/v1/listings/$listingId").json()
            detail["verificationStatus"].asString() shouldBe "UNVERIFIED"
            detail["certificate"].stringOrNull() shouldBe null
        }

        test("approve rejects a malformed expiresOn") {
            val owner = session("vc-badexp-owner@example.com")
            val listingId = createIndexedListing(owner.bearer, owner.id)
            val reviewId = claimAndGetReviewId(listingId, owner.bearer)
            val vc = signupPromoteLogin("vc-badexp-member@example.com")

            val resp = post(
                "/v1/verification-committee/reviews/$reviewId/approve",
                token = vc.bearer,
                json = linkedMapOf("reason" to "x", "expiresOn" to "not-a-date"),
            )

            resp.status shouldBe 400
            resp.json()["code"].asString() shouldBe "invalid_input"
        }

        test("deciding an already-decided review is a 409 conflict") {
            val owner = session("vc-twice-owner@example.com")
            val listingId = createIndexedListing(owner.bearer, owner.id)
            val reviewId = claimAndGetReviewId(listingId, owner.bearer)
            val vc = signupPromoteLogin("vc-twice-member@example.com")

            post(
                "/v1/verification-committee/reviews/$reviewId/deny",
                token = vc.bearer,
                json = linkedMapOf("reason" to "first"),
            )
            val resp = post(
                "/v1/verification-committee/reviews/$reviewId/approve",
                token = vc.bearer,
                json = linkedMapOf("reason" to "second"),
            )

            resp.status shouldBe 409
            resp.json()["code"].asString() shouldBe "review_not_pending"
        }

        test("a non-committee (USER) caller is forbidden from the workqueue") {
            val owner = session("vc-forbidden-owner@example.com")
            val listingId = createIndexedListing(owner.bearer, owner.id)
            claimAndGetReviewId(listingId, owner.bearer)

            val resp = reviews(owner.bearer)

            resp.status shouldBe 403
            resp.json()["code"].asString() shouldBe "forbidden"
        }

        test("unauthenticated calls are a generic 401") {
            val resp = reviews(bearer = null)

            resp.status shouldBe 401
            resp.json()["code"].asString() shouldBe "unauthorized"
        }
    }
}
