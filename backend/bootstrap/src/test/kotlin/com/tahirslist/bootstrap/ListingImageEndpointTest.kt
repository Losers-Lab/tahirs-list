package com.tahirslist.bootstrap

import io.kotest.matchers.shouldBe
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.UUID
import javax.imageio.ImageIO

/**
 * Owner image-management HTTP surface (sc-53/54): PUT/DELETE /v1/listings/{id}/image.
 *
 * End-to-end against the full application graph (route -> Add/RemoveListingImage
 * use cases -> in-memory ImagePort in the test env). Covers: 204 happy upload
 * with a read-back round-trip (GET the stored FULL variant), 204 idempotent
 * re-upload / remove, the owner-only guard (403), 404 for an unknown listing,
 * unauthenticated 401, a missing `image` part -> 400, and an undecodable upload
 * -> 400.
 *
 * Contract note: a malformed multipart body (missing part / undecodable image)
 * is now a 400 `invalid_input` envelope instead of the previous 500, and the
 * missing-part message stays `"Image part is required."`.
 */
class ListingImageEndpointTest : KtorPostgresTest() {

    private suspend fun putImage(
        listingId: String,
        bytes: ByteArray,
        contentType: String = "image/jpeg",
        bearer: String?,
    ) = put(
        "/v1/listings/$listingId/image",
        token = bearer,
        multipart = multipart { file("image", bytes, contentType, "hero.jpg") },
    )

    private suspend fun deleteImage(listingId: String, bearer: String?) =
        delete("/v1/listings/$listingId/image", token = bearer)

    private suspend fun getImage(listingId: String, variant: String = "full") =
        get("/v1/listings/$listingId/image?variant=$variant")

    init {
        test("owner uploads a hero image: 204, then the FULL variant is served back (sc-53)") {
            val session = signupAndLogin("img-put-happy@example.com")
            val listingId = createListing(session.accessToken, session.accountId)

            putImage(listingId, jpegBytes(), bearer = session.accessToken).status shouldBe 204

            // Read-back round-trip through the sc-157 GET image endpoint.
            getImage(listingId).status shouldBe 200
        }

        test("re-uploading after a previous image still succeeds (last-write-wins replace)") {
            val session = signupAndLogin("img-put-again@example.com")
            val listingId = createListing(session.accessToken, session.accountId)

            putImage(listingId, jpegBytes(), bearer = session.accessToken).status shouldBe 204
            putImage(listingId, jpegBytes(), bearer = session.accessToken).status shouldBe 204

            getImage(listingId).status shouldBe 200
        }

        test("owner removes the listing image: 204, and the variant is gone (sc-54)") {
            val session = signupAndLogin("img-del-happy@example.com")
            val listingId = createListing(session.accessToken, session.accountId)
            putImage(listingId, jpegBytes(), bearer = session.accessToken).status shouldBe 204

            deleteImage(listingId, session.accessToken).status shouldBe 204

            getImage(listingId).status shouldBe 404
        }

        test("removing a listing with no stored image is a silent 204, not an error (idempotent)") {
            val session = signupAndLogin("img-del-idem@example.com")
            val listingId = createListing(session.accessToken, session.accountId)

            deleteImage(listingId, session.accessToken).status shouldBe 204
            deleteImage(listingId, session.accessToken).status shouldBe 204
        }

        test("a non-owner upload is forbidden (403) and stores nothing") {
            val owner = signupAndLogin("img-owner@example.com")
            val listingId = createListing(owner.accessToken, owner.accountId)
            val stranger = signupAndLogin("img-stranger@example.com")

            val resp = putImage(listingId, jpegBytes(), bearer = stranger.accessToken)

            resp.status shouldBe 403
            resp.json()["code"].asString() shouldBe "not_listing_owner"
            // The stranger could not store anything: the variant stays absent.
            getImage(listingId).status shouldBe 404
        }

        test("a non-owner remove is forbidden (403)") {
            val owner = signupAndLogin("img-del-owner@example.com")
            val listingId = createListing(owner.accessToken, owner.accountId)
            putImage(listingId, jpegBytes(), bearer = owner.accessToken).status shouldBe 204
            val stranger = signupAndLogin("img-del-stranger@example.com")

            val resp = deleteImage(listingId, stranger.accessToken)

            resp.status shouldBe 403
            resp.json()["code"].asString() shouldBe "not_listing_owner"
            getImage(listingId).status shouldBe 200 // untouched
        }

        test("uploading to an unknown listing returns 404 listing_not_found") {
            val session = signupAndLogin("img-put-404@example.com")

            val resp = putImage(UUID.randomUUID().toString(), jpegBytes(), bearer = session.accessToken)

            resp.status shouldBe 404
            resp.json()["code"].asString() shouldBe "listing_not_found"
        }

        test("removing an unknown listing's image returns 404 listing_not_found") {
            val session = signupAndLogin("img-del-404@example.com")

            val resp = deleteImage(UUID.randomUUID().toString(), session.accessToken)

            resp.status shouldBe 404
            resp.json()["code"].asString() shouldBe "listing_not_found"
        }

        test("an unauthenticated upload returns a generic 401") {
            val resp = putImage(UUID.randomUUID().toString(), jpegBytes(), bearer = null)

            resp.status shouldBe 401
            resp.json()["code"].asString() shouldBe "unauthorized"
        }

        test("an unauthenticated remove returns a generic 401") {
            val resp = deleteImage(UUID.randomUUID().toString(), bearer = null)

            resp.status shouldBe 401
            resp.json()["code"].asString() shouldBe "unauthorized"
        }

        test("an upload with no `image` part returns 400 invalid_input with the fixed message") {
            val session = signupAndLogin("img-nopart@example.com")
            val listingId = createListing(session.accessToken, session.accountId)

            val resp = put(
                "/v1/listings/$listingId/image",
                token = session.accessToken,
                // A well-formed multipart body that simply carries no `image` part.
                multipart = multipart { field("description", "no image attached") },
            )

            resp.status shouldBe 400
            resp.json()["code"].asString() shouldBe "invalid_input"
            resp.json()["message"].asString() shouldBe "Image part is required."
        }

        test("an undecodable upload returns 400 invalid_input and stores nothing") {
            val session = signupAndLogin("img-garbage@example.com")
            val listingId = createListing(session.accessToken, session.accountId)

            val resp = putImage(listingId, "this is not an image".toByteArray(), bearer = session.accessToken)

            resp.status shouldBe 400
            resp.json()["code"].asString() shouldBe "invalid_input"
            getImage(listingId).status shouldBe 404
        }
    }

    /** A valid, decodable JPEG (a solid-color 3000x2000 image). */
    private fun jpegBytes(width: Int = 3000, height: Int = 2000): ByteArray {
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.color = Color(40, 90, 180)
        g.fillRect(0, 0, width, height)
        g.dispose()
        val out = ByteArrayOutputStream()
        ImageIO.write(img, "jpeg", out)
        return out.toByteArray()
    }
}
