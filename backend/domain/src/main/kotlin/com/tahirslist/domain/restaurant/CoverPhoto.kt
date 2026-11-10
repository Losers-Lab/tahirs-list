package com.tahirslist.domain.restaurant

import java.time.Instant

/**
 * One listing's cover photo: where it came from, how it may be stored, and who
 * to credit (SC-231).
 *
 * The rules are exhaustiveness, not taste:
 *
 *  - a Google Places photo is always a **cache** with an expiry — the terms
 *    allow display and limited caching, not a permanent copy;
 *  - an owner photo is permanent and has no licence to record, because there is
 *    no third party licensing it to us;
 *  - a free-licensed photo is permanent and carries the licence that permits
 *    reuse, plus the credit that licence requires.
 *
 * Everything else is refused at construction, so an illegal photo cannot reach
 * persistence even if an adapter gets it wrong.
 */
data class CoverPhoto(
    val sourceKind: PhotoSourceKind,
    val storageClass: PhotoStorage,
    val attribution: String,
    val licence: PhotoLicence? = null,
    val author: String? = null,
    val sourceUrl: String? = null,
    val placeId: String? = null,
    val photoName: String? = null,
    val expiresAt: Instant? = null,
    val contentHash: String? = null,
    val width: Int? = null,
    val height: Int? = null,
) {

    init {
        require(attribution.isNotBlank()) { "A cover photo must carry the credit that shows with it." }

        if (sourceKind.isPermanent) {
            require(storageClass == PhotoStorage.PERMANENT) {
                "$sourceKind may be stored permanently; a cache would throw the photo away."
            }
        } else {
            require(storageClass == PhotoStorage.CACHE) {
                "$sourceKind may only be cached, never stored permanently."
            }
            require(expiresAt != null) { "$sourceKind needs an expiry; a cache with none could be served forever." }
        }

        if (sourceKind.requiresLicence) {
            require(licence != null) { "$sourceKind needs the licence that permits reuse." }
        } else {
            require(licence == null) { "$sourceKind does not carry a third-party licence." }
        }

        if (licence?.requiresAttribution == true) {
            require(!author.isNullOrBlank()) { "$licence requires the author to be credited." }
        }

        if (sourceKind == PhotoSourceKind.GOOGLE_PLACES) {
            require(!placeId.isNullOrBlank()) { "A Google Places photo must record the place id that found it." }
        }
    }

    /** Whether the bytes may be served at [now]. A permanent photo never expires. */
    fun isServableAt(now: Instant): Boolean =
        storageClass == PhotoStorage.PERMANENT || expiresAt?.isAfter(now) == true
}
