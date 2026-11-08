package com.tahirslist.ingestion.pipeline

import com.tahirslist.application.geo.GeocodedPlace
import com.tahirslist.application.geo.GeocoderPort
import com.tahirslist.application.geo.GeocodingException
import com.tahirslist.application.ingestion.RejectReason
import com.tahirslist.certifiers.core.HandCutDeclaration
import com.tahirslist.domain.restaurant.LatLng
import com.tahirslist.ingestion.DirectoryStandard
import com.tahirslist.ingestion.SourceListing
import com.tahirslist.ingestion.SourceRef
import com.tahirslist.ingestion.SourceTier
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest

fun sourceListing(
    source: String = "hms",
    record: String = "H-1",
    name: String = "Paramount Fine Foods",
    address: String = "253 Yonge St",
    city: String? = "Toronto",
    region: String? = "ON",
    country: String? = "Canada",
    tier: SourceTier = SourceTier.TIER_1,
    handCut: HandCutDeclaration = HandCutDeclaration.HAND_CUT,
    lat: Double? = 43.665206,
    lng: Double? = -79.384523,
): SourceListing = SourceListing(
    source = SourceRef(certifierId = source, recordId = record, url = "https://example.test/$source/$record"),
    standard = DirectoryStandard(tier = tier, certifier = source, handCut = handCut),
    name = name,
    addressLines = listOf(address),
    city = city,
    region = region,
    postalCode = null,
    country = country,
    latitude = lat,
    longitude = lng,
    phone = null,
    website = null,
    certificateRef = null,
    certificationStatus = "Certified",
    expiresOn = null,
    providerPlaceId = null,
    category = "Restaurant",
)

class FakeGeocoder(
    private val byAddress: (String) -> GeocodedPlace? = { place("Toronto, ON, Canada") },
) : GeocoderPort {
    val queries = mutableListOf<String>()
    override suspend fun geocode(address: String): GeocodedPlace? {
        queries += address
        return byAddress(address)
    }
}

fun place(displayName: String, lat: Double = 43.665206, lng: Double = -79.384523) = GeocodedPlace(
    location = LatLng(lat, lng),
    displayName = displayName,
    providerRef = "N/1",
)

class MetroFilterTest : FunSpec({

    test("membership is the circle, so a place name cannot include or exclude a row") {
        val filter = MetroFilter()
        // A city the configuration never names, at a Toronto address.
        filter.forPoint(LatLng(43.70, -79.40))?.name shouldBe "Toronto"
        filter.forPoint(LatLng(32.78, -96.80))?.name shouldBe "Dallas"
        filter.forPoint(LatLng(40.68, -73.94))?.name shouldBe "NYC"
        // Toronto city centre, but more than 50 km away is still outside.
        filter.forPoint(LatLng(44.10, -79.60)) shouldBe null
        // The other side of the world.
        filter.forPoint(LatLng(51.50, -0.12)) shouldBe null
    }

    test("overlapping circles resolve to the nearest centre") {
        val catalog = MetroCatalog(
            listOf(
                MetroDefinition("A", LatLng(0.0, 0.0), 200.0),
                MetroDefinition("B", LatLng(0.0, 0.5), 200.0),
            ),
        )
        val filter = MetroFilter(catalog)
        filter.forPoint(LatLng(0.0, 0.1))?.name shouldBe "A"
        filter.forPoint(LatLng(0.0, 0.4))?.name shouldBe "B"
    }

    test("selection names are case-insensitive and empty means all") {
        val filter = MetroFilter()
        filter.selected(setOf("dallas")).map { it.name } shouldBe listOf("Dallas")
        filter.selected(emptySet()).size shouldBe filter.metros.size
    }

    test("a mistyped metro name fails closed instead of running an empty selection") {
        shouldThrow<IllegalArgumentException> { MetroFilter().selected(setOf("Torronto")) }
    }
})

