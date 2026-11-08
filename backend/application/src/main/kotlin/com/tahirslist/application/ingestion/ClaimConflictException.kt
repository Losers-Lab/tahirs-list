package com.tahirslist.application.ingestion

import kotlin.uuid.Uuid

/**
 * A claim `(source, source_record_ref)` already belongs to a different listing.
 * The batch aborts rather than re-pointing the audit trail (BR-21, E10).
 */
class ClaimConflictException(
    val claimKey: String,
    val existingListingId: Uuid,
    val attemptedListingId: Uuid,
) : RuntimeException(
    "claim '$claimKey' already points at listing $existingListingId; " +
        "refusing to attach it to $attemptedListingId",
)
