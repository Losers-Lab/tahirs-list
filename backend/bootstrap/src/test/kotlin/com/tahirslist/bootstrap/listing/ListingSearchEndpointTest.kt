package com.tahirslist.bootstrap.listing

import com.tahirslist.bootstrap.ApiResponse
import com.tahirslist.bootstrap.KtorPostgresTest
import com.tahirslist.bootstrap.asString
import com.tahirslist.bootstrap.dbUpdate
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.jsonObject
import java.math.BigDecimal
import java.util.UUID

/**
 * sc-10 location search endpoint (task t_847010c3): GET /v1/listings/search.
 *
 * Contract exercised end-to-end against the real Flyway seed data:
 *  1. `?center=<lat,lon>&radius=<miles>` returns listings within radius, ordered
 *     distance ascending, each carrying `distanceMiles` in straight-line miles;
 *  2. a centre with nothing nearby returns an empty list (no crash);
 *  3. malformed / missing centre or radius returns 400 `invalid_input` (not 500);
 *  4. the query is public (no token) like the rest of the sc-157 read surface.
 *
 * The 30-listing seed (V6/V7 + the V20 structured-address backfill) backs the
 * seed assertions; controlled fixture rows isolate the price / cuisine / rating /
 * delivery filters.
 */
class ListingSearchEndpointTest : KtorPostgresTest() {

    /** One controlled fixture listing, mirrored into the search projection. */
    private data class FixtureListing(
        val id: String,
        val name: String,
        val cuisine: String?,
        val isHandCut: Boolean?,
        val price: String?,
        val cuisines: List<String>,
    )

    private fun namesOf(resp: ApiResponse): List<String> =
        resp.jsonArray().map { it.jsonObject["name"].asString() }

