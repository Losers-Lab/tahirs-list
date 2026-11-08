package com.tahirslist.application.ingestion

import com.tahirslist.application.listing.RestaurantListingRepository
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
 */
data class CatalogUpdateReview(
    val runId: String,
    val writes: List<ListingWrite>,
    val held: List<HeldCandidate> = emptyList(),
    val rejects: List<IngestReject> = emptyList(),
    val brandsToCreate: Set<String> = emptySet(),
) {
    val inserted: Int get() = writes.count { it.target is WriteTarget.Insert }
    val upgraded: Int get() = writes.count { it.target is WriteTarget.Update }

    fun diffSummary(): String = buildString {
        appendLine("Ingestion review for run $runId")
        appendLine("  insert:   $inserted")
        appendLine("  upgrade:  $upgraded")
        appendLine("  held:     ${held.size}")
        appendLine("  rejected: ${rejects.size}")
        if (brandsToCreate.isNotEmpty()) appendLine("  new brands: ${brandsToCreate.joinToString()}")
        if (held.isNotEmpty()) {
            appendLine("Suspected duplicates (not written):")
            held.forEach {
                appendLine("  - ${it.candidate.name} (${it.candidate.claims.first().source}): ${it.existingListingIds.joinToString()}")
            }
        }
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
    val held: Int = 0,
    val rejected: Int = 0,
    val claimsWritten: Int = 0,
) {
    override fun toString(): String =
        "inserted=$inserted upgraded=$upgraded held=$held rejected=$rejected claims=$claimsWritten"
}

/**
 * UC-03: turn a reviewed candidate batch into catalogue rows.
 *
 * `preview` only reads: it validates, matches and resolves brands. `execute`
 * hands the resolved writes to the repository's single transaction, so brands,
 * listings, claims and the search mirror commit together or not at all (BR-13).
 */
class LoadIngestionBatch(
    private val listings: RestaurantListingRepository,
    private val brands: BrandRepository,
    private val validator: CandidateValidator = CandidateValidator(),
) {

    suspend fun preview(batch: CandidateBatch): CatalogUpdateReview {
        val rejects = mutableListOf<IngestReject>()
        val held = mutableListOf<HeldCandidate>()
        val writes = mutableListOf<ListingWrite>()
        val brandsToCreate = linkedSetOf<String>()

        for (candidate in mergeDuplicates(batch.candidates)) {
            when (val validation = validator.validate(candidate)) {
                is CandidateValidation.Invalid -> rejects += validation.reject
                is CandidateValidation.Valid -> {
                    when (val resolution = resolve(candidate)) {
                        is Resolution.Matched -> writes += ListingWrite(WriteTarget.Update(resolution.listingId), candidate)
                        is Resolution.Held -> held += HeldCandidate(candidate, resolution.listingIds)
                        is Resolution.Conflict -> rejects += IngestReject(
                            reason = RejectReason.CLAIM_CONFLICT,
                            detail = "claim '${candidate.winningClaim.key}' already points at listing " +
                                "${resolution.claimListingId}, but venue ${candidate.venueKey} matches " +
                                "listing ${resolution.venueListingId}",
                            source = candidate.winningClaim.source,
                            sourceRecordRef = candidate.winningClaim.sourceRecordRef,
                            name = candidate.name,
                        )
                        is Resolution.New -> writes += ListingWrite(WriteTarget.Insert, candidate)
                    }
                    if (brands.resolve(candidate.name) == null) brandsToCreate += candidate.name
                }
            }
        }

        return CatalogUpdateReview(
            runId = batch.runId,
            writes = writes,
            held = held,
            rejects = rejects + batch.rejects,
            brandsToCreate = brandsToCreate,
        )
    }

    suspend fun execute(review: CatalogUpdateReview): BatchLoadResult {
        val applied = listings.applyBatch(review.writes)
        return applied.copy(
            held = review.held.size,
            rejected = review.rejects.size,
        )
    }

    private suspend fun resolve(candidate: IngestionCandidate): Resolution {
        val claimListing = candidate.claims.firstNotNullOfOrNull {
            listings.findClaim(it.source, it.sourceRecordRef)
        }
        val venueListing = listings.findByVenueKey(candidate.venueKey)

        if (claimListing != null && venueListing != null && claimListing.id != venueListing.id) {
            return Resolution.Conflict(claimListing.id, venueListing.id)
        }
        if (claimListing != null) return Resolution.Matched(claimListing.id)
        if (venueListing != null) return Resolution.Matched(venueListing.id)

        val atLocation = listings.findByNormalizedLocation(candidate.location.lat, candidate.location.lng)
        return if (atLocation.isEmpty()) {
            Resolution.New
        } else {
            Resolution.Held(atLocation.map { it.id })
        }
    }

    /**
     * Two candidates that share a venue key or any claim identity are one venue
     * (E4). Merging before matching means the batch produces one write, not two
     * that race each other inside the transaction.
     */
    private fun mergeDuplicates(candidates: List<IngestionCandidate>): List<IngestionCandidate> =
        mergeCandidates(candidates)

    private sealed interface Resolution {
        data class Matched(val listingId: Uuid) : Resolution
        data class Held(val listingIds: List<Uuid>) : Resolution
        data class Conflict(val claimListingId: Uuid, val venueListingId: Uuid) : Resolution
        data object New : Resolution
    }
}
