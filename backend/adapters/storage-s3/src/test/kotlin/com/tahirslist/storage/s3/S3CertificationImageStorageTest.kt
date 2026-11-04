package com.tahirslist.storage.s3

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.createBucket
import aws.sdk.kotlin.services.s3.listObjectsV2
import aws.sdk.kotlin.services.s3.model.GetObjectRequest
import aws.sdk.kotlin.services.s3.model.NoSuchKey
import aws.smithy.kotlin.runtime.content.toByteArray
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.uuid.Uuid
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.testcontainers.containers.MinIOContainer
import org.testcontainers.utility.DockerImageName

/**
 * MinIO-backed proof that the certification-image adapter (sc-46) persists under
 * its own `certifications/` key namespace and round-trips bytes + content type.
 * Runs in the containerized test (CI); no live storage is touched.
 *
 * The adapter and its [S3Client] come from the production Kotlin-SDK factories
 * ([createS3Client] / [createCertificationStorage]); the port is suspend, so the
 * scenarios run inside [runTest].
 */
class S3CertificationImageStorageTest : FunSpec() {

    private val bucket = "certification-test"

    private val s3: S3Client = run {
        val minio: MinIOContainer = MinIOContainer(
            // minio/minio was deleted from Docker Hub (Sep 2026); silo is a
            // MinIO-compatible fork with the same S3 API and health endpoint.
            DockerImageName.parse("pgsty/silo:latest")
                .asCompatibleSubstituteFor("minio/minio"),
        )
        minio.start()
        createS3Client(
            S3StorageConfig(
                endpoint = minio.s3URL,
                region = "us-east-1",
                accessKey = minio.userName,
                secretKey = minio.password,
                bucket = bucket,
            ),
        )
    }

    private val storage = createCertificationStorage(s3, bucket)

    init {
        beforeSpec {
            // The "missing key yields NoSuchKey" scenario must not accidentally hit
            // a missing bucket (which surfaces as a different modeled 404 error).
            runTest {
                s3.createBucket { bucket = this@S3CertificationImageStorageTest.bucket }
            }
        }

        test("save writes the certification image under the certifications/ namespace and round-trips it") {
            runTest {
                val listingId = Uuid.random()
                val bytes = byteArrayOf(1, 2, 3, 4)

                storage.save(listingId, "image/jpeg", bytes)

                val listingObjects = s3.listObjectsV2 {
                    bucket = this@S3CertificationImageStorageTest.bucket
                    prefix = "certifications/$listingId/"
                }.contents.orEmpty()
                listingObjects.size shouldBe 1
                val key = requireNotNull(listingObjects[0].key)
                key.startsWith("certifications/$listingId/") shouldBe true

                val (storedBytes, storedType) = s3.getObject(
                    GetObjectRequest {
                        bucket = this@S3CertificationImageStorageTest.bucket
                        this.key = key
                    },
                ) { response ->
                    (response.body?.toByteArray() ?: ByteArray(0)) to response.contentType
                }
                storedBytes shouldBe bytes
                storedType shouldBe "image/jpeg"
            }
        }

        test("each upload for the same listing gets a fresh key (history is not overwritten)") {
            runTest {
                val listingId = Uuid.random()

                storage.save(listingId, "image/jpeg", byteArrayOf(1))
                storage.save(listingId, "image/png", byteArrayOf(2))

                val listingObjects = s3.listObjectsV2 {
                    bucket = this@S3CertificationImageStorageTest.bucket
                    prefix = "certifications/$listingId/"
                }.contents.orEmpty()
                listingObjects.size shouldBe 2
                listingObjects.mapNotNull { it.key }.toSet().size shouldBe 2
            }
        }

        test("a missing certification image key yields NoSuchKey (no silent empty read)") {
            runTest {
                val phantom = "certifications/${Uuid.random()}/${Uuid.random()}"

                shouldThrow<NoSuchKey> {
                    // shouldThrow's block is non-suspend; bridge the suspend SDK call.
                    runBlocking {
                        s3.getObject(
                            GetObjectRequest {
                                bucket = this@S3CertificationImageStorageTest.bucket
                                key = phantom
                            },
                        ) { }
                    }
                }
            }
        }

        test("loadLatest reads back the most recently saved certification image, round-tripping bytes + content type") {
            runTest {
                val listingId = Uuid.random()

                storage.save(listingId, "image/png", byteArrayOf(9, 8, 7))

                val latest = storage.loadLatest(listingId)

                latest shouldNotBe null
                latest!!.contentType shouldBe "image/png"
                latest.bytes shouldBe byteArrayOf(9, 8, 7)
            }
        }

        test("loadLatest returns non-null across re-claims (history kept) and null for a listing with no cert") {
            runTest {
                val listingId = Uuid.random()

                storage.save(listingId, "image/jpeg", byteArrayOf(1, 2))
                storage.save(listingId, "image/png", byteArrayOf(3, 4))

                storage.loadLatest(listingId) shouldNotBe null
                storage.loadLatest(Uuid.random()) shouldBe null
            }
        }
    }
}
