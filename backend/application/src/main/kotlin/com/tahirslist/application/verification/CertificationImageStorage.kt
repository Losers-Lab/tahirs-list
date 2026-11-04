package com.tahirslist.application.verification

import kotlin.uuid.Uuid

/**
 * Object-storage seam for certification images (hexagonal "out" port), mirroring
 * the [com.tahirslist.application.image.ImagePort] shape for hero images but
 * for the verification vertical.
 *
 * Provider-agnostic by design: implemented by the S3/MinIO adapter now (same
 * object store as hero images) and swappable; bucket/prefix layout stays the
 * adapter's concern. What the application layer *does* own is the returned
 * **key**: an opaque handle to one stored object, used as the immutable identity
 * of the evidence a review was approved against.
 *
 * [save] stores the cert image for a listing and returns the key of the stored
 * object (sc-46 writes; the image is submitted to the AI seam as bytes in-memory,
 * then retained as evidence). The key is recorded on the review, so [load] can
 * later read back *exactly* the artifact the committee saw — even if the owner
 * saves another certificate afterwards.
 */
interface CertificationImageStorage {
    /**
     * Store [bytes] as the certification evidence for [listingId] and return the
     * key of the stored object.
     *
     * The key is the immutable identity of this evidence: the review persists it,
     * and the public verification display resolves it back through [load]. Keys
     * are opaque to callers and unique per save (re-claims never overwrite earlier
     * evidence).
     */
    suspend fun save(listingId: Uuid, contentType: String, bytes: ByteArray): String

    /**
     * Read exactly the object addressed by [key] (as returned by [save]), or null
     * when no such object exists.
     *
     * This is the only read surface the public verification display may use: it
     * resolves the key recorded on the approved review, so an owner cannot swap
     * the displayed certificate after approval or expose unreviewed evidence.
     */
    suspend fun load(key: String): StoredCertificationImage?

    /**
     * LEGACY / OPERATIONAL ONLY — the most recently saved cert image for a
     * listing.
     *
     * **Not for the public verification display.** "Latest" means whatever was
     * uploaded last, regardless of whether the listing is verified or which
     * certificate the committee approved: serving it publicly would expose
     * pending/in-review evidence and would let an owner replace the displayed
     * certificate without re-review. Public reads must resolve the review's
     * recorded certificate key via [load].
     */
    suspend fun loadLatest(listingId: Uuid): StoredCertificationImage?

    /** The certification image as read back from storage. */
    class StoredCertificationImage(
        val contentType: String,
        val bytes: ByteArray,
    )
}
