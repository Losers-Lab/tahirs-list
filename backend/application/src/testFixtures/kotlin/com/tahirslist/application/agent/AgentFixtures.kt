package com.tahirslist.application.agent

import com.tahirslist.application.geo.GeocodedPlace
import com.tahirslist.application.geo.GeocoderPort
import com.tahirslist.application.ingestion.BatchLoadResult
import com.tahirslist.application.ingestion.ListingWrite
import com.tahirslist.application.ingestion.WriteTarget
import com.tahirslist.application.listing.ListingSearchQuery
import com.tahirslist.application.listing.ListingSearchResult
import com.tahirslist.application.listing.RestaurantListingRepository
import com.tahirslist.application.verification.HalalCertificationReviewRepository
import com.tahirslist.domain.restaurant.Cuisine
import com.tahirslist.domain.restaurant.HalalItem
import com.tahirslist.domain.restaurant.HalalScope
import com.tahirslist.domain.restaurant.LatLng
import com.tahirslist.domain.restaurant.RestaurantListing
import com.tahirslist.domain.restaurant.VerificationStatus
import com.tahirslist.domain.verification.HalalCertificationReview
import com.tahirslist.domain.verification.SuggestionVerdict
import com.tahirslist.domain.verification.VerificationSuggestion
import com.tahirslist.domain.verification.VerificationState
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.datetime.LocalDate

/** Shared fixtures for the agent-surface tests. */

val SAMPLE_ID: Uuid = Uuid.parse("11111111-2222-3333-4444-555555555555")
val OTHER_ID: Uuid = Uuid.parse("66666666-7777-8888-9999-000000000000")
val COMMITTEE_ID: Uuid = Uuid.parse("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")
private val NOW: Instant = Clock.System.now()

fun sampleListing(
    id: Uuid = SAMPLE_ID,
    name: String = "Halal Grill",
    verified: Boolean = false,
    halalScope: HalalScope = HalalScope.PARTIALLY_HALAL,
    halalItems: Set<HalalItem> = setOf(HalalItem("Chicken", true)),
    isHandCut: Boolean? = true,
    isDelivery: Boolean? = null,
): RestaurantListing = RestaurantListing.fromStorage(
    id = id,
    name = name,
    address = "123 Main St",
    city = "Toronto",
    province = "ON",
    postal = "M5V 1A1",
    country = "CA",
    location = LatLng(43.6532, -79.3832),
    cuisine = Cuisine("mediterranean"),
    isHandCut = isHandCut,
    isDelivery = isDelivery,
    price = null,
    rating = null,
    ownerId = null,
    brandId = null,
    provenance = null,
    verificationStatus = if (verified) VerificationStatus.VERIFIED else VerificationStatus.UNVERIFIED,
    createdAt = NOW,
    halalScope = halalScope,
    halalItems = halalItems,
)

fun approvedReview(
    listingId: Uuid = SAMPLE_ID,
    certificateKey: String? = "certificates/$listingId.jpg",
    certifier: String? = "Halal Certification Board",
    expiresOn: LocalDate? = LocalDate(2027, 1, 1),
): HalalCertificationReview {
    val created = HalalCertificationReview.create(
        listingId = listingId,
        submittedBy = COMMITTEE_ID,
        now = NOW,
    )
    return created
        .copy(certificateKey = certificateKey)
        .beginAiReview(NOW)
        .recordAiSuggestion(VerificationSuggestion(SuggestionVerdict.APPROVE, 0.99), NOW)
        .beginHumanReview(NOW)
        .approve(COMMITTEE_ID, now = NOW, certifier = certifier, expiresOn = expiresOn)
}

fun sampleResult(
    id: Uuid = SAMPLE_ID,
    name: String = "Halal Grill",
    distanceMiles: Double = 1.25,
): ListingSearchResult = ListingSearchResult(
    id = id,
    name = name,
    address = "123 Main St",
    city = "Toronto",
    province = "ON",
    postal = "M5V 1A1",
    country = "CA",
    location = LatLng(43.6532, -79.3832),
    cuisine = Cuisine("mediterranean"),
    isHandCut = true,
    halalScope = HalalScope.PARTIALLY_HALAL,
    isDelivery = null,
    verificationStatus = VerificationStatus.UNVERIFIED,
    rating = null,
    distanceMiles = distanceMiles,
)

/** Records the call it received and returns a fixed page. */
class RecordingSearchQuery(
    private val results: List<ListingSearchResult> = listOf(sampleResult()),
) : ListingSearchQuery {
    var center: LatLng? = null
    var radiusMiles: Double? = null
    var offset: Int? = null
    var limit: Int? = null

    override suspend fun searchNearby(
        center: LatLng,
        radiusMiles: Double,
        filters: com.tahirslist.application.listing.ListingSearchFilters,
        offset: Int,
        limit: Int,
    ): List<ListingSearchResult> {
        this.center = center
        this.radiusMiles = radiusMiles
        this.offset = offset
        this.limit = limit
        return results
    }
}

class RecordingGeocoder(private val place: GeocodedPlace?) : GeocoderPort {
    var queried: String? = null

    override suspend fun geocode(address: String): GeocodedPlace? {
        queried = address
        return place
    }
}

class FakeListingRepository(private val listing: RestaurantListing?) : RestaurantListingRepository {
    override suspend fun save(listing: RestaurantListing): RestaurantListing = listing
    override suspend fun findById(id: Uuid): RestaurantListing? = listing?.takeIf { it.id == id }
    override suspend fun update(listing: RestaurantListing): RestaurantListing? = listing
    override suspend fun updateVerificationStatus(id: Uuid, status: VerificationStatus): RestaurantListing? = listing
    override suspend fun bumpImageVersion(id: Uuid): Long? = listing?.takeIf { it.id == id }?.imageVersion
    override suspend fun findAll(): List<RestaurantListing> = listOfNotNull(listing)
    override suspend fun findClaim(source: String, sourceRecordRef: String): RestaurantListing? = null
    override suspend fun findByVenueKey(venueKey: String): RestaurantListing? = null
    override suspend fun findByNormalizedLocation(lat: Double, lng: Double): List<RestaurantListing> = emptyList()
    override suspend fun applyBatch(
        writes: List<ListingWrite>,
        merges: List<com.tahirslist.application.ingestion.ListingMerge>,
        reattachments: List<com.tahirslist.application.ingestion.ClaimReattachment>,
    ): BatchLoadResult =
        BatchLoadResult(inserted = writes.count { it.target == WriteTarget.Insert }, merged = merges.size)
}

class FakeReviewRepository(private val review: HalalCertificationReview?) :
    HalalCertificationReviewRepository {
    var lookups: Int = 0

    override suspend fun save(review: HalalCertificationReview): HalalCertificationReview = review
    override suspend fun findById(id: Uuid): HalalCertificationReview? = review?.takeIf { it.id == id }
    override suspend fun findByState(state: VerificationState): List<HalalCertificationReview> =
        listOfNotNull(review).filter { it.state == state }

    override suspend fun findLatestApprovedByListing(listingId: Uuid): HalalCertificationReview? {
        lookups++
        return review?.takeIf { it.listingId == listingId }
    }
}

fun place(lat: Double = 43.6532, lng: Double = -79.3832): GeocodedPlace = GeocodedPlace(
    location = LatLng(lat, lng),
    displayName = "Toronto, ON, Canada",
    providerRef = "N/123",
)

fun now(): Instant = NOW
