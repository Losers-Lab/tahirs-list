package com.tahirslist.persistence.listing

import com.tahirslist.application.listing.CuisineLogic
import com.tahirslist.application.listing.ListingSearchFilters
import com.tahirslist.application.listing.ListingSearchQuery
import com.tahirslist.domain.restaurant.HalalScope
import com.tahirslist.domain.restaurant.LatLng
import com.tahirslist.persistence.PostgisTestDatabase
import com.tahirslist.persistence.count
import com.tahirslist.persistence.execute
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotBeBlank
import kotlinx.coroutines.test.runTest
import java.math.BigDecimal
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

/**
 * sc-10 location search (contract ratified in task t_847010c3): proves V8
 * creates the denormalised `listing_search` projection and that
 * [ExposedListingSearchQuery] returns listings within a radius, ordered by
 * straight-line distance ascending, with distance in miles — against the real
 * seeded (V7) PostGIS data.
 *
 * sc-119 scope: the boolean hand-cut filter (`handCutOnly`, sc-42) and the
 * cross-contamination INDEX GATE (only NO_CROSS_CONTAMINATION listings are
 * returned). Seed rows are backfilled as NO_CROSS_CONTAMINATION (V18) so the
 * curated index stays searchable.
 */
class ExposedListingSearchQueryTest : FunSpec() {

    private val postgis = PostgisTestDatabase()
    private lateinit var query: ListingSearchQuery

