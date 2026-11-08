package com.tahirslist.ingestion.pipeline

import com.tahirslist.application.ingestion.IngestReject
import com.tahirslist.application.ingestion.IngestionCandidate
import com.tahirslist.application.ingestion.RejectReason
import com.tahirslist.domain.restaurant.CertificationTier
import kotlin.time.Instant

/** One source's contribution to one metro. */
data class SourceMetroCount(
    val source: String,
    val metro: String,
    val fetched: Int = 0,
    val candidates: Int = 0,
    val rejected: Int = 0,
    val capped: Int = 0,
)

/**
 * The honest account of a run (UC-04): every fetched row is either a candidate
 * or a reject, and every shortfall is a gap. The markdown is written into the
 * run directory and reviewed before the load.
 */
data class IngestReport(
    val runId: String,
    val generatedAt: Instant,
    val counts: List<SourceMetroCount>,
    val candidates: List<IngestionCandidate>,
    val rejects: List<IngestReject>,
    val gaps: List<String> = emptyList(),
) {

    val totalFetched: Int get() = counts.sumOf { it.fetched }
    val totalRejected: Int get() = rejects.size
    val totalCandidates: Int get() = candidates.size
    val totalCapped: Int get() = counts.sumOf { it.capped }

    fun renderMarkdown(): String = buildString {
        appendLine("# Ingestion run $runId")
        appendLine()
        appendLine("Generated: $generatedAt  ")
        appendLine("Status: AWAITING_REVIEW")
        appendLine()
        appendLine("## Totals")
        appendLine()
        appendLine("| metric | value |")
        appendLine("|---|---:|")
        appendLine("| fetched rows | $totalFetched |")
        appendLine("| candidates | $totalCandidates |")
        appendLine("| rejected rows | $totalRejected |")
        appendLine("| rows left out by caps | $totalCapped |")
        appendLine()

        appendLine("## By source and metro")
        appendLine()
        appendLine("| source | metro | fetched | candidates | rejected | capped |")
        appendLine("|---|---|---:|---:|---:|---:|")
        counts.sortedWith(compareBy({ it.metro }, { sourceOrder(it.source) })).forEach {
            appendLine("| ${it.source} | ${it.metro} | ${it.fetched} | ${it.candidates} | ${it.rejected} | ${it.capped} |")
        }
        appendLine()

        appendLine("## Trust coverage")
        appendLine()
        appendLine("| metric | value |")
        appendLine("|---|---:|")
        appendLine("| Tier-1 (registry-verified) candidates | ${candidates.count { it.tier == CertificationTier.TIER_1 }} |")
        appendLine("| manual candidates | ${candidates.count { it.tier != CertificationTier.TIER_1 }} |")
        appendLine("| hand-cut TRUE | ${candidates.count { it.isHandCut == true }} |")
        appendLine("| hand-cut FALSE | ${candidates.count { it.isHandCut == false }} |")
        appendLine("| hand-cut NULL | ${candidates.count { it.isHandCut == null }} |")
        appendLine("| claims | ${candidates.sumOf { it.claims.size }} |")
        appendLine()

        if (rejects.isNotEmpty()) {
            appendLine("## Rejected rows")
            appendLine()
            appendLine("| reason | source | record | name | detail |")
            appendLine("|---|---|---|---|---|")
            rejects.sortedBy { it.reason.name }.forEach {
                appendLine(
                    "| ${it.reason} | ${it.source.orEmpty()} | ${it.sourceRecordRef.orEmpty()} | " +
                        "${it.name.orEmpty()} | ${it.detail.replace("|", "\\|")} |",
                )
            }
            appendLine()
        }

        if (gaps.isNotEmpty()) {
            appendLine("## Gaps")
            appendLine()
            gaps.forEach { appendLine("- $it") }
            appendLine()
        }

        appendLine("## Reject reasons")
        appendLine()
        RejectReason.entries
            .mapNotNull { reason -> rejects.count { it.reason == reason }.takeIf { it > 0 }?.let { reason to it } }
            .forEach { (reason, count) -> appendLine("- $reason: $count") }
    }

    private fun sourceOrder(source: String): Int = when (source) {
        "hms" -> 0
        "hfsaa" -> 1
        "hma" -> 2
        "manual-zabihah" -> 3
        else -> 9
    }
}
