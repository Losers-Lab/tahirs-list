package com.tahirslist.application.ingestion

import kotlin.uuid.Uuid
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** What a curator decided about a claim conflict. */
@Serializable
enum class ClaimDecision(val wire: String) {
    /** Not decided yet; the finding stays blocking. */
    @SerialName("unresolved")
    UNRESOLVED("unresolved"),

    /** The claim belongs on the listing the venue resolves to: re-point it. */
    @SerialName("attach-to")
    ATTACH_TO("attach-to"),

    /** The citation itself is wrong: do not write it; the existing claim is left for audit. */
    @SerialName("bad-citation")
    BAD_CITATION("bad-citation"),

    /** The two listings are the same restaurant: merge them. */
    @SerialName("same-venue")
    SAME_VENUE("same-venue"),
}

/** What a curator decided about a location-only duplicate. */
@Serializable
enum class DuplicateDecision(val wire: String) {
    @SerialName("unresolved")
    UNRESOLVED("unresolved"),

    /** Same restaurant: write the candidate into the existing listing. */
    @SerialName("same-venue")
    SAME_VENUE("same-venue"),

    /** Different restaurants that happen to share a location: insert a new listing. */
    @SerialName("distinct")
    DISTINCT("distinct"),
}

/** One recorded claim decision. */
@Serializable
data class ClaimResolution(
    val source: String,
    val record: String,
    val decision: ClaimDecision = ClaimDecision.UNRESOLVED,
    val listingId: String? = null,
    val reason: String = "",
    /** Human-facing context written by the tool; ignored when reading decisions. */
    val question: String? = null,
    val evidence: List<String> = emptyList(),
) {
    val key: String get() = "claim:$source:$record"

    val decided: Boolean get() = decision != ClaimDecision.UNRESOLVED
}

/** One recorded duplicate decision. */
@Serializable
data class DuplicateResolution(
    val venueKey: String,
    val decision: DuplicateDecision = DuplicateDecision.UNRESOLVED,
    val listingId: String? = null,
    val reason: String = "",
    val question: String? = null,
    val evidence: List<String> = emptyList(),
) {
    val key: String get() = "duplicate:$venueKey"

    val decided: Boolean get() = decision != DuplicateDecision.UNRESOLVED
}

/**
 * The curator's decisions for one run, written next to `candidates.json` as
 * `resolutions.json`. It is the only input that can unlock a blocking finding,
 * so an agent and a human use the same artifact, and the file is the audit
 * trail of who decided what and why.
 */
