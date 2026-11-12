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

        if (candidates.isNotEmpty()) {
            appendLine("## Enrichment")
            appendLine()
            appendEnrichment(candidates, rejects)
        }

        if (candidates.isNotEmpty()) {
            appendLine("## Candidates and claims")
            appendLine()
            appendLine("Every row below is a candidate for the reviewed load. The claim keys are the")
            appendLine("citation identity the load matches on; a claim already on another listing is a")
            appendLine("conflict that needs a curator decision.")
            appendLine()
            appendLine("| name | city | metro | tier | status | hand-cut | cross-contamination | claims |")
            appendLine("|---|---|---|---|---|---|---|---|")
            candidates.sortedWith(compareBy({ it.metro ?: "" }, { it.name })).forEach {
                appendLine(
                    "| ${it.name.cell()} | ${it.city.orEmpty().cell()} | ${it.metro.orEmpty()} | " +
                        "${it.tier.name} | ${it.verificationStatus} | ${it.isHandCut ?: "NULL"} | " +
                        "${it.crossContamination} | " +
                        "${it.claims.joinToString("<br>") { claim -> claim.key.cell() }} |",
                )
            }
            appendLine()
        }

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

    private fun String.cell(): String = replace("|", "\\|").replace("\n", " ").trim()

    /**
     * SC-231 UC-04: what the gate admitted, what it excluded and why, and what
     * could not be decided. The section is always present — an empty one is a
     * fact, not an omission.
     */
    private fun StringBuilder.appendEnrichment(
        candidates: List<IngestionCandidate>,
        rejects: List<IngestReject>,
    ) {
        val admitted = candidates.mapNotNull { it.admission }
        val excluded = rejects.filter { it.reason == RejectReason.NOT_A_RESTAURANT }
        val undecided = rejects.filter { it.reason == RejectReason.UNDECIDED_VENUE }
        val withCuisine = candidates.filter { it.cuisineName != null }
        val conflicts = candidates.filter { it.cuisineConflict != null }

        appendLine("| metric | value |")
        appendLine("|---|---:|")
        appendLine("| admitted as restaurants | ${admitted.size} |")
        appendLine("| excluded as non-restaurants | ${excluded.size} |")
        appendLine("| undecided (held for a curator) | ${undecided.size} |")
        appendLine("| cuisine set | ${withCuisine.size} |")
        appendLine("| cuisine conflicts | ${conflicts.size} |")
        appendLine("| weak (name-keyword) decisions | ${admitted.count { it.source.isWeak } + withCuisine.count { it.cuisineSource?.isWeak == true }} |")
        appendLine()

        if (admitted.isNotEmpty()) {
            appendLine("### Admitted by type")
            appendLine()
            appendLine("| type | count |")
            appendLine("|---|---:|")
            admitted.groupingBy { it.venueType }.eachCount().toSortedMap()
                .forEach { (type, count) -> appendLine("| $type | $count |") }
            appendLine()
        }

        if (excluded.isNotEmpty()) {
            appendLine("### Excluded by class")
            appendLine()
            appendLine("| class | count | example reason |")
            appendLine("|---|---:|---|")
            excluded.groupBy { it.venueClass ?: "OTHER" }.toSortedMap().forEach { (cls, rows) ->
                appendLine("| $cls | ${rows.size} | ${rows.first().detail.cell()} |")
            }
            appendLine()
        }

        if (undecided.isNotEmpty()) {
            appendLine("### Undecided — held, not loaded")
            appendLine()
            undecided.sortedBy { it.name.orEmpty() }.forEach {
                appendLine("- ${it.name.orEmpty()} — ${it.detail.cell()}")
            }
            appendLine()
        }

        if (conflicts.isNotEmpty()) {
            appendLine("### Cuisine review queue")
            appendLine()
            conflicts.sortedBy { it.name }.forEach {
                appendLine("- ${it.name} — ${it.cuisineConflict?.cell().orEmpty()}")
            }
            appendLine()
        }

        appendLine("_Photo pass not run for this batch._")
        appendLine()
    }

    private fun sourceOrder(source: String): Int = when (source) {
        "hms" -> 0
        "hfsaa" -> 1
        "hma" -> 2
        "manual-zabihah" -> 3
        else -> 9
    }
}
