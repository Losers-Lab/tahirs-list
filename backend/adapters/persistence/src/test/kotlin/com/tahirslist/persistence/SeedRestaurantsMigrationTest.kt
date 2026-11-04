package com.tahirslist.persistence

import com.tahirslist.application.listing.RestaurantListingRepository
import com.tahirslist.domain.restaurant.CrossContamination
import com.tahirslist.domain.restaurant.Provenance
import com.tahirslist.domain.restaurant.VerificationStatus
import com.tahirslist.persistence.listing.ExposedRestaurantListingRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import java.util.UUID
import kotlin.uuid.toKotlinUuid

/**
 * Proves the sc-155 seed-ingest migrations against a real PostGIS database:
 *
 *  - V5 creates `brands` (brand/location split).
 *  - V6 makes owner_id/cuisine nullable, adds brand_id + provenance (closed
 *    vocab) and a seed-scoped unique index over the normalised location.
 *  - V7 idempotently ingests the 30 verified seeds (28 brands) as UNVERIFIED
 *    listings with provenance 'research-seed / photon-geocode'.
 *
 * It also pins the DB-level integrity contracts (provenance CHECK, brand FK,
 * location-dedupe unique index) and proves a seed row round-trips through the
 * application-layer port ([ExposedRestaurantListingRepository]) with its nullable
 * cuisine/ownerId intact.
 */
class SeedRestaurantsMigrationTest : FunSpec() {

    private val postgis = PostgisTestDatabase()
    private lateinit var listings: RestaurantListingRepository

    /** The generated seed SQL, read from the classpath, to prove re-run idempotency. */
    private val seedSql: String =
        object {}.javaClass.getResourceAsStream("/db/migration/V7__seed_restaurants.sql")
            ?.bufferedReader()?.readText()
            ?: error("V7__seed_restaurants.sql not on the test classpath")

    /**
     * V7's INSERT statements, adapted to the post-V17 schema. V17 (sc-42)
     * replaced `cutting_method` (UNSPECIFIED seed) with the nullable boolean
     * `is_hand_cut`, so re-running the historical seed against the migrated
     * schema must target the live columns. This is exactly the transform V17
     * itself applies (UNSPECIFIED -> NULL), proving the seed stays idempotently
     * re-runnable on the current schema.
     */
    private val seedSqlPostV17: String =
        seedSql
            .replace("cutting_method", "is_hand_cut")
            .replace("'UNSPECIFIED'", "NULL")

