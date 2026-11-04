package com.tahirslist.application.verification

import com.tahirslist.application.favorite.ListingNotFoundException
import com.tahirslist.application.listing.RestaurantListingRepository
import com.tahirslist.domain.restaurant.Cuisine
import com.tahirslist.domain.restaurant.LatLng
import com.tahirslist.domain.restaurant.RestaurantListing
import com.tahirslist.domain.restaurant.VerificationStatus
import com.tahirslist.domain.verification.HalalCertificationReview
import com.tahirslist.domain.verification.SuggestionVerdict
import com.tahirslist.domain.verification.VerificationSuggestion
import com.tahirslist.domain.verification.VerificationState
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runTest

/**
 * Owner-claim (sc-46) application use case, TDD:
 *
 *  - an authenticated listing **owner** submits proof of ownership + a certification
 *    image;
 *  - the image is stored (MinIO via the [CertificationImageStorage] seam) and the
 *    key it returns is recorded on the persisted review — that key is the
 *    immutable identity of the evidence the committee will review (sc-73);
 *  - the review is driven SUBMITTED -> AI_REVIEW -> AI_SUGGESTED via the sc-117
 *    [RequestVerification] seam and the resulting review is persisted;
 *  - non-owners / unknown listings are rejected; a provider outage holds the review
 *    in AI_REVIEW for a retry (never drops the claim, never auto-grants).
 */
class ClaimListingTest : FunSpec({

    val now = Instant.parse("2026-08-31T12:00:00Z")

    val listings = mockk<RestaurantListingRepository>()
    val reviews = mockk<HalalCertificationReviewRepository>()
    val certificates = FakeCertificationImageStorage()
    val requestVerification = mockk<RequestVerification>()
    val claimListing = ClaimListing(listings, reviews, certificates, requestVerification)

    val listingId = Uuid.random()
    val claimerId = Uuid.random()
    val proof = " I own this restaurant; business license TAH-2024-118. "
    val contentType = "image/jpeg"
    val imageBytes = byteArrayOf(1, 2, 3, 4)

    beforeTest {
        clearMocks(listings, reviews, requestVerification)
        certificates.reset()
    }

    test("an owner's claim stores the certification, records its key, and drives AI review to AI_SUGGESTED") {
        runTest {
            coEvery { listings.findById(listingId) } returns aListing(listingId, ownerId = claimerId)
            val suggested = HalalCertificationReview.create(listingId, claimerId, now)
                .beginAiReview(now)
                .recordAiSuggestion(VerificationSuggestion(SuggestionVerdict.NEEDS_REVIEW, 0.4), now)
            coEvery { requestVerification.execute(listingId, claimerId, any(), now, aiConsentGivenAt = now) } returns suggested
            coEvery { reviews.save(any()) } answers { firstArg() }

            val result = claimListing.execute(listingId, claimerId, proof, true, contentType, imageBytes, now)

            // the key returned by storage is the review's certificate identity
            val storedKey = certificates.savedKeys.single()
            result.certificateKey shouldBe storedKey
            // ...and the evidence itself was stored for this listing, with the
            // derived media type and the exact submitted bytes.
            val saved = certificates.saves.single()
            saved.listingId shouldBe listingId
            saved.contentType shouldBe contentType
            saved.bytes.toList() shouldBe imageBytes.toList()
            result.state shouldBe VerificationState.AI_SUGGESTED
            coVerify { requestVerification.execute(listingId, claimerId, any(), now, aiConsentGivenAt = now) }
            coVerify(exactly = 1) {
                reviews.save(match { it.id == suggested.id && it.certificateKey == storedKey })
            }
        }
    }

    test("a claim without explicit AI-analysis consent is rejected before any storage or persistence") {
        runTest {
            coEvery { listings.findById(listingId) } returns aListing(listingId, ownerId = claimerId)

            shouldThrow<IllegalArgumentException> {
                claimListing.execute(listingId, claimerId, proof, false, contentType, imageBytes, now)
            }

            // the image must never be archived or uploaded without consent
            certificates.nothingSaved shouldBe true
            coVerify(exactly = 0) { requestVerification.execute(any(), any(), any(), now) }
            coVerify(exactly = 0) { reviews.save(any()) }
        }
    }

    test("an AI APPROVE suggestion is carried but never elevates the review past AI_SUGGESTED") {
        runTest {
            coEvery { listings.findById(listingId) } returns aListing(listingId, ownerId = claimerId)
            val approve = HalalCertificationReview.create(listingId, claimerId, now)
                .beginAiReview(now)
                .recordAiSuggestion(VerificationSuggestion(SuggestionVerdict.APPROVE, 0.95), now)
            coEvery { requestVerification.execute(any(), any(), any(), now, aiConsentGivenAt = now) } returns approve
            coEvery { reviews.save(any()) } answers { firstArg() }

            val result = claimListing.execute(listingId, claimerId, proof, true, contentType, imageBytes, now)

            result.state shouldBe VerificationState.AI_SUGGESTED
            result.suggestion!!.verdict shouldBe SuggestionVerdict.APPROVE
            // the listing must NOT be auto-verified: the human (sc-73) decides that.
            result.state shouldNotBe VerificationState.APPROVED
            // the suggested evidence is still identity-tracked
            result.certificateKey shouldBe certificates.savedKeys.single()
        }
    }

    test("a claim from a non-owner is rejected and nothing is stored or persisted") {
        runTest {
            val owner = Uuid.random()
            val intruder = Uuid.random()
            coEvery { listings.findById(listingId) } returns aListing(listingId, ownerId = owner)

            shouldThrow<NotListingOwnerException> {
                claimListing.execute(listingId, intruder, proof, true, contentType, imageBytes, now)
            }

            certificates.nothingSaved shouldBe true
            coVerify(exactly = 0) { reviews.save(any()) }
            coVerify(exactly = 0) { requestVerification.execute(any(), any(), any(), now) }
        }
    }

    test("a claim for an unknown listing is rejected") {
        runTest {
            coEvery { listings.findById(listingId) } returns null
            val stranger = Uuid.random()

            shouldThrow<ListingNotFoundException> {
                claimListing.execute(listingId, stranger, proof, true, contentType, imageBytes, now)
            }

            certificates.nothingSaved shouldBe true
            coVerify(exactly = 0) { reviews.save(any()) }
        }
    }

    test("blank proof is rejected as invalid input") {
        runTest {
            coEvery { listings.findById(listingId) } returns aListing(listingId, ownerId = claimerId)

            shouldThrow<IllegalArgumentException> {
                claimListing.execute(listingId, claimerId, "   ", true, contentType, imageBytes, now)
            }

            certificates.nothingSaved shouldBe true
            coVerify(exactly = 0) { reviews.save(any()) }
        }
    }

    test("a provider outage holds the review in AI_REVIEW, records the evidence key, and raises verification_unavailable") {
        runTest {
            coEvery { listings.findById(listingId) } returns aListing(listingId, ownerId = claimerId)
            coEvery { requestVerification.execute(any(), any(), any(), now, aiConsentGivenAt = now) } throws
                VerificationProviderException("provider unreachable")
            val held = slot<HalalCertificationReview>()
            coEvery { reviews.save(capture(held)) } answers { firstArg() }

            val ex = shouldThrow<VerificationUnavailableException> {
                claimListing.execute(listingId, claimerId, proof, true, contentType, imageBytes, now)
            }

            ex.cause!!.message shouldBe "provider unreachable"
            // the claim is not dropped: a durable AI_REVIEW record was saved for a later retry
            coVerify(exactly = 1) { reviews.save(match { it.state == VerificationState.AI_REVIEW }) }
            // the archived evidence is still identity-tracked, so the retry reviews the same artifact
            held.captured.certificateKey shouldBe certificates.savedKeys.single()
        }
    }
})

