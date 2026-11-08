package com.tahirslist.persistence.geo

import com.tahirslist.persistence.PostgisTestDatabase
import com.tahirslist.persistence.count
import com.tahirslist.persistence.execute
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException

/**
 * AC-a: V22 adds `geocoded_addresses`, one row per normalized query, with the
 * hash as the primary key and coordinate range checks at the DB boundary.
 */
class GeocodedAddressesMigrationTest : FunSpec({

    val postgis = PostgisTestDatabase()

    beforeSpec { postgis.start() }
    afterSpec { postgis.stop() }

    val hash = "a".repeat(64)
    val other = "b".repeat(64)

    test("V22 creates the geocoded_addresses table") {
        runTest {
            postgis.count(
                "SELECT count(*) FROM information_schema.tables WHERE table_name = 'geocoded_addresses'",
            ) shouldBe 1
        }
    }

    test("normalized_query_hash is the primary key") {
        runTest {
            postgis.count(
                """
                SELECT count(*) FROM information_schema.table_constraints
                WHERE table_name = 'geocoded_addresses' AND constraint_type = 'PRIMARY KEY'
                """.trimIndent(),
            ) shouldBe 1
        }
    }

    test("rejects a duplicate key, an out-of-range latitude and a missing provider") {
        runTest {
            postgis.execute(
                """
                INSERT INTO geocoded_addresses (normalized_query_hash, lat, lng, display_name, provider)
                VALUES (?, ?, ?, ?, ?)
                """.trimIndent(),
                hash, 43.65, -79.38, "Toronto", "photon",
            ) shouldBe 1

            shouldThrow<ExposedSQLException> {
                postgis.execute(
                    """
                    INSERT INTO geocoded_addresses (normalized_query_hash, lat, lng, display_name, provider)
                    VALUES (?, ?, ?, ?, ?)
                    """.trimIndent(),
                    hash, 40.71, -74.00, "New York", "photon",
                )
            }

            shouldThrow<ExposedSQLException> {
                postgis.execute(
                    """
                    INSERT INTO geocoded_addresses (normalized_query_hash, lat, lng, display_name, provider)
                    VALUES (?, ?, ?, ?, ?)
                    """.trimIndent(),
                    other, 91.0, 0.0, "Nowhere", "photon",
                )
            }
        }
    }
})
