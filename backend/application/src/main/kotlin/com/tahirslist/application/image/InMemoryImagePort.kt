package com.tahirslist.application.image

import kotlin.uuid.Uuid
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory [ImagePort] used as the swappability proof and as a default bean
 * when no object store is configured (dev/test boot without MinIO).
 *
 * This is deliberately a test/dev escape hatch, NOT a production store: images
 * are lost on restart. A real deployment configures the S3 adapter instead. Its
 * presence also lets the boot context come up without MinIO so unrelated tests
 * (auth, listings) do not need object storage running.
 */
class InMemoryImagePort : ImagePort {

    private val mutex = Mutex()
    private val store = mutableMapOf<Key, StoredImage>()

    private data class Key(val listingId: Uuid, val variant: ImageVariant)

    override suspend fun save(listingId: Uuid, variant: ImageVariant, contentType: String, bytes: ByteArray) {
        mutex.withLock { store[Key(listingId, variant)] = StoredImage(bytes, contentType) }
    }

    override suspend fun load(listingId: Uuid, variant: ImageVariant): StoredImage? =
        mutex.withLock { store[Key(listingId, variant)] }

    override suspend fun delete(listingId: Uuid, variant: ImageVariant) {
        mutex.withLock { store.remove(Key(listingId, variant)) }
    }
}
