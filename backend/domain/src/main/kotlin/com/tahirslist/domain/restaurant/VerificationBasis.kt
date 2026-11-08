package com.tahirslist.domain.restaurant

/** Why a listing is `VERIFIED`. */
enum class VerificationBasis {
    /** A human Verification Committee approved an owner's certification review. */
    COMMITTEE_REVIEW,

    /** A recognized Tier-1 certifier registry entry backs the listing with a claim. */
    CERTIFIER_REGISTRY,
    ;
}
