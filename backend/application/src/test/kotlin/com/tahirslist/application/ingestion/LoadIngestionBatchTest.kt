package com.tahirslist.application.ingestion

import com.tahirslist.application.listing.RestaurantListingRepository
import com.tahirslist.domain.restaurant.LatLng
import com.tahirslist.domain.restaurant.Provenance
import com.tahirslist.domain.restaurant.RestaurantListing
import com.tahirslist.domain.restaurant.VerificationStatus
import io.kotest.assertions.throwables.shouldThrow
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
    var appliedMerges: List<ListingMerge>? = null
    var appliedReattachments: List<ClaimReattachment>? = null

    override suspend fun findClaim(source: String, sourceRecordRef: String): RestaurantListing? =
        claims["$source::$sourceRecordRef"]

    override suspend fun findByVenueKey(venueKey: String): RestaurantListing? = venues[venueKey]

    override suspend fun findByNormalizedLocation(lat: Double, lng: Double): List<RestaurantListing> =
        locations["$lat,$lng"].orEmpty()

    override suspend fun applyBatch(
        writes: List<ListingWrite>,
        merges: List<ListingMerge>,
        reattachments: List<ClaimReattachment>,
    ): BatchLoadResult {
        applied = writes
        appliedMerges = merges
        appliedReattachments = reattachments
        return BatchLoadResult(
            inserted = writes.count { it.target is WriteTarget.Insert },
            upgraded = writes.count { it.target is WriteTarget.Update },
            merged = merges.size,
            claimsWritten = writes.sumOf { it.candidate.claims.size },
        )
    }

    override suspend fun save(listing: RestaurantListing): RestaurantListing = error("not used")

    override suspend fun findById(id: Uuid): RestaurantListing? =
        (claims.values + venues.values + locations.values.flatten()).firstOrNull { it.id == id }
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
        review.unresolved shouldBe 1

        // A held row is blocking: execute refuses until the curator decides.
        shouldThrow<IllegalStateException> { load.execute(review) }
        listings.applied shouldBe null
    }

    test("a claim that points at a different listing is a blocking finding, never a silent write") {
        val claimListing = storedListing(Uuid.random(), "Claim owner")
        val venueListing = storedListing(Uuid.random(), "Venue owner", venueKey = "a".repeat(64))
        val listings = FakeListings(
            claims = mapOf("hms::HMS-1" to claimListing),
            venues = mapOf("a".repeat(64) to venueListing),
        )
        val load = LoadIngestionBatch(listings, FakeBrands())

        val review = load.preview(CandidateBatch("run-1", Instant.parse("2026-11-08T00:00:00Z"), listOf(candidate())))

        val finding = review.findings.single() as Finding.ClaimConflict
        finding.claimedBy.id shouldBe claimListing.id
        finding.resolvedTo.id shouldBe venueListing.id
        finding.key shouldBe "claim:hms:HMS-1"
        review.writes shouldBe emptyList()
        review.rejects shouldBe emptyList()
    }

    test("executing a review that still has an unresolved finding is refused") {
        val claimListing = storedListing(Uuid.random(), "Claim owner")
        val venueListing = storedListing(Uuid.random(), "Venue owner", venueKey = "a".repeat(64))
        val listings = FakeListings(
            claims = mapOf("hms::HMS-1" to claimListing),
            venues = mapOf("a".repeat(64) to venueListing),
        )
        val load = LoadIngestionBatch(listings, FakeBrands())
        val review = load.preview(CandidateBatch("run-1", Instant.parse("2026-11-08T00:00:00Z"), listOf(candidate())))

        shouldThrow<IllegalStateException> { load.execute(review) }
        listings.applied shouldBe null
    }

    test("two candidates that already belong to one listing become one write carrying both claims") {
        val existing = storedListing(Uuid.random(), "Existing")
        val listings = FakeListings(
            claims = mapOf(
                "hms::H-1" to existing,
                "hfsaa::R-1" to existing,
            ),
        )
        val first = candidate(venueKey = "b".repeat(64), claims = listOf(claim(source = "hms", record = "H-1")))
        val second = candidate(
            venueKey = "c".repeat(64),
            claims = listOf(claim(source = "hfsaa", record = "R-1", url = "https://www.hfsaa.org/ny")),
        )

        val review = LoadIngestionBatch(listings, FakeBrands())
            .preview(CandidateBatch("run-1", Instant.parse("2026-11-08T00:00:00Z"), listOf(first, second)))

        review.writes.single().target shouldBe WriteTarget.Update(existing.id)
        review.writes.single().candidate.claims.map { it.key }
            .shouldContainExactlyInAnyOrder("hms::H-1", "hfsaa::R-1")
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

/**
 * Decisions are the only way a blocking finding is resolved, and every one of
 * them is applied inside the reviewed batch (never as a side effect).
 */
class ClaimResolutionTest : FunSpec({

    val batchTime = Instant.parse("2026-11-08T00:00:00Z")
    val claimOwner = storedListing(Uuid.random(), "Claim owner")
    val venueOwner = storedListing(Uuid.random(), "Venue owner", venueKey = "a".repeat(64))

    fun conflicting(): Pair<FakeListings, LoadIngestionBatch> {
        val listings = FakeListings(
            claims = mapOf("hms::HMS-1" to claimOwner),
            venues = mapOf("a".repeat(64) to venueOwner),
        )
        return listings to LoadIngestionBatch(listings, FakeBrands())
    }

    fun decision(
        decision: ClaimDecision,
        listingId: Uuid? = null,
        reason: String = "checked the two listings",
    ) = Resolutions(
        runId = "run-1",
        claims = listOf(
            ClaimResolution(
                source = "hms",
                record = "HMS-1",
                decision = decision,
                listingId = listingId?.toString(),
                reason = reason,
            ),
        ),
    )

    test("same-venue merges the claim owner into the matched venue and updates the keeper") {
        val (listings, load) = conflicting()

        val review = load.preview(
            CandidateBatch("run-1", batchTime, listOf(candidate())),
            decision(ClaimDecision.SAME_VENUE, venueOwner.id),
        )

        review.findings shouldBe emptyList()
        review.merges.single().keepId shouldBe venueOwner.id
        review.merges.single().retireId shouldBe claimOwner.id
        review.writes.single().target shouldBe WriteTarget.Update(venueOwner.id)
        review.applied.single().decision shouldBe "same-venue"
        review.applied.single().detail shouldBe "checked the two listings"
    }

    test("a curator may keep the claim owner instead, and the venue row is retired") {
        val (_, load) = conflicting()

        val review = load.preview(
            CandidateBatch("run-1", batchTime, listOf(candidate())),
            decision(ClaimDecision.SAME_VENUE, claimOwner.id),
        )

        review.merges.single().keepId shouldBe claimOwner.id
        review.merges.single().retireId shouldBe venueOwner.id
        review.writes.single().target shouldBe WriteTarget.Update(claimOwner.id)
    }

    test("attach-to re-points the claim to the matched venue without merging") {
        val (listings, load) = conflicting()

        val review = load.preview(
            CandidateBatch("run-1", batchTime, listOf(candidate())),
            decision(ClaimDecision.ATTACH_TO, venueOwner.id),
        )

        review.merges shouldBe emptyList()
        review.reattachments.single().fromListingId shouldBe claimOwner.id
        review.reattachments.single().toListingId shouldBe venueOwner.id
        review.writes.single().target shouldBe WriteTarget.Update(venueOwner.id)
        load.execute(review)
        listings.appliedReattachments?.single()?.toListingId shouldBe venueOwner.id
    }

    test("attach-to naming the claim's current owner is refused: use same-venue") {
        val (_, load) = conflicting()
        shouldThrow<InvalidResolutionException> {
            load.preview(
                CandidateBatch("run-1", batchTime, listOf(candidate())),
                decision(ClaimDecision.ATTACH_TO, claimOwner.id),
            )
        }
    }

    test("a bad citation is dropped and the run reports the missing citation instead of writing") {
        val (listings, load) = conflicting()

        val review = load.preview(
            CandidateBatch("run-1", batchTime, listOf(candidate())),
            decision(ClaimDecision.BAD_CITATION),
        )

        review.writes shouldBe emptyList()
        review.rejects.single().reason shouldBe RejectReason.MISSING_CITATION
        review.applied.single().decision shouldBe "bad-citation"
        load.execute(review)
        listings.applied shouldBe emptyList()
    }

    test("a decision whose listing is neither side of the conflict is refused") {
        val (_, load) = conflicting()
        shouldThrow<InvalidResolutionException> {
            load.preview(
                CandidateBatch("run-1", batchTime, listOf(candidate())),
                decision(ClaimDecision.SAME_VENUE, Uuid.random()),
            )
        }
    }

    test("a decision that no longer matches a finding is refused as stale, never applied") {
        val (_, load) = conflicting()
        val stale = Resolutions(
            runId = "run-1",
            claims = listOf(
                ClaimResolution(
                    source = "hfsaa",
                    record = "R-9",
                    decision = ClaimDecision.BAD_CITATION,
                    reason = "no longer relevant",
                ),
            ),
        )

        shouldThrow<StaleResolutionException> {
            load.preview(CandidateBatch("run-1", batchTime, listOf(candidate())), stale)
        }
    }

    test("a decision file for another run is refused") {
        val (_, load) = conflicting()
        shouldThrow<ResolutionsRunMismatchException> {
            load.preview(
                CandidateBatch("run-1", batchTime, listOf(candidate())),
                Resolutions.empty("run-2"),
            )
        }
    }

    test("a decided entry without a reason is refused before any work") {
        val (_, load) = conflicting()
        shouldThrow<InvalidResolutionException> {
            load.preview(
                CandidateBatch("run-1", batchTime, listOf(candidate())),
                decision(ClaimDecision.SAME_VENUE, venueOwner.id, reason = " "),
            )
        }
    }
})

class DuplicateResolutionTest : FunSpec({

    val batchTime = Instant.parse("2026-11-08T00:00:00Z")
    val existing = storedListing(Uuid.random(), "Same place, different identity")

    fun located(): FakeListings = FakeListings(
        locations = mapOf("43.665206,-79.384523" to listOf(existing)),
    )

    test("without a decision the candidate is held, with both listings in the finding") {
        val review = LoadIngestionBatch(located(), FakeBrands())
            .preview(CandidateBatch("run-1", batchTime, listOf(candidate())))

        val finding = review.findings.single() as Finding.SuspectedDuplicate
        finding.existing.single().id shouldBe existing.id
        finding.key shouldBe "duplicate:${"a".repeat(64)}"
        review.writes shouldBe emptyList()
        review.held.single().existingListingIds shouldBe listOf(existing.id)
    }

    test("same-venue writes the candidate into the existing listing") {
        val resolutions = Resolutions(
            runId = "run-1",
            duplicates = listOf(
                DuplicateResolution(
                    venueKey = "a".repeat(64),
                    decision = DuplicateDecision.SAME_VENUE,
                    listingId = existing.id.toString(),
                    reason = "same address and sign",
                ),
            ),
        )

        val review = LoadIngestionBatch(located(), FakeBrands())
            .preview(CandidateBatch("run-1", batchTime, listOf(candidate())), resolutions)

        review.findings shouldBe emptyList()
        review.writes.single().target shouldBe WriteTarget.Update(existing.id)
        review.merges shouldBe emptyList()
    }

    test("distinct inserts a new listing at the same location, as decided") {
        val resolutions = Resolutions(
            runId = "run-1",
            duplicates = listOf(
                DuplicateResolution(
                    venueKey = "a".repeat(64),
                    decision = DuplicateDecision.DISTINCT,
                    reason = "food court: two separate counters",
                ),
            ),
        )

        val review = LoadIngestionBatch(located(), FakeBrands())
            .preview(CandidateBatch("run-1", batchTime, listOf(candidate())), resolutions)

        review.writes.single().target shouldBe WriteTarget.Insert
        review.applied.single().decision shouldBe "distinct"
    }

    test("same-venue must name a listing that is actually at that location") {
        val resolutions = Resolutions(
            runId = "run-1",
            duplicates = listOf(
                DuplicateResolution(
                    venueKey = "a".repeat(64),
                    decision = DuplicateDecision.SAME_VENUE,
                    listingId = Uuid.random().toString(),
                    reason = "close enough",
                ),
            ),
        )

        shouldThrow<InvalidResolutionException> {
            LoadIngestionBatch(located(), FakeBrands())
                .preview(CandidateBatch("run-1", batchTime, listOf(candidate())), resolutions)
        }
    }
})

class ResolutionsJsonTest : FunSpec({

    test("a generated sheet names every finding once and keeps earlier decisions") {
        val claimOwner = storedListing(Uuid.random(), "Claim owner")
        val venueOwner = storedListing(Uuid.random(), "Venue owner", venueKey = "a".repeat(64))
        val listings = FakeListings(
            claims = mapOf("hms::HMS-1" to claimOwner),
            venues = mapOf("a".repeat(64) to venueOwner),
        )
        val review = LoadIngestionBatch(listings, FakeBrands())
            .preview(CandidateBatch("run-1", Instant.parse("2026-11-08T00:00:00Z"), listOf(candidate())))

        val sheet = ResolutionsJson.sheet("run-1", review)
        val entry = sheet.claims.single()
        entry.decision shouldBe ClaimDecision.UNRESOLVED
        entry.question?.contains("HMS-1") shouldBe true
        entry.evidence.any { it.contains("Venue owner") } shouldBe true

        val answered = sheet.withClaim(
            entry.copy(decision = ClaimDecision.SAME_VENUE, listingId = venueOwner.id.toString(), reason = "same sign"),
        )
        val regenerated = answered.completeWith(review.findings)
        regenerated.claims.single().decision shouldBe ClaimDecision.SAME_VENUE

        val decoded = ResolutionsJson.decode(ResolutionsJson.encode(regenerated))
        decoded.claims.single().decision shouldBe ClaimDecision.SAME_VENUE
        decoded.runId shouldBe "run-1"
    }

    test("an unknown decision word is a decode error, not a silent default") {
        shouldThrow<Exception> {
            ResolutionsJson.decode(
                """{"runId":"run-1","claims":[{"source":"hms","record":"H-1","decision":"maybe"}]}""",
            )
        }
    }
})
