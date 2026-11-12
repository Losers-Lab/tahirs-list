package com.tahirslist.bootstrap

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * SC-205 D-1 / AC-4: every public image URL carries the listing's photo version
 * (`&v=`) once a photo exists, so replacing it changes the cache key instead of
 * serving yesterday's bytes for another 24 hours.
 *
 * The image route ignores the parameter; this test pins that the read surface
 * always supplies it, and that a replace changes it.
 */
class Sc205PhotoVersionEndpointTest : KtorPostgresTest() {

    private fun jpegBytes(width: Int = 640, height: Int = 480): ByteArray {
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.color = Color(10, 120, 200)
        g.fillRect(0, 0, width, height)
        g.dispose()
        val out = ByteArrayOutputStream()
        ImageIO.write(img, "jpeg", out)
        return out.toByteArray()
    }

    private suspend fun upload(listingId: String, token: String) =
        put(
            "/v1/listings/$listingId/image",
            token = token,
            multipart = multipart { file("image", jpegBytes(), "image/jpeg", "hero.jpg") },
        )

    private fun versionOf(url: String): String = url.substringAfter("&v=", missingDelimiterValue = "")

    init {
        test("a listing with no photo has version-less image URLs") {
            val owner = signupAndLogin("version-none@example.com")
            val listingId = createListing(owner.accessToken, owner.accountId)

            val detail = get("/v1/listings/$listingId").json()

            versionOf(detail["imageUrl"]!!.jsonPrimitive.content) shouldBe ""
            detail["imageSrcset"]!!.jsonArray.forEach {
                versionOf(it.jsonObject["url"]!!.jsonPrimitive.content) shouldBe ""
            }
        }

        test("after an upload every image URL carries the version, and all of them agree") {
            val owner = signupAndLogin("version-upload@example.com")
            val listingId = createListing(owner.accessToken, owner.accountId)

            upload(listingId, owner.accessToken).status shouldBe 204

            val detail = get("/v1/listings/$listingId").json()
            val fullUrl = detail["imageUrl"]!!.jsonPrimitive.content
            fullUrl shouldContain "&v="
            val version = versionOf(fullUrl)
            version shouldNotBe ""

            detail["imageSrcset"]!!.jsonArray.forEach {
                versionOf(it.jsonObject["url"]!!.jsonPrimitive.content) shouldBe version
            }
            versionOf(detail["imageThumbnailUrl"]!!.jsonPrimitive.content) shouldBe version
        }

        test("replacing the photo changes the version, so the URL changes too") {
            val owner = signupAndLogin("version-replace@example.com")
            val listingId = createListing(owner.accessToken, owner.accountId)

            upload(listingId, owner.accessToken).status shouldBe 204
            val first = versionOf(get("/v1/listings/$listingId").json()["imageUrl"]!!.jsonPrimitive.content)

            upload(listingId, owner.accessToken).status shouldBe 204
            val second = versionOf(get("/v1/listings/$listingId").json()["imageUrl"]!!.jsonPrimitive.content)

            second shouldNotBe first
            (second.toLong() > first.toLong()) shouldBe true
        }

        test("the versioned URL still serves the image (the parameter is opaque to the route)") {
            val owner = signupAndLogin("version-served@example.com")
            val listingId = createListing(owner.accessToken, owner.accountId)
            upload(listingId, owner.accessToken).status shouldBe 204

            val url = get("/v1/listings/$listingId").json()["imageUrl"]!!.jsonPrimitive.content
            val version = versionOf(url)
            get("/v1/listings/$listingId/image?variant=full&v=$version").status shouldBe 200
        }
    }
}
