package com.tahirslist.bootstrap

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigDecimal
import kotlin.uuid.Uuid

/**
 * AC-2/AC-4/AC-9 against the real PostGIS index: ordering, tri-state hand-cut,
 * cuisine AND/OR, price/rating exclusion of unknowns, and the cross-contamination
 * index gate. Every fixture is created through the production REST write path.
 */
class McpSearchPostgisTest : KtorPostgresTest() {

    init {
        // The shared PostGIS container is reused by every spec; leave the
        // baseline dataset exactly as other count-based search specs expect it.
        afterSpec {
            dbUpdate("DELETE FROM restaurant_listings WHERE name LIKE 'Mcp%'")
        }
    }
    private suspend fun createSearchListing(
        session: TestSession,
        name: String,
        lat: Double,
        lng: Double,
        cuisine: String = "mediterranean",
        handCut: Boolean? = true,
        cross: String = "NO_CROSS_CONTAMINATION",
    ): String = createListing(
        bearer = session.accessToken,
        ownerId = session.accountId,
        body = listingBody(
            name = name,
            lat = lat,
            lng = lng,
            cuisine = cuisine,
            isHandCut = handCut,
            overrides = mapOf("crossContamination" to cross),
        ),
    )

    private fun ApiResponse.mcpNames(): List<String> =
        mcpStructured()["results"]!!.jsonArray.map { it.jsonObject["name"]!!.jsonPrimitive.content }

    init {
        test("filtersAndOrdersByDistance") {
            val session = signupAndLogin("mcp-distance@example.com")
            createSearchListing(session, "McpNear", lat = 43.6533, lng = -79.3832)
            createSearchListing(session, "McpFar", lat = 43.6900, lng = -79.3832)

            val response = mcpCall("search_restaurants", searchArguments(radiusMiles = 10.0))
            response.status shouldBe 200

            val names = response.mcpNames()
            (names.indexOf("McpNear") in 0 until names.indexOf("McpFar")) shouldBe true

            val cards = response.mcpStructured()["results"]!!.jsonArray.map { it.jsonObject }
            val near = cards.first { it["name"]!!.jsonPrimitive.content == "McpNear" }
            val far = cards.first { it["name"]!!.jsonPrimitive.content == "McpFar" }
            (near["distanceMiles"]!!.jsonPrimitive.content.toDouble() <
                far["distanceMiles"]!!.jsonPrimitive.content.toDouble()) shouldBe true
        }

        test("handCutOnlyExcludesUnknownAndFalse") {
            val session = signupAndLogin("mcp-handcut@example.com")
            createSearchListing(session, "McpHandCutYes", 43.6533, -79.3832, handCut = true)
            createSearchListing(session, "McpHandCutNo", 43.6540, -79.3832, handCut = false)
            createSearchListing(session, "McpHandCutUnknown", 43.6547, -79.3832, handCut = null)

            val names = mcpCall(
                "search_restaurants",
                searchArguments(extra = """"handCutOnly":true"""),
            ).mcpNames()

            names shouldContain "McpHandCutYes"
            names shouldNotContain "McpHandCutNo"
            names shouldNotContain "McpHandCutUnknown"
        }

        test("cuisineAndOrSemantics") {
            val session = signupAndLogin("mcp-cuisine@example.com")
            val thai = createSearchListing(session, "McpThai", 43.6533, -79.3832, cuisine = "thai")
            createSearchListing(session, "McpPakistani", 43.6540, -79.3832, cuisine = "pakistani")

            val orNames = mcpCall(
                "search_restaurants",
                searchArguments(extra = """"cuisines":["thai","pakistani"]"""),
            ).mcpNames()
            orNames shouldContain "McpThai"
            orNames shouldContain "McpPakistani"

            dbUpdate(
                "INSERT INTO restaurant_listing_cuisines (listing_id, cuisine) VALUES (?, ?) ON CONFLICT DO NOTHING",
                Uuid.parse(thai),
                "indian",
            )

            val andNames = mcpCall(
                "search_restaurants",
                searchArguments(extra = """"cuisines":["thai","indian"],"cuisineLogic":"AND""""),
            ).mcpNames()
            andNames shouldContain "McpThai"
            andNames shouldNotContain "McpPakistani"
        }

        test("priceAndRatingExcludeUnknowns") {
            val session = signupAndLogin("mcp-price@example.com")
            val priced = createSearchListing(session, "McpPriced", 43.6533, -79.3832)
            createSearchListing(session, "McpUnpriced", 43.6540, -79.3832)

            dbUpdate(
                "UPDATE restaurant_listings SET price = ?, rating = ? WHERE id = ?",
                BigDecimal("15.00"),
                BigDecimal("4.50"),
                Uuid.parse(priced),
            )
            dbUpdate(
                "UPDATE listing_search SET price = ?, rating = ? WHERE id = ?",
                BigDecimal("15.00"),
                BigDecimal("4.50"),
                Uuid.parse(priced),
            )

            val names = mcpCall(
                "search_restaurants",
                searchArguments(
                    extra = """"minPrice":10,"maxPrice":20,"minRating":4""",
                ),
            ).mcpNames()

            names shouldContain "McpPriced"
            names shouldNotContain "McpUnpriced"
        }

        test("indexGateExcludesCrossContamination") {
            val session = signupAndLogin("mcp-cross@example.com")
            createSearchListing(session, "McpQualified", 43.6533, -79.3832, cross = "NO_CROSS_CONTAMINATION")
            createSearchListing(session, "McpUncertain", 43.6540, -79.3832, cross = "UNCERTAIN")
            createSearchListing(session, "McpPresent", 43.6547, -79.3832, cross = "PRESENT")

            val names = mcpCall("search_restaurants", searchArguments()).mcpNames()

            names shouldContain "McpQualified"
            names shouldNotContain "McpUncertain"
            names shouldNotContain "McpPresent"
        }
    }
}
