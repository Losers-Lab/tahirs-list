package com.tahirslist.domain.verification

import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * The human (Verification Committee) decision recorded when a review is
 * finalized. [VerificationOutcome.REVERSED] is recorded when a finalized
 * APPROVED/DENIED review is later reversed.
 */
class ReviewDecision(
    val outcome: VerificationOutcome,
    val decidedBy: Uuid,
    val reason: String?,
    val decidedAt: Instant,
)