    init {
        beforeSpec {
            postgis.start()
            query = ExposedListingSearchQuery(postgis.database)
        }
        afterSpec { postgis.stop() }

        test("V8 creates the listing_search table with a GiST index on location") {
            runTest {
                postgis.count(
                    "SELECT count(*) FROM information_schema.tables WHERE table_name = 'listing_search'",
                ) shouldBe 1

                postgis.count(
                    """
                    SELECT count(*) FROM pg_indexes
                    WHERE tablename = 'listing_search' AND indexdef ILIKE '%USING gist (location)%'
                    """.trimIndent(),
                ) shouldBe 1
            }
        }

        test("V8 backfilled the seeded listings into listing_search") {
            runTest {
                // All 30 V7 seed rows are immediately searchable (denormalised from
                // restaurant_listings). The V18 cross-contamination backfill keeps
                // them index-qualified.
                postgis.count("SELECT count(*) FROM listing_search") shouldBe 30
            }
        }

        test("returns listings within the radius, ordered by distance ascending, with distance in miles") {
            runTest {
                val center = LatLng(lat = 43.682921, lng = -79.418493)

                val results = query.searchNearby(center = center, radiusMiles = 5.0, offset = 0, limit = 50)

                results.firstOrNull()?.name shouldBe "Osmow's"
                results.firstOrNull()!!.distanceMiles.shouldBeLessThan(0.5)

                val distances = results.map { it.distanceMiles }
                distances.zipWithNext().forEach { (a, b) -> (a <= b) shouldBe true }

                results.forEach { (it.distanceMiles <= 5.0) shouldBe true }

                results.map { it.name } shouldContain "The Halal Guys"
                results.map { it.name } shouldContain "Lazeez Shawarma"
                results.map { it.name } shouldContain "Aroma Fine Indian Cuisine"
            }
        }

        test("excludes listings beyond the radius") {
            runTest {
                val center = LatLng(lat = 43.682921, lng = -79.418493)

                val results = query.searchNearby(center = center, radiusMiles = 0.1, offset = 0, limit = 50)

                results.size shouldBe 1
                results.single().name shouldBe "Osmow's"
            }
        }

        test("returns an empty list for a center with nothing nearby (no crash)") {
            runTest {
                val middleOfNowhere = LatLng(lat = -30.0, lng = 70.0)

                val results = query.searchNearby(center = middleOfNowhere, radiusMiles = 500.0, offset = 0, limit = 50)

                results shouldNotBe null
                results shouldBe emptyList()
            }
        }

        test("offset paging returns the next page after the first") {
            runTest {
                val center = LatLng(lat = 43.682921, lng = -79.418493)

                val pageOne = query.searchNearby(center = center, radiusMiles = 5.0, offset = 0, limit = 2)
                val pageTwo = query.searchNearby(center = center, radiusMiles = 5.0, offset = 2, limit = 50)

                pageOne.size shouldBe 2
                (pageTwo.size >= 1) shouldBe true

                val allIds = (pageOne + pageTwo).map { it.id }
                allIds.distinct().size shouldBe allIds.size
                (pageTwo.first().distanceMiles >= pageOne.last().distanceMiles) shouldBe true
            }
        }

        test("results carry the full card fields, including isHandCut and halalScope") {
            runTest {
                val center = LatLng(lat = 43.682921, lng = -79.418493)

                val results = query.searchNearby(center = center, radiusMiles = 5.0, offset = 0, limit = 50)

                val osmow = results.first { it.name == "Osmow's" }
                osmow.address.shouldNotBeBlank()
                osmow.verificationStatus.name shouldBe "UNVERIFIED"
                osmow.isHandCut shouldBe null      // seed rows carry no hand-cut claim
                osmow.halalScope shouldBe HalalScope.NOT_DISCLOSED
                osmow.isDelivery shouldBe null     // seed rows carry no delivery claim (sc-184)
            }
        }

        test("search results carry the structured address fields (sc-187)") {
            runTest {
                val center = LatLng(lat = 43.682921, lng = -79.418493)

                val results = query.searchNearby(center = center, radiusMiles = 5.0, offset = 0, limit = 50)

                val osmow = results.first { it.name == "Osmow's" }
                // Street line preserved + the V20-backfilled structured fields surface.
                osmow.address shouldBe "505 St. Clair Ave W"
                osmow.city shouldBe "Toronto"
                osmow.province shouldBe "ON"
                osmow.postal shouldBe "M6C 1A1"    // Canadian postal — not a US zip
                osmow.country shouldBe "CA"
            }
        }

        test("handCutOnly filter returns only hand-cut listings (sc-42)") {
            runTest {
                // sc-42: hand-cut is an EXTRA on/off boolean. With it off (default)
                // every listing matches — hand-cut, not-hand-cut, and unknown (NULL).
                // With it on, only listings claiming hand-cut match; unknown (NULL) is
                // treated as not-hand-cut, the same null semantics price/rating use.
                insertCuttingTestRows()
                val center = LatLng(lat = 45.0, lng = -79.0)

                // Default (no filter) matches every hand-cut status, including NULL.
                val all = query.searchNearby(center = center, radiusMiles = 5.0, offset = 0, limit = 50)
                all.map { it.name }.toSet() shouldBe setOf("Hand Cut Test", "Not Hand Cut Test")

                // handCutOnly = true narrows to hand-cut only.
                val handCut = query.searchNearby(center = center, radiusMiles = 5.0, filters = ListingSearchFilters(handCutOnly = true), offset = 0, limit = 50)
                handCut.map { it.name } shouldBe listOf("Hand Cut Test")
                handCut.single().isHandCut shouldBe true

                // handCutOnly = false is the explicit "no hand-cut filter".
                val explicitlyOff = query.searchNearby(center = center, radiusMiles = 5.0, filters = ListingSearchFilters(handCutOnly = false), offset = 0, limit = 50)
                explicitlyOff.map { it.name }.toSet() shouldBe setOf("Hand Cut Test", "Not Hand Cut Test")
            }
        }

        test("handCutOnly combines with the location radius (sc-42)") {
            runTest {
                // The filter must not bypass the radius. Nesting around Osmow's
                // (unknown = not-hand-cut) co-located seed with a tiny radius:
                // handCutOnly finds nothing there, while no filter still finds Osmow's.
                val center = LatLng(lat = 43.682921, lng = -79.418493)

                val handCut = query.searchNearby(center = center, radiusMiles = 0.1, filters = ListingSearchFilters(handCutOnly = true), offset = 0, limit = 50)
                handCut shouldBe emptyList()

                val all = query.searchNearby(center = center, radiusMiles = 0.1, offset = 0, limit = 50)
                all.single().name shouldBe "Osmow's"
            }
        }

        test("the cross-contamination index gate excludes PRESENT and UNCERTAIN listings") {
            runTest {
                insertGatedRows()
                val center = LatLng(lat = 48.0, lng = -76.0)

                val any = query.searchNearby(center = center, radiusMiles = 5.0, filters = ListingSearchFilters(), offset = 0, limit = 50)
                any.map { it.name } shouldBe listOf("Qualified Test")
            }
        }

        test("deliveryOnly filter returns only listings claiming delivery (sc-184)") {
            runTest {
                // sc-184 mirrors the sc-42 hand-cut pattern: delivery is an EXTRA
                // on/off boolean. With it off (default) every listing matches —
                // delivery, no-delivery, and unknown (NULL). With it on, only listings
                // claiming delivery match; unknown (NULL) is treated as no-delivery.
                insertDeliveryTestRows()
                val center = LatLng(lat = 49.0, lng = -75.0)

                // Default (no filter) matches every delivery status, including NULL.
                val all = query.searchNearby(center = center, radiusMiles = 5.0, offset = 0, limit = 50)
                all.map { it.name }.toSet() shouldBe setOf("Delivery Test", "Pickup Test")

                // deliveryOnly = true narrows to delivery only.
                val delivery = query.searchNearby(center = center, radiusMiles = 5.0, filters = ListingSearchFilters(deliveryOnly = true), offset = 0, limit = 50)
                delivery.map { it.name } shouldBe listOf("Delivery Test")
                delivery.single().isDelivery shouldBe true

                // deliveryOnly = false is the explicit "no delivery filter".
                val explicitlyOff = query.searchNearby(center = center, radiusMiles = 5.0, filters = ListingSearchFilters(deliveryOnly = false), offset = 0, limit = 50)
                explicitlyOff.map { it.name }.toSet() shouldBe setOf("Delivery Test", "Pickup Test")
            }
        }

        test("deliveryOnly combines with the location radius (sc-184)") {
            runTest {
                // The filter must not bypass the radius. Nesting around Osmow's
                // (unknown = no-delivery) co-located seed with a tiny radius:
                // deliveryOnly finds nothing there, while no filter still finds Osmow's.
                val center = LatLng(lat = 43.682921, lng = -79.418493)

                val delivery = query.searchNearby(
                    center = center, radiusMiles = 0.1,
                    filters = ListingSearchFilters(deliveryOnly = true), offset = 0, limit = 50,
                )
                delivery shouldBe emptyList()

                val all = query.searchNearby(center = center, radiusMiles = 0.1, offset = 0, limit = 50)
                all.single().name shouldBe "Osmow's"
            }
        }

        test("price range filter narrows results to listings whose price falls inside the range") {
            runTest {
                // sc-43: insert controlled priced rows far from the seeds so these
                // assertions are isolated from the 30 NULL-price seed rows.
                insertFilterTestRows()
                val center = LatLng(lat = 46.0, lng = -78.0)

                // No price filter -> every controlled row within the radius.
                val all = query.searchNearby(center = center, radiusMiles = 5.0, offset = 0, limit = 50)
                all.map { it.name }.toSet() shouldBe setOf("Taco Mixto", "Taco Solo", "Med Grill", "Budget Eats", "No Price Wagyu")

                // min + max bound the range (inclusive).
                val mid = query.searchNearby(
                    center = center, radiusMiles = 5.0,
                    filters = ListingSearchFilters(minPrice = BigDecimal("12"), maxPrice = BigDecimal("18")),
                    offset = 0, limit = 50,
                )
                mid.map { it.name } shouldBe listOf("Taco Mixto")

                // minPrice only.
                val minOnly = query.searchNearby(
                    center = center, radiusMiles = 5.0,
                    filters = ListingSearchFilters(minPrice = BigDecimal("11")),
                    offset = 0, limit = 50,
                )
                minOnly.map { it.name }.toSet() shouldBe setOf("Taco Mixto", "Med Grill")

                // A NULL-price row never matches a price filter (V6 null semantics).
                val low = query.searchNearby(
                    center = center, radiusMiles = 5.0,
                    filters = ListingSearchFilters(maxPrice = BigDecimal("7")),
                    offset = 0, limit = 50,
                )
                low.map { it.name } shouldBe listOf("Budget Eats")
            }
        }

        test("cuisine filter with OR (default) matches a listing with ANY selected cuisine") {
            runTest {
                // sc-44: OR is the PRD default. A multi-cuisine listing matches via any
                // one of its cuisines; a NULL-cuisine listing never matches.
                insertFilterTestRows()
                val center = LatLng(lat = 46.0, lng = -78.0)

                // Mixed case proves the filter is normalised before matching the
                // lowercase-stored cuisine values.
                val or = query.searchNearby(
                    center = center, radiusMiles = 5.0,
                    filters = ListingSearchFilters(cuisines = listOf("Mexican", "Mediterranean")),
                    offset = 0, limit = 50,
                )
                or.map { it.name }.toSet() shouldBe setOf("Taco Mixto", "Taco Solo", "Med Grill")
            }
        }

        test("cuisine filter with AND matches only a listing that has ALL selected cuisines") {
            runTest {
                // sc-44: AND is the multi-cuisine case — only the listing carrying both
                // selected cuisines qualifies. The card still reports the primary cuisine.
                insertFilterTestRows()
                val center = LatLng(lat = 46.0, lng = -78.0)

                val and = query.searchNearby(
                    center = center, radiusMiles = 5.0,
                    filters = ListingSearchFilters(cuisines = listOf("mexican", "mediterranean"), cuisineLogic = CuisineLogic.AND),
                    offset = 0, limit = 50,
                )
                and.map { it.name } shouldBe listOf("Taco Mixto")
                and.single().cuisine?.value shouldBe "mexican"
            }
        }

        test("cuisine filter never matches a listing with no cuisines (NULL-cuisine seed contract)") {
            runTest {
                insertFilterTestRows()
                val center = LatLng(lat = 46.0, lng = -78.0)

                val mex = query.searchNearby(
                    center = center, radiusMiles = 5.0,
                    filters = ListingSearchFilters(cuisines = listOf("mexican")),
                    offset = 0, limit = 50,
                )
                mex.map { it.name }.toSet() shouldBe setOf("Taco Mixto", "Taco Solo")
            }
        }

        test("price + cuisine + handCutOnly filters combine with the location radius") {
            runTest {
                // sc-43 + sc-44 + sc-42: a listing must satisfy every active predicate
                // simultaneously (hand-cut AND mexican AND price 9..16, within radius).
                insertFilterTestRows()
                val center = LatLng(lat = 46.0, lng = -78.0)

                val combined = query.searchNearby(
                    center = center, radiusMiles = 5.0,
                    filters = ListingSearchFilters(
                        handCutOnly = true,
                        cuisines = listOf("mexican"),
                        minPrice = BigDecimal("9"),
                        maxPrice = BigDecimal("16"),
                    ),
                    offset = 0, limit = 50,
                )
                combined.map { it.name } shouldBe listOf("Taco Mixto")
            }
        }

        test("minRating filter narrows results to listings whose rating meets the threshold") {
            runTest {
                insertRatedTestRows()
                val center = LatLng(lat = 47.0, lng = -77.0)

                val high = query.searchNearby(
                    center = center, radiusMiles = 5.0,
                    filters = ListingSearchFilters(minRating = BigDecimal("3.0")),
                    offset = 0, limit = 50,
                )
                high.map { it.name }.toSet() shouldBe setOf("Star Grill", "Clover Cafe")

                val ceiling = query.searchNearby(
                    center = center, radiusMiles = 5.0,
                    filters = ListingSearchFilters(minRating = BigDecimal("4.9")),
                    offset = 0, limit = 50,
                )
                ceiling shouldBe emptyList()
            }
        }

        test("minRating filter excludes NULL-rating listings (null semantics)") {
            runTest {
                insertRatedTestRows()
                val center = LatLng(lat = 47.0, lng = -77.0)

                val anyRated = query.searchNearby(
                    center = center, radiusMiles = 5.0,
                    filters = ListingSearchFilters(minRating = BigDecimal.ZERO),
                    offset = 0, limit = 50,
                )
                anyRated.map { it.name }.toSet() shouldBe setOf("Star Grill", "Clover Cafe", "Rustic Table")
            }
        }

        test("minRating filter combines with the location radius and excludes NULL-rating listings") {
            runTest {
                val center = LatLng(lat = 43.682921, lng = -79.418493)

                val unfiltered = query.searchNearby(center = center, radiusMiles = 5.0, offset = 0, limit = 50)
                (unfiltered.size > 0) shouldBe true

                val rated = query.searchNearby(
                    center = center, radiusMiles = 5.0,
                    filters = ListingSearchFilters(minRating = BigDecimal("1.0")),
                    offset = 0, limit = 50,
                )
                rated shouldBe emptyList()
            }
        }

        test("minRating combines with handCut, price, cuisine and the location radius") {
            runTest {
                insertRatedTestRows()
                val center = LatLng(lat = 47.0, lng = -77.0)

                val combined = query.searchNearby(
                    center = center, radiusMiles = 5.0,
                    filters = ListingSearchFilters(
                        minRating = BigDecimal("2.0"),
                        handCutOnly = true,
                        cuisines = listOf("mexican"),
                        minPrice = BigDecimal("12"),
                        maxPrice = BigDecimal("16"),
                    ),
                    offset = 0, limit = 50,
                )
                combined.map { it.name } shouldBe listOf("Star Grill")
            }
        }
    }

