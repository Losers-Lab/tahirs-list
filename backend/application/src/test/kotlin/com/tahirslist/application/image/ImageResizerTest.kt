package com.tahirslist.application.image

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/**
 * ImageResizer is pure JDK ImageIO: no container, no network. Unit tests cover
 * the sizing policy that underpins the sc-157/sc-183 variant contracts:
 *  - each thumbnail never exceeds its target width,
 *  - never upscale (small originals are returned unchanged),
 *  - aspect ratio preserved,
 *  - unreadable input rejected loudly before a half-ingested row can occur.
 *
 * sc-183: multiple widths (400/768/1280/1920) are produced via [ImageResizer.resizeToWidth],
 * sized to the monitor's max resolution (~full-HD 1920) rather than any live
 * container/viewport width (founder binding spec).
 */
class ImageResizerTest : FunSpec() {

    /** Build a width×height opaque jpeg. */
    private fun jpeg(width: Int, height: Int): ByteArray {
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.color = Color(200, 100, 60)
        g.fillRect(0, 0, width, height)
        g.dispose()
        val out = ByteArrayOutputStream()
        ImageIO.write(img, "jpeg", out)
        return out.toByteArray()
    }

    private fun decodedWidth(bytes: ByteArray): Int =
        ImageIO.read(java.io.ByteArrayInputStream(bytes)).width

    private fun png(width: Int, height: Int): ByteArray {
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val out = ByteArrayOutputStream()
        ImageIO.write(img, "png", out)
        return out.toByteArray()
    }

    private fun gif(width: Int, height: Int): ByteArray {
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val out = ByteArrayOutputStream()
        ImageIO.write(img, "gif", out)
        return out.toByteArray()
    }

    /**
     * A small, valid PNG whose IHDR has been rewritten to *declare*
     * [width]×[height], with the IHDR CRC recomputed.
     *
     * This is the shape of a decompression bomb: the file stays a few kilobytes
     * (the IDAT still carries the 2×2 payload) while the declared raster would be
     * gigabytes. `ImageIO` parses the header normally — so `getWidth`/`getHeight`
     * report the huge values and the reader really is "decodable" up to metadata
     * — and only `read()` would blow up. A real PNG was patched rather than a
     * hand-built header because the JDK reader validates the IHDR CRC and expects
     * an IDAT marker before it will report dimensions.
     */
    private fun pngDeclaring(width: Int, height: Int): ByteArray {
        val patched = png(2, 2).copyOf()
        // Layout: 8-byte signature, then length(4) "IHDR" data(13) crc(4).
        writeInt(patched, at = 16, value = width)
        writeInt(patched, at = 20, value = height)
        // The CRC covers the chunk type plus its 13 data bytes (offsets 12..28).
        val crc = java.util.zip.CRC32().apply { update(patched, 12, 17) }.value
        writeInt(patched, at = 29, value = (crc and 0xFFFF_FFFFL).toInt())
        return patched
    }

    private fun writeInt(target: ByteArray, at: Int, value: Int) {
        target[at] = (value ushr 24).toByte()
        target[at + 1] = (value ushr 16).toByte()
        target[at + 2] = (value ushr 8).toByte()
        target[at + 3] = value.toByte()
    }

    private val contentType = "image/jpeg"