    init {
        beforeSpec {
            postgis.start()
            listings = ExposedRestaurantListingRepository(postgis.database)
        }
        afterSpec { postgis.stop() }

        test("V5 creates the brands table with a unique name") {
            runTest {
                postgis.count("SELECT count(*) FROM information_schema.tables WHERE table_name = 'brands'") shouldBe 1
            }
        }

        test("V6 makes owner_id and cuisine nullable on restaurant_listings") {
            runTest {
                for (column in listOf("owner_id", "cuisine")) {
                    postgis.string(
                        "SELECT is_nullable FROM information_schema.columns " +
                            "WHERE table_name = 'restaurant_listings' AND column_name = ?",
                        column,
                    ) shouldBe "YES"
                }
            }
        }

        test("V6 adds brand_id and provenance columns") {
            runTest {
                val cols = postgis.rows(
                    "SELECT column_name FROM information_schema.columns " +
                        "WHERE table_name = 'restaurant_listings'",
                ).map { it["column_name"] }
                cols shouldContain "brand_id"
                cols shouldContain "provenance"
            }
        }

        test("V7 seeds 30 listings across 28 brands, all unverified + provenance") {
            runTest {
                postgis.count("SELECT count(*) FROM restaurant_listings") shouldBe 30
                postgis.count("SELECT count(*) FROM brands") shouldBe 28

                postgis.count(
                    "SELECT count(*) FROM restaurant_listings WHERE provenance = 'research-seed / photon-geocode'",
                ) shouldBe 30

                postgis.count(
                    "SELECT count(*) FROM restaurant_listings WHERE verification_status = 'UNVERIFIED'",
                ) shouldBe 30
            }
        }

        test("brand/location split: The Halal Guys is one brand with three locations") {
            runTest {
                postgis.count(
                    "SELECT count(*) FROM restaurant_listings " +
                        "WHERE brand_id = (SELECT id FROM brands WHERE name = 'The Halal Guys')",
                ) shouldBe 3
            }
        }

        test("V7 is idempotent: re-running the seed SQL adds no duplicates") {
            runTest {
                // Re-execute the exact generated seed statements (ON CONFLICT DO NOTHING),
                // adapted to the post-V17 is_hand_cut schema (sc-42).
                postgis.executeScript(seedSqlPostV17)
                postgis.count("SELECT count(*) FROM restaurant_listings") shouldBe 30
                postgis.count("SELECT count(*) FROM brands") shouldBe 28
            }
        }

        test("location-dedupe index rejects a raw duplicate seed insert (not by name)") {
            // The Halal Guys Toronto's coordinates: inserting the same normalised
            // location with seed provenance, but WITHOUT ON CONFLICT, must violate
            // the partial unique index — i.e. dedupe is by location, not by name.
            runTest {
                shouldThrow<ExposedSQLException> {
                    postgis.execute(
                        """
                        INSERT INTO restaurant_listings
                            (name, address, location, cuisine, is_hand_cut, owner_id, brand_id, provenance, verification_status)
                        VALUES (
                            'The Halal Guys', 'duplicate address',
                            ST_SetSRID(ST_MakePoint(-79.384523, 43.665206), 4326)::geography,
                            NULL, NULL, NULL, NULL, 'research-seed / photon-geocode', 'UNVERIFIED'
                        )
                        """.trimIndent(),
                    )
                }
            }
        }

        test("provenance CHECK rejects an out-of-vocabulary value") {
            runTest {
                shouldThrow<ExposedSQLException> {
                    postgis.execute(
                        """
                        INSERT INTO restaurant_listings
                            (name, address, location, cuisine, is_hand_cut, owner_id, brand_id, provenance, verification_status)
                        VALUES (
                            'X', '1 St', ST_SetSRID(ST_MakePoint(0.0, 0.0), 4326)::geography,
                            NULL, NULL, NULL, NULL, 'some-other-source', 'UNVERIFIED'
                        )
                        """.trimIndent(),
                    )
                }
            }
        }

        test("brand foreign key rejects an unknown brand_id") {
            runTest {
                shouldThrow<ExposedSQLException> {
                    postgis.execute(
                        """
                        INSERT INTO restaurant_listings
                            (name, address, location, cuisine, is_hand_cut, owner_id, brand_id, provenance, verification_status)
                        VALUES (
                            'Ghost', '1 St', ST_SetSRID(ST_MakePoint(1.0, 1.0), 4326)::geography,
                            NULL, NULL, NULL, '${UUID.randomUUID()}', 'research-seed / photon-geocode', 'UNVERIFIED'
                        )
                        """.trimIndent(),
                    )
                }
            }
        }

        test("a seed row round-trips through the repository port with nullable cuisine/owner + provenance") {
            runTest {
                val seedId = postgis.row(
                    "SELECT id FROM restaurant_listings WHERE name = 'Osmow''s' LIMIT 1",
                ).values.single()
                    .let { (it as UUID).toKotlinUuid() }

                val found = listings.findById(seedId)
                found.shouldNotBeNull()
                found.cuisine shouldBe null
                found.ownerId shouldBe null
                found.provenance shouldBe Provenance.RESEARCH_SEED_PHOTON_GEOCODE
                found.verificationStatus shouldBe VerificationStatus.UNVERIFIED
                found.isHandCut shouldBe null
                // Seed rows are backfilled as index-qualified (no cross-contamination)
                // so the curated index stays searchable (sc-119 backfill decision).
                found.crossContamination shouldBe CrossContamination.NO_CROSS_CONTAMINATION
                found.brandId.shouldNotBeNull()
                // OSM node coordinate round-trips.
                found.location.lat shouldBe (43.682921 plusOrMinus 0.0001)
                found.location.lng shouldBe (-79.418493 plusOrMinus 0.0001)
                // sc-187: the V20 backfill is visible through the read port, and the
                // street line (address) is preserved for backward compat.
                found.address shouldBe "505 St. Clair Ave W"
                found.city shouldBe "Toronto"
                found.province shouldBe "ON"
                found.postal shouldBe "M6C 1A1"
                found.country shouldBe "CA"
            }
        }

        test("V20 adds the structured address columns to both tables, nullable (sc-187)") {
            runTest {
                for (table in listOf("restaurant_listings", "listing_search")) {
                    for (column in listOf("city", "province", "postal", "country")) {
                        val row = postgis.row(
                            "SELECT is_nullable FROM information_schema.columns " +
                                "WHERE table_name = ? AND column_name = ?",
                            table, column,
                        )
                        row["is_nullable"] shouldBe "YES" // legacy/user rows carry no structured address
                    }
                }
            }
        }

        test("V20 backfilled the CA seed rows with city/province/postal/country (sc-187 non-US)") {
            runTest {
                val row = postgis.row(
                    "SELECT city, province, postal, country FROM restaurant_listings WHERE name = 'Osmow''s' LIMIT 1",
                )
                row["city"] shouldBe "Toronto"
                row["province"] shouldBe "ON"
                row["postal"] shouldBe "M6C 1A1"   // Canadian postal string — no US zip assumption
                row["country"] shouldBe "CA"
            }
        }

        test("V20 backfilled the US seed rows with structured address (sc-187)") {
            runTest {
                val row = postgis.row(
                    "SELECT city, province, postal, country FROM restaurant_listings " +
                        "WHERE name = 'Al-Amir Lebanese Restaurant & Club' LIMIT 1",
                )
                row["city"] shouldBe "Addison"
                row["province"] shouldBe "TX"
                row["postal"] shouldBe "75001"
                row["country"] shouldBe "US"
            }
        }

        test("V20 mirrored the structured address into the search projection (sc-187)") {
            runTest {
                val rows = postgis.rows(
                    """
                    SELECT s.city, s.province, s.postal, s.country
                    FROM listing_search s
                    JOIN restaurant_listings r ON r.id = s.id
                    WHERE r.name = 'Dera' LIMIT 1
                    """.trimIndent(),
                )
                rows.size shouldBe 1
                rows.single()["city"] shouldBe "Jackson Heights, Queens"
                rows.single()["province"] shouldBe "NY"
                rows.single()["postal"] shouldBe "11372"
                rows.single()["country"] shouldBe "US"
            }
        }
    }
}
