package com.tahirslist.persistence

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException

/**
 * V22 is the schema behind the runtime ingestion load: the claims table, the
 * deterministic venue key, and the widened provenance vocabulary.
 */
class IngestionSchemaMigrationTest : FunSpec() {

    private val postgis = PostgisTestDatabase()

    init {
        beforeSpec { postgis.start() }
        afterSpec { postgis.stop() }

        // Each helper call gets its own coordinate pair so a test that is not
        // about location dedupe never trips the research-location index.
        var pointSeq = 0
        fun freshPoint(): String {
            pointSeq += 1
            val lat = 43.0 + pointSeq * 0.001
            val lng = -79.0 - pointSeq * 0.001
            return "ST_SetSRID(ST_MakePoint($lng, $lat), 4326)::geography"
        }

        fun pointExpr(lat: Double, lng: Double): String =
            "ST_SetSRID(ST_MakePoint($lng, $lat), 4326)::geography"

        fun listingSql(
            id: UUID,
            name: String,
            venueKey: String? = null,
            point: String = freshPoint(),
        ): String =
            """
            INSERT INTO restaurant_listings
                (id, name, address, location, cuisine, is_hand_cut, owner_id, brand_id,
                 provenance, venue_key, verification_status, alcohol_served, halal_scope, cross_contamination)
            VALUES (
                '$id', '$name', '1 St',
                $point,
                NULL, NULL, NULL, NULL, 'certifier-hma', ${venueKey?.let { "'$it'" } ?: "NULL"},
                'UNVERIFIED', false, 'NOT_DISCLOSED', 'UNCERTAIN'
            )
            """.trimIndent()

        fun claimSql(
            id: UUID,
            listingId: UUID,
            source: String = "hms",
            record: String = "HMS-1",
            tier: Int = 1,
            declaration: String = "HAND_CUT",
            url: String = "https://example.test/listing",
            firstSeen: String = "now()",
            lastSeen: String = "now()",
        ): String = """
            INSERT INTO external_certification_claims
                (id, listing_id, source, source_tier, source_record_ref, source_url,
                 certifier, hand_cut_declaration, first_seen, last_seen, created_at)
            VALUES (
                '$id', '$listingId', '$source', $tier, '$record',
                '$url', 'Example Certifier', '$declaration',
                $firstSeen, $lastSeen, now()
            )
        """.trimIndent()

        test("V22 creates the claims table with the source-record columns") {
            runTest {
                val columns = postgis.rows(
                    "SELECT column_name FROM information_schema.columns " +
                        "WHERE table_name = 'external_certification_claims'",
                ).map { it["column_name"] }

                columns shouldContainAll listOf(
                    "id", "listing_id", "source", "source_tier", "source_record_ref",
                    "source_url", "certifier", "expires_on", "hand_cut_declaration",
                    "first_seen", "last_seen", "created_at",
                )
            }
        }

        test("V22 adds venue_key and a partial unique index") {
            runTest {
                postgis.count(
                    "SELECT count(*) FROM information_schema.columns " +
                        "WHERE table_name = 'restaurant_listings' AND column_name = 'venue_key'",
                ) shouldBe 1

                val key = "a".repeat(64)
                postgis.execute(listingSql(UUID.randomUUID(), "One", key))
                shouldThrow<ExposedSQLException> {
                    postgis.execute(listingSql(UUID.randomUUID(), "Two", key))
                }
                // NULL venue keys are allowed many times.
                postgis.execute(listingSql(UUID.randomUUID(), "Three", null))
                postgis.execute(listingSql(UUID.randomUUID(), "Four", null))
            }
        }

        test("V24 allows two different businesses at one point, as a curator decided") {
            runTest {
                val shared = pointExpr(43.5, -79.5)
                postgis.execute(listingSql(UUID.randomUUID(), "Counter one", point = shared))
                postgis.execute(listingSql(UUID.randomUUID(), "Counter two", point = shared))

                // An ordinary user row (provenance NULL) may share the point too.
                postgis.execute(
                    """
                    INSERT INTO restaurant_listings
                        (name, address, location, provenance, verification_status, alcohol_served, halal_scope, cross_contamination)
                    VALUES ('User row', '1 St', $shared, NULL, 'UNVERIFIED', false, 'NOT_DISCLOSED', 'UNCERTAIN')
                    """.trimIndent(),
                )

                postgis.count(
                    "SELECT count(*) FROM restaurant_listings WHERE location_lat_6 = round(43.5::numeric, 6) " +
                        "AND location_lng_6 = round(-79.5::numeric, 6)",
                ) shouldBe 3
            }
        }

        test("V24 keeps the seed corpus unique per point so re-seeding stays a no-op") {
            runTest {
                val shared = pointExpr(43.6, -79.6)
                val seed = {
                    """
                    INSERT INTO restaurant_listings
                        (name, address, location, provenance, verification_status, alcohol_served, halal_scope, cross_contamination)
                    VALUES ('Seed row', '1 St', $shared, 'research-seed / photon-geocode', 'UNVERIFIED', false, 'NOT_DISCLOSED', 'UNCERTAIN')
                    """.trimIndent()
                }
                postgis.execute(seed())
                shouldThrow<ExposedSQLException> { postgis.execute(seed()) }
            }
        }

        test("V24 records where certifier-derived trust came from") {
            runTest {
                postgis.count(
                    "SELECT count(*) FROM information_schema.columns " +
                        "WHERE table_name = 'restaurant_listings' AND column_name = 'verification_basis'",
                ) shouldBe 1
                shouldThrow<ExposedSQLException> {
                    postgis.execute(
                        """
                        INSERT INTO restaurant_listings
                            (name, address, location, provenance, verification_status, verification_basis,
                             alcohol_served, halal_scope, cross_contamination)
                        VALUES ('Bad basis', '1 St', ${freshPoint()}, 'certifier-hma', 'UNVERIFIED', 'COMMITTEE',
                                false, 'NOT_DISCLOSED', 'UNCERTAIN')
                        """.trimIndent(),
                    )
                }
            }
        }

        test("a source record is globally unique across listings") {
            runTest {
                val first = UUID.randomUUID()
                val second = UUID.randomUUID()
                postgis.execute(listingSql(first, "First"))
                postgis.execute(listingSql(second, "Second"))
                postgis.execute(claimSql(UUID.randomUUID(), first))

                shouldThrow<ExposedSQLException> {
                    postgis.execute(claimSql(UUID.randomUUID(), second))
                }
            }
        }

        test("the widened provenance vocabulary accepts certifier stamps and rejects unknown ones") {
            runTest {
                postgis.execute(listingSql(UUID.randomUUID(), "Certifier row"))

                shouldThrow<ExposedSQLException> {
                    postgis.execute(
                        """
                        INSERT INTO restaurant_listings
                            (name, address, location, provenance, verification_status, alcohol_served, halal_scope, cross_contamination)
                        VALUES ('Bad', '1 St', ST_SetSRID(ST_MakePoint(0, 0), 4326)::geography,
                                'not-a-certifier', 'UNVERIFIED', false, 'NOT_DISCLOSED', 'UNCERTAIN')
                        """.trimIndent(),
                    )
                }
            }
        }

        test("the hand-cut declaration CHECK rejects an unknown value") {
            runTest {
                val listing = UUID.randomUUID()
                postgis.execute(listingSql(listing, "Decl"))

                shouldThrow<ExposedSQLException> {
                    postgis.execute(
                        claimSql(UUID.randomUUID(), listing, source = "hma", record = "DECL-1", declaration = "MAYBE"),
                    )
                }
            }
        }

        test("claim CHECKs reject a bad source, tier, url and seen order") {
            runTest {
                val listing = UUID.randomUUID()
                postgis.execute(listingSql(listing, "Checked"))

                shouldThrow<ExposedSQLException> {
                    postgis.execute(claimSql(UUID.randomUUID(), listing, source = "HMS", record = "CHECK-1"))
                }
                shouldThrow<ExposedSQLException> {
                    postgis.execute(claimSql(UUID.randomUUID(), listing, source = "hfsaa", record = "CHECK-2", tier = 0))
                }
                shouldThrow<ExposedSQLException> {
                    postgis.execute(claimSql(UUID.randomUUID(), listing, source = "hfsaa", record = "CHECK-3", tier = 4))
                }
                shouldThrow<ExposedSQLException> {
                    postgis.execute(claimSql(UUID.randomUUID(), listing, source = "hfsaa", record = "CHECK-4", url = "https://"))
                }
                shouldThrow<ExposedSQLException> {
                    postgis.execute(
                        claimSql(UUID.randomUUID(), listing, source = "hfsaa", record = "CHECK-5", url = "http://example.test/x"),
                    )
                }
                shouldThrow<ExposedSQLException> {
                    postgis.execute(
                        claimSql(
                            UUID.randomUUID(),
                            listing,
                            source = "hfsaa",
                            record = "CHECK-6",
                            firstSeen = "now()",
                            lastSeen = "now() - interval '1 day'",
                        ),
                    )
                }
            }
        }

        test("a claim blocks deleting its listing (audit trail survives)") {
            runTest {
                val listing = UUID.randomUUID()
                postgis.execute(listingSql(listing, "Audited"))
                postgis.execute(claimSql(UUID.randomUUID(), listing, source = "hfsaa", record = "AUDIT-1"))

                shouldThrow<ExposedSQLException> {
                    postgis.execute("DELETE FROM restaurant_listings WHERE id = '$listing'")
                }
            }
        }

        test("the venue key CHECK rejects non-hex and uppercase keys") {
            runTest {
                shouldThrow<ExposedSQLException> {
                    postgis.execute(listingSql(UUID.randomUUID(), "Upper", "A".repeat(64)))
                }
                shouldThrow<ExposedSQLException> {
                    postgis.execute(listingSql(UUID.randomUUID(), "Short", "a".repeat(63)))
                }
                shouldThrow<ExposedSQLException> {
                    postgis.execute(listingSql(UUID.randomUUID(), "NonHex", "z".repeat(64)))
                }
            }
        }

        test("V24 restores the seed-only unique index and makes the research one a plain lookup") {
            runTest {
                postgis.count(
                    "SELECT count(*) FROM pg_indexes WHERE indexname = 'uq_restaurant_listings_seed_location' " +
                        "AND indexdef LIKE 'CREATE UNIQUE INDEX%'",
                ) shouldBe 1
                postgis.count(
                    "SELECT count(*) FROM pg_indexes WHERE indexname = 'uq_restaurant_listings_research_location'",
                ) shouldBe 0
                postgis.count(
                    "SELECT count(*) FROM pg_indexes WHERE indexname = 'idx_restaurant_listings_research_location' " +
                        "AND indexdef LIKE 'CREATE INDEX%'",
                ) shouldBe 1
            }
        }
    }
}
