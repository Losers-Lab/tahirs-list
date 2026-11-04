package com.tahirslist.application.image

import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.imageio.ImageIO

/**
 * Pure JDK image resize for thumbnail pre-generation (docs/design/sc-157-image-variants.md).
 *
 * Uses `ImageIO` + a high-quality `Graphics2D` downscale — no third-party
 * imaging dependency, so the resize step stays in the framework-free
 * application layer (and in the classpath of every adapter).
 *
 * Policy:
 *  - Output is re-encoded to `image/jpeg` (quality 0.85) for photographic
 *    source; PNG with alpha is kept as PNG so transparency is preserved.
 *  - **Never upscales**: the resized width is `min(targetWidth, originalWidth)`.
 *  - Aspect ratio is preserved; only width is the target.
 *  - The stored media type is DERIVED from the bytes ([contentType]), never
 *    taken from a client header — those bytes are re-served publicly later.
 *  - An image whose declared dimensions exceed [MAX_IMAGE_EDGE_PX] or
 *    [MAX_IMAGE_PIXELS] is rejected from its header, before decoding.
 *  - [IllegalArgumentException] on unreadable / unsupported input (empty bytes,
 *    not a decodable image) — a symptom of bad data, surfaced loudly not silently.
 *
 * sc-183: multiple widths are produced at ingest via [resizeToWidth], covering
 * the monitor's max resolution (~1920) rather than any live viewport.
 */
object ImageResizer {

    private const val JPEG_QUALITY = 0.85f

    /**
     * The largest accepted width or height, in pixels (10 000).
     *
     * No phone camera or certificate scan comes anywhere near this, while a
     * single axis that long already produces a multi-gigabyte bitmap.
     */
    private const val MAX_IMAGE_EDGE_PX = 10_000

    /**
     * The largest accepted pixel count (40 million ≈ 8000×5000).
     *
     * The edge cap alone is not enough: 10 000×10 000 is 100 million pixels
     * (~400 MB decoded), so the total is capped too. Both checks run on the
     * header metadata, which is what stops a "decompression bomb" — a tiny
     * compressed file that expands to gigabytes of heap — from ever being
     * decoded. Dimensions are compared as [Long] so the product cannot overflow.
     */
    private const val MAX_IMAGE_PIXELS = 40_000_000L

    /**
     * Produce an image no wider than [targetWidth], preserving aspect ratio.
     *
     * @return the resized image as [StoredImage] with a concrete contentType
     *         (`image/jpeg` or `image/png`). An original already narrower than
     *         [targetWidth] is returned unchanged (never upscaled).
     * @throws IllegalArgumentException if [original.bytes] is not a decodable
     *         image, or declares dimensions beyond the supported limits.
     */
    fun resizeToWidth(
        original: StoredImage,
        targetWidth: Int,
    ): StoredImage {
        if (targetWidth <= 0) throw IllegalArgumentException("targetWidth must be positive")

        val buffered = decodeChecked(original)

        val originalWidth = buffered.width
        val scale = if (originalWidth <= targetWidth) 1.0 else targetWidth.toDouble() / originalWidth
        if (scale >= 1.0) return original // no upscale; already small enough

        val hasAlpha = buffered.colorModel.hasAlpha()
        val targetHeight = (buffered.height * scale).toInt().coerceAtLeast(1)
        val bufferType = if (hasAlpha) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB
        val resized = BufferedImage(targetWidth, targetHeight, bufferType)
        val g2 = resized.createGraphics()
        try {
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g2.drawImage(buffered, 0, 0, targetWidth, targetHeight, null)
        } finally {
            g2.dispose()
        }

        val contentType = if (hasAlpha) "image/png" else "image/jpeg"
        val out = ByteArrayOutputStream()
        val written = if (hasAlpha) {
            ImageIO.write(resized, "png", out)
        } else {
            val jpg = ImageIO.createImageOutputStream(out)
            try {
                ImageIO.write(resized, "jpeg", jpg)
            } finally {
                jpg.close()
            }
        }
        if (!written) throw IllegalArgumentException("Could not re-encode resized image")
        return StoredImage(bytes = out.toByteArray(), contentType = contentType)
    }

    /**
     * The media type of [bytes], derived from the image format the JDK decoder
     * actually recognises — never from a client-supplied `Content-Type`.
     *
     * Only the four formats this pipeline can serve are named; anything else is
     * an opaque `application/octet-stream` at the serving boundary. (`image/webp`
     * is mapped for completeness but the stock JDK ships no WebP reader, so it
     * is unreachable without an extra ImageIO plugin.)
     *
     * @throws IllegalArgumentException when [bytes] is not a decodable image.
     */
    fun contentType(bytes: ByteArray): String =
        contentTypeOrNull(bytes) ?: throw IllegalArgumentException("Input is not a decodable image")

    /** [contentType], or `null` when [bytes] is not a decodable image. */
    fun contentTypeOrNull(bytes: ByteArray): String? {
        if (bytes.isEmpty()) return null
        val input = ImageIO.createImageInputStream(ByteArrayInputStream(bytes)) ?: return null
        try {
            val readers = ImageIO.getImageReaders(input)
            if (!readers.hasNext()) return null
            val reader = readers.next()
            try {
                return contentTypeForFormat(reader.formatName)
            } finally {
                reader.dispose()
            }
        } catch (ignored: IOException) {
            return null
        } finally {
            input.close()
        }
    }

    private fun contentTypeForFormat(formatName: String): String? = when (formatName.lowercase()) {
        "jpeg", "jpg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        else -> null
    }

    /**
     * Decode [original] only after its *declared* dimensions pass the guard.
     *
     * The gate has to run on the header metadata: `ImageIO.read` offers no hook
     * between "parse the header" and "allocate the full-size raster", which is
     * exactly the gap a decompression bomb exploits. So the reader is driven
     * explicitly — `getWidth`/`getHeight` come from the header parse, and
     * `read(0)` runs only once the dimensions are known to be acceptable.
     */
    private fun decodeChecked(original: StoredImage): BufferedImage {
        val input = ImageIO.createImageInputStream(ByteArrayInputStream(original.bytes))
            ?: throw notDecodable(original)
        try {
            val readers = ImageIO.getImageReaders(input)
            if (!readers.hasNext()) throw notDecodable(original)
            val reader = readers.next()
            try {
                reader.input = input
                val width = reader.getWidth(0)
                val height = reader.getHeight(0)
                if (width > MAX_IMAGE_EDGE_PX ||
                    height > MAX_IMAGE_EDGE_PX ||
                    width.toLong() * height.toLong() > MAX_IMAGE_PIXELS
                ) {
                    throw IllegalArgumentException("Image dimensions ${width}x$height are not supported")
                }
                return reader.read(0) ?: throw notDecodable(original)
            } finally {
                reader.dispose()
            }
        } catch (cause: IOException) {
            // A truncated / corrupt stream is bad data, not a server fault.
            throw IllegalArgumentException(
                "Input is not a decodable image (contentType=${original.contentType})",
                cause,
            )
        } finally {
            input.close()
        }
    }

    private fun notDecodable(original: StoredImage): IllegalArgumentException =
        IllegalArgumentException("Input is not a decodable image (contentType=${original.contentType})")
}
