package com.tahirslist.ingestion.pipeline

import com.tahirslist.application.ingestion.CandidateBatch
import com.tahirslist.application.ingestion.CandidateBatchJson
import com.tahirslist.application.ingestion.RejectReason
import com.tahirslist.domain.restaurant.CertificationTier
import com.tahirslist.domain.restaurant.CrossContamination
import com.tahirslist.domain.restaurant.HandCutDeclaration
import com.tahirslist.domain.restaurant.VerificationStatus
import com.tahirslist.ingestion.SourceListing
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.test.runTest

private const val VALID_BATCH = """
{
  "batch": "zabihah-sample-2026-11",
  "accessDate": "2026-11-08",
  "rows": [
    {
      "name": "Paramount Fine Foods",
      "address": "253 Yonge St",
      "city": "Toronto",
      "region": "ON",
      "country": "Canada",
      "url": "https://www.zabihah.com/listing/paramount",
      "crossContamination": "UNCERTAIN"
    },
    {
      "name": "No City Place",
      "address": "1 Nowhere",
      "city": "",
      "region": "ON",
      "country": "Canada",
      "url": "https://www.zabihah.com/listing/bad"
    }
  ]
}
"""

private fun manualListing(): SourceListing = sourceListing(
    source = "manual-zabihah",
    record = "Z-1",
    tier = com.tahirslist.ingestion.SourceTier.TIER_3,
    handCut = com.tahirslist.certifiers.core.HandCutDeclaration.UNSPECIFIED,
)

class CompileManualBatchTest : FunSpec({

    test("a cited manual row becomes an unverified candidate with explicit cross-contamination") {
        val file = Files.createTempFile("manual-batch", ".json")
        Files.writeString(file, VALID_BATCH)
        val artifacts = FakeArtifacts()
        val compile = CompileManualBatch(
            source = JsonCuratedBatchSource(),
            artifacts = artifacts,
            factory = CandidateFactory(namedGeocoder()),
        )

        val result = compile.execute(file, runId = "manual-1")

        result.report.rejects.single().reason shouldBe RejectReason.INVALID_MANUAL_ENTRY
        val candidate = result.report.candidates.single()
        candidate.name shouldBe "Paramount Fine Foods"
        candidate.verificationStatus shouldBe VerificationStatus.UNVERIFIED
        candidate.verificationBasis shouldBe null
        candidate.isHandCut shouldBe null
        candidate.crossContamination shouldBe CrossContamination.UNCERTAIN
        candidate.claims.single().source shouldBe "manual-zabihah"
        candidate.claims.single().tier shouldBe CertificationTier.TIER_3
        candidate.claims.single().firstSeen.toString() shouldBe "2026-11-08T00:00:00Z"
        artifacts.completedRun shouldBe "manual-1"
    }

    test("merging with a Tier-1 candidate keeps the registry outcome and adds the manual claim") {
        val existing = CandidateFactory(namedGeocoder())
            .build(
                sourceListing(
                    source = "hms",
                    record = "H-1",
                    name = "Paramount Fine Foods",
                    city = "Toronto",
                    region = "ON",
                    country = "Canada",
                ),
                "Toronto",
            )
            .let { (it as CandidateBuild.Built).candidate }

        // Same venue, same normalized name/address/city/region/country.
        val file = Files.createTempFile("manual-batch", ".json")
        Files.writeString(file, VALID_BATCH)
        val artifacts = FakeArtifacts()
        val compile = CompileManualBatch(
            source = JsonCuratedBatchSource(),
            artifacts = artifacts,
            factory = CandidateFactory(namedGeocoder()),
        )

        val result = compile.execute(
            file,
            runId = "manual-2",
            mergeFrom = CandidateBatch("run-1", java.time.Instant.EPOCH.let {
                kotlin.time.Instant.fromEpochMilliseconds(it.toEpochMilli())
            }, listOf(existing)),
        )

        val merged = result.report.candidates.single()
        merged.claims.map { it.source }.sorted() shouldBe listOf("hms", "manual-zabihah")
        merged.verificationStatus shouldBe VerificationStatus.VERIFIED
        merged.isHandCut shouldBe true
    }

    test("the curated file is validated per row, and the rest still compiles") {
        val read = JsonCuratedBatchSource().read(writeBatch(VALID_BATCH))

        read.listings.size shouldBe 1
        read.rejects.single().reason shouldBe RejectReason.INVALID_MANUAL_ENTRY
    }

    test("a non-https citation is rejected") {
        val batch = VALID_BATCH.replace("https://www.zabihah.com/listing/paramount", "http://example.test/x")
        val read = JsonCuratedBatchSource().read(writeBatch(batch))

        read.rejects.map { it.reason }.distinct() shouldBe listOf(RejectReason.INVALID_MANUAL_ENTRY)
        read.listings shouldBe emptyList()
    }

    test("the manual record id is stable for the same citation URL") {
        val first = JsonCuratedBatchSource().read(writeBatch(VALID_BATCH)).listings.single()
        val second = JsonCuratedBatchSource().read(writeBatch(VALID_BATCH)).listings.single()

        first.listing.source.recordId shouldBe second.listing.source.recordId
        first.listing.source.recordId.startsWith("z-") shouldBe true
    }
})

private fun writeBatch(contents: String): Path {
    val file = Files.createTempFile("manual-batch", ".json")
    Files.writeString(file, contents)
    return file
}