    init {
        test("search returns listings within the radius, ordered by distance ascending, with distanceMiles") {
            // Center on Osmow's (St. Clair, Toronto).
            val resp = get("/v1/listings/search?center=43.682921,-79.418493&radius=5.0")

            resp.status shouldBe 200
            val results = resp.jsonArray()
            (results.size > 0) shouldBe true

            // Osmow's co-located with the centre — the nearest result.
            results[0].jsonObject["name"].asString() shouldBe "Osmow's"
            (results[0].jsonObject["distanceMiles"].asString().toDouble() < 0.5) shouldBe true

            // Ordered ascending by distance.
            val distances = results.map { it.jsonObject["distanceMiles"].asString().toDouble() }
            distances.zipWithNext().forEach { (a, b) -> (a <= b) shouldBe true }

            // Every result carries a distance within the radius and lands inside it.
            results.forEach { node ->
                val result = node.jsonObject
                (result["distanceMiles"].asString().toDouble() <= 5.0) shouldBe true
                result.containsKey("id") shouldBe true
                result.containsKey("name") shouldBe true
                result.containsKey("address") shouldBe true
                result.containsKey("lat") shouldBe true
                result.containsKey("lng") shouldBe true
            }
        }

        test("search returns an empty array for a centre far from every listing (no crash)") {
            // Middle of the Indian Ocean.
            val resp = get("/v1/listings/search?center=-30.0,70.0&radius=500.0")

            resp.status shouldBe 200
            resp.jsonArray().size shouldBe 0
        }

        test("a successful search advertises the freshness window (SC-199 AC-2)") {
            val resp = get("/v1/listings/search?center=43.682921,-79.418493&radius=5.0")

            resp.status shouldBe 200
            resp.header("Cache-Control") shouldBe
                "public, max-age=60, s-maxage=300, stale-while-revalidate=60"
        }

        test("an error response keeps no-store and advertises no freshness (SC-199 AC-5)") {
            val resp = get("/v1/listings/search?center=not-a-latlong&radius=5.0")

            resp.status shouldBe 400
            resp.header("Cache-Control") shouldBe "no-store"
        }

        test("two centres in one 4 dp cell return the same body (SC-199 AC-4)") {
            val first = get("/v1/listings/search?center=43.68292100,-79.41849300&radius=5.0")
            val second = get("/v1/listings/search?center=43.68292140,-79.41849340&radius=5.0")

            first.status shouldBe 200
            second.status shouldBe 200
            second.body shouldBe first.body
        }

        test("search is public (no token required), like the rest of the read surface") {
            get("/v1/listings/search?center=43.682921,-79.418493&radius=5.0").status shouldBe 200
        }

        test("a malformed centre returns 400 invalid_input, not a crash") {
            val resp = get("/v1/listings/search?center=not-a-latlong&radius=5.0")

            resp.status shouldBe 400
            resp.json()["code"].asString() shouldBe "invalid_input"
        }

        test("a centre out of range returns 400 invalid_input") {
            val resp = get("/v1/listings/search?center=999.0,-999.0&radius=5.0")

            resp.status shouldBe 400
            resp.json()["code"].asString() shouldBe "invalid_input"
        }

        test("a missing centre returns 400 invalid_input") {
            get("/v1/listings/search?radius=5.0").status shouldBe 400
        }

        test("a zero radius returns 400 invalid_input, not a crash") {
            get("/v1/listings/search?center=43.682921,-79.418493&radius=0.0").status shouldBe 400
        }

        test("a negative radius returns 400 invalid_input") {
            get("/v1/listings/search?center=43.682921,-79.418493&radius=-5.0").status shouldBe 400
        }

        test("handCutOnly=true narrows the search and excludes the unknown seed (sc-42)") {
            // sc-42: the entire seed set has an unknown (NULL) hand-cut status, so
            // a handCutOnly filter around St. Clair returns nothing, while the
            // same query without the filter returns hits — proving it narrows.
            (get("/v1/listings/search?center=43.682921,-79.418493&radius=5.0").jsonArray().size > 0) shouldBe true

            val handCut = get("/v1/listings/search?center=43.682921,-79.418493&radius=5.0&handCutOnly=true")
            handCut.status shouldBe 200
            handCut.jsonArray().size shouldBe 0
        }

        test("handCutOnly=false matches the no-filter result set (any cutting status)") {
            val noFilter = get("/v1/listings/search?center=43.682921,-79.418493&radius=5.0")
            val explicitlyOff = get("/v1/listings/search?center=43.682921,-79.418493&radius=5.0&handCutOnly=false")

            explicitlyOff.status shouldBe 200
            val results = explicitlyOff.jsonArray()
            (results.size > 0) shouldBe true
            results[0].jsonObject["name"].asString() shouldBe "Osmow's"
            (results.size == noFilter.jsonArray().size) shouldBe true
        }

        test("a non-boolean handCutOnly returns 400 invalid_input, not a crash") {
            val resp = get("/v1/listings/search?center=43.682921,-79.418493&radius=5.0&handCutOnly=STUNK")

            resp.status shouldBe 400
            resp.json()["code"].asString() shouldBe "invalid_input"
        }

        test("deliveryOnly=true narrows the search and excludes the unknown seed (sc-184)") {
            // sc-184: the entire seed set has an unknown (NULL) delivery status, so
            // a deliveryOnly filter around St. Clair returns nothing, while the
            // same query without the filter returns hits — proving it narrows.
            (get("/v1/listings/search?center=43.682921,-79.418493&radius=5.0").jsonArray().size > 0) shouldBe true

            val delivery = get("/v1/listings/search?center=43.682921,-79.418493&radius=5.0&deliveryOnly=true")
            delivery.status shouldBe 200
            delivery.jsonArray().size shouldBe 0
        }

        test("deliveryOnly=false matches the no-filter result set (any delivery status)") {
            val noFilter = get("/v1/listings/search?center=43.682921,-79.418493&radius=5.0")
            val explicitlyOff = get("/v1/listings/search?center=43.682921,-79.418493&radius=5.0&deliveryOnly=false")

            explicitlyOff.status shouldBe 200
            val results = explicitlyOff.jsonArray()
            (results.size > 0) shouldBe true
            results[0].jsonObject["name"].asString() shouldBe "Osmow's"
            (results.size == noFilter.jsonArray().size) shouldBe true
        }

        test("a non-boolean deliveryOnly returns 400 invalid_input, not a crash") {
            val resp = get("/v1/listings/search?center=43.682921,-79.418493&radius=5.0&deliveryOnly=STUNK")

            resp.status shouldBe 400
            resp.json()["code"].asString() shouldBe "invalid_input"
        }

        test("each search card exposes the isDelivery read field (sc-184)") {
            // Insert controlled delivery rows far from the NULL-delivery seeds so
            // the read field can be asserted as true/false on the wire.
            insertDeliveryEndpointRows()
            val base = "/v1/listings/search?center=48.0,-76.0&radius=2.0"

            val resp = get(base)
            resp.status shouldBe 200
            val byName = resp.jsonArray().associate { it.jsonObject["name"].asString() to it.jsonObject }
            byName.getValue("Delivery Test")["isDelivery"].asString().toBoolean() shouldBe true
            byName.getValue("Pickup Test")["isDelivery"].asString().toBoolean() shouldBe false

            // deliveryOnly narrows the same rows.
            namesOf(get("$base&deliveryOnly=true")) shouldBe listOf("Delivery Test")
        }

        test("each search card exposes the structured address fields (sc-187)") {
            // A seed row around St. Clair surfaces the V20-backfilled structured
            // fields on the search card, with the street line preserved.
            val resp = get("/v1/listings/search?center=43.682921,-79.418493&radius=5.0")
            resp.status shouldBe 200

            val results = resp.jsonArray()
            val osmow = results[0].jsonObject // Osmow's is co-located with the centre
            osmow["name"].asString() shouldBe "Osmow's"
            osmow["address"].asString() shouldBe "505 St. Clair Ave W"
            osmow["city"].asString() shouldBe "Toronto"
            osmow["province"].asString() shouldBe "ON"
            osmow["postal"].asString() shouldBe "M6C 1A1" // Canadian postal — not a US zip
            osmow["country"].asString() shouldBe "CA"
        }

        test("price range narrows results through the live search endpoint (sc-43)") {
            // Controlled priced rows far from the seeds (mirrored into listing_search).
            insertFilterRows()
            val base = "/v1/listings/search?center=45.5,-78.5&radius=2.0"

            val resp = get("$base&minPrice=12&maxPrice=18")
            resp.status shouldBe 200
            namesOf(resp) shouldBe listOf("Taco Mixto")
        }

        test("cuisine filter OR (default, no cuisineLogic) matches any selected cuisine (sc-44)") {
            insertFilterRows()
            val base = "/v1/listings/search?center=45.5,-78.5&radius=2.0"

            // No cuisineLogic -> PRD-default OR. Multi-cuisine Taco Mixto matches
            // via either cuisine; NULL-cuisine Budget Eats / No Price Wagyu never match.
            val resp = get("$base&cuisine=mexican&cuisine=mediterranean")
            resp.status shouldBe 200
            namesOf(resp).toSet() shouldBe setOf("Taco Mixto", "Taco Solo", "Med Grill")
        }

        test("cuisine filter AND requires the listing to carry every selected cuisine (sc-44)") {
            insertFilterRows()
            val base = "/v1/listings/search?center=45.5,-78.5&radius=2.0"

            val resp = get("$base&cuisine=mexican&cuisine=mediterranean&cuisineLogic=AND")
            resp.status shouldBe 200
            namesOf(resp) shouldBe listOf("Taco Mixto")
        }

        test("cuisine values are normalised (mixed case matches the lowercase-stored value)") {
            insertFilterRows()
            val base = "/v1/listings/search?center=45.5,-78.5&radius=2.0"

            val resp = get("$base&cuisine=MEXICAN")
            resp.status shouldBe 200
            namesOf(resp).toSet() shouldBe setOf("Taco Mixto", "Taco Solo")
        }

        test("an invalid cuisineLogic returns 400 invalid_input, not a crash") {
            val resp = get("/v1/listings/search?center=45.5,-78.5&radius=2.0&cuisine=mexican&cuisineLogic=BOTH")

            resp.status shouldBe 400
            resp.json()["code"].asString() shouldBe "invalid_input"
        }

        test("a negative minPrice returns 400 invalid_input") {
            val resp = get("/v1/listings/search?center=45.5,-78.5&radius=2.0&minPrice=-5.0")

            resp.status shouldBe 400
            resp.json()["code"].asString() shouldBe "invalid_input"
        }

        test("minPrice greater than maxPrice returns 400 invalid_input") {
            val resp = get("/v1/listings/search?center=45.5,-78.5&radius=2.0&minPrice=20.0&maxPrice=10.0")

            resp.status shouldBe 400
            resp.json()["code"].asString() shouldBe "invalid_input"
        }

        test("minRating narrows results through the live search endpoint (sc-45)") {
            // Controlled rated rows far from the NULL-rating seeds.
            insertRatedEndpointRows()
            val base = "/v1/listings/search?center=47.0,-77.0&radius=2.0"

            val resp = get("$base&minRating=4.0")
            resp.status shouldBe 200
            namesOf(resp) shouldBe listOf("Star Grill")
        }

        test("minRating combines with distance radius and all prior filters through the endpoint") {
            insertRatedEndpointRows()
            val base = "/v1/listings/search?center=47.0,-77.0&radius=2.0"

            val resp = get("$base&minRating=1.0&handCutOnly=true&cuisine=mexican&minPrice=12&maxPrice=16")
            resp.status shouldBe 200
            namesOf(resp) shouldBe listOf("Star Grill")
        }

        test("a negative minRating returns 400 invalid_input") {
            val resp = get("/v1/listings/search?center=45.5,-78.5&radius=2.0&minRating=-1.0")

            resp.status shouldBe 400
            resp.json()["code"].asString() shouldBe "invalid_input"
        }

        test("a minRating above the 0..5 scale returns 400 invalid_input") {
            val resp = get("/v1/listings/search?center=45.5,-78.5&radius=2.0&minRating=5.5")

            resp.status shouldBe 400
            resp.json()["code"].asString() shouldBe "invalid_input"
        }
    }

