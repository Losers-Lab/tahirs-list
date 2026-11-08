package com.tahirslist.persistence.brand

import com.tahirslist.application.ingestion.Brand
import com.tahirslist.application.ingestion.BrandRepository
import com.tahirslist.persistence.db.Brands
import com.tahirslist.persistence.db.bind
import com.tahirslist.persistence.db.executeUpdate
import com.tahirslist.persistence.db.queryRows
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import java.util.UUID
import kotlin.uuid.toKotlinUuid

/**
 * Brand/location split read/write path (BR-15) over the V5 `brands` table.
 *
 * Resolution is case-insensitive so "The Halal Guys" and "the halal guys" are
 * one brand; creation keeps the caller's spelling and relies on the unique name
 * index for idempotency.
 */
class ExposedBrandRepository(private val db: Database) : BrandRepository {

    override suspend fun resolve(name: String): Brand? = suspendTransaction(db) {
        resolveInTransaction(name)
    }

    override suspend fun resolveOrCreate(name: String): Brand = suspendTransaction(db) {
        resolveInTransaction(name) ?: run {
            executeUpdate(
                "INSERT INTO brands (name) VALUES (?) ON CONFLICT (name) DO NOTHING",
                listOf(Brands.name bind name),
                StatementType.INSERT,
            )
            resolveInTransaction(name) ?: error("brand '$name' could not be created or found")
        }
    }

    internal fun JdbcTransaction.resolveInTransaction(name: String): Brand? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        return queryRows(
            "SELECT id, name FROM brands WHERE lower(name) = lower(?) ORDER BY created_at LIMIT 1",
            listOf(Brands.name bind trimmed),
        ) { Brand(id = it.getObject("id", UUID::class.java).toKotlinUuid(), name = it.getString("name")) }
            .firstOrNull()
    }
}
