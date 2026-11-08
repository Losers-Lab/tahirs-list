package com.tahirslist.application.ingestion

import com.tahirslist.domain.restaurant.CertificationTier
import com.tahirslist.domain.restaurant.CrossContamination
import com.tahirslist.domain.restaurant.Provenance
import com.tahirslist.domain.restaurant.VerificationStatus

/**
 * Ingestion upgrades are **monotonic** (D-10): a batch may raise a listing's
 * trust, never lower it. A later Tier-3 manual row that matches an already
 * verified venue must not flip it to UNVERIFIED, clear its hand-cut value, or
 * drop it out of the search index — the reviewer would see an "upgrade" with no
 * warning while the venue silently left the public catalogue.
 *
 * Committee verification and certifier-registry verification are both
 * `VERIFIED`; neither is demoted by an ingestion batch.
 */
object TrustMerge {

    fun verificationStatus(
        existing: VerificationStatus,
        incoming: VerificationStatus,
    ): VerificationStatus = when {
        existing == VerificationStatus.VERIFIED || incoming == VerificationStatus.VERIFIED ->
            VerificationStatus.VERIFIED
        else -> VerificationStatus.UNVERIFIED
    }

    /** TRUE wins over FALSE wins over NULL; an unknown incoming value never erases a known one. */
    fun handCut(existing: Boolean?, incoming: Boolean?): Boolean? = when {
        existing == true || incoming == true -> true
        existing == false || incoming == false -> false
        else -> null
    }

    /** The index gate is the strongest value: once qualified, an update cannot disqualify. */
    fun crossContamination(
        existing: CrossContamination,
        incoming: CrossContamination,
    ): CrossContamination = when {
        existing == CrossContamination.NO_CROSS_CONTAMINATION ||
            incoming == CrossContamination.NO_CROSS_CONTAMINATION ->
            CrossContamination.NO_CROSS_CONTAMINATION
        existing == CrossContamination.PRESENT || incoming == CrossContamination.PRESENT ->
            CrossContamination.PRESENT
        else -> CrossContamination.UNCERTAIN
    }

    /** A certifier-registry provenance is never replaced by a manual or seed stamp. */
    fun provenance(existing: Provenance?, incoming: Provenance): Provenance =
        if (existing?.isCertifierRegistry == true) existing else incoming

    /** The winning tier of the merged row, for reporting only. */
    fun tier(existing: CertificationTier?, incoming: CertificationTier): CertificationTier =
        if (existing != null && existing.rank < incoming.rank) existing else incoming
}
