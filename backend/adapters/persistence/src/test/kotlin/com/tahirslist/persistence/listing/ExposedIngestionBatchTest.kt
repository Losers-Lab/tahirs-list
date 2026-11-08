package com.tahirslist.persistence.listing

import com.tahirslist.application.ingestion.BatchLoadResult
import com.tahirslist.application.ingestion.ClaimConflictException
import com.tahirslist.application.ingestion.IngestionCandidate
import com.tahirslist.application.ingestion.ListingWrite
import com.tahirslist.application.ingestion.WriteTarget
import com.tahirslist.application.ingestion.BrandRepository
import com.tahirslist.domain.account.Account
import com.tahirslist.domain.account.Email
import com.tahirslist.domain.restaurant.CertificationClaim
import com.tahirslist.domain.restaurant.CertificationTier
import com.tahirslist.domain.restaurant.CrossContamination
import com.tahirslist.domain.restaurant.Cuisine
import com.tahirslist.domain.restaurant.HandCutDeclaration
import com.tahirslist.domain.restaurant.LatLng
import com.tahirslist.domain.restaurant.Provenance
import com.tahirslist.domain.restaurant.RestaurantListing
import com.tahirslist.domain.restaurant.VerificationStatus
import com.tahirslist.persistence.PostgisTestDatabase
import com.tahirslist.persistence.account.ExposedAccountRepository
import com.tahirslist.persistence.brand.ExposedBrandRepository
import com.tahirslist.persistence.count
import com.tahirslist.persistence.execute
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runTest

private const val VENUE_KEY = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

private fun claim(
    source: String = "hms",
    record: String = "H-1",
    tier: CertificationTier = CertificationTier.TIER_1,
    declaredHandCut: HandCutDeclaration = HandCutDeclaration.HAND_CUT,
    expiresOn: kotlinx.datetime.LocalDate? = null,
): CertificationClaim = CertificationClaim(
    source = source,
    sourceRecordRef = record,
    sourceUrl = if (source == "manual-zabihah") "https://www.zabihah.com/listing/1" else "https://hmsusa.org/certified-listing/",
    certifier = if (source == "manual-zabihah") "Zabihah" else "Halal Monitoring Services (HMS)",
    tier = tier,
    declaredHandCut = declaredHandCut,
    expiresOn = expiresOn,
)

private fun candidate(
    name: String = "Paramount Fine Foods",
    venueKey: String = VENUE_KEY,
    claims: List<CertificationClaim> = listOf(claim()),
    crossContamination: CrossContamination = CrossContamination.DEFAULT,
    lat: Double = 45.501900,
    lng: Double = -73.567400,
): IngestionCandidate = IngestionCandidate(
    name = name,
    address = "253 Yonge St",
    city = "Toronto",
    region = "ON",
    postal = "M5B 1N8",
    country = "CA",
    location = LatLng(lat, lng),
    venueKey = venueKey,
    claims = claims,
    metro = "Toronto",
    manualCrossContamination = crossContamination,
)

class ExposedIngestionBatchTest : FunSpec() {

    private val postgis = PostgisTestDatabase()
    private lateinit var listings: ExposedRestaurantListingRepository
    private lateinit var brands: BrandRepository
    private lateinit var accounts: ExposedAccountRepository

