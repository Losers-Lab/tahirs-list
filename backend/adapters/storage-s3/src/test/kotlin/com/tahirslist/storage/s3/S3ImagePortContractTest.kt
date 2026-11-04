package com.tahirslist.storage.s3

import com.tahirslist.application.image.ImagePort
import com.tahirslist.application.image.ImagePortContractSpec
import org.testcontainers.containers.MinIOContainer
import org.testcontainers.utility.DockerImageName
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client

/**
 * The sc-157 adapter-swap proof: the SAME [ImagePortContractSpec] that passes
 * against the in-memory fake (application module) runs unmodified against the
 * real object store here, via a Testcontainers MinIO container. If both green,
 * any ImagePort implementation is interchangeable by config, not by code.
 *
 * Testcontainers-in-Docker networking: TESTCONTAINERS_HOST_OVERRIDE /
 * RYUK_DISABLED come from scripts/backend-test.sh so MinIO's S3 URL reaches
 * back across the gateway.
 */
class S3ImagePortContractTest : ImagePortContractSpec(
    port = createMinioBackedPort(),
) {

    companion object {
        /** Number of times this spec's shared MinIO is started (constructed once per test class). */
        fun createMinioBackedPort(): ImagePort {
            val minio: MinIOContainer = MinIOContainer(
                // minio/minio was deleted from Docker Hub (Sep 2026); silo is a
                // MinIO-compatible fork with the same S3 API and health endpoint.
                DockerImageName.parse("pgsty/silo:latest")
                    .asCompatibleSubstituteFor("minio/minio"),
            )
            minio.start()

            val s3 = S3Client.builder()
                .endpointOverride(java.net.URI.create(minio.s3URL))
                .credentialsProvider(
                    StaticCredentialsProvider.create(AwsBasicCredentials.create(minio.userName, minio.password)),
                )
                .region(Region.of("us-east-1"))
                .serviceConfiguration(
                    software.amazon.awssdk.services.s3.S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build(),
                )
                .build()
            return S3ImagePort(s3 = s3, bucket = "contract-test")
        }
    }
}