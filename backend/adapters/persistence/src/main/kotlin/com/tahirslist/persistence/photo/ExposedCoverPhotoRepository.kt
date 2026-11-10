package com.tahirslist.persistence.photo

import com.tahirslist.application.photo.CoverPhotoRepository
import com.tahirslist.domain.restaurant.CoverPhoto
import com.tahirslist.domain.restaurant.PhotoLicence
import com.tahirslist.domain.restaurant.PhotoSourceKind
import com.tahirslist.domain.restaurant.PhotoStorage
import com.tahirslist.persistence.db.ListingCoverPhotos
import com.tahirslist.persistence.db.bind
import com.tahirslist.persistence.db.executeUpdate
import com.tahirslist.persistence.db.queryRows
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import java.sql.ResultSet
import java.time.Instant
import java.time.ZoneOffset
import kotlin.uuid.Uuid

/**
 * The cover-photo table (SC-231, V27).
 *
 * One row per listing, upserted: a refreshed Places cache replaces the credit row
 * rather than accumulating rows, so "the photo" is a fact about the listing and
 * not a history to reconcile.
 */
class ExposedCoverPhotoRepository(private val db: Database) : CoverPhotoRepository {

    override suspend fun find(listingId: Uuid): CoverPhoto? = suspendTransaction(db) {
        queryRows(
            """
            SELECT $COVER_COLUMNS
            FROM listing_cover_photos
            WHERE listing_id = ?
            """.trimIndent(),
            listOf(ListingCoverPhotos.listingId bind listingId),
        ) { it.toCoverPhoto() }.firstOrNull()
    }

    override suspend fun save(listingId: Uuid, photo: CoverPhoto) {
        suspendTransaction(db) {
            executeUpdate(
                """
                INSERT INTO listing_cover_photos (
                    listing_id, source_kind, storage_class, licence, attribution, author,
                    source_url, google_place_id, google_photo_name, expires_at,
                    content_hash, width, height
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (listing_id) DO UPDATE SET
                    source_kind = EXCLUDED.source_kind,
                    storage_class = EXCLUDED.storage_class,
                    licence = EXCLUDED.licence,
                    attribution = EXCLUDED.attribution,
                    author = EXCLUDED.author,
                    source_url = EXCLUDED.source_url,
                    google_place_id = EXCLUDED.google_place_id,
                    google_photo_name = EXCLUDED.google_photo_name,
                    expires_at = EXCLUDED.expires_at,
                    content_hash = EXCLUDED.content_hash,
                    width = EXCLUDED.width,
                    height = EXCLUDED.height,
                    created_at = now()
                """.trimIndent(),
                listOf(
                    ListingCoverPhotos.listingId bind listingId,
                    ListingCoverPhotos.sourceKind bind photo.sourceKind.name,
                    ListingCoverPhotos.storageClass bind photo.storageClass.name,
                    ListingCoverPhotos.licence bind photo.licence?.name,
                    ListingCoverPhotos.attribution bind photo.attribution,
                    ListingCoverPhotos.author bind photo.author,
                    ListingCoverPhotos.sourceUrl bind photo.sourceUrl,
                    ListingCoverPhotos.googlePlaceId bind photo.placeId,
                    ListingCoverPhotos.googlePhotoName bind photo.photoName,
                    ListingCoverPhotos.expiresAt bind photo.expiresAt?.atOffset(ZoneOffset.UTC),
                    ListingCoverPhotos.contentHash bind photo.contentHash,
                    ListingCoverPhotos.width bind photo.width,
                    ListingCoverPhotos.height bind photo.height,
                ),
                StatementType.INSERT,
            )
        }
    }
}

/** The column list both this repository and the listing read share. */
internal const val COVER_COLUMNS =
    "listing_id, source_kind, storage_class, licence, attribution, author, source_url, " +
        "google_place_id, google_photo_name, expires_at, content_hash, width, height"

/**
 * Map a cover-photo row. [prefix] is used when the columns arrive from the
 * listing read's LEFT JOIN (`cp_`).
 */
internal fun ResultSet.toCoverPhoto(prefix: String = ""): CoverPhoto? {
    val sourceKind = getString(prefix + "source_kind") ?: return null
    return CoverPhoto(
        sourceKind = PhotoSourceKind.valueOf(sourceKind),
        storageClass = PhotoStorage.valueOf(getString(prefix + "storage_class")),
        attribution = getString(prefix + "attribution"),
        licence = getString(prefix + "licence")?.let { PhotoLicence.valueOf(it) },
        author = getString(prefix + "author"),
        sourceUrl = getString(prefix + "source_url"),
        placeId = getString(prefix + "google_place_id"),
        photoName = getString(prefix + "google_photo_name"),
        expiresAt = getObject(prefix + "expires_at", java.time.OffsetDateTime::class.java)?.toInstant(),
        contentHash = getString(prefix + "content_hash")?.trim(),
        width = getObject(prefix + "width") as Int?,
        height = getObject(prefix + "height") as Int?,
    )
}

/** A `java.time.Instant` as the TIMESTAMPTZ value Exposed binds. */
internal fun Instant.toTimestamp(): java.time.OffsetDateTime = atOffset(ZoneOffset.UTC)
