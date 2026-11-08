package com.tahirslist.application.ingestion

import com.tahirslist.application.listing.RestaurantListingRepository
import com.tahirslist.domain.restaurant.LatLng
import com.tahirslist.domain.restaurant.Provenance
import com.tahirslist.domain.restaurant.RestaurantListing
import com.tahirslist.domain.restaurant.VerificationStatus
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runTest

private fun storedListing(
    id: Uuid,
    name: String,
    venueKey: String? = null,
): RestaurantListing = RestaurantListing.fromStorage(
    id = id,
    name = name,
    address = "1 St",
    location = LatLng(43.0, -79.0),
    cuisine = null,
    ownerId = null,
    brandId = null,
    provenance = Provenance.RESEARCH_SEED,
    venueKey = venueKey,
    verificationStatus = VerificationStatus.UNVERIFIED,
    createdAt = Instant.parse("2026-11-01T00:00:00Z"),
)

private class FakeListings(
    private val claims: Map<String, RestaurantListing> = emptyMap(),
    private val venues: Map<String, RestaurantListing> = emptyMap(),
    private val locations: Map<String, List<RestaurantListing>> = emptyMap(),
) : RestaurantListingRepository {
    var applied: List<ListingWrite>? = null

    override suspend fun findClaim(source: String, sourceRecordRef: String): RestaurantListing? =
        claims["$source::$sourceRecordRef"]

    override suspend fun findByVenueKey(venueKey: String): RestaurantListing? = venues[venueKey]

    override suspend fun findByNormalizedLocation(lat: Double, lng: Double): List<RestaurantListing> =
        locations["$lat,$lng"].orEmpty()

    override suspend fun applyBatch(writes: List<ListingWrite>): BatchLoadResult {
        applied = writes
        return BatchLoadResult(
            inserted = writes.count { it.target is WriteTarget.Insert },
            upgraded = writes.count { it.target is WriteTarget.Update },
            claimsWritten = writes.sumOf { it.candidate.claims.size },
        )
    }

    override suspend fun save(listing: RestaurantListing): RestaurantListing = error("not used")
    override suspend fun findById(id: Uuid): RestaurantListing? = error("not used")
    override suspend fun update(listing: RestaurantListing): RestaurantListing? = error("not used")
    override suspend fun updateVerificationStatus(id: Uuid, status: VerificationStatus): RestaurantListing? =
        error("not used")

    override suspend fun findAll(): List<RestaurantListing> = error("not used")
}

private class FakeBrands(private val known: Map<String, Brand> = emptyMap()) : BrandRepository {
    override suspend fun resolve(name: String): Brand? = known[name]
    override suspend fun resolveOrCreate(name: String): Brand = known[name] ?: error("not used")
}

class LoadIngestionBatchTest : FunSpec({

    test("an unmatched candidate is inserted, and execute applies it in one batch") {
        val listings = FakeListings()
        val load = LoadIngestionBatch(listings, FakeBrands())

        val review = load.preview(CandidateBatch("run-1", Instant.parse("2026-11-08T00:00:00Z"), listOf(candidate())))
        review.inserted shouldBe 1
        review.upgraded shouldBe 0
        listings.applied shouldBe null

        val result = load.execute(review)
        result.inserted shouldBe 1
        result.claimsWritten shouldBe 1
        listings.applied?.single()?.target shouldBe WriteTarget.Insert
    }

    test("an existing claim upgrades its listing instead of inserting a duplicate") {
        val existing = storedListing(Uuid.random(), "Existing")
        val listings = FakeListings(claims = mapOf("hms::HMS-1" to existing))
        val load = LoadIngestionBatch(listings, FakeBrands())

        val review = load.preview(CandidateBatch("run-1", Instant.parse("2026-11-08T00:00:00Z"), listOf(candidate())))

        review.upgraded shouldBe 1
        review.inserted shouldBe 0
        review.writes.single().target shouldBe WriteTarget.Update(existing.id)
    }

    test("a venue-key match upgrades even when the claim is new") {
        val existing = storedListing(Uuid.random(), "Existing", venueKey = "a".repeat(64))
        val listings = FakeListings(venues = mapOf("a".repeat(64) to existing))
        val load = LoadIngestionBatch(listings, FakeBrands())

        val review = load.preview(CandidateBatch("run-1", Instant.parse("2026-11-08T00:00:00Z"), listOf(candidate())))

        review.upgraded shouldBe 1
        review.writes.single().target shouldBe WriteTarget.Update(existing.id)
    }

    test("a location-only match is held as a suspected duplicate, never written") {
        val existing = storedListing(Uuid.random(), "Same place, different identity")
        val listings = FakeListings(locations = mapOf("43.665206,-79.384523" to listOf(existing)))
        val load = LoadIngestionBatch(listings, FakeBrands())

        val review = load.preview(CandidateBatch("run-1", Instant.parse("2026-11-08T00:00:00Z"), listOf(candidate())))

        review.held.single().existingListingIds shouldBe listOf(existing.id)
        review.writes shouldBe emptyList()

        val result = load.execute(review)
        result.held shouldBe 1
        result.inserted shouldBe 0
    }

    test("a claim that points at a different listing aborts the preview as a conflict") {
        val claimListing = storedListing(Uuid.random(), "Claim owner")
        val venueListing = storedListing(Uuid.random(), "Venue owner", venueKey = "a".repeat(64))
        val listings = FakeListings(
            claims = mapOf("hms::HMS-1" to claimListing),
            venues = mapOf("a".repeat(64) to venueListing),
        )
        val load = LoadIngestionBatch(listings, FakeBrands())

        val review = load.preview(CandidateBatch("run-1", Instant.parse("2026-11-08T00:00:00Z"), listOf(candidate())))

        review.rejects.single().reason shouldBe RejectReason.CLAIM_CONFLICT
        review.writes shouldBe emptyList()
    }

    test("two candidates for the same venue become one write carrying both claims") {
        val first = candidate(claims = listOf(claim(source = "hms", record = "H-1")))
        val second = candidate(
            claims = listOf(claim(source = "hfsaa", record = "R-1", url = "https://www.hfsaa.org/ny")),
        )

        val review = LoadIngestionBatch(FakeListings(), FakeBrands())
            .preview(CandidateBatch("run-1", Instant.parse("2026-11-08T00:00:00Z"), listOf(first, second)))

        review.inserted shouldBe 1
        review.writes.single().candidate.claims.map { it.key }
            .shouldContainExactlyInAnyOrder("hms::H-1", "hfsaa::R-1")
    }

    test("a missing brand is reported for creation without writing during preview") {
        val listings = FakeListings()
        val review = LoadIngestionBatch(listings, FakeBrands())
            .preview(CandidateBatch("run-1", Instant.parse("2026-11-08T00:00:00Z"), listOf(candidate())))

        review.brandsToCreate shouldBe setOf("Paramount Fine Foods")
        listings.applied shouldBe null
    }

    test("a candidate without a metro is rejected before any match") {
        val review = LoadIngestionBatch(FakeListings(), FakeBrands()).preview(
            CandidateBatch(
                "run-1",
                Instant.parse("2026-11-08T00:00:00Z"),
                listOf(candidate(metro = null)),
            ),
        )

        review.rejects.single().reason shouldBe RejectReason.MISSING_METRO
        review.writes shouldBe emptyList()
    }
})
