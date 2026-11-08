package com.tahirslist.application.agent

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.datetime.LocalDate

class CertificateFactsTest : FunSpec({

    test("requiresVerifiedApprovedAndArchivedKey") {
        val verified = sampleListing(verified = true)
        val approved = approvedReview()

        CertificateFacts.of(verified, approved).shouldNotBeNull()
        CertificateFacts.of(verified, null).shouldBeNull()
        CertificateFacts.of(sampleListing(verified = false), approved).shouldBeNull()
        CertificateFacts.of(verified, approved.copy(certificateKey = null)).shouldBeNull()
        CertificateFacts.of(verified, approved.copy(decision = null)).shouldBeNull()
    }

    test("carriesCertifierReviewDateExpiryAndKey") {
        val facts = CertificateFacts.of(
            sampleListing(verified = true),
            approvedReview(certifier = "Board", expiresOn = LocalDate(2027, 3, 1)),
        )

        facts shouldNotBe null
        facts!!.certifier shouldBe "Board"
        facts.expiresOn shouldBe LocalDate(2027, 3, 1)
        facts.reviewedOn shouldBe now()
        facts.certificateKey shouldBe "certificates/$SAMPLE_ID.jpg"
    }

    test("reversedDecisionIsNotDisplayedAsApproved") {
        val approved = approvedReview()
        val reversed = approved.reverse(COMMITTEE_ID, now = now())

        CertificateFacts.of(sampleListing(verified = true), reversed).shouldBeNull()
    }
})
