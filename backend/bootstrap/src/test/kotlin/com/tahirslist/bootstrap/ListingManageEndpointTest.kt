package com.tahirslist.bootstrap

import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * SC-205 D-2 / AC-6: `GET /v1/listings/{id}/manage` answers the *same* question
 * as the image write — `owner || ADMIN` — and only a boolean.
 *
 * The admin case is proven with a genuinely signed token carrying
 * `role = ADMIN` (the JWT claim is the authority, D-13) against a listing the
 * admin does not own.
 */
class ListingManageEndpointTest : KtorPostgresTest() {

    init {
        test("the owner is told they may manage the photo") {
            val owner = signupAndLogin("manage-owner@example.com")
            val listingId = createListing(owner.accessToken, owner.accountId)

            val resp = get("/v1/listings/$listingId/manage", token = owner.accessToken)

            resp.status shouldBe 200
            resp.json()["canManage"]!!.jsonPrimitive.boolean shouldBe true
        }

        test("an admin may manage a listing they do not own (AC-6)") {
            val owner = signupAndLogin("manage-admin-owner@example.com")
            val listingId = createListing(owner.accessToken, owner.accountId)
            val admin = signupAndLogin("manage-admin@example.com")
            val adminToken = TestJwt.mint(role = "ADMIN", sub = admin.accountId)

            val resp = get("/v1/listings/$listingId/manage", token = adminToken)

            resp.status shouldBe 200
            resp.json()["canManage"]!!.jsonPrimitive.boolean shouldBe true
        }

        test("a signed-in non-owner is told they may not, and learns nothing about the owner") {
            val owner = signupAndLogin("manage-other-owner@example.com")
            val listingId = createListing(owner.accessToken, owner.accountId)
            val stranger = signupAndLogin("manage-stranger@example.com")

            val resp = get("/v1/listings/$listingId/manage", token = stranger.accessToken)

            resp.status shouldBe 200
            val body = resp.json().jsonObject
            body["canManage"]!!.jsonPrimitive.boolean shouldBe false
            // No owner id (or any other listing field) in the answer.
            body.keys shouldBe setOf("canManage")
        }

        test("the manage read requires a session") {
            val owner = signupAndLogin("manage-anon-owner@example.com")
            val listingId = createListing(owner.accessToken, owner.accountId)

            get("/v1/listings/$listingId/manage").status shouldBe 401
        }

        test("an unknown listing is 404") {
            val admin = signupAndLogin("manage-unknown@example.com")
            val adminToken = TestJwt.mint(role = "ADMIN", sub = admin.accountId)

            get("/v1/listings/2f3c1f9a-3f4b-4f6f-8bf7-2f8b1d3a9f5e/manage", token = adminToken).status shouldBe 404
        }
    }
}
