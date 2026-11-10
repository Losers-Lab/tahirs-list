package com.tahirslist.application.listing

import com.tahirslist.application.audit.AuditRecorder
import com.tahirslist.application.favorite.ListingNotFoundException
import com.tahirslist.application.image.ImagePort
import com.tahirslist.application.image.IngestHeroImage
import com.tahirslist.application.image.StoredImage
import com.tahirslist.application.verification.NotListingOwnerException
import kotlin.uuid.Uuid

/**
 * Add / replace a listing's hero image (sc-53, sc-205). An authenticated actor —
 * the listing's owner **or an admin** (D-2, AC-6) — uploads an image; the bytes
 * are stored via the existing [ImagePort] (S3/MinIO in production) as the FULL
 * original plus every sc-183 thumbnail width.
 *
 * Flow and its failure semantics ("what happens when it fails"):
 *
 *  1. The listing must already exist ([ListingNotFoundException] → 404).
 *  2. [PhotoWritePolicy] must allow the actor ([NotListingOwnerException] → 403).
 *     The actor is the verified JWT identity; ownership is never taken from the
 *     request.
 *  3. The bytes must be a decodable image; [IngestHeroImage] derives the stored
 *     media type from the bytes (never from the upload's `Content-Type` header),
 *     resizes and stores every thumbnail width FIRST and rejects an undecodable
 *     upload with an [IllegalArgumentException] (→ 400) before anything is
 *     persisted — a half-written row is impossible.
 *  4. The photo version is stamped LAST, so every failure above leaves the
 *     published photo and its cache key untouched ([RestaurantListingRepository.bumpImageVersion]).
 *  5. When the actor is not the owner (an admin override), [AuditRecorder]
 *     records the attribution, because the write is otherwise indistinguishable
 *     from the owner's own.
 *
 * This is a **last-write-wins replace**: the listing has exactly ONE hero
 * image, so uploading again overwrites the previously stored variants.
 * Gallery/multi-image management is explicitly deferred.
 */
class AddListingImage(
    private val listings: RestaurantListingRepository,
    private val images: ImagePort,
    private val audit: AuditRecorder,
) {

    private val ingest: IngestHeroImage = IngestHeroImage(images)

    suspend fun execute(listingId: Uuid, actor: Actor, original: StoredImage) {
        val listing = listings.findById(listingId)
            ?: throw ListingNotFoundException(listingId)

        if (!PhotoWritePolicy.mayManage(actor, listing)) {
            throw NotListingOwnerException(listingId, actor.accountId)
        }

        val previousVersion = listing.imageVersion
        ingest.ingest(listingId, original)

        // The stamp is the commit point of the write: if the listing vanished
        // between the load and here, fail loudly after the (harmless) object
        // writes rather than report a success nobody can see.
        val newVersion = listings.bumpImageVersion(listingId)
            ?: throw ListingNotFoundException(listingId)

        if (actor.accountId != listing.ownerId) {
            audit.imageSet(listingId, actor, previousVersion, newVersion)
        }
    }
}