    /** Inserts five controlled filter-test rows at 46N/78W, all NO_CROSS_CONTAMINATION. Idempotent. */
    private suspend fun insertFilterTestRows() {
        // (id, name, cuisine, isHandCut, price, cuisines)
        val rows = listOf(
            listOf("20000000-0000-0000-0000-000000000001", "Taco Mixto", "mexican", true, "15.00", listOf("mexican", "mediterranean")),
            listOf("20000000-0000-0000-0000-000000000002", "Taco Solo", "mexican", null, "10.00", listOf("mexican")),
            listOf("20000000-0000-0000-0000-000000000003", "Med Grill", "mediterranean", true, "20.00", listOf("mediterranean")),
            listOf("20000000-0000-0000-0000-000000000004", "Budget Eats", null, null, "5.00", emptyList<String>()),
            listOf("20000000-0000-0000-0000-000000000005", "No Price Wagyu", null, null, null, emptyList<String>()),
        )
        val ids = rows.map { Uuid.parse(it[0] as String) }

        rows.forEach { row ->
            postgis.execute(
                """
                INSERT INTO restaurant_listings (id, name, address, location, cuisine, is_hand_cut, verification_status, price, cross_contamination)
                VALUES (?, ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?, ?, 'UNVERIFIED', ?, 'NO_CROSS_CONTAMINATION')
                ON CONFLICT (id) DO NOTHING
                """.trimIndent(),
                Uuid.parse(row[0] as String).toJavaUuid(),
                row[1] as String,
                "46.00, -78.00",
                -78.0, 46.0,
                row[2] as String?,
                row[3] as Boolean?,
                (row[4] as String?)?.let { BigDecimal(it) },
            )
        }

        // Mirror into the denormalised projection, exactly the sc-10 save() contract.
        postgis.execute(
            """
            INSERT INTO listing_search (id, name, address, location, cuisine, is_hand_cut, verification_status, price, cross_contamination)
            SELECT id, name, address, location, cuisine, is_hand_cut, verification_status, price, cross_contamination
            FROM restaurant_listings WHERE id IN (?, ?, ?, ?, ?)
            ON CONFLICT (id) DO NOTHING
            """.trimIndent(),
            ids[0].toJavaUuid(), ids[1].toJavaUuid(), ids[2].toJavaUuid(), ids[3].toJavaUuid(), ids[4].toJavaUuid(),
        )

        rows.filter { (it[5] as List<*>).isNotEmpty() }.forEach { row ->
            val id = Uuid.parse(row[0] as String)
            (row[5] as List<*>).forEach { cuisine ->
                postgis.execute(
                    """
                    INSERT INTO restaurant_listing_cuisines (listing_id, cuisine)
                    VALUES (?, ?)
                    ON CONFLICT (listing_id, cuisine) DO NOTHING
                    """.trimIndent(),
                    id.toJavaUuid(), cuisine as String,
                )
            }
        }
    }

