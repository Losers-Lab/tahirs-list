package com.tahirslist.application.ingestion

import com.tahirslist.domain.restaurant.CertificationClaim
import com.tahirslist.domain.restaurant.CertificationTier
import com.tahirslist.domain.restaurant.CrossContamination
import com.tahirslist.domain.restaurant.LatLng
import com.tahirslist.domain.restaurant.Provenance
import com.tahirslist.domain.restaurant.VerificationBasis
import com.tahirslist.domain.restaurant.VerificationStatus

/**
 * Merge candidates that name the same venue (same venue key) or share any
 * claim identity. Used by the run, the manual compile and the load preview so
 * all three produce the same one-write-per-venue plan (E4).
 */
fun mergeCandidates(candidates: List<IngestionCandidate>): List<IngestionCandidate> {
    val merged = mutableListOf<IngestionCandidate>()
    for (candidate in candidates) {
        val index = merged.indexOfFirst { existing ->
            existing.venueKey == candidate.venueKey ||
                existing.claims.any { left -> candidate.claims.any { right -> left.key == right.key } }
        }
        if (index >= 0) merged[index] = merged[index].mergeWith(candidate) else merged += candidate
    }
    return merged
}

/** Why a fetched or curated row never became a candidate. */
enum class RejectReason {
    /** The source could not be fetched (network, HTTP failure, provider outage). */
    SOURCE_UNAVAILABLE,

    /** The source answered but its body could not be parsed. */
    SOURCE_PARSE_FAILED,

    /** The source refused access (robots, allow-list, 403, oversized body). */
    SOURCE_BLOCKED,

    /** A curated manual row failed its schema validation. */
    INVALID_MANUAL_ENTRY,

    /** A candidate is structurally unusable (blank name/address). */
    INVALID_CANDIDATE,

    /** A candidate has no citation claim at all. */
    MISSING_CITATION,

    /** A candidate claims VERIFIED without a Tier-1 claim, or hand-cut TRUE without a declaration. */
    INVALID_TRUST_STATE,

    /** The geocoder returned no place for the address. */
    NO_GEOCODER_MATCH,

    /** The geocoder returned a place that does not match the venue. */
    AMBIGUOUS_GEOCODER_MATCH,

    /** The geocoder returned a place in a different region than the source row. */
    WRONG_REGION,

    /** A candidate does not name its metro (needed for the run and the report). */
    MISSING_METRO,

    /** A location-only match was found; the row is held, not inserted. */
    SUSPECTED_DUPLICATE,

    /** A claim already points at a different listing. */
    CLAIM_CONFLICT,
}

/** One accounted-for row that will not be loaded, with a human-readable reason. */
data class IngestReject(
    val reason: RejectReason,
    val detail: String,
    val source: String? = null,
    val sourceRecordRef: String? = null,
    val name: String? = null,
) {
    init {
        require(detail.isNotBlank()) { "a reject must explain itself" }
    }
}

/**
 * One reconciled venue: the confirmed location, the deterministic venue key and
 * every citation observed for it.
 *
 * Verification and hand-cut are *derived* from the winning claim rather than
 * stored independently, so a candidate can never claim a trust state its
 * evidence does not support (D-11, BR-6a/BR-11):
 *
 *  - winning claim is Tier-1 -> `VERIFIED` + `CERTIFIER_REGISTRY`, index-qualified;
 *  - otherwise -> `UNVERIFIED` + no basis, and the explicit cross-contamination
 *    value the curated row carried (default `UNCERTAIN`);
 *  - hand-cut is TRUE only when the winning Tier-1 claim declares hand-cut, and
 *    FALSE for every other Tier-1 declaration (declared-not or silent); NULL
 *    when no Tier-1 claim exists.
 */
