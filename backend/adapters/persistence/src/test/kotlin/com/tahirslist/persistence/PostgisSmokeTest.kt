package com.tahirslist.persistence

import com.tahirslist.persistence.db.Migrations
import com.tahirslist.persistence.db.queryRows
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction

/**
 * TDD harness smoke test. Proves that a real PostGIS container can be started
 * via Testcontainers, reached through Exposed, queried with PostGIS spatial
 * functions, and migrated by Flyway — the exact tooling every persistence test
 * relies on.
 */
class PostgisSmokeTest : FunSpec() {

    private val postgis = PostgisTestDatabase()

    init {
        // No migration here: the second test exercises the Flyway facade itself.
        beforeSpec { postgis.start(migrate = false) }
        afterSpec { postgis.stop() }

        test("connects to PostGIS over Exposed and can run a spatial query") {
            postgis.isRunning shouldBe true

            runTest {
                suspendTransaction(postgis.database) {
                    // idempotent — exercises the PostGIS extension regardless of
                    // whether the image pre-enabled it in the test database.
                    exec("CREATE EXTENSION IF NOT EXISTS postgis")
                    queryRows("SELECT postgis_version()") { rs -> rs.getString(1) }
                        .single()
                        .shouldNotBeBlank()
                }
            }
        }

        test("Flyway connects and runs against the live PostGIS database") {
            val result = Migrations.migrate(postgis.jdbcUrl, postgis.username, postgis.password)

            // users (V1), refresh_tokens (V2), the sc-136 token-family
            // columns on refresh_tokens (V3), restaurant_listings (V4),
            // brands (V5), the seed-listing schema (V6), the seed data
            // (V7), the sc-10 search projection list (V8), the sc-43/44
            // price + multi-cuisine columns (V9), the sc-45/72 rating
            // column (V10), the sc-118 alcohol_served column (V11), the
            // sc-50/51/52 favorites table (V12), the sc-46
            // halal_certification_reviews table (V13), the sc-120
            // ai_consent_at column on reviews (V14), the sc-73
            // VERIFIED-status opening on restaurant_listings (V15), the
            // sc-73 read-surface certifier + expires_on columns on reviews
            // (V16), the sc-42 hand-cut boolean (V17), the sc-119 partial-halal
            // columns + restaurant_halal_items (V18), the sc-184
            // is_delivery column (V19), the sc-187 structured address
            // columns (V20), the sc-73 certificate_key column on reviews
            // (V21), the sc-211 ingestion schema (V22), the SC-198
            // geocoded_addresses cache table (V23), and the sc-211 round-2
            // trust + location correction (V24) all run cleanly on a real
            // PostGIS DB.
            result.migrationsExecuted shouldBe 26
        }
    }
}
