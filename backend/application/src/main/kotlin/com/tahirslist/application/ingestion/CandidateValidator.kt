package com.tahirslist.application.ingestion

import com.tahirslist.domain.restaurant.CertificationTier
import com.tahirslist.domain.restaurant.VerificationBasis
import com.tahirslist.domain.restaurant.VerificationStatus

/** The result of checking one candidate before it can be written. */
sealed interface CandidateValidation {
    data class Valid(val candidate: IngestionCandidate) : CandidateValidation
    data class Invalid(val reject: IngestReject) : CandidateValidation
}

/**
 * Refuses a candidate whose trust state is not backed by its own evidence
 * (BR-11, E3/INV-1/INV-3), and refuses rows that cannot be loaded at all.
 *
 * The candidate derives status and hand-cut from its winning claim, so these
 * checks are deliberately defensive: they are the guard against a hand-edited or
 * corrupted `candidates.json`, not against the run that produced it.
 */
class CandidateValidator {

    fun validate(candidate: IngestionCandidate): CandidateValidation {
        val reject = firstProblem(candidate)
        return if (reject == null) CandidateValidation.Valid(candidate) else CandidateValidation.Invalid(reject)
    }

    private fun firstProblem(candidate: IngestionCandidate): IngestReject? {
        val source = candidate.claims.firstOrNull()?.source
        val record = candidate.claims.firstOrNull()?.sourceRecordRef

        if (candidate.name.isBlank() || candidate.address.isBlank()) {
            return reject(RejectReason.INVALID_CANDIDATE, "candidate name and address must not be blank", source, record, candidate)
        }
        if (candidate.metro.isNullOrBlank()) {
            return reject(RejectReason.MISSING_METRO, "candidate does not name its metro", source, record, candidate)
        }
        if (candidate.claims.isEmpty()) {
            return reject(RejectReason.MISSING_CITATION, "candidate has no citation claim", source, record, candidate)
        }
        candidate.claims.firstOrNull { IngestionCandidate.sourcePriority(it.source) >= 50 }?.let {
            return reject(
                RejectReason.MISSING_CITATION,
                "claim source '${it.source}' is not a recognised ingestion source",
                it.source,
                it.sourceRecordRef,
                candidate,
            )
        }

        val tierOne = candidate.claims.any { it.tier == CertificationTier.TIER_1 }
        if (candidate.verificationStatus == VerificationStatus.VERIFIED) {
            if (candidate.verificationBasis != VerificationBasis.CERTIFIER_REGISTRY || !tierOne) {
                return reject(
                    RejectReason.INVALID_TRUST_STATE,
                    "VERIFIED requires a Tier-1 certifier-registry claim",
                    source,
                    record,
                    candidate,
                )
            }
            if (candidate.provenance.isCertifierRegistry.not()) {
                return reject(
                    RejectReason.INVALID_TRUST_STATE,
                    "VERIFIED requires a certifier-registry provenance",
                    source,
                    record,
                    candidate,
                )
            }
        }
        if (candidate.isHandCut == true) {
            val declaresHandCut = candidate.claims.any {
                it.tier == CertificationTier.TIER_1 && it.declaredHandCut.isHandCut
            }
            if (!declaresHandCut) {
                return reject(
                    RejectReason.INVALID_TRUST_STATE,
                    "hand-cut TRUE requires a Tier-1 claim that declares hand-cut",
                    source,
                    record,
                    candidate,
                )
            }
        }
        if (candidate.verificationStatus == VerificationStatus.UNVERIFIED && candidate.verificationBasis != null) {
            return reject(
                RejectReason.INVALID_TRUST_STATE,
                "an UNVERIFIED candidate must not carry a verification basis",
                source,
                record,
                candidate,
            )
        }
        return null
    }

    private fun reject(
        reason: RejectReason,
        detail: String,
        source: String?,
        record: String?,
        candidate: IngestionCandidate,
    ) = IngestReject(
        reason = reason,
        detail = detail,
        source = source,
        sourceRecordRef = record,
        name = candidate.name,
    )
}
