package com.tahirslist.bootstrap.favorite

import com.tahirslist.bootstrap.ApiResponse
import com.tahirslist.bootstrap.KtorPostgresTest
import com.tahirslist.bootstrap.TestJwt
import com.tahirslist.bootstrap.asString
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.json.jsonObject
import java.util.UUID

/**
 * Favourites HTTP surface (sc-50/51/52): POST/DELETE/GET /v1/favorites.
 *
 * End-to-end against the full application graph (route -> favourites use cases
 * -> Exposed favourites repository -> real Flyway V12). Covers the mandated
 * cases: the idempotent favourite/unfavourite contract, the browse-card read
 * shape, unauthenticated 401, expired/tampered-token 401 (Omar's R1 — the first
 * protected endpoint), and a 404 for a non-existent listing.
 */
class FavoritesEndpointTest : KtorPostgresTest() {

    private suspend fun favorite(listingId: String, bearer: String?) =
        post("/v1/favorites/$listingId", token = bearer)

    private suspend fun unfavorite(listingId: String, bearer: String?) =
        delete("/v1/favorites/$listingId", token = bearer)

    private suspend fun getFavorites(bearer: String?) = get("/v1/favorites", token = bearer)

    private suspend fun listFavorites(bearer: String?): List<String> {
        val resp = getFavorites(bearer)
        resp.status shouldBe 200
        return resp.jsonArray().map { it.jsonObject["id"].asString() }
    }

    private fun codeOf(resp: ApiResponse): String? = resp.code()

    init {
        test("POST /v1/favorites/{id} favourites a listing (204) and is idempotent") {
            val session = signupAndLogin("fav-happy@example.com")
            val listingId = createListing(session.accessToken, session.accountId)

            favorite(listingId, session.accessToken).status shouldBe 204

            // duplicate — no-op
            favorite(listingId, session.accessToken).status shouldBe 204

            listFavorites(session.accessToken) shouldBe listOf(listingId)
        }

        test("DELETE /v1/favorites/{id} unfavourites (204) and is idempotent") {
            val session = signupAndLogin("fav-unfav@example.com")
            val listingId = createListing(session.accessToken, session.accountId)
            favorite(listingId, session.accessToken).status shouldBe 204

            unfavorite(listingId, session.accessToken).status shouldBe 204
            listFavorites(session.accessToken) shouldBe emptyList()

            // already gone — no-op
            unfavorite(listingId, session.accessToken).status shouldBe 204
            listFavorites(session.accessToken) shouldBe emptyList()
        }

        test("GET /v1/favorites returns the user's favourites as browse-card objects identical to /v1/listings shape") {
            val session = signupAndLogin("fav-card@example.com")
            val listingId = createListing(session.accessToken, session.accountId)
            favorite(listingId, session.accessToken).status shouldBe 204

            val resp = getFavorites(session.accessToken)
            resp.status shouldBe 200
            val cards = resp.jsonArray()
            cards.size shouldBe 1

            val card = cards.single().jsonObject
            card["id"].asString() shouldBe listingId
            card["name"].asString() shouldBe "Halal Grill"
            card["address"].asString() shouldBe "123 Main St"
            card["lat"].asString().toDouble() shouldBe 40.7128
            card["lng"].asString().toDouble() shouldBe -74.0060
            card["cuisine"].asString() shouldBe "mediterranean"
            card["isHandCut"].asString().toBoolean() shouldBe true
            card["halalScope"].asString() shouldBe "NOT_DISCLOSED"
            card["verificationStatus"].asString() shouldBe "UNVERIFIED"
            card["imageThumbnailUrl"].asString() shouldNotBe ""
        }

        test("POST /v1/favorites/{id} without a token returns a generic 401") {
            val resp = favorite(UUID.randomUUID().toString(), bearer = null)

            resp.status shouldBe 401
            codeOf(resp) shouldBe "unauthorized"
        }

        test("GET /v1/favorites without a token returns a generic 401") {
            val resp = getFavorites(bearer = null)

            resp.status shouldBe 401
            codeOf(resp) shouldBe "unauthorized"
        }

        test("DELETE /v1/favorites/{id} without a token returns a generic 401") {
            val resp = unfavorite(UUID.randomUUID().toString(), bearer = null)

            resp.status shouldBe 401
            codeOf(resp) shouldBe "unauthorized"
        }

        test("an expired access token is rejected with a generic 401 on the favourites surface (Omar R1)") {
            val expired = TestJwt.mint(role = "USER", sub = UUID.randomUUID().toString(), expSecondsAhead = -60)

            val resp = getFavorites(expired)

            resp.status shouldBe 401
            codeOf(resp) shouldBe "unauthorized"
        }

        test("a tampered access token is rejected with a generic 401 on the favourites surface (Omar R1)") {
            val minted = TestJwt.mint(role = "USER", sub = UUID.randomUUID().toString(), expSecondsAhead = 3600)

            val resp = getFavorites(TestJwt.tamper(minted))

            resp.status shouldBe 401
            codeOf(resp) shouldBe "unauthorized"
        }

        test("POST /v1/favorites/{id} for a non-existent listing returns 404 listing_not_found") {
            val session = signupAndLogin("fav-404@example.com")

            val resp = favorite(UUID.randomUUID().toString(), session.accessToken)

            resp.status shouldBe 404
            codeOf(resp) shouldBe "listing_not_found"
        }

        test("favourites are scoped to the authenticated user, not leaked") {
            val userA = signupAndLogin("fav-user-a@example.com")
            val listingA = createListing(userA.accessToken, userA.accountId)
            favorite(listingA, userA.accessToken).status shouldBe 204

            val userB = signupAndLogin("fav-user-b@example.com")
            listFavorites(userB.accessToken) shouldBe emptyList()

            listFavorites(userA.accessToken) shouldBe listOf(listingA)
        }
    }
}
