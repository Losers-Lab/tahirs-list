package com.tahirslist.storage.s3

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.createBucket
import aws.sdk.kotlin.services.s3.deleteObject
import aws.sdk.kotlin.services.s3.headBucket
import aws.sdk.kotlin.services.s3.model.GetObjectRequest
import aws.sdk.kotlin.services.s3.model.NoSuchBucket
import aws.sdk.kotlin.services.s3.model.NoSuchKey
import aws.sdk.kotlin.services.s3.model.NotFound
import aws.sdk.kotlin.services.s3.model.S3Exception
import aws.sdk.kotlin.services.s3.putObject
import aws.smithy.kotlin.runtime.content.ByteStream
import aws.smithy.kotlin.runtime.content.toByteArray
import com.tahirslist.application.image.ImagePort
import com.tahirslist.application.image.ImageVariant
import com.tahirslist.application.image.StoredImage
import kotlin.uuid.Uuid

/**
 * S3-compatible [ImagePort] (MinIO now, Cloudflare R2 later — both speak the S3
 * API) using the **AWS SDK for Kotlin** (suspend, coroutines-native). The
 * endpoint/region/credentials come from [S3StorageConfig]; this class uses only
 * standard S3 operations (PutObject/GetObject/CreateBucket) so no AWS-only
 * feature is relied upon (docs/design/sc-157-image-variants.md).
 *
 * Storage layout (bucket owned by this adapter):
 *   `heroes/{listingId}/{variant}`  ->  object bytes  (content-type on object)
 *
 * The application never sees keys or the bucket; that isolation is what keeps
 * the adapter swappable. The bucket is created lazily on first [save] so a
 * fresh MinIO needs no pre-provisioning step.
 */
class S3ImagePort(
    private val s3: S3Client,
    private val bucket: String,
) : ImagePort {

    override suspend fun save(listingId: Uuid, variant: ImageVariant, contentType: String, bytes: ByteArray) {
        ensureBucket()
        val objectKey = key(listingId, variant)
        val mediaType = contentType
        s3.putObject {
            bucket = this@S3ImagePort.bucket
            key = objectKey
            this.contentType = mediaType
            body = ByteStream.fromBytes(bytes)
        }
    }

    override suspend fun load(listingId: Uuid, variant: ImageVariant): StoredImage? {
        ensureBucket()
        val objectKey = key(listingId, variant)
        val request = GetObjectRequest {
            bucket = this@S3ImagePort.bucket
            key = objectKey
        }
        return try {
            s3.getObject(request) { response ->
                val body = response.body ?: return@getObject null
                StoredImage(
                    bytes = body.toByteArray(),
                    contentType = response.contentType.orEmpty().ifBlank { "application/octet-stream" },
                )
            }
        } catch (e: S3Exception) {
            if (e.isNotFound()) null else throw e // listing has no stored image for this variant
        }
    }

    override suspend fun delete(listingId: Uuid, variant: ImageVariant) {
        ensureBucket()
        val objectKey = key(listingId, variant)
        // S3 DeleteObject of a non-existent key is an idempotent no-op (204),
        // matching the port's "delete of unsaved variant is silent" contract.
        s3.deleteObject {
            bucket = this@S3ImagePort.bucket
            key = objectKey
        }
    }

    private fun key(listingId: Uuid, variant: ImageVariant): String =
        "heroes/$listingId/${variant.name}"

    private suspend fun ensureBucket() {
        val exists = try {
            s3.headBucket { bucket = this@S3ImagePort.bucket }
            true
        } catch (e: S3Exception) {
            if (e.isNotFound()) false else throw e
        }
        if (!exists) {
            s3.createBucket { bucket = this@S3ImagePort.bucket }
        }
    }
}

/**
 * True when an S3 error means "the bucket/object is not there" (HTTP 404).
 *
 * The AWS SDK for Kotlin does not expose the HTTP status code on
 * [S3Exception.sdkErrorMetadata], so the modeled 404 error types and their error
 * codes are checked instead. `GetObject` maps 404 to [NoSuchKey] (or `NotFound`
 * when the service returns no body); `HeadBucket` maps 404 to [NotFound]. The
 * code-string fallback keeps S3-compatible stores (MinIO/R2) that report
 * `NoSuchBucket` on other operations on the same 404 path.
 */
internal fun S3Exception.isNotFound(): Boolean =
    this is NotFound ||
        this is NoSuchKey ||
        this is NoSuchBucket ||
        sdkErrorMetadata.errorCode?.let { it in NOT_FOUND_ERROR_CODES } == true

private val NOT_FOUND_ERROR_CODES = setOf("NoSuchKey", "NoSuchBucket", "NotFound", "404")
