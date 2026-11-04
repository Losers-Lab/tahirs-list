package com.tahirslist.bootstrap

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.util.UUID

/**
 * sc-138 HTTP surface for Add Listing: POST /v1/listings, plus the owner edit
 * surface PATCH /v1/listings/{id} (sc-23/47/48).
 *
 * End-to-end against the full application graph (route -> use case -> Exposed
 * repository) with the real Flyway V4 migration. Covers the mandated cases:
 * happy path, unauthenticated 401, validation 4xx, owner-not-found, and a
 * persistence round-trip (reads the row back from PostGIS).
 *
 * Contract note: a malformed UUID path parameter is now a 400 `invalid_input`
 * (the Ktor route parses the id itself) rather than the previous 500.
 */
class ListingEndpointTest : KtorPostgresTest() {

    private suspend fun createListingWithToken(bearer: String?, body: Map<String, Any?>) =
        post("/v1/listings", token = bearer, json = body)

    private suspend fun patchListing(bearer: String?, listingId: String, body: Map<String, Any?>) =
        patch("/v1/listings/$listingId", token = bearer, json = body)

    private fun editBody(name: String = "Halal Grill") = linkedMapOf<String, Any?>(
        "name" to name,
        "address" to "123 Main St",
        "lat" to 40.7128,
        "lng" to -74.0060,
        "cuisine" to "mediterranean",
        "isHandCut" to true,
    )

