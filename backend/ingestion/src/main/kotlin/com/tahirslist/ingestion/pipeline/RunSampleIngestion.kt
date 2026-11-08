package com.tahirslist.ingestion.pipeline

import com.tahirslist.application.geo.CheckGeocoderHealth
import com.tahirslist.application.geo.GeocoderHealth
import com.tahirslist.application.ingestion.CandidateBatch
import com.tahirslist.application.ingestion.CandidateBatchJson
import com.tahirslist.application.ingestion.IngestReject
import com.tahirslist.application.ingestion.IngestionCandidate
import com.tahirslist.application.ingestion.RejectReason
import com.tahirslist.application.ingestion.mergeCandidates
import com.tahirslist.certifiers.core.CertifierException
import com.tahirslist.domain.restaurant.LatLng
import com.tahirslist.certifiers.core.SourceBlockedException
import com.tahirslist.certifiers.core.SourceParseException
import com.tahirslist.certifiers.core.SourceTooLargeException
import com.tahirslist.ingestion.SourceListing
import com.tahirslist.ingestion.artifacts.ArtifactStore
import com.tahirslist.ingestion.artifacts.PendingRun
import com.tahirslist.ingestion.artifacts.PendingRunPolicy
import java.nio.charset.StandardCharsets
import java.time.Clock
import kotlin.coroutines.cancellation.CancellationException

/** What the curator asked the run to do. */
data class IngestSelection(
    val runId: String,
    /** Metro names; empty means every configured metro. */
    val metros: Set<String> = emptySet(),
    val capPerMetro: Int = 25,
    /** Source ids; empty means every feed. */
    val sources: Set<String> = emptySet(),
    val refresh: Boolean = false,
    val onPending: PendingRunPolicy = PendingRunPolicy.ABORT,
) {
    init {
        require(runId.isNotBlank()) { "a run must have an id" }
        require(capPerMetro > 0) { "capPerMetro must be positive" }
    }
}

data class IngestRunResult(
    val report: IngestReport,
    val pending: PendingRun,
    val candidatesJson: String,
)

/**
 * UC-01: fetch, translate, filter, geocode, reconcile and write one immutable
 * run directory. It never writes to the catalogue; the reviewed load does that.
 *
 * Order is the safety property (ING-1): the pending-run guard and the geocoder
 * pre-flight both run before the first directory request. Any geocoder failure
 * mid-run aborts before `complete`, so there is no manifest and the run is
 * never mistaken for reviewable.
 */
