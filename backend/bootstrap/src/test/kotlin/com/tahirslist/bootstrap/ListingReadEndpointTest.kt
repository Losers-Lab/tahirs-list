package com.tahirslist.bootstrap

import com.tahirslist.application.image.ImageVariant
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.UUID
import javax.imageio.ImageIO

/**
 * sc-157 public read surface (docs/design/sc-157-image-variants.md §"URL / serving
 * contract"): end-to-end against the full application graph with the real Flyway
 * seed data and the in-memory [com.tahirslist.application.image.ImagePort]
 * fallback (no MinIO — the S3 adapter is gated behind `storage`, which boot tests
 * never set).
 *
 *  - browse carries thumbnail-size URLs ONLY — full-res never appears on a card;
 *  - detail carries the full-res `imageUrl` (and MAY carry the thumbnail);
 *  - the image proxy returns the exact requested variant bytes with the stored
 *    content-type + `Cache-Control: public, max-age=86400`;
 *  - unknown variant = 400, absent image = 404;
 *  - the list and sub-resource routes under /v1/listings are public (search /
 *    browse is core public UX) while writes stay authenticated (deny-by-default);
 *  - no object store is configured, so the in-memory image port is wired, and the
 *    seed-image ingest runner stays gated off so boot tests never hit the network.
 *
 * The variants are produced through the real owner upload path (ingest stores the
 * original as FULL and pre-generates the sc-183 thumbnail widths), so the proxy
 * assertions read back real, independently stored objects.
 */
class ListingReadEndpointTest : KtorPostgresTest() {

    private suspend fun seededListingId(name: String): String =
        (dbRow("SELECT id FROM restaurant_listings WHERE name = ? LIMIT 1", name).values.single() as UUID)
            .toString()

    /** Uploads a real 3000x2000 JPEG as the listing's hero and returns its bytes. */
    private suspend fun uploadHero(bearer: String, listingId: String): ByteArray {
        val original = jpegBytes()
        val resp = put(
            "/v1/listings/$listingId/image",
            token = bearer,
            multipart = multipart { file("image", original, "image/jpeg", "hero.jpg") },
        )
        resp.status shouldBe 204
        return original
    }

    private fun imageWidth(bytes: ByteArray): Int =
        ImageIO.read(ByteArrayInputStream(bytes))?.width ?: error("response is not a decodable image")

    private fun assertPublicCached(resp: ApiResponse) {
        resp.header("Cache-Control") shouldBe "public, max-age=86400"
    }

