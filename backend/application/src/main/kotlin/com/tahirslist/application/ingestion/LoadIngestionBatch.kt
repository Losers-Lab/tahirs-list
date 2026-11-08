package com.tahirslist.application.ingestion

import com.tahirslist.application.listing.RestaurantListingRepository
import com.tahirslist.domain.restaurant.CertificationClaim
import com.tahirslist.domain.restaurant.RestaurantListing
import kotlin.uuid.Uuid

/** What the batch should do with a candidate. */
sealed interface WriteTarget {
    /** The venue is new: create a listing. */
    data object Insert : WriteTarget

    /** The venue already exists: upgrade the listing in place. */
    data class Update(val listingId: Uuid) : WriteTarget
}

/** One candidate resolved to a concrete write. */
data class ListingWrite(
    val target: WriteTarget,
    val candidate: IngestionCandidate,
)

/** A location-only match: it looks like the same venue, but the identity does not. */
data class HeldCandidate(
    val candidate: IngestionCandidate,
    val existingListingIds: List<Uuid>,
)

/**
 * The reviewed diff produced by [LoadIngestionBatch.preview]. Carrying the
 * resolved writes (not just counts) is what makes `execute` a single, auditable
 * transaction with no second matching pass.
 *
 * [findings] are the decisions still missing; a run is only loadable when this
 * list is empty. [merges] and [reattachments] are the effects of the decisions
 * already recorded, applied in the same transaction as the writes.
 */
data class CatalogUpdateReview(
    val runId: String,
    val writes: List<ListingWrite>,
    val findings: List<Finding> = emptyList(),
    val rejects: List<IngestReject> = emptyList(),
    val brandsToCreate: Set<String> = emptySet(),
    val merges: List<ListingMerge> = emptyList(),
    val reattachments: List<ClaimReattachment> = emptyList(),
    val applied: List<AppliedResolution> = emptyList(),
) {
    val inserted: Int get() = writes.count { it.target is WriteTarget.Insert }
    val upgraded: Int get() = writes.count { it.target is WriteTarget.Update }
    val merged: Int get() = merges.size
    val unresolved: Int get() = findings.size
    val isResolved: Boolean get() = findings.isEmpty()

    /** Held rows, kept for the report shape; the decisions live in [findings]. */
    val held: List<HeldCandidate>
        get() = findings.filterIsInstance<Finding.SuspectedDuplicate>()
            .map { HeldCandidate(it.candidate, it.existing.map { evidence -> evidence.id }) }

    fun diffSummary(): String = buildString {
        appendLine("Ingestion review for run $runId")
        appendLine("  insert:   $inserted")
        appendLine("  upgrade:  $upgraded")
        appendLine("  merged:   $merged")
        appendLine("  awaiting: $unresolved")
        appendLine("  rejected: ${rejects.size}")
        if (brandsToCreate.isNotEmpty()) appendLine("  new brands: ${brandsToCreate.joinToString()}")
        if (applied.isNotEmpty()) {
            appendLine("Decisions applied:")
            applied.forEach { appendLine("  - ${it.key}: ${it.decision}${it.listingId?.let { id -> " → $id" } ?: ""} (${it.detail})") }
        }
        if (findings.isNotEmpty()) appendLine(ResolutionsJson.describe(this@CatalogUpdateReview))
        if (rejects.isNotEmpty()) {
            appendLine("Rejected:")
            rejects.forEach { appendLine("  - ${it.reason}: ${it.detail}") }
        }
    }
}

/** Counts from the committed transaction. */
data class BatchLoadResult(
    val inserted: Int = 0,
    val upgraded: Int = 0,
    val skipped: Int = 0,
    val merged: Int = 0,
    val held: Int = 0,
    val rejected: Int = 0,
    val claimsWritten: Int = 0,
) {
    override fun toString(): String =
        "inserted=$inserted upgraded=$upgraded merged=$merged held=$held rejected=$rejected claims=$claimsWritten"
}

