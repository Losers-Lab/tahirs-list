package com.tahirslist.application.ingestion

import com.tahirslist.domain.restaurant.CrossContamination
import com.tahirslist.domain.restaurant.Provenance
import com.tahirslist.domain.restaurant.VerificationStatus
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class TrustMergeTest : FunSpec({

    test("verification never goes backwards") {
        TrustMerge.verificationStatus(VerificationStatus.VERIFIED, VerificationStatus.UNVERIFIED) shouldBe
            VerificationStatus.VERIFIED
        TrustMerge.verificationStatus(VerificationStatus.UNVERIFIED, VerificationStatus.VERIFIED) shouldBe
            VerificationStatus.VERIFIED
        TrustMerge.verificationStatus(VerificationStatus.UNVERIFIED, VerificationStatus.UNVERIFIED) shouldBe
            VerificationStatus.UNVERIFIED
    }

    test("hand-cut is TRUE > FALSE > NULL and an unknown value never erases a known one") {
        TrustMerge.handCut(true, null) shouldBe true
        TrustMerge.handCut(null, true) shouldBe true
        TrustMerge.handCut(false, null) shouldBe false
        TrustMerge.handCut(true, false) shouldBe true
        TrustMerge.handCut(null, null) shouldBe null
    }

    test("the index gate is never disqualified by an update") {
        TrustMerge.crossContamination(
            CrossContamination.NO_CROSS_CONTAMINATION,
            CrossContamination.UNCERTAIN,
        ) shouldBe CrossContamination.NO_CROSS_CONTAMINATION
        TrustMerge.crossContamination(CrossContamination.UNCERTAIN, CrossContamination.PRESENT) shouldBe
            CrossContamination.PRESENT
        TrustMerge.crossContamination(CrossContamination.UNCERTAIN, CrossContamination.UNCERTAIN) shouldBe
            CrossContamination.UNCERTAIN
    }

    test("a certifier-registry provenance is never replaced by a manual stamp") {
        TrustMerge.provenance(Provenance.CERTIFIER_HMS, Provenance.MANUAL_ZABIHAH) shouldBe Provenance.CERTIFIER_HMS
        TrustMerge.provenance(Provenance.MANUAL_ZABIHAH, Provenance.CERTIFIER_HFSAA) shouldBe Provenance.CERTIFIER_HFSAA
        TrustMerge.provenance(null, Provenance.MANUAL_ZABIHAH) shouldBe Provenance.MANUAL_ZABIHAH
    }
})
