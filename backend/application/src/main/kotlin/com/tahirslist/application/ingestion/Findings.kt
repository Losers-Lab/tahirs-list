package com.tahirslist.application.ingestion

import com.tahirslist.domain.restaurant.CertificationClaim
import kotlin.uuid.Uuid

/**
 * Enough of an existing listing to decide a finding without opening the
 * database: the curator sees the id, name and address the decision applies to.
 */
data class ListingEvidence(
    val id: Uuid,
    val name: String,
    val address: String,
    val city: String? = null,
) {
    fun label(): String = buildString {
        append('"').append(name).append('"')
        if (address.isNotBlank()) append(" — ").append(address)
        if (!city.isNullOrBlank()) append(", ").append(city)
        append(" [").append(id).append(']')
    }
}

/**
 * Something the load cannot decide by itself. A finding is *blocking*: the
 * candidate is not written until a curator records a decision for it (UC-03).
 */
sealed interface Finding {
    /** Stable identity of the finding, used as the decision-file key. */
    val key: String

    val candidate: IngestionCandidate

    /** One-line question for the interactive prompt. */
    fun question(): String

    /** The evidence a decision needs, in plain language. */
    fun evidence(): List<String>

    /**
     * A claim already belongs to a listing, while the venue resolves to a
     * different one. Either the two listings are the same restaurant, or the
     * citation is wrong, or the claim sits on the wrong listing.
     */
    data class ClaimConflict(
        val claim: CertificationClaim,
        val claimedBy: ListingEvidence,
        /** The listing this venue resolves to; never the same row as [claimedBy]. */
        val resolvedTo: ListingEvidence,
        override val candidate: IngestionCandidate,
    ) : Finding {
        override val key: String = "claim:${claim.source}:${claim.sourceRecordRef}"

        override fun question(): String =
            "Claim ${claim.source}::${claim.sourceRecordRef} already belongs to ${claimedBy.name}, " +
                "but this venue resolves to ${resolvedTo.name}. What is true?"

        override fun evidence(): List<String> = buildList {
            add("claim: ${claim.source}::${claim.sourceRecordRef} (${claim.sourceUrl})")
            add("claim currently on: ${claimedBy.label()}")
            add("venue resolves to: ${resolvedTo.label()}")
            add("candidate:          ${candidate.name} — ${candidate.address}")
        }
    }

    /** The venue matches an existing listing only by location, never by identity. */
    data class SuspectedDuplicate(
        val existing: List<ListingEvidence>,
        override val candidate: IngestionCandidate,
    ) : Finding {
        override val key: String = "duplicate:${candidate.venueKey}"

        override fun question(): String =
            "This venue sits at the same location as ${existing.joinToString { it.name }}, " +
                "but its identity does not match. Is it the same restaurant?"

        override fun evidence(): List<String> = buildList {
            add("candidate: ${candidate.name} — ${candidate.address} (${candidate.location.lat}, ${candidate.location.lng})")
            existing.forEach { add("same place: ${it.label()}") }
        }
    }
}

/** A decision already applied to the reviewed diff, kept for the audit line. */
data class AppliedResolution(
    val key: String,
    val decision: String,
    val listingId: Uuid?,
    val detail: String,
)

/**
 * A physical merge of two listings, applied inside the batch transaction:
 * claims, child rows and reviews move to [keepId], the mirror row is removed and
 * [retireId] is deleted. The decision is explicit and reasoned; there is no
 * automatic de-duplication anywhere else.
 */
data class ListingMerge(
    val keepId: Uuid,
    val retireId: Uuid,
) {
    init {
        require(keepId != retireId) { "a listing cannot be merged into itself" }
    }
}

/** Explicit re-pointing of one claim, only ever from an `attach-to` decision. */
data class ClaimReattachment(
    val source: String,
    val sourceRecordRef: String,
    val fromListingId: Uuid,
    val toListingId: Uuid,
) {
    init {
        require(fromListingId != toListingId) { "a claim cannot be re-pointed to its current listing" }
    }
}

/** The decision file names a different run than the batch being loaded. */
class ResolutionsRunMismatchException(
    val batchRunId: String,
    val resolutionsRunId: String,
) : RuntimeException(
    "resolution file is for run '$resolutionsRunId' but this batch is run '$batchRunId'",
)

/** A decision no longer matches any finding, so applying it would be guesswork. */
class StaleResolutionException(
    val stale: List<String>,
) : RuntimeException(
    "decision(s) no longer match a finding in this run: ${stale.joinToString()}. " +
        "Re-run the preview and decide again; a stale decision is never applied.",
)

/** A decision is malformed or does not fit the finding it names. */
class InvalidResolutionException(message: String) : RuntimeException(message)

/**
 * A merge or claim re-pointing no longer matches the catalogue state seen at
 * preview time. The batch transaction rolls back rather than guessing.
 */
class BatchPlanException(message: String) : RuntimeException(message)