class RunSampleIngestion(
    private val feeds: List<DirectoryFeed>,
    private val health: CheckGeocoderHealth,
    private val artifacts: ArtifactStore,
    private val factory: CandidateFactory,
    private val metroFilter: MetroFilter = MetroFilter(),
    private val clock: Clock = Clock.systemUTC(),
) {

    suspend fun execute(selection: IngestSelection): IngestRunResult {
        artifacts.begin(selection.runId, selection.onPending)

        when (val checked = health.execute()) {
            is GeocoderHealth.Unavailable ->
                throw GeocoderUnavailableException("geocoder pre-flight failed: ${checked.reason}")
            is GeocoderHealth.Available -> Unit
        }

        val metros = metroFilter.selected(selection.metros)
        val enabled = feeds.filter { selection.sources.isEmpty() || it.sourceId in selection.sources }
        val rejects = mutableListOf<IngestReject>()
        val gaps = mutableListOf<String>()
        val snapshots = linkedMapOf<String, ByteArray>()
        val fetched = linkedMapOf<Pair<String, String>, Int>()
        val rowsByMetro = linkedMapOf<String, LinkedHashMap<String, MutableList<SourceListing>>>()
        val outsideBySource = linkedMapOf<String, Int>()

        for (feed in enabled) {
            val page = try {
                feed.fetch(selection.refresh)
            } catch (e: CancellationException) {
                throw e
            } catch (e: CertifierException) {
                rejects += sourceReject(feed.sourceId, e)
                gaps += "${feed.sourceId}: ${e.message}"
                continue
            }
            page.warnings.forEach { gaps += "${feed.sourceId}: $it" }
            page.snapshots.forEach { snapshot ->
                snapshots[snapshotName(snapshot.certifierId, snapshot.sha256, snapshot.contentType)] =
                    snapshot.body.toByteArray(StandardCharsets.UTF_8)
            }
            for (listing in page.listings) {
                // Membership is the circle. A row that carries coordinates is
                // filtered without a geocoder call; a row that does not is
                // geocoded once here, and the factory reuses that result.
                val metro = when (val point = listing.point()) {
                    null -> {
                        val place = factory.locate(listing)
                        if (place == null) {
                            rejects += IngestReject(
                                reason = RejectReason.NO_GEOCODER_MATCH,
                                detail = "no geocoder match for '${listing.name}'",
                                source = feed.sourceId,
                                sourceRecordRef = listing.source.recordId,
                                name = listing.name,
                            )
                        }
                        place?.let { metroFilter.forPoint(it.location) }
                    }
                    else -> metroFilter.forPoint(point)
                }
                if (metro == null) {
                    outsideBySource[feed.sourceId] = (outsideBySource[feed.sourceId] ?: 0) + 1
                    continue
                }
                if (metros.none { it.name == metro.name }) continue
                fetched[feed.sourceId to metro.name] = (fetched[feed.sourceId to metro.name] ?: 0) + 1
                rowsByMetro.getOrPut(metro.name) { linkedMapOf() }
                    .getOrPut(feed.sourceId) { mutableListOf() }
                    .add(listing)
            }
        }

        // Rows outside every configured circle are not silently missing from the
        // account of the run.
        outsideBySource.forEach { (source, count) ->
            gaps += "$source: $count row(s) outside every configured metro"
        }

        val accepted = mutableListOf<IngestionCandidate>()
        val counts = mutableListOf<SourceMetroCount>()

        for (metro in metros) {
            val bySource = rowsByMetro[metro.name].orEmpty()
            val selected = interleave(bySource, selection.capPerMetro)
            val selectedBySource = selected.groupBy { it.source.certifierId }

            if (bySource.isEmpty()) gaps += "${metro.name}: no rows matched this metro"

            for ((sourceAndMetro, fetchedCount) in fetched.filterKeys { it.second == metro.name }) {
                val source = sourceAndMetro.first
                val rows = selectedBySource[source].orEmpty()
                var sourceAccepted = 0
                var sourceRejected = 0
                for (listing in rows) {
                    when (val build = factory.build(listing, metro)) {
                        is CandidateBuild.Built -> {
                            accepted += build.candidate
                            sourceAccepted++
                        }
                        is CandidateBuild.Rejected -> {
                            rejects += build.reject
                            sourceRejected++
                        }
                    }
                }
                counts += SourceMetroCount(
                    source = source,
                    metro = metro.name,
                    fetched = fetchedCount,
                    candidates = sourceAccepted,
                    rejected = sourceRejected,
                    capped = fetchedCount - rows.size,
                )
            }
            val capped = bySource.values.sumOf { it.size } - selected.size
            if (capped > 0) {
                gaps += "${metro.name}: capped at ${selection.capPerMetro} (${capped} row(s) left out)"
            }
        }

        val candidates = mergeCandidates(accepted)
        if (candidates.size < accepted.size) {
            gaps += "duplicate candidates merged: ${accepted.size - candidates.size}"
        }
        val generatedAt = clock.instant()
        val batch = CandidateBatch(selection.runId, generatedAt.toKotlinInstant(), candidates, rejects)
        val report = IngestReport(
            runId = selection.runId,
            generatedAt = generatedAt.toKotlinInstant(),
            counts = counts,
            candidates = candidates,
            rejects = rejects,
            gaps = gaps,
        )
        val json = CandidateBatchJson.encode(batch)
        val pending = artifacts.complete(selection.runId, json, report.renderMarkdown(), snapshots)

        return IngestRunResult(report = report, pending = pending, candidatesJson = json)
    }

    /**
     * Round-robin across sources, so a metro's cap is filled from every enabled
     * directory instead of being consumed by the first one. Within a source the
     * order is the source-record id, so a re-run selects the same rows.
     */
    private fun interleave(
        bySource: Map<String, List<SourceListing>>,
        cap: Int,
    ): List<SourceListing> {
        val queues = bySource.entries
            .sortedBy { sourceOrder(it.key) }
            .map { it.value.sortedBy { row -> row.source.recordId } }
        val selected = mutableListOf<SourceListing>()
        var index = 0
        while (selected.size < cap) {
            var added = false
            for (queue in queues) {
                if (index < queue.size && selected.size < cap) {
                    selected += queue[index]
                    added = true
                }
            }
            if (!added) break
            index++
        }
        return selected
    }

    private fun sourceReject(source: String, error: CertifierException): IngestReject {
        val reason = when (error) {
            is SourceBlockedException, is SourceTooLargeException -> RejectReason.SOURCE_BLOCKED
            is SourceParseException -> RejectReason.SOURCE_PARSE_FAILED
            else -> RejectReason.SOURCE_UNAVAILABLE
        }
        return IngestReject(reason = reason, detail = error.message ?: error::class.simpleName.orEmpty(), source = source)
    }

    private fun snapshotName(certifierId: String, sha256: String, contentType: String?): String {
        val extension = if (contentType?.contains("json", ignoreCase = true) == true) "json" else "html"
        return "$certifierId-${sha256.take(12)}.$extension"
    }

    private fun sourceOrder(source: String): Int = when (source) {
        "hms" -> 0
        "hfsaa" -> 1
        "hma" -> 2
        "manual-zabihah" -> 3
        else -> 9
    }
}

private fun java.time.Instant.toKotlinInstant(): kotlin.time.Instant = kotlin.time.Instant.fromEpochMilliseconds(toEpochMilli())

/** The directory's own point, when it has a usable one; never the accepted location. */
private fun SourceListing.point(): LatLng? {
    val lat = latitude ?: return null
    val lng = longitude ?: return null
    return runCatching { LatLng(lat, lng) }.getOrNull()
}
