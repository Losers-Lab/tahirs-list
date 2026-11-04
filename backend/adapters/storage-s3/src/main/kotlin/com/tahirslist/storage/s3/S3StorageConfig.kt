package com.tahirslist.storage.s3

import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.s3.S3Client
import aws.smithy.kotlin.runtime.net.url.Url
import com.tahirslist.application.image.ImagePort
import com.tahirslist.application.verification.CertificationImageStorage

/**
 * Configuration for the S3-compatible object store. MinIO local defaults;
 * Cloudflare R2 later is a config change (endpoint/region/creds), not a code
 * change. No AWS-only feature is relied upon.
 *
 * [endpoint] is the full base URL of the S3-compatible service (e.g.
 * `http://localhost:9000`). It is required: the adapter only exists when an
 * object store is configured, and the bootstrap wiring decides between this
 * adapter and the in-memory fallback.
 *
 * This is a plain Kotlin config object (no framework annotations): the
 * bootstrap/composition layer builds it from its own property source and calls
 * the factories below.
 */
data class S3StorageConfig(
    val endpoint: String,
    val region: String = "us-east-1",
    val accessKey: String? = null,
    val secretKey: String? = null,
    val bucket: String = "halal-images",
) {
    init {
        require(endpoint.isNotBlank()) { "S3 endpoint must not be blank." }
        require(region.isNotBlank()) { "S3 region must not be blank." }
        require(bucket.isNotBlank()) { "S3 bucket must not be blank." }
    }
}

/**
 * Builds the suspend [S3Client] (AWS SDK for Kotlin) for an S3-compatible store.
 *
 * - Path-style addressing is forced: MinIO (and R2) do not use virtual-hosted
 *   bucket addressing, and it is not the AWS default.
 * - Static credentials are used only when both key parts are configured;
 *   otherwise the SDK's default credential chain applies (e.g. env/IAM role).
 * - The client owns a connection pool/engine and should be closed by the
 *   composition layer that built it (usually at application shutdown).
 */
fun createS3Client(config: S3StorageConfig): S3Client {
    val accessKey = config.accessKey
    val secretKey = config.secretKey
    return S3Client {
        region = config.region
        endpointUrl = Url.parse(config.endpoint)
        forcePathStyle = true
        if (accessKey != null && secretKey != null) {
            credentialsProvider = StaticCredentialsProvider {
                accessKeyId = accessKey
                secretAccessKey = secretKey
            }
        }
    }
}

/** The hero-image [ImagePort] backed by [client], storing under [bucket]. */
fun createImagePort(client: S3Client, bucket: String): ImagePort =
    S3ImagePort(s3 = client, bucket = bucket)

/** The certification-image [CertificationImageStorage] backed by [client], storing under [bucket]. */
fun createCertificationStorage(client: S3Client, bucket: String): CertificationImageStorage =
    S3CertificationImageStorage(s3 = client, bucket = bucket)
