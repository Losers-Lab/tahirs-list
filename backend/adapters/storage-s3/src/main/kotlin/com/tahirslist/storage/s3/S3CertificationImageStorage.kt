package com.tahirslist.storage.s3

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.headBucket
import aws.sdk.kotlin.services.s3.listObjectsV2
import aws.sdk.kotlin.services.s3.model.GetObjectRequest
import aws.sdk.kotlin.services.s3.model.S3Exception
import aws.sdk.kotlin.services.s3.putObject
import aws.smithy.kotlin.runtime.content.ByteStream
import aws.smithy.kotlin.runtime.content.toByteArray
import com.tahirslist.application.verification.CertificationImageStorage
import kotlin.uuid.Uuid
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * S3-compatible [CertificationImageStorage] (MinIO now, Cloudflare R2 later) —
 * the certification image archive for the owner-claim vertical (sc-46), using
 * the suspend **AWS SDK for Kotlin**. Shares the same S3 client/bucket as
 * [S3ImagePort] but a distinct key namespace, so a cert image can never be
 * mistaken for a hero variant.
 *
 * Storage layout (bucket owned like the hero adapter):
 *   `certifications/{listingId}/{uuid}`  ->  object bytes  (content-type on object)
 *
 * Each upload gets a fresh UUID so re-claims never silently overwrite earlier
 * evidence. The generated key is returned from [save] and recorded on the review
 * (opaque to the application layer, but persisted there): [load] resolves that
 * exact object, so the public display serves the artifact the committee reviewed
 * rather than the most recent upload. Like [S3ImagePort], the bucket is
 * provisioned out of band and only verified here (no runtime `CreateBucket`).
 */
class S3CertificationImageStorage(
    private val s3: S3Client,
    private val bucket: String,
) : CertificationImageStorage {

    private val verifyMutex = Mutex()

    @Volatile
    private var bucketVerified = false

    override suspend fun save(listingId: Uuid, contentType: String, bytes: ByteArray): String {
        requireBucket()
        val objectKey = "certifications/$listingId/${Uuid.random()}"
        val mediaType = contentType
        s3.putObject {
            bucket = this@S3CertificationImageStorage.bucket
            key = objectKey
            this.contentType = mediaType
            body = ByteStream.fromBytes(bytes)
        }
        return objectKey
    }

    override suspend fun load(key: String): CertificationImageStorage.StoredCertificationImage? {
        requireBucket()
        return readObject(key)
    }

    override suspend fun loadLatest(listingId: Uuid): CertificationImageStorage.StoredCertificationImage? {
        requireBucket()
        val keyPrefix = "certifications/$listingId/"
        val objects = s3.listObjectsV2 {
            bucket = this@S3CertificationImageStorage.bucket
            prefix = keyPrefix
        }.contents.orEmpty()
        // Keys carry no timestamps; history keeps one object per submission, so
        // "latest" is the most recently modified of the listing's objects.
        val latest = objects
            .mapNotNull { obj -> obj.lastModified?.let { modified -> obj to modified } }
            .maxByOrNull { it.second }
            ?.first
            ?: return null
        val latestKey = latest.key ?: return null
        return readObject(latestKey)
    }

    /** Read [key] back, or null when the object does not exist (404). */
    private suspend fun readObject(key: String): CertificationImageStorage.StoredCertificationImage? {
        val request = GetObjectRequest {
            bucket = this@S3CertificationImageStorage.bucket
            this.key = key
        }
        return try {
            s3.getObject(request) { response ->
                CertificationImageStorage.StoredCertificationImage(
                    contentType = response.contentType ?: "application/octet-stream",
                    bytes = response.body?.toByteArray() ?: ByteArray(0),
                )
            }
        } catch (e: S3Exception) {
            if (e.isNotFound()) null else throw e // no object at this key
        }
    }

    /**
     * Verify the bucket exists once, then trust it for the process lifetime —
     * the same least-privilege rule as [S3ImagePort]: provisioning is a
     * deployment step, so a typo'd bucket fails loudly instead of being
     * silently created by the app's (otherwise narrower) credentials.
     */
    private suspend fun requireBucket() {
        if (bucketVerified) return
        verifyMutex.withLock {
            if (bucketVerified) return
            try {
                s3.headBucket { bucket = this@S3CertificationImageStorage.bucket }
            } catch (e: S3Exception) {
                if (e.isNotFound()) {
                    throw IllegalStateException(
                        "S3 bucket '$bucket' does not exist. Provision it before using certification storage " +
                            "(the compose stack runs minio-init; production is an ops step).",
                        e,
                    )
                }
                throw e
            }
            bucketVerified = true
        }
    }
}
