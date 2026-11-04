package com.tahirslist.storage.s3

import com.tahirslist.application.image.ImagePort
import com.tahirslist.application.image.ImagePortContractSpec
import org.testcontainers.containers.MinIOContainer
import org.testcontainers.utility.DockerImageName

/**
 * The sc-157 adapter-swap proof: the SAME [ImagePortContractSpec] that passes
 * against the in-memory fake (application module) runs unmodified against the
 * real object store here, via a Testcontainers MinIO container. If both green,
 * any ImagePort implementation is interchangeable by config, not by code.
 *
 * The S3 client comes from the production [createS3Client] factory with a plain
 * [S3StorageConfig] — no framework wiring involved — so this also pins that the
 * factory's path-style + endpoint/static-credential setup works against MinIO.
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

            val config = S3StorageConfig(
                endpoint = minio.s3URL,
                region = "us-east-1",
                accessKey = minio.userName,
                secretKey = minio.password,
                bucket = "contract-test",
            )
            return createImagePort(createS3Client(config), config.bucket)
        }
    }
}