    /**
     * Inserts five controlled price/cuisine rows at 45.5N/78.5W and mirrors them
     * into listing_search, so the price/cuisine endpoint assertions are isolated
     * from the NULL-cuisine/NULL-price seed rows. Idempotent via ON CONFLICT —
     * the shared container accumulates rows across the suite, so repeated runs
     * must not duplicate.
     */
    private suspend fun insertFilterRows() {
        val rows = listOf(
            FixtureListing(
                "30000000-0000-0000-0000-000000000001", "Taco Mixto", "mexican", true, "15.00",
                listOf("mexican", "mediterranean"),
            ),
            FixtureListing("30000000-0000-0000-0000-000000000002", "Taco Solo", "mexican", null, "10.00", listOf("mexican")),
            FixtureListing("30000000-0000-0000-0000-000000000003", "Med Grill", "mediterranean", null, "20.00", listOf("mediterranean")),
            FixtureListing("30000000-0000-0000-0000-000000000004", "Budget Eats", null, null, "5.00", emptyList()),
            FixtureListing("30000000-0000-0000-0000-000000000005", "No Price Wagyu", null, null, null, emptyList()),
        )
        rows.forEach { row ->
            dbUpdate(
                """
                INSERT INTO restaurant_listings (id, name, address, location, cuisine, is_hand_cut, verification_status, price, cross_contamination)
                VALUES (?, ?, ?, ST_SetSRID(ST_MakePoint(-78.5, 45.5), 4326)::geography, ?, ?, 'UNVERIFIED', ?, 'NO_CROSS_CONTAMINATION')
                ON CONFLICT (id) DO NOTHING
                """.trimIndent(),
                UUID.fromString(row.id), row.name, "45.50, -78.50",
                row.cuisine, row.isHandCut, row.price?.let { BigDecimal(it) },
            )
        }
        dbUpdate(
            """
            INSERT INTO listing_search (id, name, address, location, cuisine, is_hand_cut, verification_status, price, cross_contamination)
            SELECT id, name, address, location, cuisine, is_hand_cut, verification_status, price, cross_contamination
            FROM restaurant_listings WHERE id IN (?, ?, ?, ?, ?)
            ON CONFLICT (id) DO NOTHING
            """.trimIndent(),
            UUID.fromString(rows[0].id),
            UUID.fromString(rows[1].id),
            UUID.fromString(rows[2].id),
            UUID.fromString(rows[3].id),
            UUID.fromString(rows[4].id),
        )
        rows.filter { it.cuisines.isNotEmpty() }.forEach { row ->
            row.cuisines.forEach { cuisine ->
                dbUpdate(
                    "INSERT INTO restaurant_listing_cuisines (listing_id, cuisine) VALUES (?, ?) ON CONFLICT DO NOTHING",
                    UUID.fromString(row.id), cuisine,
                )
            }
        }
    }

