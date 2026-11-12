package com.tahirslist.persistence

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException

/**
 * SC-205 schema deltas:
 *  - V25 adds `restaurant_listings.image_version` (opaque photo version, 0 = none);
 *  - V26 admits `ADMIN` to the closed role vocabulary without promoting anyone.
 */
class Sc205SchemaMigrationTest : FunSpec({

    val postgis = PostgisTestDatabase()

    beforeSpec { postgis.start() }
    afterSpec { postgis.stop() }

    test("V25 adds a non-null image_version that defaults to 0") {
        runTest {
            val row = postgis.row(
                """
                SELECT data_type, is_nullable, column_default
                FROM information_schema.columns
                WHERE table_name = 'restaurant_listings' AND column_name = 'image_version'
                """.trimIndent(),
            )
            row["data_type"] shouldBe "bigint"
            row["is_nullable"] shouldBe "NO"
            row["column_default"] shouldBe "0"
        }
    }

    test("a listing inserted without a version starts at 0") {
        runTest {
            val id = UUID.randomUUID()
            postgis.execute(
                """
                INSERT INTO restaurant_listings
                    (id, name, address, location, cuisine, is_hand_cut, owner_id, brand_id,
                     provenance, venue_key, verification_status, alcohol_served, halal_scope,
                     cross_contamination)
                VALUES (
                    '$id', 'Versionless', '1 St',
                    ST_SetSRID(ST_MakePoint(-79.1, 43.1), 4326)::geography,
                    NULL, NULL, NULL, NULL, NULL, NULL,
                    'UNVERIFIED', false, 'NOT_DISCLOSED', 'UNCERTAIN'
                )
                """.trimIndent(),
            )

            (postgis.row("SELECT image_version FROM restaurant_listings WHERE id = '$id'")
                .values.single() as Number).toLong() shouldBe 0L
        }
    }

    test("V26 admits ADMIN and still rejects an unknown role") {
        runTest {
            // The migration promotes nobody: the table starts empty of admins.
            postgis.count("SELECT count(*) FROM users WHERE role = 'ADMIN'") shouldBe 0

            postgis.execute(
                """
                INSERT INTO users (email, password_hash, role)
                VALUES ('admin@example.test', 'hash', 'ADMIN')
                """.trimIndent(),
            )
            postgis.string("SELECT role FROM users WHERE email = 'admin@example.test'") shouldBe "ADMIN"

            shouldThrow<ExposedSQLException> {
                postgis.execute(
                    """
                    INSERT INTO users (email, password_hash, role)
                    VALUES ('nope@example.test', 'hash', 'SUPERUSER')
                    """.trimIndent(),
                )
            }
        }
    }
})
