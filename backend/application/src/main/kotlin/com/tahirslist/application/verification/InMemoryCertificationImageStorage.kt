package com.tahirslist.application.verification

import kotlin.uuid.Uuid
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory [CertificationImageStorage] used only for dev/test boot when no real
 * object store is configured (mirrors [com.tahirslist.application.image.InMemoryImagePort]
 * for hero images). Never used in production — the S3 adapter wins whenever
 * `app.storage.s3.endpoint` is set.
 */
class InMemoryCertificationImageStorage : CertificationImageStorage {

    data class StoredCertificationImage(val contentType: String, val bytes: ByteArray)

    private val mutex = Mutex()

    /** listingId -> certification images stored for it, in submission order. */
    val stored: MutableMap<Uuid, MutableList<StoredCertificationImage>> = mutableMapOf()

    override suspend fun save(listingId: Uuid, contentType: String, bytes: ByteArray) {
        mutex.withLock {
            stored.getOrPut(listingId) { mutableListOf() }
                .add(StoredCertificationImage(contentType, bytes.copyOf()))
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