/**
 * Test double for [CertificationImageStorage]: records every save and returns a
 * distinct, listing-scoped key per call, mirroring the S3 adapter's contract that
 * a key identifies exactly one stored object.
 */
private class FakeCertificationImageStorage : CertificationImageStorage {

    private val keys = mutableListOf<String>()

    /** What `save` was asked to store, in call order (guards the request contract). */
    val saves = mutableListOf<SavedCertification>()

    val savedKeys: List<String> get() = keys.toList()

    val nothingSaved: Boolean get() = keys.isEmpty()

    fun reset() {
        keys.clear()
        saves.clear()
    }

    override suspend fun save(listingId: Uuid, contentType: String, bytes: ByteArray): String {
        saves += SavedCertification(listingId, contentType, bytes)
        return "certifications/$listingId/${Uuid.random()}".also { keys += it }
    }

    override suspend fun load(key: String): CertificationImageStorage.StoredCertificationImage? = null

    override suspend fun loadLatest(listingId: Uuid): CertificationImageStorage.StoredCertificationImage? = null
}

private data class SavedCertification(
    val listingId: Uuid,
    val contentType: String,
    val bytes: ByteArray,
)

private fun aListing(id: Uuid, ownerId: Uuid): RestaurantListing = RestaurantListing.fromStorage(
    id = id,
    name = "Halal Grill",
    address = "123 Main St",
    location = LatLng(40.7128, -74.0060),
    cuisine = Cuisine("mediterranean"),
    isHandCut = true,
    ownerId = ownerId,
    brandId = null,
    provenance = null,
    verificationStatus = VerificationStatus.UNVERIFIED,
    createdAt = Clock.System.now(),
)
