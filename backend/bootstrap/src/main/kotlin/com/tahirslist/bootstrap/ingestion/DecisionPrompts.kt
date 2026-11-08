package com.tahirslist.bootstrap.ingestion

import com.tahirslist.application.ingestion.ClaimDecision
import com.tahirslist.application.ingestion.ClaimResolution
import com.tahirslist.application.ingestion.DuplicateDecision
import com.tahirslist.application.ingestion.DuplicateResolution
import com.tahirslist.application.ingestion.Finding
import com.tahirslist.application.ingestion.Resolutions
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintStream

/**
 * One question per finding, at preview time. The answer is written into the
 * run's `resolutions.json` immediately (so an interrupted session keeps its
 * progress) and the caller re-previews, which is what applies the decision.
 *
 * The decisions offered are the whole vocabulary: there is no free-form action
 * and no SQL. A reason is always required — the file is the audit record.
 */
class DecisionPrompts(
    private val input: BufferedReader = BufferedReader(InputStreamReader(System.`in`)),
    private val out: PrintStream = System.out,
) {

    fun answer(
        findings: List<Finding>,
        resolutions: Resolutions,
        afterEach: (Resolutions) -> Unit,
    ): Resolutions {
        var current = resolutions
        findings.forEachIndexed { index, finding ->
            out.println()
            out.println("[${index + 1}/${findings.size}] ${finding.question()}")
            finding.evidence().forEach { out.println("    $it") }
            current = when (finding) {
                is Finding.ClaimConflict -> current.withClaim(askClaim(finding))
                is Finding.SuspectedDuplicate -> current.withDuplicate(askDuplicate(finding))
            }
            afterEach(current)
        }
        return current
    }

    private fun askClaim(finding: Finding.ClaimConflict): ClaimResolution {
        out.println("  1) same venue — merge them, keep ${finding.resolvedTo.name}")
        out.println("  2) same venue — merge them, keep ${finding.claimedBy.name}")
        out.println("  3) attach-to — the claim belongs to ${finding.resolvedTo.name}; re-point it")
        out.println("  4) bad citation — do not write this claim")
        val choice = ask("choice [1-4]:", setOf("1", "2", "3", "4"))
        val reason = askReason()
        return ClaimResolution(
            source = finding.claim.source,
            record = finding.claim.sourceRecordRef,
            decision = when (choice) {
                "1", "2" -> ClaimDecision.SAME_VENUE
                "3" -> ClaimDecision.ATTACH_TO
                else -> ClaimDecision.BAD_CITATION
            },
            listingId = when (choice) {
                "1" -> finding.resolvedTo.id.toString()
                "2" -> finding.claimedBy.id.toString()
                "3" -> finding.resolvedTo.id.toString()
                else -> null
            },
            reason = reason,
            question = finding.question(),
            evidence = finding.evidence(),
        )
    }

    private fun askDuplicate(finding: Finding.SuspectedDuplicate): DuplicateResolution {
        val existing = finding.existing
        existing.forEachIndexed { index, listing ->
            out.println("  ${index + 1}) same venue — write this candidate into ${listing.name} (${listing.id})")
        }
        val distinct = existing.size + 1
        out.println("  $distinct) distinct — different restaurant at the same location; insert it")
        val choice = ask(
            "choice [1-$distinct]:",
            (1..distinct).map { it.toString() }.toSet(),
        ).toInt()
        val reason = askReason()
        val keep = if (choice == distinct) null else existing[choice - 1]
        return DuplicateResolution(
            venueKey = finding.candidate.venueKey,
            decision = if (keep != null) DuplicateDecision.SAME_VENUE else DuplicateDecision.DISTINCT,
            listingId = keep?.id?.toString(),
            reason = reason,
            question = finding.question(),
            evidence = finding.evidence(),
        )
    }

    private fun ask(prompt: String, allowed: Set<String>): String {
        while (true) {
            out.print("  $prompt ")
            out.flush()
            val answer = readLine()?.trim() ?: throw IllegalStateException(
                "interactive input closed with findings still undecided; " +
                    "the decisions recorded so far are in resolutions.json",
            )
            if (answer in allowed) return answer
            out.println("  please answer one of ${allowed.sorted().joinToString()}")
        }
    }

    private fun askReason(): String {
        while (true) {
            out.print("  reason (recorded in resolutions.json): ")
            out.flush()
            val reason = readLine()?.trim().orEmpty()
            if (reason.isNotBlank()) return reason
            out.println("  a reason is required: the decision file is the audit record")
        }
    }

    private fun readLine(): String? = input.readLine()
}
