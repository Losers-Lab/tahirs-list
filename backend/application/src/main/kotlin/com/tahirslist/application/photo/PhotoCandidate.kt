package com.tahirslist.application.photo

import com.tahirslist.domain.restaurant.CoverPhoto
import com.tahirslist.domain.restaurant.PhotoLicence
import com.tahirslist.domain.restaurant.PhotoSourceKind
import com.tahirslist.domain.restaurant.PhotoStorage
import java.time.Instant

/**
 * One photo a source is offering for a listing (SC-231, UC-03).
 *
 * It is a *candidate*, not a photo: the bytes have not been fetched and the
 * licence has not been judged. [AttachCoverPhoto] turns a candidate into a
 * [CoverPhoto], or refuses it and says why.
 *
 * [fetchUrl] may carry a provider key (Places media URLs do). It is transient and
 * must never be stored: [sourceUrl] is the human-facing credit link, and only
 * that reaches the database.
 */
data class PhotoCandidate(
    val sourceKind: PhotoSourceKind,
    /** Where the bytes come from. Blank means the candidate is unusable, and why is reported. */
    val fetchUrl: String? = null,
    /** The credit that must appear with the photo. */
    val attribution: String,
    val licence: PhotoLicence? = null,
    val author: String? = null,
    /** A public link a reader can follow to the source. */
    val sourceUrl: String? = null,
    val placeId: String? = null,
    val photoName: String? = null,
    /**
     * Why the source itself refused this candidate (a name/address near-miss, a
     * licence it cannot state). Set instead of [fetchUrl] when the source found
     * something but will not offer it; [AttachCoverPhoto] reports the reason
     * rather than calling it "no photo".
     */
    val reason: String? = null,
) {
    init {
        require(attribution.isNotBlank()) { "A candidate without credit cannot be shown." }
    }

    val storageClass: PhotoStorage
        get() = if (sourceKind.isPermanent) PhotoStorage.PERMANENT else PhotoStorage.CACHE

    /** The persisted photo. For a cache, [expiresAt] is required by [CoverPhoto]. */
    fun toCoverPhoto(contentHash: String? = null, expiresAt: Instant? = null): CoverPhoto = CoverPhoto(
        sourceKind = sourceKind,
        storageClass = storageClass,
        attribution = attribution,
        licence = licence,
        author = author,
        sourceUrl = sourceUrl,
        placeId = placeId,
        photoName = photoName,
        expiresAt = expiresAt,
        contentHash = contentHash,
    )
}