@Serializable
data class Resolutions(
    val runId: String,
    val claims: List<ClaimResolution> = emptyList(),
    val duplicates: List<DuplicateResolution> = emptyList(),
) {

    fun claim(source: String, record: String): ClaimResolution? =
        claims.firstOrNull { it.source == source && it.record == record }

    fun duplicate(venueKey: String): DuplicateResolution? =
        duplicates.firstOrNull { it.venueKey == venueKey }

    /** Every deciding entry, for the stale-decision check. */
    fun decidedKeys(): Set<String> =
        claims.filter { it.decided }.mapTo(linkedSetOf()) { it.key } +
            duplicates.filter { it.decided }.mapTo(linkedSetOf()) { it.key }

    fun withClaim(resolution: ClaimResolution): Resolutions = copy(
        claims = claims.filterNot { it.key == resolution.key } + resolution,
    )

    fun withDuplicate(resolution: DuplicateResolution): Resolutions = copy(
        duplicates = duplicates.filterNot { it.key == resolution.key } + resolution,
    )

    /**
     * Fill in an entry for every finding that has none yet, keeping earlier
     * decisions. This is what makes the generated sheet idempotent: re-running
     * the preview never erases an answer.
     */
    fun completeWith(findings: List<Finding>): Resolutions {
        var result = this
        for (finding in findings) {
            when (finding) {
                is Finding.ClaimConflict -> if (claim(finding.claim.source, finding.claim.sourceRecordRef) == null) {
                    result = result.withClaim(
                        ClaimResolution(
                            source = finding.claim.source,
                            record = finding.claim.sourceRecordRef,
                            question = finding.question(),
                            evidence = finding.evidence(),
                        ),
                    )
                }
                is Finding.SuspectedDuplicate -> if (duplicate(finding.candidate.venueKey) == null) {
                    result = result.withDuplicate(
                        DuplicateResolution(
                            venueKey = finding.candidate.venueKey,
                            question = finding.question(),
                            evidence = finding.evidence(),
                        ),
                    )
                }
            }
        }
        return result
    }

    /** Reject a decision file that cannot be applied, before any database work. */
    fun validate() {
        claims.forEach {
            if (it.source.isBlank() || it.record.isBlank()) {
                throw InvalidResolutionException("a claim decision needs a source and a record")
            }
            if (it.decided && it.reason.isBlank()) {
                throw InvalidResolutionException("claim decision '${it.key}' needs a reason")
            }
            if (it.decision == ClaimDecision.BAD_CITATION && it.listingId != null) {
                throw InvalidResolutionException(
                    "claim decision '${it.key}' is bad-citation and must not name a listing",
                )
            }
            if (it.decision in setOf(ClaimDecision.ATTACH_TO, ClaimDecision.SAME_VENUE) && it.listingId == null) {
                throw InvalidResolutionException("claim decision '${it.key}' must name a listingId")
            }
            it.listingId?.let { id -> parseListingId(id, it.key) }
        }
        duplicates.forEach {
            if (it.venueKey.isBlank()) throw InvalidResolutionException("a duplicate decision needs a venueKey")
            if (it.decided && it.reason.isBlank()) {
                throw InvalidResolutionException("duplicate decision '${it.key}' needs a reason")
            }
            if (it.decision == DuplicateDecision.SAME_VENUE && it.listingId == null) {
                throw InvalidResolutionException("duplicate decision '${it.key}' must name a listingId")
            }
            it.listingId?.let { id -> parseListingId(id, it.key) }
        }
    }

    fun requireRun(batchRunId: String) {
        if (runId != batchRunId) throw ResolutionsRunMismatchException(batchRunId, runId)
    }

    companion object {
        fun empty(runId: String): Resolutions = Resolutions(runId)

        fun parseListingId(value: String, key: String): Uuid = runCatching { Uuid.parse(value) }
            .getOrElse { throw InvalidResolutionException("'$key' names '$value', which is not a listing id") }
    }
}

/** The JSON shape of `resolutions.json`. */
object ResolutionsJson {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun encode(resolutions: Resolutions): String = json.encodeToString(resolutions)

    fun decode(text: String): Resolutions = json.decodeFromString(text)

    /** The decision sheet generated from the *unresolved* findings of a preview. */
    fun sheet(runId: String, review: CatalogUpdateReview): Resolutions =
        Resolutions.empty(runId).completeWith(review.findings)

    /**
     * A human-readable summary of what is still undecided, so a non-interactive
     * caller knows exactly what to answer.
     */
    fun describe(review: CatalogUpdateReview): String = buildString {
        if (review.findings.isEmpty()) return@buildString
        appendLine("${review.findings.size} decision(s) needed before this run can load:")
        review.findings.forEachIndexed { index, finding ->
            appendLine("  ${index + 1}. ${finding.question()}")
            finding.evidence().forEach { appendLine("       $it") }
            val options = when (finding) {
                is Finding.ClaimConflict ->
                    "same-venue (merge, keep the listing you name), attach-to (re-point the claim), " +
                        "bad-citation (drop the claim)"
                is Finding.SuspectedDuplicate -> "same-venue (write into an existing listing) or distinct (insert)"
            }
            appendLine("     decide: $options")
            appendLine("     key:    ${finding.key}")
        }
    }
}