    /** Inserts two controlled cutting-test rows at 45N/79W, all NO_CROSS_CONTAMINATION. Idempotent. */
    private suspend fun insertCuttingTestRows() {
        val handId = Uuid.parse("10000000-0000-0000-0000-000000000001")
        val notHandId = Uuid.parse("10000000-0000-0000-0000-000000000002")

        postgis.execute(
            """
            INSERT INTO restaurant_listings (id, name, address, location, cuisine, is_hand_cut, verification_status, cross_contamination)
            VALUES (?, ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?, ?, 'UNVERIFIED', 'NO_CROSS_CONTAMINATION')
            ON CONFLICT (id) DO NOTHING
            """.trimIndent(),
            handId.toJavaUuid(), "Hand Cut Test", "45.00, -79.00", -79.0, 45.0, "Grill", true,
        )
        postgis.execute(
            """
            INSERT INTO restaurant_listings (id, name, address, location, cuisine, is_hand_cut, verification_status, cross_contamination)
            VALUES (?, ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?, ?, 'UNVERIFIED', 'NO_CROSS_CONTAMINATION')
            ON CONFLICT (id) DO NOTHING
            """.trimIndent(),
            notHandId.toJavaUuid(), "Not Hand Cut Test", "45.00, -79.00", -79.0, 45.0, "Grill", false,
        )
        // Mirror into the denormalised projection, exactly the sc-10 save() contract.
        postgis.execute(
            """
            INSERT INTO listing_search (id, name, address, location, cuisine, is_hand_cut, verification_status, cross_contamination)
            SELECT id, name, address, location, cuisine, is_hand_cut, verification_status, cross_contamination
            FROM restaurant_listings WHERE id IN (?, ?)
            ON CONFLICT (id) DO NOTHING
            """.trimIndent(),
            handId.toJavaUuid(), notHandId.toJavaUuid(),
        )
    }