    init {
        test("POST /v1/listings with a valid token creates an unverified listing owned by the authenticated account (round-trip)") {
            val session = signupAndLogin("listing-happy@example.com")

            val resp = createListingWithToken(session.accessToken, listingBody())

            resp.status shouldBe 201
            val body = resp.json()
            body["name"].asString() shouldBe "Halal Grill"
            body["address"].asString() shouldBe "123 Main St"
            body["lat"].asString().toDouble() shouldBe 40.7128
            body["lng"].asString().toDouble() shouldBe -74.0060
            body["cuisine"].asString() shouldBe "mediterranean"
            body["isHandCut"].asString().toBoolean() shouldBe true
            body["halalScope"].asString() shouldBe "NOT_DISCLOSED"
            body["verificationStatus"].asString() shouldBe "UNVERIFIED"
            body["ownerId"].asString() shouldBe session.accountId
            UUID.fromString(body["id"].asString()) shouldNotBe null

            // Persistence round-trip: read the row back from PostGIS.
            val row = dbRow(
                """
                SELECT name, address, cuisine, is_hand_cut, is_delivery, verification_status, owner_id,
                       ST_Y(location::geometry) AS lat, ST_X(location::geometry) AS lng
                FROM restaurant_listings WHERE id = ?
                """.trimIndent(),
                UUID.fromString(body["id"].asString()),
            )
            row["name"] shouldBe "Halal Grill"
            row["address"] shouldBe "123 Main St"
            row["cuisine"] shouldBe "mediterranean"
            row["is_hand_cut"] shouldBe true
            row["is_delivery"] shouldBe true
            row["verification_status"] shouldBe "UNVERIFIED"
            row["owner_id"].toString() shouldBe session.accountId
            (row["lat"] as Number).toDouble() shouldBe 40.7128
            (row["lng"] as Number).toDouble() shouldBe -74.0060
        }

        test("POST /v1/listings accepts an isDelivery flag and echoes it in the response (sc-184)") {
            val session = signupAndLogin("listing-delivery@example.com")

            val resp = createListingWithToken(
                session.accessToken,
                listingBody(overrides = mapOf("isDelivery" to false)),
            )

            resp.status shouldBe 201
            resp.json()["isDelivery"].asString().toBoolean() shouldBe false
        }

        test("POST /v1/listings defaults isDelivery to null (unknown) when omitted (sc-184)") {
            val session = signupAndLogin("listing-nodelivery@example.com")

            // listingBody() carries isDelivery; drop it so the field is omitted.
            val resp = createListingWithToken(session.accessToken, listingBody().minus("isDelivery"))

            resp.status shouldBe 201
            val body = resp.json()
            body.containsKey("isDelivery") shouldBe true
            // Explicit null on the wire (encodeDefaults) — "unknown", not false.
            body["isDelivery"].stringOrNull() shouldBe null
        }

        test("POST /v1/listings without a token returns a generic 401") {
            val resp = createListingWithToken(null, listingBody())

            resp.status shouldBe 401
            resp.json()["code"].asString() shouldBe "unauthorized"
        }

        test("POST /v1/listings accepts halal items in the {name, isHalal} wire shape and persists them (sc-119)") {
            val session = signupAndLogin("listing-halal-items@example.com")

            val resp = createListingWithToken(
                session.accessToken,
                listingBody(
                    overrides = mapOf(
                        "halalScope" to "PARTIALLY_HALAL",
                        "halalItems" to listOf(
                            linkedMapOf("name" to "Chicken Shawarma", "isHalal" to true),
                            linkedMapOf("name" to "Baklava", "isHalal" to false),
                        ),
                    ),
                ),
            )

            resp.status shouldBe 201
            resp.json()["halalScope"].asString() shouldBe "PARTIALLY_HALAL"

            val listingId = UUID.fromString(resp.json()["id"].asString())
            val items = dbRows(
                "SELECT name, is_halal FROM restaurant_halal_items WHERE listing_id = ? ORDER BY name",
                listingId,
            )
            items.associate { it["name"] as String to it["is_halal"] as Boolean } shouldBe mapOf(
                "Baklava" to false,
                "Chicken Shawarma" to true,
            )
        }

        test("POST /v1/listings rejects a blank name with 400 invalid_input") {
            val session = signupAndLogin("listing-blank-name@example.com")

            val resp = createListingWithToken(
                session.accessToken,
                listingBody(overrides = mapOf("name" to "   ")),
            )

            resp.status shouldBe 400
            resp.json()["code"].asString() shouldBe "invalid_input"
        }

        test("POST /v1/listings rejects an out-of-range latitude with 400 invalid_input") {
            val session = signupAndLogin("listing-bad-lat@example.com")

            val resp = createListingWithToken(
                session.accessToken,
                listingBody(overrides = mapOf("lat" to 95.0)),
            )

            resp.status shouldBe 400
            resp.json()["code"].asString() shouldBe "invalid_input"
        }

        test("POST /v1/listings with a token for a non-existent account returns 404 owner_not_found") {
            val minted = TestJwt.mint(role = "USER", sub = UUID.randomUUID().toString(), expSecondsAhead = 3600)

            val resp = createListingWithToken(minted, listingBody())

            resp.status shouldBe 404
            resp.json()["code"].asString() shouldBe "owner_not_found"
        }

        test("PATCH /v1/listings/{id} lets the owner edit own listing fields and echoes/persists them (sc-23/47/48)") {
            val session = signupAndLogin("edit-owner@example.com")
            val listingId = createListing(session.accessToken, session.accountId)

            val updateBody = linkedMapOf<String, Any?>(
                "name" to "Halal Grill Renamed",
                "address" to "999 Renamed Ave",
                "lat" to 41.0,
                "lng" to -73.0,
                "cuisine" to "turkish",
                "isHandCut" to false,
                "isDelivery" to false,
                "halalScope" to "PARTIALLY_HALAL",
                "alcoholServed" to true,
            )
            val resp = patchListing(session.accessToken, listingId, updateBody)

            resp.status shouldBe 200
            val body = resp.json()
            body["id"].asString() shouldBe listingId
            body["name"].asString() shouldBe "Halal Grill Renamed"
            body["address"].asString() shouldBe "999 Renamed Ave"
            body["lat"].asString().toDouble() shouldBe 41.0
            body["lng"].asString().toDouble() shouldBe -73.0
            body["cuisine"].asString() shouldBe "turkish"
            body["isHandCut"].asString().toBoolean() shouldBe false
            body["isDelivery"].asString().toBoolean() shouldBe false
            body["halalScope"].asString() shouldBe "PARTIALLY_HALAL"
            body["verificationStatus"].asString() shouldBe "UNVERIFIED"
            body["ownerId"].asString() shouldBe session.accountId

            // Persistence round-trip: the source row reflects the edit.
            val row = dbRow(
                "SELECT name, address, cuisine, owner_id, verification_status FROM restaurant_listings WHERE id = ?",
                UUID.fromString(listingId),
            )
            row["name"] shouldBe "Halal Grill Renamed"
            row["address"] shouldBe "999 Renamed Ave"
            row["cuisine"] shouldBe "turkish"
            row["owner_id"].toString() shouldBe session.accountId
            row["verification_status"] shouldBe "UNVERIFIED"
        }

        test("PATCH /v1/listings/{id} is a full replace of editable fields: a null bool clears it, governance fields stay preserved") {
            val session = signupAndLogin("edit-null@example.com")
            val listingId = createListing(session.accessToken, session.accountId)

            // Full editable payload; isDelivery=null (unknown/pickup-baseline)
            // explicitly clears the earlier true claim. Governance fields
            // (verification_status, owner_id) are never touched by an edit.
            val updateBody = linkedMapOf<String, Any?>(
                "name" to "Full Replace Grill",
                "address" to "123 Main St",
                "lat" to 40.7128,
                "lng" to -74.0060,
                "cuisine" to "mediterranean",
                "isHandCut" to true,
                "isDelivery" to null,
            )
            val resp = patchListing(session.accessToken, listingId, updateBody)

            resp.status shouldBe 200
            val row = dbRow(
                "SELECT name, is_delivery, verification_status, owner_id FROM restaurant_listings WHERE id = ?",
                UUID.fromString(listingId),
            )
            row["name"] shouldBe "Full Replace Grill"
            row["is_delivery"] shouldBe null
            row["verification_status"] shouldBe "UNVERIFIED"
            row["owner_id"] shouldNotBe null
        }

        test("PATCH /v1/listings/{id} by a non-owner returns 403 not_listing_owner") {
            val owner = signupAndLogin("edit-owner-b@example.com")
            val listingId = createListing(owner.accessToken, owner.accountId)

            // A different account edits -> 403, and the row is untouched.
            val stranger = signupAndLogin("edit-stranger@example.com")
            val resp = patchListing(stranger.accessToken, listingId, editBody(name = "Hijack Grill"))

            resp.status shouldBe 403
            resp.json()["code"].asString() shouldBe "not_listing_owner"

            val row = dbRow(
                "SELECT name, owner_id FROM restaurant_listings WHERE id = ?",
                UUID.fromString(listingId),
            )
            row["name"] shouldBe "Halal Grill"
            row["owner_id"].toString() shouldBe owner.accountId
        }

        test("PATCH /v1/listings/{id} for an unknown listing returns 404 listing_not_found") {
            val session = signupAndLogin("edit-ghost@example.com")

            val resp = patchListing(session.accessToken, UUID.randomUUID().toString(), editBody(name = "Ghost Grill"))

            resp.status shouldBe 404
            resp.json()["code"].asString() shouldBe "listing_not_found"
        }

        test("PATCH /v1/listings/{id} without a token returns a generic 401") {
            val resp = patchListing(null, UUID.randomUUID().toString(), editBody(name = "X"))

            resp.status shouldBe 401
            resp.json()["code"].asString() shouldBe "unauthorized"
        }

        test("PATCH /v1/listings/{id} rejects a blank name with 400 invalid_input") {
            val session = signupAndLogin("edit-blank@example.com")
            val listingId = createListing(session.accessToken, session.accountId)

            val resp = patchListing(session.accessToken, listingId, editBody(name = "   "))

            resp.status shouldBe 400
            resp.json()["code"].asString() shouldBe "invalid_input"
        }

        test("PATCH /v1/listings/{id} with a malformed id returns 400 invalid_input (not a 500)") {
            val session = signupAndLogin("edit-bad-id@example.com")

            val resp = patchListing(session.accessToken, "not-a-uuid", editBody())

            resp.status shouldBe 400
            resp.json()["code"].asString() shouldBe "invalid_input"
        }
    }
}