    init {
        beforeSpec {
            postgis.start()
            listings = ExposedRestaurantListingRepository(postgis.database)
            brands = ExposedBrandRepository(postgis.database)
            accounts = ExposedAccountRepository(postgis.database)
        }
        afterSpec { postgis.stop() }

        // Each case starts from an empty catalogue (the V7 seed rows included),
        // so venue keys, coordinates and claims never leak between cases.
        beforeTest {
            postgis.execute("TRUNCATE restaurant_listings, brands, external_certification_claims CASCADE")
        }

        suspend fun insertDefault() {
            listings.applyBatch(listOf(ListingWrite(WriteTarget.Insert, candidate())))
        }

        test("applyBatch inserts a listing, its brand, its claim and the search mirror") {
            runTest {
                val result = listings.applyBatch(listOf(ListingWrite(WriteTarget.Insert, candidate())))

                result.inserted shouldBe 1
                result.claimsWritten shouldBe 1

                val listing = listings.findByVenueKey(VENUE_KEY) ?: error("listing not found")
                listing.name shouldBe "Paramount Fine Foods"
                listing.provenance shouldBe Provenance.CERTIFIER_HMS
                listing.verificationStatus shouldBe VerificationStatus.VERIFIED
                listing.isHandCut shouldBe true
                listing.crossContamination shouldBe CrossContamination.NO_CROSS_CONTAMINATION
                listing.brandId shouldBe brands.resolve("Paramount Fine Foods")?.id

                postgis.count("SELECT count(*) FROM external_certification_claims WHERE source = 'hms' AND source_record_ref = 'H-1'") shouldBe 1
                postgis.count("SELECT count(*) FROM listing_search WHERE id = '${listing.id}'") shouldBe 1
            }
        }

        test("findClaim resolves the listing that owns the source record") {
            runTest {
                insertDefault()
                val listing = listings.findClaim("hms", "H-1") ?: error("claim match missing")
                listing.venueKey shouldBe VENUE_KEY
                listings.findClaim("hms", "does-not-exist") shouldBe null
            }
        }

        test("findByNormalizedLocation matches the same 6-dp cell only") {
            runTest {
                insertDefault()
                listings.findByNormalizedLocation(45.501900, -73.567400).map { it.venueKey } shouldBe listOf(VENUE_KEY)
                listings.findByNormalizedLocation(45.501901, -73.567400).size shouldBe 0
            }
        }

        test("replaying the same candidate is an update, not a duplicate") {
            runTest {
                insertDefault()
                val before = listings.findClaim("hms", "H-1") ?: error("missing")
                val result = listings.applyBatch(listOf(ListingWrite(WriteTarget.Update(before.id), candidate())))

                result.upgraded shouldBe 1
                postgis.count("SELECT count(*) FROM restaurant_listings WHERE venue_key = '$VENUE_KEY'") shouldBe 1
                postgis.count("SELECT count(*) FROM external_certification_claims WHERE source = 'hms' AND source_record_ref = 'H-1'") shouldBe 1
            }
        }

        test("a claim conflict aborts the whole batch") {
            runTest {
                insertDefault()
                val owner = listings.findClaim("hms", "H-1") ?: error("missing")
                val other = listings.save(
                    RestaurantListing.new(
                        name = "Other",
                        address = "1 Other St",
                        location = LatLng(43.7, -79.4),
                        cuisine = Cuisine("pakistani"),
                        ownerId = accounts.save(Account.new(Email("owner-${Uuid.random()}@example.com"), "argon2id\$h")).id,
                    ),
                )
                val conflict = candidate(name = "Conflict", venueKey = "b".repeat(64))

                shouldThrow<ClaimConflictException> {
                    listings.applyBatch(listOf(ListingWrite(WriteTarget.Update(other.id), conflict)))
                }

                listings.findById(other.id)?.name shouldBe "Other"
                listings.findClaim("hms", "H-1")?.id shouldBe owner.id
            }
        }

        test("an upgrade preserves owner-set content while replacing ingested identity") {
            runTest {
                val owner = accounts.save(Account.new(Email("owner-${Uuid.random()}@gmail.com"), "argon2id\$h")).id
                val original = listings.save(
                    RestaurantListing.new(
                        name = "User Name",
                        address = "User Address",
                        location = LatLng(43.9, -79.9),
                        cuisine = Cuisine("pakistani"),
                        ownerId = owner,
                        crossContamination = CrossContamination.NO_CROSS_CONTAMINATION,
                    ),
                )
                val key = "c".repeat(64)
                val upgraded = candidate(name = "Directory Name", venueKey = key)

                listings.applyBatch(listOf(ListingWrite(WriteTarget.Update(original.id), upgraded)))

                val after = listings.findById(original.id) ?: error("missing")
                after.name shouldBe "Directory Name"
                after.cuisine shouldBe Cuisine("pakistani")
                after.ownerId shouldBe owner
                after.verificationStatus shouldBe VerificationStatus.VERIFIED
                after.isHandCut shouldBe true
                after.venueKey shouldBe key
            }
        }

        test("a manual candidate stays unverified, with hand-cut NULL and no index row") {
            runTest {
                val manual = candidate(
                    name = "Manual Place",
                    venueKey = "d".repeat(64),
                    claims = listOf(
                        claim(
                            source = "manual-zabihah",
                            record = "Z-1",
                            tier = CertificationTier.TIER_3,
                            declaredHandCut = HandCutDeclaration.UNSPECIFIED,
                        ),
                    ),
                    crossContamination = CrossContamination.UNCERTAIN,
                    lat = 43.7,
                    lng = -79.5,
                )

                listings.applyBatch(listOf(ListingWrite(WriteTarget.Insert, manual)))

                val listing = listings.findByVenueKey("d".repeat(64)) ?: error("missing")
                listing.verificationStatus shouldBe VerificationStatus.UNVERIFIED
                listing.isHandCut shouldBe null
                listing.provenance shouldBe Provenance.MANUAL_ZABIHAH
                postgis.count("SELECT count(*) FROM listing_search WHERE id = '${listing.id}'") shouldBe 0
            }
        }

        test("two writes for the same brand name share one brand row") {
            runTest {
                val first = candidate(
                    name = "Chain",
                    venueKey = "e".repeat(64),
                    claims = listOf(claim(source = "hms", record = "H-10")),
                    lat = 43.1,
                    lng = -79.1,
                )
                val second = candidate(
                    name = "Chain",
                    venueKey = "f".repeat(64),
                    claims = listOf(claim(source = "hms", record = "H-11")),
                    lat = 43.2,
                    lng = -79.2,
                )

                val result = listings.applyBatch(
                    listOf(
                        ListingWrite(WriteTarget.Insert, first),
                        ListingWrite(WriteTarget.Insert, second),
                    ),
                )

                result.inserted shouldBe 2
                postgis.count("SELECT count(*) FROM brands WHERE name = 'Chain'") shouldBe 1
            }
        }
    }
}
