package com.tahirslist.application.verification

import kotlin.uuid.Uuid
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory [CertificationImageStorage] used only for dev/test boot when no real
 * object store is configured (mirrors [com.tahirslist.application.image.InMemoryImagePort]
 * for hero images). Never used in production — the S3 adapter wins whenever
 * `app.storage.s3.endpoint` is set.
 *
 * Keys are unique per save (same shape as the S3 adapter's
 * `certifications/{listingId}/{uuid}`), so a review that records a key keeps
 * pointing at exactly the bytes it was approved against.
 */
class InMemoryCertificationImageStorage : CertificationImageStorage {

    data class StoredCertificationImage(val contentType: String, val bytes: ByteArray)

    private val mutex = Mutex()

    /** listingId -> certification images stored for it, in submission order. */
    val stored: MutableMap<Uuid, MutableList<StoredCertificationImage>> = mutableMapOf()

    /** Stored object key -> the image it addresses. */
    private val byKey: MutableMap<String, CertificationImageStorage.StoredCertificationImage> = mutableMapOf()

    override suspend fun save(listingId: Uuid, contentType: String, bytes: ByteArray): String =
        mutex.withLock {
            val image = StoredCertificationImage(contentType, bytes.copyOf())
            stored.getOrPut(listingId) { mutableListOf() }.add(image)
            val key = "certifications/$listingId/${Uuid.random()}"
            byKey[key] = CertificationImageStorage.StoredCertificationImage(
                contentType = image.contentType,
                bytes = image.bytes,
            )
            key
        }

    override suspend fun load(key: String): CertificationImageStorage.StoredCertificationImage? =
        mutex.withLock {
            byKey[key]?.let { storedImage ->
                CertificationImageStorage.StoredCertificationImage(
                    contentType = storedImage.contentType,
                    bytes = storedImage.bytes.copyOf(),
                )
            }
        }

    override suspend fun loadLatest(listingId: Uuid): CertificationImageStorage.StoredCertificationImage? =
        mutex.withLock {
            val latest = stored[listingId]?.lastOrNull() ?: return@withLock null
            CertificationImageStorage.StoredCertificationImage(
                contentType = latest.contentType,
                bytes = latest.bytes.copyOf(),
            )
        }
}
