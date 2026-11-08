package com.tahirslist.application.agent

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class GetListingForAgentTest : FunSpec({

    test("missingListingThrowsNotFound") {
        val detail = GetListingForAgent(FakeListingRepository(null), FakeReviewRepository(null))

        val failure = shouldThrow<AgentListingNotFoundException> { detail.execute(SAMPLE_ID) }

        failure.message shouldBe
            "Restaurant not found. Use an id returned by search_restaurants."
    }

    test("verifiedListingReadsLatestApprovedReview") {
        val reviews = FakeReviewRepository(approvedReview())
        val detail = GetListingForAgent(
            FakeListingRepository(sampleListing(verified = true)),
            reviews,
        )

        val result = detail.execute(SAMPLE_ID)

        reviews.lookups shouldBe 1
        result.certificate shouldNotBe null
        result.certificate!!.certifier shouldBe "Halal Certification Board"
        result.certificate!!.certificateKey shouldBe "certificates/$SAMPLE_ID.jpg"
    }

    test("unverifiedListingSkipsTheReviewLookup") {
        val reviews = FakeReviewRepository(approvedReview())
        val detail = GetListingForAgent(FakeListingRepository(sampleListing(verified = false)), reviews)

        val result = detail.execute(SAMPLE_ID)

        reviews.lookups shouldBe 0
        result.certificate.shouldBeNull()
    }

    test("verifiedWithoutApprovedReviewOmitsCertificate") {
        val detail = GetListingForAgent(
            FakeListingRepository(sampleListing(verified = true)),
            FakeReviewRepository(null),
        )

        detail.execute(SAMPLE_ID).certificate.shouldBeNull()
    }

    test("partialHalalKeepsScopeAndDeclaredItems") {
        val listing = sampleListing(
            verified = true,
            halalScope = com.tahirslist.domain.restaurant.HalalScope.PARTIALLY_HALAL,
            halalItems = setOf(
                com.tahirslist.domain.restaurant.HalalItem("chicken", true),
                com.tahirslist.domain.restaurant.HalalItem("bacon", false),
            ),
        )
        val detail = GetListingForAgent(FakeListingRepository(listing), FakeReviewRepository(approvedReview()))

        val result = detail.execute(SAMPLE_ID)

        result.listing.halalScope shouldBe com.tahirslist.domain.restaurant.HalalScope.PARTIALLY_HALAL
        result.listing.halalItems.map { it.name to it.isHalal }.toSet() shouldBe
            setOf("chicken" to true, "bacon" to false)
    }
})
