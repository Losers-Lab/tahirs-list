package com.tahirslist.web

import com.tahirslist.application.agent.CertificateFacts
import com.tahirslist.application.image.ImageVariant
import com.tahirslist.application.listing.ListingSearchResult
import com.tahirslist.application.verification.HalalCertificationReviewRepository
import com.tahirslist.domain.restaurant.RestaurantListing
import com.tahirslist.domain.restaurant.VerificationStatus
import io.ktor.server.application.ApplicationCall
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/** One responsive image candidate: `url` serves an image [width] pixels wide. */
@Serializable
data class SrcsetEntry(val width: Int, val url: String)

/** Minimal browse/search card; carries a thumbnail only ("no oversized fetch on cards"). */
@Serializable
data class BrowseCard(
    val id: String,
    val name: String,
    val address: String,
    val city: String?,
    val province: String?,
    val postal: String?,
    val country: String?,
    val lat: Double,
    val lng: Double,
    val cuisine: String?,
    val isHandCut: Boolean?,
    val halalScope: String,
    val isDelivery: Boolean?,
    val verificationStatus: String,
    val imageThumbnailUrl: String,
    val imageSrcset: List<SrcsetEntry>,
    /** SC-231: the credit the photo's source requires; null when there is no photo. */
    val photoAttribution: String? = null,
    val photoLicence: String? = null,
    val photoSource: String? = null,
    val photoSourceUrl: String? = null,
)

/** A browse card plus the search-only facts (rating, straight-line distance). */
@Serializable
data class SearchCard(
    val id: String,
    val name: String,
    val address: String,
    val city: String?,
    val province: String?,
    val postal: String?,
    val country: String?,
    val lat: Double,
    val lng: Double,
    val cuisine: String?,
    val isHandCut: Boolean?,
    val halalScope: String,
    val isDelivery: Boolean?,
    val verificationStatus: String,
    val imageThumbnailUrl: String,
    val imageSrcset: List<SrcsetEntry>,
    val rating: Double?,
    val distanceMiles: Double,
)

/** CertificatePanel display facts sourced from the latest APPROVED review. */
@Serializable
data class CertificatePayload(
    val certifier: String?,
    /** ISO-8601 instant the committee approved the certification. */
    val reviewedOn: String,
    /** ISO-8601 date the certification expires; null when unknown. */
    val expiresOn: String?,
    /** Same-origin URL serving the archived certification image. */
    val certificateUrl: String,
)

/** Full listing detail, including the full-res hero and certificate facts. */
@Serializable
data class DetailResponse(
    val id: String,
    val name: String,
    val address: String,
    val city: String?,
    val province: String?,
    val postal: String?,
    val country: String?,
    val lat: Double,
    val lng: Double,
    val cuisine: String?,
    val isHandCut: Boolean?,
    val halalScope: String,
    val crossContamination: String,
    val isDelivery: Boolean?,
    val verificationStatus: String,
    val imageThumbnailUrl: String,
    val imageSrcset: List<SrcsetEntry>,
    val imageUrl: String,
    val certificate: CertificatePayload? = null,
    /** SC-231: the credit the photo's source requires; null when there is no photo. */
    val photoAttribution: String? = null,
    val photoLicence: String? = null,
    val photoSource: String? = null,
    val photoSourceUrl: String? = null,
)

/**
 * The query-value slug for a variant. `thumbnail` stays the alias of the 400px
 * width (the legacy sc-157 URL semantics), and the wider widths are exposed as
 * `thumbnail_<width>` so a srcset can tag each candidate with an exact width.
 */
internal fun variantSlug(variant: ImageVariant): String = when (variant) {
    ImageVariant.THUMBNAIL_400 -> "thumbnail"
    ImageVariant.THUMBNAIL_768 -> "thumbnail_768"
    ImageVariant.THUMBNAIL_1280 -> "thumbnail_1280"
    ImageVariant.THUMBNAIL_1920 -> "thumbnail_1920"
    ImageVariant.FULL -> "full"
}

internal fun parseVariant(raw: String): ImageVariant? = when (raw.lowercase()) {
    "thumbnail", "thumbnail_400" -> ImageVariant.THUMBNAIL_400
    "thumbnail_768" -> ImageVariant.THUMBNAIL_768
    "thumbnail_1280" -> ImageVariant.THUMBNAIL_1280
    "thumbnail_1920" -> ImageVariant.THUMBNAIL_1920
    "full" -> ImageVariant.FULL
    else -> null
}

internal fun ApplicationCall.imageUrl(id: Uuid, variant: ImageVariant): String =
    "${baseUrl()}/v1/listings/$id/image?variant=${variantSlug(variant)}"

internal fun ApplicationCall.certificateUrl(id: Uuid): String =
    "${baseUrl()}/v1/listings/$id/certificate"

/** The sc-183 responsive width set a frontend may turn into a `srcset`. */
internal fun ApplicationCall.imageSrcset(id: Uuid): List<SrcsetEntry> =
    ImageVariant.thumbnailVariants.map { variant ->
        SrcsetEntry(width = variant.widthPx!!, url = imageUrl(id, variant))
    }

