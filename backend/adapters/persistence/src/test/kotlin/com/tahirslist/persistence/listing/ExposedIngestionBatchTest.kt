package com.tahirslist.persistence.listing

import com.tahirslist.application.ingestion.BatchLoadResult
import com.tahirslist.application.ingestion.BatchPlanException
import com.tahirslist.application.ingestion.ClaimConflictException
import com.tahirslist.application.ingestion.ClaimReattachment
import com.tahirslist.application.ingestion.IngestionCandidate
import com.tahirslist.application.ingestion.ListingMerge
import com.tahirslist.application.ingestion.ListingWrite
import com.tahirslist.application.ingestion.WriteTarget
import com.tahirslist.application.ingestion.BrandRepository
import com.tahirslist.domain.account.Account
import com.tahirslist.domain.account.Email
import com.tahirslist.domain.restaurant.AdmissionDecision
import com.tahirslist.domain.restaurant.CertificationClaim
import com.tahirslist.domain.restaurant.CertificationTier
import com.tahirslist.domain.restaurant.CrossContamination
import com.tahirslist.domain.restaurant.Cuisine
import com.tahirslist.domain.restaurant.EnrichmentSource
import com.tahirslist.domain.restaurant.HandCutDeclaration
import com.tahirslist.domain.restaurant.LatLng
import com.tahirslist.domain.restaurant.Provenance
import com.tahirslist.domain.restaurant.RestaurantListing
import com.tahirslist.domain.restaurant.VerificationStatus
import com.tahirslist.domain.restaurant.VenueType
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
    admission: AdmissionDecision.Admitted? = null,
    cuisine: String? = null,
    cuisineSource: EnrichmentSource? = null,
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
    admission = admission,
    cuisineName = cuisine,
    cuisineSource = cuisineSource,
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

        // ---- SC-231: enrichment on the write path ---------------------------

        test("applyBatch writes the derived type and cuisine, and the filter can see them") {
            runTest {
                listings.applyBatch(
                    listOf(
                        ListingWrite(
                            WriteTarget.Insert,
                            candidate(
                                admission = AdmissionDecision.Admitted(
                                    VenueType.RESTAURANT,
                                    EnrichmentSource.SOURCE_CATEGORY,
                                ),
                                cuisine = "lebanese",
                                cuisineSource = EnrichmentSource.BRAND_MAP,
                            ),
                        ),
                    ),
                )

                val listing = listings.findByVenueKey(VENUE_KEY) ?: error("listing not found")
                listing.venueType shouldBe VenueType.RESTAURANT
                listing.venueTypeSource shouldBe EnrichmentSource.SOURCE_CATEGORY
                listing.cuisine?.value shouldBe "lebanese"
                listing.cuisineSource shouldBe EnrichmentSource.BRAND_MAP
                // sc-44: the cuisine filter reads the child table, and the search
                // mirror reads the column — a derived cuisine must land in both.
                postgis.count(
                    "SELECT count(*) FROM restaurant_listing_cuisines " +
                        "WHERE listing_id = '${listing.id}' AND cuisine = 'lebanese'",
                ) shouldBe 1
                postgis.count(
                    "SELECT count(*) FROM listing_search WHERE id = '${listing.id}' AND cuisine = 'lebanese'",
                ) shouldBe 1
            }
        }

        test("a re-run never overwrites a cuisine an owner set") {
            runTest {
                listings.applyBatch(
                    listOf(
                        ListingWrite(
                            WriteTarget.Insert,
                            candidate(
                                admission = AdmissionDecision.Admitted(
                                    VenueType.RESTAURANT,
                                    EnrichmentSource.SOURCE_CATEGORY,
                                ),
                                cuisine = "lebanese",
                                cuisineSource = EnrichmentSource.BRAND_MAP,
                            ),
                        ),
                    ),
                )
                val seeded = listings.findByVenueKey(VENUE_KEY) ?: error("listing not found")

                // The owner corrects the cuisine: the value is theirs, so no derived source.
                postgis.execute(
                    "UPDATE restaurant_listings SET cuisine = 'syrian', cuisine_source = NULL " +
                        "WHERE id = '${seeded.id}'",
                )
                postgis.execute(
                    "UPDATE restaurant_listing_cuisines SET cuisine = 'syrian' WHERE listing_id = '${seeded.id}'",
                )

                listings.applyBatch(
                    listOf(
                        ListingWrite(
                            WriteTarget.Update(seeded.id),
                            candidate(
                                admission = AdmissionDecision.Admitted(
                                    VenueType.RESTAURANT,
                                    EnrichmentSource.SOURCE_CATEGORY,
                                ),
                                cuisine = "afghan",
                                cuisineSource = EnrichmentSource.NAME_KEYWORD,
                            ),
                        ),
                    ),
                )

                val after = listings.findById(seeded.id) ?: error("listing vanished")
                after.cuisine?.value shouldBe "syrian"
                // The source describes the value that is there, so it stays null.
                after.cuisineSource shouldBe null
                postgis.count(
                    "SELECT count(*) FROM restaurant_listing_cuisines WHERE listing_id = '${after.id}'",
                ) shouldBe 1
                postgis.count(
                    "SELECT count(*) FROM restaurant_listing_cuisines " +
                        "WHERE listing_id = '${after.id}' AND cuisine = 'afghan'",
                ) shouldBe 0
            }
        }

        test("a re-run keeps the first derived type rather than re-deciding it") {
            runTest {
                val inserted = candidate(
                    admission = AdmissionDecision.Admitted(VenueType.CAFE, EnrichmentSource.SOURCE_CATEGORY),
                    cuisine = "lebanese",
                    cuisineSource = EnrichmentSource.BRAND_MAP,
                )
                listings.applyBatch(listOf(ListingWrite(WriteTarget.Insert, inserted)))
                val first = listings.findByVenueKey(VENUE_KEY) ?: error("listing not found")

                listings.applyBatch(
                    listOf(
                        ListingWrite(
                            WriteTarget.Update(first.id),
                            inserted.copy(
                                admission = AdmissionDecision.Admitted(
                                    VenueType.RESTAURANT,
                                    EnrichmentSource.NAME_KEYWORD,
                                ),
                            ),
                        ),
                    ),
                )

                val after = listings.findById(first.id) ?: error("listing vanished")
                after.venueType shouldBe VenueType.CAFE
                after.venueTypeSource shouldBe EnrichmentSource.SOURCE_CATEGORY
                postgis.count(
                    "SELECT count(*) FROM restaurant_listing_cuisines WHERE listing_id = '${after.id}'",
                ) shouldBe 1
            }
        }

        test("an owner cuisine edit clears the derived source (provenance is not carried over)") {
            runTest {
                listings.applyBatch(
                    listOf(
                        ListingWrite(
                            WriteTarget.Insert,
                            candidate(
                                admission = AdmissionDecision.Admitted(
                                    VenueType.RESTAURANT,
                                    EnrichmentSource.SOURCE_CATEGORY,
                                ),
                                cuisine = "lebanese",
                                cuisineSource = EnrichmentSource.BRAND_MAP,
                            ),
                        ),
                    ),
                )
                val seeded = listings.findByVenueKey(VENUE_KEY) ?: error("listing not found")

                val edited = listings.update(
                    seeded.copy(cuisine = Cuisine("syrian")),
                ) ?: error("listing vanished")

                edited.cuisine?.value shouldBe "syrian"
                // The owner's value is not a brand-map decision.
                edited.cuisineSource shouldBe null
                postgis.count(
                    "SELECT count(*) FROM restaurant_listing_cuisines WHERE listing_id = '${edited.id}'",
                ) shouldBe 1
                postgis.count(
                    "SELECT count(*) FROM restaurant_listing_cuisines " +
                        "WHERE listing_id = '${edited.id}' AND cuisine = 'syrian'",
                ) shouldBe 1
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

        test("a Tier-3 manual update never demotes a verified Tier-1 listing") {
            runTest {
                insertDefault()
                val tierOne = listings.findByVenueKey(VENUE_KEY) ?: error("missing")
                tierOne.verificationStatus shouldBe VerificationStatus.VERIFIED
                postgis.count("SELECT count(*) FROM listing_search WHERE id = '${tierOne.id}'") shouldBe 1

                val manual = candidate(
                    claims = listOf(
                        claim(
                            source = "manual-zabihah",
                            record = "Z-1",
                            tier = CertificationTier.TIER_3,
                            declaredHandCut = HandCutDeclaration.UNSPECIFIED,
                        ),
                    ),
                    crossContamination = CrossContamination.UNCERTAIN,
                )

                listings.applyBatch(listOf(ListingWrite(WriteTarget.Update(tierOne.id), manual)))

                val after = listings.findById(tierOne.id) ?: error("missing")
                after.verificationStatus shouldBe VerificationStatus.VERIFIED
                after.isHandCut shouldBe true
                after.provenance shouldBe Provenance.CERTIFIER_HMS
                after.crossContamination shouldBe CrossContamination.NO_CROSS_CONTAMINATION
                postgis.count("SELECT count(*) FROM listing_search WHERE id = '${tierOne.id}'") shouldBe 1
                postgis.count("SELECT count(*) FROM external_certification_claims WHERE listing_id = '${tierOne.id}'") shouldBe 2
            }
        }

        test("a manual candidate stays unverified, with hand-cut NULL and no index row") {            runTest {
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
                    name = "CHAIN",
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
                // Case-insensitive: the second row reuses the first brand.
                postgis.count("SELECT count(*) FROM brands WHERE lower(name) = 'chain'") shouldBe 1
                postgis.count(
                    "SELECT count(DISTINCT brand_id) FROM restaurant_listings WHERE lower(name) = 'chain'",
                ) shouldBe 1
            }
        }

        test("a same-venue merge moves claims, child rows and reviews, then deletes the duplicate") {
            runTest {
                val keep = candidate(
                    name = "Keep",
                    venueKey = "1".repeat(64),
                    claims = listOf(claim(source = "hms", record = "H-1")),
                    lat = 43.1,
                    lng = -79.1,
                )
                val retire = candidate(
                    name = "Duplicate",
                    venueKey = "2".repeat(64),
                    claims = listOf(claim(source = "hms", record = "H-2")),
                    lat = 43.2,
                    lng = -79.2,
                )
                listings.applyBatch(
                    listOf(ListingWrite(WriteTarget.Insert, keep), ListingWrite(WriteTarget.Insert, retire)),
                )
                val kept = listings.findByVenueKey("1".repeat(64)) ?: error("missing keeper")
                val removed = listings.findByVenueKey("2".repeat(64)) ?: error("missing duplicate")

                val user = accounts.save(Account.new(email = Email("merge-${Uuid.random()}@example.com"), passwordHash = "argon2id\$h")).id
                postgis.execute("INSERT INTO restaurant_listing_cuisines (listing_id, cuisine) VALUES ('${kept.id}'::uuid, 'PAKISTANI')")
                postgis.execute("INSERT INTO restaurant_listing_cuisines (listing_id, cuisine) VALUES ('${removed.id}'::uuid, 'PAKISTANI')")
                postgis.execute("INSERT INTO restaurant_listing_cuisines (listing_id, cuisine) VALUES ('${removed.id}'::uuid, 'AFGHAN')")
                postgis.execute("INSERT INTO restaurant_halal_items (listing_id, name, is_halal) VALUES ('${removed.id}'::uuid, 'Chicken', true)")
                postgis.execute("INSERT INTO favorites (user_id, listing_id) VALUES ('$user'::uuid, '${kept.id}'::uuid)")
                postgis.execute("INSERT INTO favorites (user_id, listing_id) VALUES ('$user'::uuid, '${removed.id}'::uuid)")
                postgis.execute(
                    "INSERT INTO halal_certification_reviews (listing_id, submitted_by, state) " +
                        "VALUES ('${removed.id}'::uuid, '$user'::uuid, 'SUBMITTED')",
                )

                val result = listings.applyBatch(
                    writes = emptyList(),
                    merges = listOf(ListingMerge(keepId = kept.id, retireId = removed.id)),
                )

                result.merged shouldBe 1
                listings.findById(removed.id) shouldBe null
                listings.findClaim("hms", "H-2")?.id shouldBe kept.id
                postgis.count("SELECT count(*) FROM listing_search WHERE id = '${removed.id}'") shouldBe 0
                postgis.count("SELECT count(*) FROM listing_search WHERE id = '${kept.id}'") shouldBe 1
                postgis.count("SELECT count(*) FROM restaurant_listing_cuisines WHERE listing_id = '${kept.id}'") shouldBe 2
                postgis.count("SELECT count(*) FROM restaurant_listing_cuisines WHERE listing_id = '${removed.id}'") shouldBe 0
                postgis.count("SELECT count(*) FROM restaurant_halal_items WHERE listing_id = '${kept.id}'") shouldBe 1
                postgis.count("SELECT count(*) FROM favorites WHERE listing_id = '${kept.id}'") shouldBe 1
                postgis.count("SELECT count(*) FROM favorites WHERE listing_id = '${removed.id}'") shouldBe 0
                postgis.count("SELECT count(*) FROM halal_certification_reviews WHERE listing_id = '${kept.id}'") shouldBe 1
                postgis.count("SELECT count(*) FROM halal_certification_reviews WHERE listing_id = '${removed.id}'") shouldBe 0
            }
        }

        test("a certifier withdrawing hand-cut on a refresh is reflected, not frozen at TRUE") {
            runTest {
                insertDefault()
                val listing = listings.findByVenueKey(VENUE_KEY) ?: error("missing")
                listing.isHandCut shouldBe true

                val refreshed = candidate(
                    claims = listOf(
                        claim(record = "H-1", declaredHandCut = HandCutDeclaration.NOT_HAND_CUT),
                    ),
                )
                listings.applyBatch(listOf(ListingWrite(WriteTarget.Update(listing.id), refreshed)))

                val after = listings.findById(listing.id) ?: error("missing")
                after.verificationStatus shouldBe VerificationStatus.VERIFIED
                after.isHandCut shouldBe false
                postgis.count(
                    "SELECT count(*) FROM restaurant_listings WHERE id = '${listing.id}' " +
                        "AND verification_basis = 'CERTIFIER_REGISTRY'",
                ) shouldBe 1
            }
        }

        test("a certifier going silent is FALSE, not a retained TRUE") {
            runTest {
                insertDefault()
                val listing = listings.findByVenueKey(VENUE_KEY) ?: error("missing")

                listings.applyBatch(
                    listOf(
                        ListingWrite(
                            WriteTarget.Update(listing.id),
                            candidate(
                                claims = listOf(
                                    claim(record = "H-1", declaredHandCut = HandCutDeclaration.UNSPECIFIED),
                                ),
                            ),
                        ),
                    ),
                )

                listings.findById(listing.id)?.isHandCut shouldBe false
            }
        }

        test("a merge into a Tier-3 keeper keeps the folded-in Tier-1 verification") {
            runTest {
                val verified = candidate(
                    name = "Verified",
                    venueKey = "7".repeat(64),
                    claims = listOf(claim(source = "hms", record = "H-7")),
                    lat = 43.7,
                    lng = -79.7,
                )
                val manual = candidate(
                    name = "Manual",
                    venueKey = "8".repeat(64),
                    claims = listOf(
                        claim(
                            source = "manual-zabihah",
                            record = "Z-8",
                            tier = CertificationTier.TIER_3,
                            declaredHandCut = HandCutDeclaration.UNSPECIFIED,
                        ),
                    ),
                    crossContamination = CrossContamination.UNCERTAIN,
                    lat = 43.8,
                    lng = -79.8,
                )
                listings.applyBatch(
                    listOf(ListingWrite(WriteTarget.Insert, verified), ListingWrite(WriteTarget.Insert, manual)),
                )
                val keeper = listings.findByVenueKey("8".repeat(64)) ?: error("missing keeper")
                val retired = listings.findByVenueKey("7".repeat(64)) ?: error("missing verified")

                listings.applyBatch(
                    writes = emptyList(),
                    merges = listOf(ListingMerge(keepId = keeper.id, retireId = retired.id)),
                )

                val after = listings.findById(keeper.id) ?: error("missing keeper")
                after.verificationStatus shouldBe VerificationStatus.VERIFIED
                after.isHandCut shouldBe true
                after.provenance shouldBe Provenance.CERTIFIER_HMS
                after.crossContamination shouldBe CrossContamination.NO_CROSS_CONTAMINATION
                postgis.count("SELECT count(*) FROM listing_search WHERE id = '${keeper.id}'") shouldBe 1
            }
        }

        test("a committee-verified listing is never demoted by an ingestion update") {
            runTest {
                val manual = candidate(
                    name = "Committee Verified",
                    venueKey = "9".repeat(64),
                    claims = listOf(
                        claim(
                            source = "manual-zabihah",
                            record = "Z-9",
                            tier = CertificationTier.TIER_3,
                            declaredHandCut = HandCutDeclaration.UNSPECIFIED,
                        ),
                    ),
                    crossContamination = CrossContamination.UNCERTAIN,
                    lat = 43.9,
                    lng = -79.9,
                )
                listings.applyBatch(listOf(ListingWrite(WriteTarget.Insert, manual)))
                val listing = listings.findByVenueKey("9".repeat(64)) ?: error("missing")
                // The committee promotes directly; there is no certifier basis.
                postgis.execute(
                    "UPDATE restaurant_listings SET verification_status = 'VERIFIED' WHERE id = '${listing.id}'",
                )

                listings.applyBatch(
                    listOf(
                        ListingWrite(
                            WriteTarget.Update(listing.id),
                            candidate(
                                venueKey = "9".repeat(64),
                                claims = listOf(
                                    claim(
                                        source = "manual-zabihah",
                                        record = "Z-9",
                                        tier = CertificationTier.TIER_3,
                                        declaredHandCut = HandCutDeclaration.UNSPECIFIED,
                                    ),
                                ),
                                crossContamination = CrossContamination.UNCERTAIN,
                                lat = 43.9,
                                lng = -79.9,
                            ),
                        ),
                    ),
                )

                listings.findById(listing.id)?.verificationStatus shouldBe VerificationStatus.VERIFIED
            }
        }

        test("two distinct businesses may share one point, as a curator decided") {
            runTest {
                val first = candidate(
                    name = "Counter One",
                    venueKey = "a1".repeat(32),
                    claims = listOf(claim(record = "H-21")),
                    lat = 43.75,
                    lng = -79.75,
                )
                val second = candidate(
                    name = "Counter Two",
                    venueKey = "a2".repeat(32),
                    claims = listOf(claim(record = "H-22")),
                    lat = 43.75,
                    lng = -79.75,
                )

                val result = listings.applyBatch(
                    listOf(ListingWrite(WriteTarget.Insert, first), ListingWrite(WriteTarget.Insert, second)),
                )

                result.inserted shouldBe 2
                postgis.count(
                    "SELECT count(*) FROM restaurant_listings WHERE location_lat_6 = round(43.75::numeric, 6) " +
                        "AND location_lng_6 = round(-79.75::numeric, 6)",
                ) shouldBe 2
            }
        }

        test("attach-to re-points exactly one claim, and the source row loses the trust it conferred") {
            runTest {
                listings.applyBatch(
                    listOf(
                        ListingWrite(
                            WriteTarget.Insert,
                            candidate(
                                name = "Claim owner",
                                venueKey = "3".repeat(64),
                                claims = listOf(claim(source = "hms", record = "H-1")),
                                lat = 43.3,
                                lng = -79.3,
                            ),
                        ),
                        ListingWrite(
                            WriteTarget.Insert,
                            candidate(
                                name = "Venue owner",
                                venueKey = "4".repeat(64),
                                claims = listOf(
                                    claim(
                                        source = "manual-zabihah",
                                        record = "Z-2",
                                        tier = CertificationTier.TIER_3,
                                        declaredHandCut = HandCutDeclaration.UNSPECIFIED,
                                    ),
                                ),
                                crossContamination = CrossContamination.UNCERTAIN,
                                lat = 43.4,
                                lng = -79.4,
                            ),
                        ),
                    ),
                )
                val from = listings.findByVenueKey("3".repeat(64)) ?: error("missing")
                val to = listings.findByVenueKey("4".repeat(64)) ?: error("missing")
                to.verificationStatus shouldBe VerificationStatus.UNVERIFIED

                listings.applyBatch(
                    writes = emptyList(),
                    reattachments = listOf(
                        ClaimReattachment(
                            source = "hms",
                            sourceRecordRef = "H-1",
                            fromListingId = from.id,
                            toListingId = to.id,
                        ),
                    ),
                )

                listings.findClaim("hms", "H-1")?.id shouldBe to.id
                // The source had no other claim: it may not stay VERIFIED with zero evidence.
                val emptied = listings.findById(from.id) ?: error("missing")
                emptied.verificationStatus shouldBe VerificationStatus.UNVERIFIED
                emptied.isHandCut shouldBe null
                emptied.crossContamination shouldBe CrossContamination.UNCERTAIN
                postgis.count("SELECT count(*) FROM listing_search WHERE id = '${from.id}'") shouldBe 0
                // The destination gains the claim and the trust it confers, even
                // in a batch that contains only the re-attachment.
                postgis.count("SELECT count(*) FROM external_certification_claims WHERE listing_id = '${to.id}'") shouldBe 2
                val gained = listings.findById(to.id) ?: error("missing")
                gained.verificationStatus shouldBe VerificationStatus.VERIFIED
                gained.isHandCut shouldBe true
                gained.provenance shouldBe Provenance.CERTIFIER_HMS
                postgis.count("SELECT count(*) FROM listing_search WHERE id = '${to.id}'") shouldBe 1
            }
        }

        test("a curated cross-contamination correction persists and moves the index row") {
            runTest {
                val manual = candidate(
                    name = "Manual Venue",
                    venueKey = "b1".repeat(32),
                    claims = listOf(
                        claim(
                            source = "manual-zabihah",
                            record = "Z-11",
                            tier = CertificationTier.TIER_3,
                            declaredHandCut = HandCutDeclaration.UNSPECIFIED,
                        ),
                    ),
                    crossContamination = CrossContamination.UNCERTAIN,
                    lat = 43.65,
                    lng = -79.65,
                )
                listings.applyBatch(listOf(ListingWrite(WriteTarget.Insert, manual)))
                val listing = listings.findByVenueKey("b1".repeat(32)) ?: error("missing")
                listing.crossContamination shouldBe CrossContamination.UNCERTAIN
                postgis.count("SELECT count(*) FROM listing_search WHERE id = '${listing.id}'") shouldBe 0

                listings.applyBatch(
                    listOf(
                        ListingWrite(
                            WriteTarget.Update(listing.id),
                            manual.copy(manualCrossContamination = CrossContamination.NO_CROSS_CONTAMINATION),
                        ),
                    ),
                )
                listings.findById(listing.id)?.crossContamination shouldBe
                    CrossContamination.NO_CROSS_CONTAMINATION
                postgis.count("SELECT count(*) FROM listing_search WHERE id = '${listing.id}'") shouldBe 1

                listings.applyBatch(
                    listOf(
                        ListingWrite(
                            WriteTarget.Update(listing.id),
                            manual.copy(manualCrossContamination = CrossContamination.PRESENT),
                        ),
                    ),
                )
                listings.findById(listing.id)?.crossContamination shouldBe CrossContamination.PRESENT
                postgis.count("SELECT count(*) FROM listing_search WHERE id = '${listing.id}'") shouldBe 0
            }
        }

        test("a stale re-point fails the batch") {
            runTest {
                listings.applyBatch(
                    listOf(
                        ListingWrite(
                            WriteTarget.Insert,
                            candidate(
                                name = "Claim owner",
                                venueKey = "3".repeat(64),
                                claims = listOf(claim(source = "hms", record = "H-1")),
                                lat = 43.3,
                                lng = -79.3,
                            ),
                        ),
                        ListingWrite(
                            WriteTarget.Insert,
                            candidate(
                                name = "Venue owner",
                                venueKey = "4".repeat(64),
                                claims = listOf(claim(source = "hms", record = "H-2")),
                                lat = 43.4,
                                lng = -79.4,
                            ),
                        ),
                    ),
                )
                val from = listings.findByVenueKey("3".repeat(64)) ?: error("missing")
                val to = listings.findByVenueKey("4".repeat(64)) ?: error("missing")
                val reattachment = ClaimReattachment(
                    source = "hms",
                    sourceRecordRef = "H-1",
                    fromListingId = from.id,
                    toListingId = to.id,
                )

                listings.applyBatch(writes = emptyList(), reattachments = listOf(reattachment))
                // The claim is no longer on `from`: replaying the same decision
                // must fail the batch rather than re-point someone else's claim.
                shouldThrow<BatchPlanException> {
                    listings.applyBatch(writes = emptyList(), reattachments = listOf(reattachment))
                }
            }
        }
    }
}