    init {
        test("browse cards expose a thumbnail URL ONLY — full-res never appears on cards") {
            val resp = get("/v1/listings")
            resp.status shouldBe 200

            val cards = resp.jsonArray()
            (cards.size >= 5) shouldBe true // the 30-seed DB backs the browse surface

            cards.forEach { card ->
                card.jsonObject.containsKey("imageThumbnailUrl") shouldBe true
                val thumbUrl = card.jsonObject["imageThumbnailUrl"].asString()
                thumbUrl.contains("/v1/listings/") shouldBe true
                thumbUrl.contains("variant=thumbnail") shouldBe true
                // The sc-157 "no oversized fetch on cards" rule.
                card.jsonObject.containsKey("imageUrl") shouldBe false
                thumbUrl.contains("variant=full") shouldBe false
            }
        }

        test("detail carries the full-res imageUrl and the thumbnail URL") {
            val id = seededListingId("Aroma Fine Indian Cuisine")

            val resp = get("/v1/listings/$id")
            resp.status shouldBe 200
            val detail = resp.json()

            detail["imageUrl"].asString().contains("variant=full") shouldBe true
            detail["imageThumbnailUrl"].asString().contains("variant=thumbnail") shouldBe true
            detail["imageThumbnailUrl"].asString().contains("variant=full") shouldBe false

            // Detail hero also gets the responsive sc-183 width set.
            detail.containsKey("imageSrcset") shouldBe true
            detail["imageSrcset"]!!.jsonArray.size shouldBe ImageVariant.thumbnailVariants.size
        }

        test("browse and detail cards expose the structured address fields (sc-187)") {
            // Browse card for a US seed row surfaces city/province/postal/country.
            val alAmir = get("/v1/listings").jsonArray()
                .map { it.jsonObject }
                .first { it["name"].asString() == "Al-Amir Lebanese Restaurant & Club" }
            alAmir["address"].asString() shouldBe "3885 Belt Line Rd" // street line preserved
            alAmir["city"].asString() shouldBe "Addison"
            alAmir["province"].asString() shouldBe "TX"
            alAmir["postal"].asString() shouldBe "75001"
            alAmir["country"].asString() shouldBe "US"

            // Detail for a CA seed row surfaces the same fields (non-US locality).
            val id = seededListingId("Aroma Fine Indian Cuisine")
            val detail = get("/v1/listings/$id").json()
            detail["address"].asString() shouldBe "287 King St W"
            detail["city"].asString() shouldBe "Toronto"
            detail["province"].asString() shouldBe "ON"
            detail["postal"].asString() shouldBe "M5V 0W3"
            detail["country"].asString() shouldBe "CA"
        }

        test("detail for an unknown listing returns 404") {
            get("/v1/listings/${UUID.randomUUID()}").status shouldBe 404
        }

        test("detail for a malformed listing id returns 400 invalid_input (not a 500)") {
            val resp = get("/v1/listings/not-a-uuid")

            resp.status shouldBe 400
            resp.code() shouldBe "invalid_input"
        }

        test("the image proxy serves the exact requested variant bytes with content-type + cache header") {
            val session = signupAndLogin("read-image-owner@example.com")
            val listingId = createListing(session.accessToken, session.accountId)
            val original = uploadHero(session.accessToken, listingId)

            // FULL is the untouched original.
            val full = get("/v1/listings/$listingId/image?variant=full")
            full.status shouldBe 200
            full.header("Content-Type") shouldBe "image/jpeg"
            assertPublicCached(full)
            full.bytes.contentEquals(original) shouldBe true

            // The thumbnail is a separately stored, genuinely resized object.
            val thumb = get("/v1/listings/$listingId/image?variant=thumbnail")
            thumb.status shouldBe 200
            thumb.header("Content-Type") shouldBe "image/jpeg"
            assertPublicCached(thumb)
            imageWidth(thumb.bytes) shouldBe 400
            thumb.bytes.contentEquals(original) shouldBe false
        }

        test("the image proxy serves each sc-183 thumbnail width as a distinct variant") {
            val session = signupAndLogin("read-image-widths@example.com")
            val listingId = createListing(session.accessToken, session.accountId)
            uploadHero(session.accessToken, listingId)

            val widthBySlug = linkedMapOf(
                "thumbnail" to 400,
                "thumbnail_768" to 768,
                "thumbnail_1280" to 1280,
                "thumbnail_1920" to 1920,
            )
            widthBySlug.forEach { (slug, expectedWidth) ->
                val resp = get("/v1/listings/$listingId/image?variant=$slug")
                resp.status shouldBe 200
                resp.header("Content-Type") shouldBe "image/jpeg"
                imageWidth(resp.bytes) shouldBe expectedWidth
            }
        }

        test("browse cards carry an imageSrcset covering every sc-183 width, never the full-res variant") {
            val resp = get("/v1/listings")
            resp.status shouldBe 200

            resp.jsonArray().forEach { card ->
                val node = card.jsonObject
                node.containsKey("imageSrcset") shouldBe true
                val srcset = node["imageSrcset"]!!.jsonArray
                srcset.size shouldBe ImageVariant.thumbnailVariants.size
                val widths = srcset.map { it.jsonObject["width"].asString().toInt() }
                widths shouldBe listOf(400, 768, 1280, 1920)
                srcset.forEach { entry ->
                    val url = entry.jsonObject["url"].asString()
                    url.contains("/v1/listings/") shouldBe true
                    // srcset is a thumbnail *set* — full-res never leaks into it.
                    url.contains("variant=full") shouldBe false
                }
            }
        }

        test("the image proxy returns 400 for an unknown variant") {
            val session = signupAndLogin("read-image-badvariant@example.com")
            val listingId = createListing(session.accessToken, session.accountId)
            uploadHero(session.accessToken, listingId)

            get("/v1/listings/$listingId/image?variant=blur").status shouldBe 400
        }

        test("the image proxy returns 404 when no image is stored for the variant") {
            get("/v1/listings/${UUID.randomUUID()}/image?variant=thumbnail").status shouldBe 404
        }

        test("the certificate proxy serves only the approved artifact, never pending or later evidence") {
            val session = signupAndLogin("read-cert-owner@example.com")
            val listingId = createListing(session.accessToken, session.accountId)
            val listingUuid = UUID.fromString(listingId)

            // The claim archives certification evidence. These bytes are not a
            // decodable image, so no image type can be derived for them — and
            // the client's `text/html` part header must not be adopted either.
            post(
                "/v1/listings/$listingId/claim",
                token = session.accessToken,
                multipart = multipart {
                    field("proof", "I own this; license TAH-2024-118.")
                    field("aiConsent", "true")
                    file("certImage", byteArrayOf(1, 2, 3, 4), "text/html", "cert.html")
                },
            ).status shouldBe 201

            // Pending evidence is private: nothing is served before approval.
            get("/v1/listings/$listingId/certificate").status shouldBe 404

            // Approve out of band — the gate, not the committee flow, is under test.
            dbUpdate(
                "UPDATE restaurant_listings SET verification_status = 'VERIFIED' WHERE id = ?",
                listingUuid,
            )
            dbUpdate(
                "UPDATE halal_certification_reviews SET state = 'APPROVED' WHERE listing_id = ?",
                listingUuid,
            )

            val approved = get("/v1/listings/$listingId/certificate")
            approved.status shouldBe 200
            approved.header("Content-Type") shouldBe "application/octet-stream"
            approved.header("Content-Disposition") shouldBe "attachment"
            assertPublicCached(approved)
            val approvedBytes = approved.bytes.toList()

            // A later re-claim stores new evidence, but the approved identity is
            // fixed: the public surface keeps serving what the committee reviewed.
            post(
                "/v1/listings/$listingId/claim",
                token = session.accessToken,
                multipart = multipart {
                    field("proof", "I own this; license TAH-2024-118.")
                    field("aiConsent", "true")
                    file("certImage", byteArrayOf(9, 9, 9), "text/html", "cert2.html")
                },
            ).status shouldBe 201

            val afterReclaim = get("/v1/listings/$listingId/certificate")
            afterReclaim.status shouldBe 200
            afterReclaim.bytes.toList() shouldBe approvedBytes
        }

        test("a VERIFIED listing whose approved review has no archived key advertises no certificate") {
            // A pre-V21 review can be APPROVED with `certificate_key = NULL` (the
            // migration's fail-closed case). The detail payload must not promise a
            // URL the certificate proxy will 404.
            val session = signupAndLogin("read-cert-nokey@example.com")
            val listingId = createListing(session.accessToken, session.accountId)
            val listingUuid = UUID.fromString(listingId)

            dbUpdate(
                "UPDATE restaurant_listings SET verification_status = 'VERIFIED' WHERE id = ?",
                listingUuid,
            )
            dbUpdate(
                """
                INSERT INTO halal_certification_reviews
                    (id, listing_id, submitted_by, state, decision_outcome, decision_by,
                     decision_at, created_at, updated_at)
                VALUES (?, ?, ?, 'APPROVED', 'APPROVED', ?, now(), now(), now())
                """.trimIndent(),
                UUID.randomUUID(),
                listingUuid,
                UUID.fromString(session.accountId),
                UUID.fromString(session.accountId),
            )

            val detail = get("/v1/listings/$listingId").json()
            detail["verificationStatus"].asString() shouldBe "VERIFIED"
            // Must be JSON null specifically: the loose `stringOrNull()` helper also
            // returns null for an object, which would let a wrongly-advertised
            // certificate payload pass this test.
            detail["certificate"] shouldBe JsonNull
            get("/v1/listings/$listingId/certificate").status shouldBe 404
        }

        test("public browse/detail reads need no token; writes stay authenticated (401)") {
            // Public reads (already exercised above without a token) work; a write
            // without a token is refused by the deny-by-default resource guard.
            val resp = post(
                "/v1/listings",
                json = linkedMapOf("name" to "X", "address" to "1 St", "lat" to 1.0, "lng" to 1.0),
            )

            resp.status shouldBe 401
            resp.json()["code"].asString() shouldBe "unauthorized"
        }

        test("the app boots without object storage, so the in-memory image port is in use") {
            // `storage` is absent in tests: no S3 adapter is activated and the
            // in-memory port backs the upload/serve round-trip asserted above.
            (appConfig.storage == null) shouldBe true
            (appConfig.verification == null) shouldBe true
        }

        test("the seed-image ingest runner is gated OFF by default (boot tests never hit the network)") {
            appConfig.seedIngest.enabled shouldBe false

            // The manifest is on the classpath for the gated runner's default
            // `classpath:seed-photos.json`, ready for an explicitly-enabled ingest.
            (javaClass.classLoader.getResource("seed-photos.json") != null) shouldBe true
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