internal fun ApplicationCall.toBrowseCard(listing: RestaurantListing): BrowseCard = BrowseCard(
    id = listing.id.toString(),
    name = listing.name,
    address = listing.address,
    city = listing.city,
    province = listing.province,
    postal = listing.postal,
    country = listing.country,
    lat = listing.location.lat,
    lng = listing.location.lng,
    cuisine = listing.cuisine?.value,
    isHandCut = listing.isHandCut,
    halalScope = listing.halalScope.name,
    isDelivery = listing.isDelivery,
    verificationStatus = listing.verificationStatus.name,
    imageThumbnailUrl = imageUrl(listing.id, ImageVariant.THUMBNAIL_400),
    imageSrcset = imageSrcset(listing.id),
    photoAttribution = credit(listing)?.attribution,
    photoLicence = credit(listing)?.licence?.name,
    photoSource = credit(listing)?.sourceKind?.name,
    photoSourceUrl = credit(listing)?.sourceUrl,
)

/**
 * SC-231: the photo a page may show — and therefore the credit it must show.
 * An expired Places cache is already invisible (the endpoint 404s), so its
 * credit must not be advertised either.
 */
internal fun credit(listing: RestaurantListing) =
    listing.coverPhoto?.takeIf { it.isServableAt(java.time.Instant.now()) }

/**
 * SC-231: a cached photo must not outlive its own expiry in a browser or CDN.
 * A permanent photo keeps the normal day; a cache is bounded by the remaining
 * window, so the licence deadline is respected by every cache on the path.
 */
internal fun coverCacheControl(cover: com.tahirslist.domain.restaurant.CoverPhoto?, now: java.time.Instant): String {
    if (cover == null || cover.storageClass == com.tahirslist.domain.restaurant.PhotoStorage.PERMANENT) {
        return "public, max-age=86400"
    }
    val remaining = java.time.Duration.between(now, cover.expiresAt).seconds.coerceIn(0, 86_400)
    return "public, max-age=$remaining"
}

internal fun ApplicationCall.toSearchCard(result: ListingSearchResult): SearchCard = SearchCard(
    id = result.id.toString(),
    name = result.name,
    address = result.address,
    city = result.city,
    province = result.province,
    postal = result.postal,
    country = result.country,
    lat = result.location.lat,
    lng = result.location.lng,
    cuisine = result.cuisine?.value,
    isHandCut = result.isHandCut,
    halalScope = result.halalScope.name,
    isDelivery = result.isDelivery,
    verificationStatus = result.verificationStatus.name,
    imageThumbnailUrl = imageUrl(result.id, ImageVariant.THUMBNAIL_400),
    imageSrcset = imageSrcset(result.id),
    rating = result.rating?.value?.toDouble(),
    distanceMiles = result.distanceMiles,
)

internal suspend fun ApplicationCall.toDetail(
    listing: RestaurantListing,
    reviews: HalalCertificationReviewRepository,
): DetailResponse = DetailResponse(
    id = listing.id.toString(),
    name = listing.name,
    address = listing.address,
    city = listing.city,
    province = listing.province,
    postal = listing.postal,
    country = listing.country,
    lat = listing.location.lat,
    lng = listing.location.lng,
    cuisine = listing.cuisine?.value,
    isHandCut = listing.isHandCut,
    halalScope = listing.halalScope.name,
    crossContamination = listing.crossContamination.name,
    isDelivery = listing.isDelivery,
    verificationStatus = listing.verificationStatus.name,
    imageThumbnailUrl = imageUrl(listing.id, ImageVariant.THUMBNAIL_400),
    imageSrcset = imageSrcset(listing.id),
    imageUrl = imageUrl(listing.id, ImageVariant.FULL),
    certificate = certificateDetails(listing, reviews),
    photoAttribution = credit(listing)?.attribution,
    photoLicence = credit(listing)?.licence?.name,
    photoSource = credit(listing)?.sourceKind?.name,
    photoSourceUrl = credit(listing)?.sourceUrl,
)

/**
 * The certificate display facts for a VERIFIED listing. Source of truth is the
 * latest APPROVED certification review; null for an unverified listing or one
 * whose certificate was never approved, so the detail page renders no panel.
 *
 * The review must also carry an archived key (V21): without it the public proxy
 * has nothing to serve, so advertising a URL would promise a 404.
 */
private suspend fun ApplicationCall.certificateDetails(
    listing: RestaurantListing,
    reviews: HalalCertificationReviewRepository,
): CertificatePayload? {
    val latestApproved = if (listing.verificationStatus == VerificationStatus.VERIFIED) {
        reviews.findLatestApprovedByListing(listing.id)
    } else {
        null
    }
    val facts = CertificateFacts.of(listing, latestApproved) ?: return null
    return CertificatePayload(
        certifier = facts.certifier,
        reviewedOn = facts.reviewedOn.toString(),
        expiresOn = facts.expiresOn?.toString(),
        certificateUrl = certificateUrl(listing.id),
    )
}