    /**
     * Inserts four controlled rating rows at 47N/77W and mirrors them into
     * listing_search, isolating the rating endpoint assertions from the
     * NULL-rating seeds and the price/cuisine rows at 45.5N/78.5W. All qualified
     * (index gate). Idempotent via ON CONFLICT.
     */
    private suspend fun insertRatedEndpointRows() {
        // (id, name, cuisine, isHandCut, price, rating)
        val rows = listOf(
            listOf("50000000-0000-0000-0000-000000000001", "Star Grill", "mexican", true, "15.00", "4.8"),
            listOf("50000000-0000-0000-0000-000000000002", "Clover Cafe", "mexican", null, "10.00", "3.5"),
            listOf("50000000-0000-0000-0000-000000000003", "Rustic Table", "mediterranean", false, "20.00", "2.5"),
            listOf("50000000-0000-0000-0000-000000000004", "No Rating Bistro", null, null, "5.00", null),
        )
        val ids = rows.map { UUID.fromString(it[0] as String) }

        rows.forEach { row ->
            dbUpdate(
                """
                INSERT INTO restaurant_listings (id, name, address, location, cuisine, is_hand_cut, verification_status, price, rating, cross_contamination)
                VALUES (?, ?, ?, ST_SetSRID(ST_MakePoint(-77.0, 47.0), 4326)::geography, ?, ?, 'UNVERIFIED', ?, ?, 'NO_CROSS_CONTAMINATION')
                ON CONFLICT (id) DO NOTHING
                """.trimIndent(),
                UUID.fromString(row[0] as String), row[1] as String, "47.00, -77.00",
                row[2] as String?, row[3] as Boolean?,
                (row[4] as String?)?.let { BigDecimal(it) }, (row[5] as String?)?.let { BigDecimal(it) },
            )
        }
        dbUpdate(
            """
            INSERT INTO listing_search (id, name, address, location, cuisine, is_hand_cut, verification_status, price, rating, cross_contamination)
            SELECT id, name, address, location, cuisine, is_hand_cut, verification_status, price, rating, cross_contamination
            FROM restaurant_listings WHERE id IN (?, ?, ?, ?)
            ON CONFLICT (id) DO NOTHING
            """.trimIndent(),
            ids[0], ids[1], ids[2], ids[3],
        )
        rows.filter { (it[2] as String?) != null }.forEach { row ->
            dbUpdate(
                "INSERT INTO restaurant_listing_cuisines (listing_id, cuisine) VALUES (?, ?) ON CONFLICT DO NOTHING",
                UUID.fromString(row[0] as String), row[2] as String,
            )
        }
    }