data class IngestionCandidate(
    val name: String,
    val address: String,
    val city: String? = null,
    val region: String? = null,
    val postal: String? = null,
    val country: String? = null,
    val location: LatLng,
    val venueKey: String,
    val claims: List<CertificationClaim>,
    val metro: String? = null,
    /** Curated manual rows carry an explicit value; ignored for Tier-1 rows. */
    val manualCrossContamination: CrossContamination = CrossContamination.DEFAULT,
) {
    init {
        require(name.isNotBlank()) { "candidate name must not be blank" }
        require(address.isNotBlank()) { "candidate address must not be blank" }
        require(claims.isNotEmpty()) { "a candidate must carry at least one citation claim" }
        require(VENUE_KEY.matches(venueKey)) { "candidate venueKey must be a lowercase SHA-256 hex" }
    }

    /** The claim that decides the row's trust outcome: highest tier, then source priority. */
    val winningClaim: CertificationClaim
        get() = claims.minWith(CLAIM_ORDER)

    val verificationStatus: VerificationStatus
        get() = if (tier == CertificationTier.TIER_1) VerificationStatus.VERIFIED else VerificationStatus.UNVERIFIED

    val verificationBasis: VerificationBasis?
        get() = if (tier == CertificationTier.TIER_1) VerificationBasis.CERTIFIER_REGISTRY else null

    /** TRUE/FALSE for a Tier-1 row per its declaration; NULL for a manual row. */
    val isHandCut: Boolean?
        get() = if (tier == CertificationTier.TIER_1) winningClaim.declaredHandCut.isHandCut else null

    val provenance: Provenance
        get() = provenanceFor(winningClaim.source)

    val crossContamination: CrossContamination
        get() = if (tier == CertificationTier.TIER_1) {
            CrossContamination.NO_CROSS_CONTAMINATION
        } else {
            manualCrossContamination
        }

    val tier: CertificationTier get() = winningClaim.tier

    /** One candidate per real venue: union the claims and keep the strongest evidence. */
    fun mergeWith(other: IngestionCandidate): IngestionCandidate {
        val winner = if (CLAIM_ORDER.compare(other.winningClaim, winningClaim) < 0) other else this
        val mergedClaims = (claims + other.claims)
            .associateBy { it.key }
            .values
            .sortedWith(CLAIM_ORDER)
        return winner.copy(
            claims = mergedClaims,
            metro = metro ?: other.metro,
            manualCrossContamination = strongerCrossContamination(
                manualCrossContamination,
                other.manualCrossContamination,
            ),
        )
    }

    companion object {
        private val VENUE_KEY = Regex("^[0-9a-f]{64}$")

        /**
         * Deterministic source priority for ties: the order the design lists the
         * certifiers. A manual claim never wins a tie against a registry claim.
         */
        fun sourcePriority(source: String): Int = when (source) {
            "hms" -> 0
            "hfsaa" -> 1
            "hma" -> 2
            "manual-zabihah" -> 9
            else -> 50
        }

        private val CLAIM_ORDER = compareBy<CertificationClaim> { it.tier.rank }
            .thenBy { sourcePriority(it.source) }
            .thenBy { it.sourceRecordRef }

        fun provenanceFor(source: String): Provenance = when (source) {
            "hms" -> Provenance.CERTIFIER_HMS
            "hfsaa" -> Provenance.CERTIFIER_HFSAA
            "hma" -> Provenance.CERTIFIER_HMA
            "manual-zabihah" -> Provenance.MANUAL_ZABIHAH
            else -> throw IllegalArgumentException("unknown ingestion source: '$source'")
        }

        private fun strongerCrossContamination(
            left: CrossContamination,
            right: CrossContamination,
        ): CrossContamination = when {
            left == CrossContamination.NO_CROSS_CONTAMINATION -> left
            right == CrossContamination.NO_CROSS_CONTAMINATION -> right
            left == CrossContamination.PRESENT -> left
            right == CrossContamination.PRESENT -> right
            else -> CrossContamination.DEFAULT
        }
    }
}