/**
 * UC-03: turn a reviewed candidate batch into catalogue rows.
 *
 * `preview` only reads: it validates, matches, resolves brands and turns every
 * ambiguity into a [Finding] that needs a curator decision. With a
 * [Resolutions] sheet it additionally validates and applies those decisions and
 * refuses a decision that no longer matches a finding ([StaleResolutionException]).
 * `execute` hands the resolved writes, merges and claim re-pointings to the
 * repository's single transaction, so brands, listings, claims, child rows and
 * the search mirror commit together or not at all (BR-13).
 */
class LoadIngestionBatch(
    private val listings: RestaurantListingRepository,
    private val brands: BrandRepository,
    private val validator: CandidateValidator = CandidateValidator(),
) {

    suspend fun preview(
        batch: CandidateBatch,
        resolutions: Resolutions = Resolutions.empty(batch.runId),
    ): CatalogUpdateReview {
        resolutions.requireRun(batch.runId)
        resolutions.validate()

        val rejects = mutableListOf<IngestReject>()
        val findings = mutableListOf<Finding>()
        val writes = mutableListOf<ListingWrite>()
        val brandsToCreate = linkedSetOf<String>()
        val merges = mutableListOf<ListingMerge>()
        val reattachments = mutableListOf<ClaimReattachment>()
        val applied = mutableListOf<AppliedResolution>()
        val consumed = linkedSetOf<String>()

        for (candidate in mergeDuplicates(batch.candidates)) {
            when (val validation = validator.validate(candidate)) {
                is CandidateValidation.Invalid -> rejects += validation.reject
                is CandidateValidation.Valid -> {
                    val planned = plan(candidate, resolutions)
                    writes += planned.writes
                    findings += planned.findings
                    merges += planned.merges
                    reattachments += planned.reattachments
                    applied += planned.applied
                    rejects += planned.rejects
                    consumed += planned.consumed
                    if (brands.resolve(candidate.name) == null) brandsToCreate += candidate.name
                }
            }
        }

        // A decision that no longer matches a finding is not applied on a best
        // guess: the catalogue state changed since it was made.
        val stale = resolutions.decidedKeys() - consumed
        if (stale.isNotEmpty()) throw StaleResolutionException(stale.sorted())

        return CatalogUpdateReview(
            runId = batch.runId,
            // A same-venue decision retires one listing; any other candidate in
            // the same batch that targeted the retired row must follow it to the
            // keeper, or the merge would delete the row its write expects.
            writes = dedupeWrites(redirectWrites(writes, merges)),
            findings = findings,
            rejects = rejects + batch.rejects,
            brandsToCreate = brandsToCreate,
            merges = merges.distinctBy { it.keepId to it.retireId },
            reattachments = reattachments.distinctBy { it.source to it.sourceRecordRef },
            applied = applied,
        )
    }

    suspend fun execute(review: CatalogUpdateReview): BatchLoadResult {
        check(review.isResolved) {
            "run ${review.runId} still has ${review.unresolved} unresolved finding(s); " +
                "record a decision for each before executing"
        }
        val applied = listings.applyBatch(
            writes = review.writes,
            merges = review.merges,
            reattachments = review.reattachments,
        )
        return applied.copy(
            held = review.findings.count { it is Finding.SuspectedDuplicate },
            rejected = review.rejects.size,
        )
    }

    /**
     * Resolve one candidate to at most one write, consuming decisions for any
     * finding it hits. A conflict is never resolved silently: without a decided
     * entry it becomes a blocking finding and nothing is written for it.
     */
    private suspend fun plan(candidate: IngestionCandidate, resolutions: Resolutions): Planned {
        val result = Planned()
        var current = candidate
        // A claim re-pointed by an `attach-to` decision, before the transaction.
        val boundClaims = mutableMapOf<String, Uuid>()
        // A listing retired by a `same-venue` decision: its rows now belong to the keeper.
        val moved = mutableMapOf<Uuid, Uuid>()
        // Evidence already read from the catalogue, so prompts and messages never
        // need a second query (or invent a name).
        val evidence = mutableMapOf<Uuid, ListingEvidence>()
        var venue: RestaurantListing? = listings.findByVenueKey(current.venueKey)
        venue?.let { evidence[it.id] = it.evidence() }

        suspend fun loadClaimOwner(claim: CertificationClaim): RestaurantListing? {
            boundClaims[claim.key]?.let { id -> return listings.findById(id)?.also { evidence[id] = it.evidence() } }
            return listings.findClaim(claim.source, claim.sourceRecordRef)?.also { evidence[it.id] = it.evidence() }
        }

        fun resolveMoved(id: Uuid): Uuid {
            var currentId = id
            var guard = 0
            while (guard++ < 32) {
                val next = moved[currentId] ?: return currentId
                if (next == currentId) return currentId
                currentId = next
            }
            return currentId
        }

        while (true) {
            val owners = current.claims.mapNotNull { claim -> loadClaimOwner(claim)?.let { claim to it } }
            val base = resolveMoved(venue?.id ?: owners.firstOrNull()?.second?.id ?: break)
            val conflict = owners.firstOrNull { (claim, owner) -> resolveMoved(owner.id) != base }
                ?: break

            val (claim, owner) = conflict
            val resolvedTo = evidence[base]
                ?: venue?.takeIf { it.id == base }?.evidence()
                ?: owners.firstOrNull { resolveMoved(it.second.id) == base }?.second?.evidence()
                ?: throw BatchPlanException("listing $base is not readable while resolving a claim conflict")
            val finding = Finding.ClaimConflict(
                claim = claim,
                claimedBy = evidence[owner.id] ?: owner.evidence(),
                resolvedTo = resolvedTo,
                candidate = current,
            )
            val resolution = resolutions.claim(claim.source, claim.sourceRecordRef)
            if (resolution == null || !resolution.decided) {
                result.findings += finding
                return result.finished()
            }
            result.consumed += finding.key

            when (resolution.decision) {
                ClaimDecision.UNRESOLVED -> error("unreachable: undecided handled above")

                ClaimDecision.BAD_CITATION -> {
                    val remaining = current.claims.filterNot { it.key == claim.key }
                    result.applied += AppliedResolution(
                        key = finding.key,
                        decision = resolution.decision.wire,
                        listingId = null,
                        detail = resolution.reason,
                    )
                    if (remaining.isEmpty()) {
                        result.rejects += IngestReject(
                            reason = RejectReason.MISSING_CITATION,
                            detail = "claim '${finding.key}' was ruled a bad citation and no other claim remains",
                            source = claim.source,
                            sourceRecordRef = claim.sourceRecordRef,
                            name = current.name,
                        )
                        return result.finished()
                    }
                    current = current.copy(claims = remaining)
                }

                ClaimDecision.ATTACH_TO -> {
                    val to = Resolutions.parseListingId(
                        resolution.listingId ?: error("validated: attach-to needs a listingId"),
                        finding.key,
                    )
                    if (to != base) {
                        throw InvalidResolutionException(
                            "attach-to on '${finding.key}' must name the listing the venue resolves to " +
                                "($base); use same-venue to merge the two listings instead of naming $to",
                        )
                    }
                    result.reattachments += ClaimReattachment(
                        source = claim.source,
                        sourceRecordRef = claim.sourceRecordRef,
                        fromListingId = owner.id,
                        toListingId = to,
                    )
                    boundClaims[claim.key] = to
                    result.applied += AppliedResolution(finding.key, resolution.decision.wire, to, resolution.reason)
                }

                ClaimDecision.SAME_VENUE -> {
                    val keep = Resolutions.parseListingId(
                        resolution.listingId ?: error("validated: same-venue needs a listingId"),
                        finding.key,
                    )
                    val involved = setOf(owner.id, base)
                    if (keep !in involved) {
                        throw InvalidResolutionException(
                            "same-venue on '${finding.key}' must name one of the two listings in the " +
                                "conflict (${involved.joinToString()}), not $keep",
                        )
                    }
                    val retire = involved.first { it != keep }
                    moved[retire] = keep
                    result.merges += ListingMerge(keepId = keep, retireId = retire)
                    result.applied += AppliedResolution(finding.key, resolution.decision.wire, keep, resolution.reason)
                }
            }
        }

        var targetId: Uuid? = venue?.let { resolveMoved(it.id) }
        if (targetId == null) {
            for (claim in current.claims) {
                val owner = loadClaimOwner(claim) ?: continue
                targetId = resolveMoved(owner.id)
                break
            }
        }
        if (targetId != null) {
            result.writes += ListingWrite(WriteTarget.Update(targetId), current)
            return result
        }

        val atLocation = listings.findByNormalizedLocation(current.location.lat, current.location.lng)
        if (atLocation.isEmpty()) {
            result.writes += ListingWrite(WriteTarget.Insert, current)
            return result
        }
        atLocation.forEach { evidence[it.id] = it.evidence() }

        val finding = Finding.SuspectedDuplicate(
            existing = atLocation.map { evidence[it.id] ?: it.evidence() },
            candidate = current,
        )
        val resolution = resolutions.duplicate(current.venueKey)
        if (resolution == null || !resolution.decided) {
            result.findings += finding
            return result
        }
        result.consumed += finding.key
        when (resolution.decision) {
            DuplicateDecision.UNRESOLVED -> error("unreachable: undecided handled above")
            DuplicateDecision.DISTINCT -> result.writes += ListingWrite(WriteTarget.Insert, current)
            DuplicateDecision.SAME_VENUE -> {
                val keep = Resolutions.parseListingId(
                    resolution.listingId ?: error("validated: same-venue needs a listingId"),
                    finding.key,
                )
                if (atLocation.none { it.id == keep }) {
                    throw InvalidResolutionException(
                        "same-venue on '${finding.key}' must name one of the listings at that location " +
                            "(${atLocation.joinToString { it.id.toString() }}), not $keep",
                    )
                }
                result.writes += ListingWrite(WriteTarget.Update(keep), current)
            }
        }
        result.applied += AppliedResolution(
            key = finding.key,
            decision = resolution.decision.wire,
            listingId = resolution.listingId?.let { Resolutions.parseListingId(it, finding.key) },
            detail = resolution.reason,
        )
        return result
    }

    private fun RestaurantListing.evidence(): ListingEvidence =
        ListingEvidence(id = id, name = name, address = address, city = city)

    /** Merge two candidates that share a venue key or any claim identity (E4). */
    private fun mergeDuplicates(candidates: List<IngestionCandidate>): List<IngestionCandidate> =
        mergeCandidates(candidates)

    /** Follow a merge chain: a write aimed at a retired listing belongs to its keeper. */
    private fun redirectWrites(writes: List<ListingWrite>, merges: List<ListingMerge>): List<ListingWrite> {
        if (merges.isEmpty()) return writes
        val redirect = merges.associate { it.retireId to it.keepId }

        fun resolve(id: Uuid): Uuid {
            var currentId = id
            repeat(redirect.size + 1) {
                val next = redirect[currentId] ?: return currentId
                if (next == currentId) return currentId
                currentId = next
            }
            return currentId
        }

        return writes.map { write ->
            when (val target = write.target) {
                WriteTarget.Insert -> write
                is WriteTarget.Update -> {
                    val keeper = resolve(target.listingId)
                    if (keeper == target.listingId) write else write.copy(target = WriteTarget.Update(keeper))
                }
            }
        }
    }

    /**
     * One write per target. Two different source rows can resolve to the same
     * existing listing through different claims; each would otherwise update and
     * mirror it a second time, and the last row's name would win. The claims are
     * unioned instead, so the listing ends with every citation and one write.
     */
    private fun dedupeWrites(writes: List<ListingWrite>): List<ListingWrite> {
        val byTarget = linkedMapOf<String, ListingWrite>()
        for (write in writes) {
            val key = when (val target = write.target) {
                WriteTarget.Insert -> "insert:${write.candidate.venueKey}"
                is WriteTarget.Update -> "update:${target.listingId}"
            }
            val existing = byTarget[key]
            byTarget[key] = if (existing == null) {
                write
            } else {
                existing.copy(candidate = existing.candidate.mergeWith(write.candidate))
            }
        }
        return byTarget.values.toList()
    }

    private class Planned {
        val writes = mutableListOf<ListingWrite>()
        val findings = mutableListOf<Finding>()
        val rejects = mutableListOf<IngestReject>()
        val merges = mutableListOf<ListingMerge>()
        val reattachments = mutableListOf<ClaimReattachment>()
        val applied = mutableListOf<AppliedResolution>()
        val consumed = linkedSetOf<String>()

        fun finished(): Planned = this
    }
}
