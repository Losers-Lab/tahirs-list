package com.tahirslist.application.image

import kotlin.uuid.Uuid

/**
 * Ingest (or re-ingest) one restaurant's hero image: stores the full-res
 * original and every pre-generated thumbnail width (docs/design/sc-157-image-variants.md;
 * sc-183 makes the thumbnail set 400/768/1280/1920).
 *
 * Thumbnail pre-generation happens HERE (at ingest), not on the serving path, so
 * a card never receives oversized bytes or triggers on-the-fly processing. All
 * variants are written independently to [ImagePort], one per storage key.
 *
 * The media type persisted with every variant is derived from the bytes
 * ([ImageResizer.contentType]), never copied from the caller: the stored type is
 * later re-served on the public image endpoint, so a client-chosen value (say
 * `text/html`) must never reach storage.
 *
 * @throws IllegalArgumentException if [original] is not a decodable image (the
 *         resize step rejects it before anything is persisted).
 */
class IngestHeroImage(private val port: ImagePort) {

    /**
     * Store [original] as the listing's hero: FULL = original bytes, plus one
     * distinct variant per sc-183 thumbnail width (400/768/1280/1920).
     *
     * @param listingId the owning restaurant listing.
     * @param original the original image bytes; its declared content type is
     *        ignored in favour of the type derived from the bytes.
     */
    suspend fun ingest(listingId: Uuid, original: StoredImage) {
        // Derive the type first: it also validates that the bytes decode, so an
        // unreadable upload fails before anything is persisted.
        val contentType = ImageResizer.contentType(original.bytes)
        val source = original.copy(contentType = contentType)

        // Resize every thumbnail width FIRST: reject a bad image before persisting
        // the original, so a half-ingested row (original saved, a thumb failed)
        // is impossible.
        val thumbs: List<Pair<ImageVariant, StoredImage>> =
            ImageVariant.thumbnailVariants.map { variant ->
                variant to ImageResizer.resizeToWidth(source, variant.widthPx!!)
            }

        port.save(listingId, ImageVariant.FULL, contentType, original.bytes)
        thumbs.forEach { (variant, resized) ->
            port.save(listingId, variant, resized.contentType, resized.bytes)
        }
    }
}