    init {
        test("large jpeg is downscaled to each target width, aspect preserved") {
            val original = StoredImage(jpeg(3000, 2000), contentType)

            val w400 = ImageResizer.resizeToWidth(original, 400)
            decodedWidth(w400.bytes) shouldBe 400
            ImageIO.read(java.io.ByteArrayInputStream(w400.bytes)).height shouldBe (400 * 2000 / 3000)

            val w768 = ImageResizer.resizeToWidth(original, 768)
            decodedWidth(w768.bytes) shouldBe 768
            ImageIO.read(java.io.ByteArrayInputStream(w768.bytes)).height shouldBe (768 * 2000 / 3000)

            val w1280 = ImageResizer.resizeToWidth(original, 1280)
            decodedWidth(w1280.bytes) shouldBe 1280
            ImageIO.read(java.io.ByteArrayInputStream(w1280.bytes)).height shouldBe (1280 * 2000 / 3000)

            val w1920 = ImageResizer.resizeToWidth(original, 1920)
            decodedWidth(w1920.bytes) shouldBe 1920
            ImageIO.read(java.io.ByteArrayInputStream(w1920.bytes)).height shouldBe (1920 * 2000 / 3000)

            w400.contentType shouldBe "image/jpeg"
            w768.contentType shouldBe "image/jpeg"
            w1280.contentType shouldBe "image/jpeg"
            w1920.contentType shouldBe "image/jpeg"
        }

        test("thumbnail never exceeds the target width for any input") {
            val original = StoredImage(jpeg(3000, 2000), contentType)
            decodedWidth(ImageResizer.resizeToWidth(original, 400).bytes) shouldBe 400
            decodedWidth(ImageResizer.resizeToWidth(original, 768).bytes) shouldBe 768
            decodedWidth(ImageResizer.resizeToWidth(original, 1280).bytes) shouldBe 1280
            decodedWidth(ImageResizer.resizeToWidth(original, 1920).bytes) shouldBe 1920
        }

        test("smaller-than-target originals are returned unchanged (never upscale)") {
            val original = StoredImage(jpeg(200, 150), contentType)

            ImageResizer.resizeToWidth(original, 1920).bytes.contentEquals(original.bytes) shouldBe true
            val decoded = ImageIO.read(java.io.ByteArrayInputStream(original.bytes))
            decoded.width shouldBe 200
            decoded.height shouldBe 150
        }

        test("PNG with alpha is re-encoded as PNG (transparency preserved)") {
            val img = BufferedImage(800, 600, BufferedImage.TYPE_INT_ARGB)
            val out = ByteArrayOutputStream()
            ImageIO.write(img, "png", out)

            val thumb = ImageResizer.resizeToWidth(StoredImage(out.toByteArray(), "image/png"), 400)
            thumb.contentType shouldBe "image/png"
            val decoded = ImageIO.read(java.io.ByteArrayInputStream(thumb.bytes))
            decoded.width shouldBe 400
            decoded.colorModel.hasAlpha() shouldBe true
        }

        test("unreadable bytes are rejected with IllegalArgumentException") {
            shouldThrow<IllegalArgumentException> {
                ImageResizer.resizeToWidth(StoredImage("not-an-image".toByteArray(), "image/jpeg"), 400)
            }
        }

        test("an image declaring an edge over 10 000 px is rejected from its header, before any decode") {
            // 20 000x20 000 would be a 1.6 GB raster; the file itself stays tiny
            // (its IDAT still holds a 2x2 payload). The guard must fire on the
            // header metadata, i.e. before `read()` allocates the raster.
            val bomb = pngDeclaring(width = 20_000, height = 20_000)

            // The bytes ARE a recognisable PNG (the reader parsed the header) —
            // so the rejection below is the dimension gate, not "not an image".
            ImageResizer.contentType(bomb) shouldBe "image/png"

            val error = shouldThrow<IllegalArgumentException> {
                ImageResizer.resizeToWidth(StoredImage(bomb, "image/png"), 400)
            }
            // Pin the gate's own message: a plain decode failure throws the same
            // exception type, so the type alone would not prove the header gate ran.
            error.message.orEmpty() shouldContain "dimensions"
        }

        test("an image whose pixel count exceeds the limit is rejected even with allowed edges") {
            // 8000x8000 = 64 Mpx: each edge is within 10 000, the total is not.
            val bomb = pngDeclaring(width = 8_000, height = 8_000)

            val error = shouldThrow<IllegalArgumentException> {
                ImageResizer.resizeToWidth(StoredImage(bomb, "image/png"), 400)
            }
            error.message.orEmpty() shouldContain "dimensions"
        }

        test("contentType is derived from the bytes, never from the caller's label") {
            ImageResizer.contentType(jpeg(40, 30)) shouldBe "image/jpeg"
            ImageResizer.contentType(png(40, 30)) shouldBe "image/png"
            ImageResizer.contentType(gif(40, 30)) shouldBe "image/gif"

            // The label the caller passes is irrelevant where the type is stored:
            // a PNG labelled text/html is still re-encoded and stored as an image.
            val alphaPng = ByteArrayOutputStream().also {
                ImageIO.write(BufferedImage(800, 600, BufferedImage.TYPE_INT_ARGB), "png", it)
            }.toByteArray()
            ImageResizer.resizeToWidth(StoredImage(alphaPng, "text/html"), 400)
                .contentType shouldBe "image/png"
        }

        test("contentType rejects undecodable bytes, and the nullable variant reports null") {
            val garbage = "this is not an image".toByteArray()

            shouldThrow<IllegalArgumentException> { ImageResizer.contentType(garbage) }
            ImageResizer.contentTypeOrNull(garbage) shouldBe null
            ImageResizer.contentTypeOrNull(ByteArray(0)) shouldBe null
        }
    }
}