    /**
     * Inserts two controlled delivery-test rows at 49N/75W (far from the seeds
     * and the gate rows at 48N/76W) and mirrors them into listing_search exactly
     * as save() does, isolating the delivery assertions from the NULL-delivery
     * seeds and the cutting (45N/79W) / filter (46N/78W) / rating (47N/77W) /
     * gate (48N/76W) rows. Both are NO_CROSS_CONTAMINATION so they pass the
     * sc-119 index gate. Idempotent via ON CONFLICT.
     */
    private suspend fun insertDeliveryTestRows() {
        val deliveryId = Uuid.parse("60000000-0000-0000-0000-000000000001")
        val pickupId = Uuid.parse("60000000-0000-0000-0000-000000000002")

        postgis.execute(
            """
            INSERT INTO restaurant_listings (id, name, address, location, cuisine, is_hand_cut, is_delivery, verification_status, cross_contamination)
            VALUES (?, ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?, ?, ?, 'UNVERIFIED', 'NO_CROSS_CONTAMINATION')
            ON CONFLICT (id) DO NOTHING
            """.trimIndent(),
            deliveryId.toJavaUuid(), "Delivery Test", "49.00, -75.00", -75.0, 49.0, "Grill", true, true,
        )
        postgis.execute(
            """
            INSERT INTO restaurant_listings (id, name, address, location, cuisine, is_hand_cut, is_delivery, verification_status, cross_contamination)
            VALUES (?, ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?, ?, ?, 'UNVERIFIED', 'NO_CROSS_CONTAMINATION')
            ON CONFLICT (id) DO NOTHING
            """.trimIndent(),
            pickupId.toJavaUuid(), "Pickup Test", "49.00, -75.00", -75.0, 49.0, "Grill", false, false,
        )
        // Mirror into the denormalised projection, exactly the sc-10 save() contract.
        postgis.execute(
            """
            INSERT INTO listing_search (id, name, address, location, cuisine, is_hand_cut, is_delivery, verification_status, cross_contamination)
            SELECT id, name, address, location, cuisine, is_hand_cut, is_delivery, verification_status, cross_contamination
            FROM restaurant_listings WHERE id IN (?, ?)
            ON CONFLICT (id) DO NOTHING
            """.trimIndent(),
            deliveryId.toJavaUuid(), pickupId.toJavaUuid(),
        )
    }