    /**
     * Inserts two controlled delivery rows at 48N/76W and mirrors them into
     * listing_search, so the isDelivery read field + deliveryOnly endpoint
     * assertions are isolated from the NULL-delivery seeds and the price/cuisine
     * (45.5N/78.5W) / rating (47N/77W) rows. Both are NO_CROSS_CONTAMINATION so
     * they pass the sc-119 query-level index gate. Idempotent via ON CONFLICT.
     */
    private suspend fun insertDeliveryEndpointRows() {
        val deliveryId = UUID.fromString("70000000-0000-0000-0000-000000000001")
        val pickupId = UUID.fromString("70000000-0000-0000-0000-000000000002")

        dbUpdate(
            """
            INSERT INTO restaurant_listings (id, name, address, location, cuisine, is_hand_cut, is_delivery, verification_status, cross_contamination)
            VALUES (?, ?, ?, ST_SetSRID(ST_MakePoint(-76.0, 48.0), 4326)::geography, ?, ?, ?, 'UNVERIFIED', 'NO_CROSS_CONTAMINATION')
            ON CONFLICT (id) DO NOTHING
            """.trimIndent(),
            deliveryId, "Delivery Test", "48.00, -76.00", "mexican", true, true,
        )
        dbUpdate(
            """
            INSERT INTO restaurant_listings (id, name, address, location, cuisine, is_hand_cut, is_delivery, verification_status, cross_contamination)
            VALUES (?, ?, ?, ST_SetSRID(ST_MakePoint(-76.0, 48.0), 4326)::geography, ?, ?, ?, 'UNVERIFIED', 'NO_CROSS_CONTAMINATION')
            ON CONFLICT (id) DO NOTHING
            """.trimIndent(),
            pickupId, "Pickup Test", "48.00, -76.00", "mexican", false, false,
        )
        dbUpdate(
            """
            INSERT INTO listing_search (id, name, address, location, cuisine, is_hand_cut, is_delivery, verification_status, cross_contamination)
            SELECT id, name, address, location, cuisine, is_hand_cut, is_delivery, verification_status, cross_contamination
            FROM restaurant_listings WHERE id IN (?, ?)
            ON CONFLICT (id) DO NOTHING
            """.trimIndent(),
            deliveryId, pickupId,
        )
    }
}
