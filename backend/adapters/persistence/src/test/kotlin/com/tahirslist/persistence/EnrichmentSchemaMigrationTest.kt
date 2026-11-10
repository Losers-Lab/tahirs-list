package com.tahirslist.persistence

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException

/**
 * V27 is the schema behind SC-231: the enrichment columns, and a cover-photo
 * table where the licence rules are constraints rather than comments.
 *
 * The interesting assertions are the negative ones: the database must refuse to
 * record a permanent Google Places photo, a cache without an expiry, or a
 * free-licensed photo with no licence. If a future change relaxes one of those,
 * this spec fails before a byte reaches a bucket.
 */
class EnrichmentSchemaMigrationTest : FunSpec() {

    private val postgis = PostgisTestDatabase()

    init {
        beforeSpec { postgis.start() }
        afterSpec { postgis.stop() }

        fun listingSql(
            id: UUID = UUID.randomUUID(),
            venueType: String? = null,
            venueTypeSource: String? = null,
        ): String {
            val lat = 44.0 + (id.hashCode() % 1000) / 100_000.0
            return """
                INSERT INTO restaurant_listings
                    (id, name, address, location, provenance, verification_status,
                     alcohol_served, halal_scope, cross_contamination, venue_type, venue_type_source)
                VALUES (
                    '$id', 'Row $id', '1 St',
                    ST_SetSRID(ST_MakePoint(-79.0, $lat), 4326)::geography,
                    'certifier-hms', 'VERIFIED', false, 'NOT_DISCLOSED', 'NO_CROSS_CONTAMINATION',
                    ${venueType?.let { "'$it'" } ?: "NULL"},
                    ${venueTypeSource?.let { "'$it'" } ?: "NULL"}
                )
            """.trimIndent()
        }

        fun photoSql(
            listingId: UUID,
            sourceKind: String = "OSM",
            storageClass: String = "PERMANENT",
            licence: String? = "CC_BY",
            expiresAt: String? = null,
        ): String = """
            INSERT INTO listing_cover_photos
                (listing_id, source_kind, storage_class, licence, attribution, expires_at)
            VALUES (
                '$listingId', '$sourceKind', '$storageClass',
                ${licence?.let { "'$it'" } ?: "NULL"},
                'Photo credit',
                ${expiresAt?.let { "'$it'::timestamptz" } ?: "NULL"}
            )
        """.trimIndent()

        test("V27 adds the three enrichment columns") {
            runTest {
                val columns = postgis
                    .rows(
                        "SELECT column_name FROM information_schema.columns " +
                            "WHERE table_name = 'restaurant_listings'",
                    )
                    .map { it["column_name"] }
                columns shouldContainAll listOf("venue_type", "venue_type_source", "cuisine_source")
            }
        }

        test("venue_type is a closed restaurant vocabulary") {
            runTest {
                postgis.execute(listingSql(venueType = "BAKERY", venueTypeSource = "NAME_KEYWORD"))

                // A shop is never a stored type: the gate excludes it before the load.
                shouldThrow<ExposedSQLException> {
                    postgis.execute(listingSql(venueType = "BUTCHER", venueTypeSource = "NAME_KEYWORD"))
                }
            }
        }

        test("a derived type without its evidence is refused") {
            runTest {
                shouldThrow<ExposedSQLException> {
                    postgis.execute(listingSql(venueType = "RESTAURANT", venueTypeSource = null))
                }
                shouldThrow<ExposedSQLException> {
                    postgis.execute(listingSql(venueType = null, venueTypeSource = "SOURCE_CATEGORY"))
                }
            }
        }

        test("the cover-photo table has the credit columns") {
            runTest {
                val columns = postgis
                    .rows(
                        "SELECT column_name FROM information_schema.columns " +
                            "WHERE table_name = 'listing_cover_photos'",
                    )
                    .map { it["column_name"] }
                columns shouldContainAll listOf(
                    "listing_id", "source_kind", "storage_class", "licence", "attribution",
                    "author", "source_url", "google_place_id", "google_photo_name",
                    "expires_at", "content_hash", "width", "height", "created_at",
                )
            }
        }

        test("a Google Places photo can only be a cache with an expiry") {
            runTest {
                val listing = UUID.randomUUID()
                postgis.execute(listingSql(id = listing))

                // Permanent Places photo: the terms say no.
                shouldThrow<ExposedSQLException> {
                    postgis.execute(photoSql(listing, sourceKind = "GOOGLE_PLACES", storageClass = "PERMANENT"))
                }
                // Cache with no expiry could be served forever.
                shouldThrow<ExposedSQLException> {
                    postgis.execute(photoSql(listing, sourceKind = "GOOGLE_PLACES", storageClass = "CACHE", licence = null))
                }

                postgis.execute(
                    photoSql(
                        listing,
                        sourceKind = "GOOGLE_PLACES",
                        storageClass = "CACHE",
                        licence = null,
                        expiresAt = "2026-12-10T00:00:00Z",
                    ),
                )
                postgis.count("SELECT count(*) FROM listing_cover_photos WHERE listing_id = '$listing'") shouldBe 1
            }
        }

        test("a free-licensed photo must carry its licence") {
            runTest {
                val listing = UUID.randomUUID()
                postgis.execute(listingSql(id = listing))

                shouldThrow<ExposedSQLException> {
                    postgis.execute(photoSql(listing, sourceKind = "WIKIMEDIA", licence = null))
                }
                postgis.execute(photoSql(listing, sourceKind = "WIKIMEDIA", licence = "CC_BY_SA"))
            }
        }

        test("an owner photo is permanent and carries no licence") {
            runTest {
                val withPhoto = UUID.randomUUID()
                postgis.execute(listingSql(id = withPhoto))
                postgis.execute(
                    photoSql(withPhoto, sourceKind = "OWNER_UPLOAD", storageClass = "PERMANENT", licence = null),
                )

                val withLicence = UUID.randomUUID()
                postgis.execute(listingSql(id = withLicence))
                shouldThrow<ExposedSQLException> {
                    postgis.execute(photoSql(withLicence, sourceKind = "OWNER_UPLOAD", licence = "CC_BY"))
                }
            }
        }

        test("one cover photo per listing") {
            runTest {
                val listing = UUID.randomUUID()
                postgis.execute(listingSql(id = listing))
                postgis.execute(photoSql(listing))

                shouldThrow<ExposedSQLException> { postgis.execute(photoSql(listing)) }
            }
        }

        test("deleting a listing takes its photo with it") {
            runTest {
                val listing = UUID.randomUUID()
                postgis.execute(listingSql(id = listing))
                postgis.execute(photoSql(listing))

                postgis.execute("DELETE FROM restaurant_listings WHERE id = '$listing'")

                postgis.count("SELECT count(*) FROM listing_cover_photos WHERE listing_id = '$listing'") shouldBe 0
            }
        }
    }
}
