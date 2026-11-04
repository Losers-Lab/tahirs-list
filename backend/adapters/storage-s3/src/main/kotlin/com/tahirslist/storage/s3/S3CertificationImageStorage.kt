package com.tahirslist.storage.s3

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.createBucket
import aws.sdk.kotlin.services.s3.headBucket
import aws.sdk.kotlin.services.s3.listObjectsV2
import aws.sdk.kotlin.services.s3.model.GetObjectRequest
import aws.sdk.kotlin.services.s3.model.S3Exception
import aws.sdk.kotlin.services.s3.putObject
import aws.smithy.kotlin.runtime.content.ByteStream
import aws.smithy.kotlin.runtime.content.toByteArray
import com.tahirslist.application.verification.CertificationImageStorage
import kotlin.uuid.Uuid

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
 * Keys live entirely inside this adapter (never leaked to the application layer),
 * and each upload gets a fresh UUID so re-claims never silently overwrite earlier
 * evidence. The bucket is created lazily on first save, mirroring [S3ImagePort].
 */
class S3CertificationImageStorage(
    private val s3: S3Client,
    private val bucket: String,
) : CertificationImageStorage {

    override suspend fun save(listingId: Uuid, contentType: String, bytes: ByteArray) {
        ensureBucket()
        val objectKey = "certifications/$listingId/${Uuid.random()}"
        val mediaType = contentType
        s3.putObject {
            bucket = this@S3CertificationImageStorage.bucket
            key = objectKey
            this.contentType = mediaType
            body = ByteStream.fromBytes(bytes)
        }
    }

    override suspend fun loadLatest(listingId: Uuid): CertificationImageStorage.StoredCertificationImage? {
        ensureBucket()
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
        val request = GetObjectRequest {
            bucket = this@S3CertificationImageStorage.bucket
            key = latestKey
        }
        return s3.getObject(request) { response ->
            CertificationImageStorage.StoredCertificationImage(
                contentType = response.contentType ?: "application/octet-stream",
                bytes = response.body?.toByteArray() ?: ByteArray(0),
            )
        }
    }

    private suspend fun ensureBucket() {
        val exists = try {
            s3.headBucket { bucket = this@S3CertificationImageStorage.bucket }
            true
        } catch (e: S3Exception) {
            if (e.isNotFound()) false else throw e
        }
        if (!exists) {
            s3.createBucket { bucket = this@S3CertificationImageStorage.bucket }
        }
    }
}
