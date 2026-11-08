package com.tahirslist.application.ingestion

import kotlin.uuid.Uuid

/** A brand row, as the ingestion load sees it. */
data class Brand(
    val id: Uuid,
    val name: String,
) {
    init {
        require(name.isNotBlank()) { "a brand must have a name" }
    }
}

/**
 * Brand/location split (BR-15): a brand is resolved by name before any listing
 * is written. The preview only resolves ([resolve]); the transactional apply
 * creates a missing brand inside the same transaction ([resolveOrCreate]) so a
 * failed batch never leaves orphan brands.
 */
interface BrandRepository {
    suspend fun resolve(name: String): Brand?
    suspend fun resolveOrCreate(name: String): Brand
}