class MatchConfirmationPolicyTest : FunSpec({

    val policy = MatchConfirmationPolicy()
    val query = MatchQuery("Paramount Fine Foods", "Toronto", "ON", "Canada")

    test("a resolved label that shares a meaningful name token is confirmed") {
        policy.confirm(query, place("Paramount Fine Foods, 253 Yonge St, Toronto"))
            .shouldBeInstanceOf<MatchDecision.Confirmed>()
    }

    test("a city hit is enough when the label omits the trade name") {
        policy.confirm(query, place("253 Yonge Street, Toronto, Ontario"))
            .shouldBeInstanceOf<MatchDecision.Confirmed>()
    }

    test("generic words alone never confirm a match") {
        val decision = policy.confirm(
            MatchQuery("The Halal Grill", "Toronto", "ON", "Canada"),
            place("Some Other Restaurant, Toronto"),
        )
        // The city still matches, so this is confirmed by city; drop the city to
        // prove the generic-only path is rejected.
        decision.shouldBeInstanceOf<MatchDecision.Confirmed>()
        policy.confirm(
            MatchQuery("The Halal Grill", null, null, null),
            place("Some Other Restaurant"),
        ).shouldBeInstanceOf<MatchDecision.Rejected>()
    }

    test("a row with a known city/region/country that resolves elsewhere is rejected") {
        val decision = policy.confirm(
            MatchQuery("Paramount Fine Foods", "Toronto", "ON", "Canada"),
            place("1 Main St, Buffalo, New York, United States"),
        )
        decision.shouldBeInstanceOf<MatchDecision.Rejected>()
    }
})

class CandidateFactoryTest : FunSpec({

    test("an accepted row carries the confirmed location, venue key and a citation claim") {
        val factory = CandidateFactory(FakeGeocoder())

        val built = factory.build(sourceListing(), "Toronto")
            .shouldBeInstanceOf<CandidateBuild.Built>()

        built.candidate.location shouldBe LatLng(43.665206, -79.384523)
        built.candidate.venueKey.length shouldBe 64
        built.candidate.metro shouldBe "Toronto"
        built.candidate.claims.single().source shouldBe "hms"
        built.candidate.claims.single().sourceRecordRef shouldBe "H-1"
        built.candidate.verificationStatus.name shouldBe "VERIFIED"
        built.candidate.isHandCut shouldBe true
    }

    test("the directory's own coordinates are never the accepted location") {
        val directoryRow = sourceListing().copy(latitude = 10.0, longitude = 10.0)
        val candidate = CandidateFactory(FakeGeocoder())
            .build(directoryRow, "Toronto")
            .shouldBeInstanceOf<CandidateBuild.Built>()
            .candidate

        candidate.location shouldBe LatLng(43.665206, -79.384523)
    }

    test("a confirmed location outside the metro radius is rejected with the distance") {
        // The label matches the row, so the match policy confirms it; only the
        // metro circle can reject it - which is the point.
        val montreal = FakeGeocoder { place("Paramount Fine Foods, Toronto, Ontario", 45.5019, -73.5674) }
        val reject = CandidateFactory(montreal)
            .build(sourceListing(), "Toronto")
            .shouldBeInstanceOf<CandidateBuild.Rejected>()
            .reject

        reject.reason shouldBe RejectReason.OUTSIDE_METRO
        reject.detail.contains("Toronto") shouldBe true
    }

    test("a metro that is not configured is a reject, not an invented metro") {
        val reject = CandidateFactory(FakeGeocoder())
            .build(sourceListing(), "Atlantis")
            .shouldBeInstanceOf<CandidateBuild.Rejected>()
            .reject

        reject.reason shouldBe RejectReason.MISSING_METRO
    }

    test("no geocoder hit is a reject, never invented coordinates") {
        val factory = CandidateFactory(FakeGeocoder { null })
        factory.build(sourceListing(), "Toronto")
            .shouldBeInstanceOf<CandidateBuild.Rejected>()
            .reject.reason shouldBe RejectReason.NO_GEOCODER_MATCH
    }

    test("a geocoder outage aborts the run instead of becoming a reject") {
        val factory = CandidateFactory(
            object : GeocoderPort {
                override suspend fun geocode(address: String): GeocodedPlace? =
                    throw GeocodingException("provider down")
            },
        )

        shouldThrow<GeocoderUnavailableException> {
            factory.build(sourceListing(), "Toronto")
        }
    }

    test("a manual row keeps an explicit cross-contamination value and no hand-cut claim") {
        val manual = sourceListing(
            source = "manual-zabihah",
            tier = SourceTier.TIER_3,
            handCut = HandCutDeclaration.UNSPECIFIED,
        )
        val candidate = CandidateFactory(FakeGeocoder())
            .build(manual, "Toronto", com.tahirslist.domain.restaurant.CrossContamination.PRESENT)
            .shouldBeInstanceOf<CandidateBuild.Built>()
            .candidate

        candidate.verificationStatus.name shouldBe "UNVERIFIED"
        candidate.isHandCut shouldBe null
        candidate.crossContamination.name shouldBe "PRESENT"
    }
})
