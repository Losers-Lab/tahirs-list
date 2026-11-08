package com.tahirslist.domain.restaurant

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.datetime.LocalDate

class CertificationClaimTest : FunSpec({

    fun claim(
        source: String = "hms",
        record: String = "HMS-1",
        url: String = "https://hmsusa.org/certified-listing/",
        certifier: String = "Halal Monitoring Services (HMS)",
        tier: CertificationTier = CertificationTier.TIER_1,
        handCut: HandCutDeclaration = HandCutDeclaration.HAND_CUT,
        expiresOn: LocalDate? = LocalDate(2027, 12, 31),
    ) = CertificationClaim(
        source = source,
        sourceRecordRef = record,
        sourceUrl = url,
        certifier = certifier,
        tier = tier,
        declaredHandCut = handCut,
        expiresOn = expiresOn,
    )

    test("keeps the source record identity and key") {
        val claim = claim()

        claim.source shouldBe "hms"
        claim.sourceRecordRef shouldBe "HMS-1"
        claim.key shouldBe "hms::HMS-1"
        claim.tier shouldBe CertificationTier.TIER_1
        claim.expiresOn shouldBe LocalDate(2027, 12, 31)
    }

    test("requires a source, record and certifier") {
        shouldThrow<IllegalArgumentException> { claim(source = " ") }
        shouldThrow<IllegalArgumentException> { claim(record = " ") }
        shouldThrow<IllegalArgumentException> { claim(certifier = " ") }
    }

    test("requires a normalized source and a trimmed record reference") {
        shouldThrow<IllegalArgumentException> { claim(source = "HMS") }
        shouldThrow<IllegalArgumentException> { claim(record = " HMS-1 ") }
    }

    test("lastSeen must not precede firstSeen") {
        val first = kotlin.time.Instant.parse("2026-11-08T00:00:00Z")
        val earlier = kotlin.time.Instant.parse("2026-11-07T00:00:00Z")

        shouldThrow<IllegalArgumentException> {
            claim().copy(firstSeen = first, lastSeen = earlier)
        }
    }

    test("requires an https citation URL") {
        shouldThrow<IllegalArgumentException> { claim(url = "http://hmsusa.org/certified-listing/") }
        shouldThrow<IllegalArgumentException> { claim(url = "not-a-url") }
        shouldThrow<IllegalArgumentException> { claim(url = "https://") }
        shouldThrow<IllegalArgumentException> { claim(url = "https://hms usa.org/x") }
    }

    test("rejects a source that is not a lowercase slug") {
        shouldThrow<IllegalArgumentException> { claim(source = "hms_usa") }
        shouldThrow<IllegalArgumentException> { claim(source = "hms usa") }
    }

    test("caps claim fields at the persisted column widths") {
        shouldThrow<IllegalArgumentException> { claim(source = "a".repeat(CertificationClaim.SOURCE_MAX_LENGTH + 1)) }
        shouldThrow<IllegalArgumentException> { claim(record = "a".repeat(CertificationClaim.RECORD_MAX_LENGTH + 1)) }
        shouldThrow<IllegalArgumentException> { claim(certifier = "a".repeat(CertificationClaim.CERTIFIER_MAX_LENGTH + 1)) }
        shouldThrow<IllegalArgumentException> {
            claim(url = "https://example.test/" + "a".repeat(CertificationClaim.URL_MAX_LENGTH))
        }
    }

    test("tiers are ordered and only Tier-1 confers registry verification") {
        CertificationTier.TIER_1.rank shouldBe 1
        CertificationTier.TIER_2.rank shouldBe 2
        CertificationTier.TIER_3.rank shouldBe 3
        CertificationTier.TIER_1.confersRegistryVerification shouldBe true
        CertificationTier.TIER_2.confersRegistryVerification shouldBe false
        CertificationTier.TIER_3.confersRegistryVerification shouldBe false
    }

    test("hand-cut is TRUE only on a declared hand-cut") {
        HandCutDeclaration.HAND_CUT.isHandCut shouldBe true
        HandCutDeclaration.NOT_HAND_CUT.isHandCut shouldBe false
        HandCutDeclaration.UNSPECIFIED.isHandCut shouldBe false
    }

    test("the same source record produces the same key; a different source does not") {
        claim() shouldBe claim()
        claim().key shouldBe claim().key
        claim(source = "hfsaa").key shouldNotBe claim(source = "hms").key
    }
})
