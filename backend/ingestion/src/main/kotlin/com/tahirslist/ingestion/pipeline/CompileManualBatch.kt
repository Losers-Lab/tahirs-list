package com.tahirslist.ingestion.pipeline

import com.tahirslist.application.ingestion.CandidateBatch
import com.tahirslist.application.ingestion.CandidateBatchJson
import com.tahirslist.application.ingestion.IngestReject
import com.tahirslist.application.ingestion.IngestionCandidate
import com.tahirslist.application.ingestion.RejectReason
import com.tahirslist.application.ingestion.mergeCandidates
import com.tahirslist.ingestion.artifacts.ArtifactStore
import com.tahirslist.ingestion.artifacts.PendingRunPolicy
import java.nio.file.Path
import java.time.Clock
import kotlin.time.Instant

/**
 * UC-02: validate, geocode and merge a hand-curated Tier-3 batch.
 *
 * The curated rows are merged into whatever Tier-1 candidates the caller passes
 * in; the highest tier keeps the venue's trust outcome and every claim is kept
 * (BR-10). A new run directory is written rather than editing an existing run,
 * so the immutability rule (D-17) still holds.
 */
class CompileManualBatch(
    private val source: CuratedBatchSource,
    private val artifacts: ArtifactStore,
    private val factory: CandidateFactory,
    private val metroFilter: MetroFilter = MetroFilter(),
    private val clock: Clock = Clock.systemUTC(),
) {

    suspend fun execute(
        batchFile: Path,
        runId: String,
        mergeFrom: CandidateBatch? = null,
        onPending: PendingRunPolicy = PendingRunPolicy.ABORT,
    ): IngestRunResult {
        artifacts.begin(runId, onPending)

        val read = source.read(batchFile)
        val rejects = mutableListOf<IngestReject>()
        rejects += read.rejects
        val accepted = mutableListOf<IngestionCandidate>()

        for (curated in read.listings) {
            val metro = metroFilter.metroFor(curated.listing) ?: run {
                rejects += IngestReject(
                    reason = RejectReason.MISSING_METRO,
                    detail = "curated row in '${curated.listing.city}' is outside the configured metros",
                    source = curated.listing.source.certifierId,
                    sourceRecordRef = curated.listing.source.recordId,
                    name = curated.listing.name,
                )
                null
            } ?: continue

            when (val build = factory.build(curated.listing, metro.name, curated.crossContamination)) {
                is CandidateBuild.Built -> accepted += build.candidate.stamped(curated.accessedOn)
                is CandidateBuild.Rejected -> rejects += build.reject
            }
        }

        val all = mergeCandidates(mergeFrom?.candidates.orEmpty() + accepted)

        val counts = accepted
            .groupingBy { it.metro ?: "unknown" }
            .eachCount()
            .map { (metro, count) ->
                SourceMetroCount(
                    source = "manual-zabihah",
                    metro = metro,
                    fetched = count,
                    candidates = count,
                    rejected = 0,
                )
            }
        val gaps = mutableListOf<String>()

        val generatedAt = clock.instant().let { Instant.fromEpochMilliseconds(it.toEpochMilli()) }
        val batch = CandidateBatch(runId, generatedAt, all, rejects + mergeFrom?.rejects.orEmpty())
        val report = IngestReport(
            runId = runId,
            generatedAt = generatedAt,
            counts = counts,
            candidates = all,
            rejects = rejects + mergeFrom?.rejects.orEmpty(),
            gaps = gaps,
        )
        val json = CandidateBatchJson.encode(batch)
        val pending = artifacts.complete(runId, json, report.renderMarkdown())

        return IngestRunResult(report = report, pending = pending, candidatesJson = json)
    }

    /** Record when the curator accessed the cited page; the claim carries it. */
    private fun IngestionCandidate.stamped(accessedOn: String): IngestionCandidate {
        val firstSeen = runCatching {
            Instant.parse("${accessedOn.trim()}T00:00:00Z")
        }.getOrNull() ?: return this
        return copy(claims = claims.map { it.copy(firstSeen = it.firstSeen ?: firstSeen) })
    }
}