    /**
     * Inserts three controlled cross-contamination gate rows at 48N/76W and
     * mirrors them into listing_search exactly as save() does, relying on the
     * query-level gate to exclude the PRESENT/UNCERTAIN rows
     * (defence-in-depth). Idempotent via ON CONFLICT.
     */
    private suspend fun insertGatedRows() {
        val qualified = Uuid.parse("30000000-0000-0000-0000-000000000001")
        val present = Uuid.parse("30000000-0000-0000-0000-000000000002")
        val uncertain = Uuid.parse("30000000-0000-0000-0000-000000000003")

        postgis.execute(
            """
            INSERT INTO restaurant_listings (id, name, address, location, cuisine, is_hand_cut, verification_status, cross_contamination)
            VALUES (?, ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?, ?, 'UNVERIFIED', ?)
            ON CONFLICT (id) DO NOTHING
            """.trimIndent(),
            qualified.toJavaUuid(), "Qualified Test", "48.00, -76.00", -76.0, 48.0, "Grill", true, "NO_CROSS_CONTAMINATION",
        )
        postgis.execute(
            """
            INSERT INTO restaurant_listings (id, name, address, location, cuisine, is_hand_cut, verification_status, cross_contamination)
            VALUES (?, ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?, ?, 'UNVERIFIED', ?)
            ON CONFLICT (id) DO NOTHING
            """.trimIndent(),
            present.toJavaUuid(), "Gated Present Test", "48.00, -76.00", -76.0, 48.0, "Grill", false, "PRESENT",
        )
        postgis.execute(
            """
            INSERT INTO restaurant_listings (id, name, address, location, cuisine, is_hand_cut, verification_status, cross_contamination)
            VALUES (?, ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?, ?, 'UNVERIFIED', ?)
            ON CONFLICT (id) DO NOTHING
            """.trimIndent(),
            uncertain.toJavaUuid(), "Gated Uncertain Test", "48.00, -76.00", -76.0, 48.0, "Grill", null, "UNCERTAIN",
        )

        // Mirror all three into the index and rely on the query-level gate to
        // exclude the non-qualified rows, proving defence-in-depth.
        postgis.execute(
            """
            INSERT INTO listing_search (id, name, address, location, cuisine, is_hand_cut, verification_status, cross_contamination)
            SELECT id, name, address, location, cuisine, is_hand_cut, verification_status, cross_contamination
            FROM restaurant_listings WHERE id IN (?, ?, ?)
            ON CONFLICT (id) DO NOTHING
            """.trimIndent(),
            qualified.toJavaUuid(), present.toJavaUuid(), uncertain.toJavaUuid(),
        )
    }

