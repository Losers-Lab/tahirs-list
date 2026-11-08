package com.tahirslist.application.ingestion

import com.tahirslist.domain.restaurant.CertificationClaim
import com.tahirslist.domain.restaurant.CertificationTier
import com.tahirslist.domain.restaurant.CrossContamination
import com.tahirslist.domain.restaurant.HandCutDeclaration
import com.tahirslist.domain.restaurant.LatLng
import com.tahirslist.domain.restaurant.Provenance
import com.tahirslist.domain.restaurant.VerificationBasis
import com.tahirslist.domain.restaurant.VerificationStatus
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Instant

fun claim(
    source: String = "hms",
    record: String = "HMS-1",
    url: String = "https://hmsusa.org/certified-listing/",
    certifier: String = "Halal Monitoring Services (HMS)",
    tier: CertificationTier = CertificationTier.TIER_1,
    declaredHandCut: HandCutDeclaration = HandCutDeclaration.HAND_CUT,
    expiresOn: kotlinx.datetime.LocalDate? = null,
): CertificationClaim = CertificationClaim(
    source = source,
    sourceRecordRef = record,
    sourceUrl = url,
    certifier = certifier,
    tier = tier,
    declaredHandCut = declaredHandCut,
    expiresOn = expiresOn,
)

fun candidate(
    name: String = "Paramount Fine Foods",
    address: String = "253 Yonge St",
    city: String = "Toronto",
    region: String = "ON",
    metro: String? = "Toronto",
    venueKey: String = "a".repeat(64),
    location: LatLng = LatLng(43.665206, -79.384523),
    claims: List<CertificationClaim> = listOf(claim()),
    crossContamination: CrossContamination = CrossContamination.DEFAULT,
): IngestionCandidate = IngestionCandidate(
    name = name,
    address = address,
    city = city,
    region = region,
    postal = null,
    country = "CA",
    location = location,
    venueKey = venueKey,
    claims = claims,
    metro = metro,
    manualCrossContamination = crossContamination,
)

class IngestionModelTest : FunSpec({

    test("a Tier-1 claim confers registry verification and index qualification") {
        val c = candidate()

        c.tier shouldBe CertificationTier.TIER_1
        c.verificationStatus shouldBe VerificationStatus.VERIFIED
        c.verificationBasis shouldBe VerificationBasis.CERTIFIER_REGISTRY
        c.provenance shouldBe Provenance.CERTIFIER_HMS
        c.crossContamination shouldBe CrossContamination.NO_CROSS_CONTAMINATION
    }

    test("hand-cut is TRUE only on a declared hand-cut; declared-not and silent are FALSE") {
        candidate(claims = listOf(claim(declaredHandCut = HandCutDeclaration.HAND_CUT))).isHandCut shouldBe true
        candidate(claims = listOf(claim(declaredHandCut = HandCutDeclaration.NOT_HAND_CUT))).isHandCut shouldBe false
        candidate(claims = listOf(claim(declaredHandCut = HandCutDeclaration.UNSPECIFIED))).isHandCut shouldBe false
    }

    test("a manual claim never verifies and never claims a cut") {
        val manual = candidate(
            claims = listOf(
                claim(
                    source = "manual-zabihah",
                    record = "Z-1",
                    url = "https://www.zabihah.com/listing/1",
                    certifier = "Zabihah",
                    tier = CertificationTier.TIER_3,
                    declaredHandCut = HandCutDeclaration.UNSPECIFIED,
                ),
            ),
            crossContamination = CrossContamination.PRESENT,
        )

        manual.verificationStatus shouldBe VerificationStatus.UNVERIFIED
        manual.verificationBasis shouldBe null
        manual.isHandCut shouldBe null
        manual.provenance shouldBe Provenance.MANUAL_ZABIHAH
        manual.crossContamination shouldBe CrossContamination.PRESENT
    }

    test("merging keeps both claims and lets the highest tier decide the outcome") {
        val manual = candidate(
            name = "Manual Name",
            claims = listOf(
                claim(
                    source = "manual-zabihah",
                    record = "Z-1",
                    url = "https://www.zabihah.com/listing/1",
                    certifier = "Zabihah",
                    tier = CertificationTier.TIER_3,
                ),
            ),
        )
        val tierOne = candidate(claims = listOf(claim(source = "hfsaa", record = "R-1", url = "https://www.hfsaa.org/ny")))

        val merged = manual.mergeWith(tierOne)

        merged.name shouldBe "Paramount Fine Foods"
        merged.claims.map { it.key }.toSet() shouldBe setOf("manual-zabihah::Z-1", "hfsaa::R-1")
        merged.verificationStatus shouldBe VerificationStatus.VERIFIED
        merged.provenance shouldBe Provenance.CERTIFIER_HFSAA
    }

    test("ties are broken by source priority, deterministically") {
        val hms = claim(source = "hms", record = "H-1")
        val hfsaa = claim(source = "hfsaa", record = "R-1", url = "https://www.hfsaa.org/ny")

        candidate(claims = listOf(hfsaa, hms)).winningClaim.source shouldBe "hms"
    }

    test("the candidate batch JSON round-trips without losing trust evidence") {
        val batch = CandidateBatch(
            runId = "run-1",
            generatedAt = Instant.parse("2026-11-08T00:00:00Z"),
            candidates = listOf(candidate()),
            rejects = listOf(IngestReject(RejectReason.NO_GEOCODER_MATCH, "no match", "hms", "H-9", "X")),
        )

        val decoded = CandidateBatchJson.decode(CandidateBatchJson.encode(batch))

        decoded.runId shouldBe batch.runId
        decoded.candidates.single().winningClaim shouldBe batch.candidates.single().winningClaim
        decoded.candidates.single().verificationStatus shouldBe VerificationStatus.VERIFIED
        decoded.rejects.single().reason shouldBe RejectReason.NO_GEOCODER_MATCH
    }
})
