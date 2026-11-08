package com.tahirslist.persistence.geo

import com.tahirslist.application.geo.GeocodedAddress
import com.tahirslist.application.geo.GeocodedAddressStore
import com.tahirslist.domain.restaurant.LatLng
import com.tahirslist.persistence.db.GeocodedAddresses
import com.tahirslist.persistence.db.bind
import com.tahirslist.persistence.db.executeUpdate
import com.tahirslist.persistence.db.queryRows
import com.tahirslist.persistence.db.toSqlTimestamp
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import java.sql.ResultSet
import kotlin.time.Instant
import kotlin.time.toKotlinInstant

/**
 * Exposed implementation of [GeocodedAddressStore] against
 * `geocoded_addresses` (V22__sc_198_geocoded_addresses.sql).
 *
 * Reads are a single primary-key lookup with the freshness cutoff applied in
 * SQL, so a stale row is simply not a hit. The write is `INSERT … ON CONFLICT
 * (normalized_query_hash) DO UPDATE`, which keeps one row per query, refreshes a
 * stale one in place, and makes concurrent first lookups safe.
 *
 * Each call is its own short transaction: no transaction is ever held across
 * the provider's HTTP call (INV-10).
 */
class ExposedGeocodedAddressStore(private val db: Database) : GeocodedAddressStore {

    override suspend fun findByQueryHash(hash: String, notBefore: Instant): GeocodedAddress? =
        suspendTransaction(db) {
            queryRows(
                """
                SELECT normalized_query_hash, lat, lng, display_name, provider_ref, provider, created_at
                FROM geocoded_addresses
                WHERE normalized_query_hash = ? AND created_at >= ?
                """.trimIndent(),
                listOf(
                    GeocodedAddresses.normalizedQueryHash bind hash,
                    GeocodedAddresses.createdAt bind notBefore.toSqlTimestamp(),
                ),
            ) { rs -> rs.toEntry() }.firstOrNull()
        }

    override suspend fun save(entry: GeocodedAddress) {
        suspendTransaction(db) {
            executeUpdate(
                """
                INSERT INTO geocoded_addresses
                    (normalized_query_hash, lat, lng, display_name, provider_ref, provider, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (normalized_query_hash) DO UPDATE SET
                    lat          = EXCLUDED.lat,
                    lng          = EXCLUDED.lng,
                    display_name = EXCLUDED.display_name,
                    provider_ref = EXCLUDED.provider_ref,
                    provider     = EXCLUDED.provider,
                    created_at   = EXCLUDED.created_at
                """.trimIndent(),
                listOf(
                    GeocodedAddresses.normalizedQueryHash bind entry.queryHash,
                    GeocodedAddresses.lat bind entry.location.lat,
                    GeocodedAddresses.lng bind entry.location.lng,
                    GeocodedAddresses.displayName bind entry.displayName,
                    GeocodedAddresses.providerRef bind entry.providerRef,
                    GeocodedAddresses.provider bind entry.provider,
                    GeocodedAddresses.createdAt bind entry.createdAt.toSqlTimestamp(),
                ),
                StatementType.INSERT,
            )
        }
    }

    private fun ResultSet.toEntry(): GeocodedAddress = GeocodedAddress(
        queryHash = getString("normalized_query_hash").trim(),
        location = LatLng(lat = getDouble("lat"), lng = getDouble("lng")),
        displayName = getString("display_name"),
        providerRef = getString("provider_ref"),
        provider = getString("provider"),
        createdAt = getTimestamp("created_at").toInstant().toKotlinInstant(),
    )
}