    /** Inserts four controlled rating rows at 47N/77W, all NO_CROSS_CONTAMINATION. Idempotent. */
    private suspend fun insertRatedTestRows() {
        // (id, name, cuisine, isHandCut, price, rating)
        val rows = listOf(
            listOf("40000000-0000-0000-0000-000000000001", "Star Grill", "mexican", true, "15.00", "4.8"),
            listOf("40000000-0000-0000-0000-000000000002", "Clover Cafe", "mexican", null, "10.00", "3.5"),
            listOf("40000000-0000-0000-0000-000000000003", "Rustic Table", "mediterranean", false, "20.00", "2.5"),
            listOf("40000000-0000-0000-0000-000000000004", "No Rating Bistro", null, null, "5.00", null),
        )
        val ids = rows.map { Uuid.parse(it[0] as String) }

        rows.forEach { row ->
            postgis.execute(
                """
                INSERT INTO restaurant_listings (id, name, address, location, cuisine, is_hand_cut, verification_status, price, rating, cross_contamination)
                VALUES (?, ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?, ?, 'UNVERIFIED', ?, ?, 'NO_CROSS_CONTAMINATION')
                ON CONFLICT (id) DO NOTHING
                """.trimIndent(),
                Uuid.parse(row[0] as String).toJavaUuid(),
                row[1] as String,
                "47.00, -77.00",
                -77.0, 47.0,
                row[2] as String?,
                row[3] as Boolean?,
                (row[4] as String?)?.let { BigDecimal(it) },
                (row[5] as String?)?.let { BigDecimal(it) },
            )
        }

        // Mirror into the denormalised projection, exactly the sc-10 save() contract.
        postgis.execute(
            """
            INSERT INTO listing_search (id, name, address, location, cuisine, is_hand_cut, verification_status, price, rating, cross_contamination)
            SELECT id, name, address, location, cuisine, is_hand_cut, verification_status, price, rating, cross_contamination
            FROM restaurant_listings WHERE id IN (?, ?, ?, ?)
            ON CONFLICT (id) DO NOTHING
            """.trimIndent(),
            ids[0].toJavaUuid(), ids[1].toJavaUuid(), ids[2].toJavaUuid(), ids[3].toJavaUuid(),
        )

        // Multi-cuisine rows for the combined-filter assertion.
        rows.filter { (it[2] as String?) != null }.forEach { row ->
            postgis.execute(
                """
                INSERT INTO restaurant_listing_cuisines (listing_id, cuisine)
                VALUES (?, ?)
                ON CONFLICT (listing_id, cuisine) DO NOTHING
                """.trimIndent(),
                Uuid.parse(row[0] as String).toJavaUuid(), row[2] as String,
            )
        }
    }
}
