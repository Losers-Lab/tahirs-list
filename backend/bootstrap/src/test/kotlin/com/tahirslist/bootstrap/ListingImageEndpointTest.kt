package com.tahirslist.bootstrap

import io.kotest.matchers.shouldBe
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.ZoneOffset
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

        test("an upload over the 8 MiB file cap is refused with 413 and stores nothing") {
            val session = signupAndLogin("img-too-big@example.com")
            val listingId = createListing(session.accessToken, session.accountId)

            // 8 MiB + 1 mirrors the adapter's MAX_UPLOAD_BYTES; the cap fires
            // while the part is being streamed, so nothing is decoded or stored.
            val oversized = ByteArray(8 * 1024 * 1024 + 1)
            val resp = putImage(listingId, oversized, bearer = session.accessToken)

            resp.status shouldBe 413
            resp.json()["code"].asString() shouldBe "payload_too_large"
            getImage(listingId).status shouldBe 404
        }

        test("the stored image type comes from the bytes, never from the part's Content-Type") {
            val session = signupAndLogin("img-html-label@example.com")
            val listingId = createListing(session.accessToken, session.accountId)

            // The client lies about the type; the bytes are a real JPEG, so the
            // stored type is the derived image type and the proxy renders it inline.
            putImage(listingId, jpegBytes(), contentType = "text/html", bearer = session.accessToken)
                .status shouldBe 204

            val full = getImage(listingId)
            full.status shouldBe 200
            full.header("Content-Type") shouldBe "image/jpeg"
            full.header("Content-Disposition") shouldBe "inline"
        }

        // ---- SC-231 UC-03 P1: a Places cache stops being served at its expiry ----

        test("a Places cache is served while it is live and 404s once it expires") {
            val session = signupAndLogin("img-cache-expiry@example.com")
            val listingId = createListing(session.accessToken, session.accountId)
            putImage(listingId, jpegBytes(), bearer = session.accessToken).status shouldBe 204

            insertCoverCache(listingId, expiresAt = Instant.now().plusSeconds(3600))
            getImage(listingId).status shouldBe 200

            insertCoverCache(listingId, expiresAt = Instant.now().minusSeconds(60))
            val expired = getImage(listingId)
            expired.status shouldBe 404
            expired.json()["code"].asString() shouldBe "not_found"
        }
    }

    /** Put a Places cache row on the listing, as the photo pass would. */
    private fun insertCoverCache(listingId: String, expiresAt: Instant) {
        SharedPostgres.dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO listing_cover_photos (
                    listing_id, source_kind, storage_class, attribution,
                    google_place_id, google_photo_name, expires_at
                ) VALUES (?, 'GOOGLE_PLACES', 'CACHE', 'Google Maps, A. Reviewer', 'ChIJ', 'places/abc/photos/def', ?)
                ON CONFLICT (listing_id) DO UPDATE SET
                    source_kind = EXCLUDED.source_kind,
                    storage_class = EXCLUDED.storage_class,
                    attribution = EXCLUDED.attribution,
                    google_place_id = EXCLUDED.google_place_id,
                    google_photo_name = EXCLUDED.google_photo_name,
                    expires_at = EXCLUDED.expires_at
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, UUID.fromString(listingId))
                statement.setObject(2, expiresAt.atOffset(ZoneOffset.UTC))
                statement.executeUpdate()
            }
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
