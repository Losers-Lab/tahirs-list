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
    private val byAddress: (String) -> GeocodedPlace? = {
        place("Paramount Fine Foods, 253 Yonge Street, Toronto, ON, Canada", name = "Paramount Fine Foods")
    },
) : GeocoderPort {
    val queries = mutableListOf<String>()
    override suspend fun geocode(address: String): GeocodedPlace? {
        queries += address
        return byAddress(address)
    }
}

fun place(displayName: String, lat: Double = 43.665206, lng: Double = -79.384523, name: String? = null) =
    GeocodedPlace(
        location = LatLng(lat, lng),
        displayName = displayName,
        providerRef = "N/1",
        name = name,
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
    val query = MatchQuery(
        name = "Paramount Fine Foods",
        city = "Toronto",
        region = "ON",
        country = "Canada",
        street = "253 Yonge Street",
        houseNumber = "253",
        streetTokens = listOf("yonge"),
    )

    test("a resolved label that shares a meaningful name token is confirmed") {
        policy.confirm(query, place("Paramount Fine Foods, 253 Yonge St, Toronto", name = "Paramount Fine Foods"))
            .shouldBeInstanceOf<MatchDecision.Confirmed>()
    }

    test("a house number and street are enough when the label omits the trade name") {
        policy.confirm(query, place("253 Yonge Street, Toronto, ON, Canada"))
            .shouldBeInstanceOf<MatchDecision.Confirmed>()
    }

    test("a city alone is not evidence: any POI in the city would pass") {
        policy.confirm(
            MatchQuery("Cloud Naan", "Richardson", "TX", "United States", street = "888 South Greenville Avenue", houseNumber = "888", streetTokens = listOf("greenville")),
            place("Muslim Legal Fund of America, 100 North Central Expressway, Richardson, TX"),
        ).shouldBeInstanceOf<MatchDecision.Rejected>()
    }

    test("a street-level hit without the house number is rejected as imprecise") {
        val decision = policy.confirm(
            MatchQuery("SPICE WOK", "Dallas", "TX", "United States", street = "13340 Audelia Road", houseNumber = "13340", streetTokens = listOf("audelia")),
            place("Audelia Road, 75243 Dallas, TX, United States"),
        )
        decision.shouldBeInstanceOf<MatchDecision.Rejected>()
        decision.shouldBeInstanceOf<MatchDecision.Rejected>().detail.contains("house 13340") shouldBe true
    }

    test("a different house number on the same street is rejected") {
        policy.confirm(query, place("255 Yonge Street, Toronto, ON, Canada"))
            .shouldBeInstanceOf<MatchDecision.Rejected>()
    }

    test("the same block of a Queens hyphenated number is confirmed") {
        val queens = MatchQuery(
            name = "Filli Cafe Floral Park",
            city = "Queens",
            region = "NY",
            country = "United States",
            street = "258-03 Hillside Avenue",
            houseNumber = "258-03",
            streetTokens = listOf("hillside"),
            postalCode = "11004",
        )
        policy.confirm(queens, place("Hillside Avenue 258-01, 11004 New York, NY, United States"))
            .shouldBeInstanceOf<MatchDecision.Confirmed>()
    }

    test("a numeric street name matches its ordinal form") {
        val burgermania = MatchQuery(
            name = "Burgermania 40 Street",
            city = "New York",
            region = "NY",
            country = "United States",
            street = "274 West 40 Street",
            houseNumber = "274",
            streetTokens = listOf("40"),
        )
        policy.confirm(
            burgermania,
            place("Kiss My Slice, 274 West 40th Street, 10018 New York, NY, United States", name = "Kiss My Slice"),
        ).shouldBeInstanceOf<MatchDecision.Confirmed>()
    }

    test("a hit with no provider name cannot be confirmed by its street") {
        val mainStreet = MatchQuery(
            name = "Main Street Grill",
            city = "Dallas",
            region = "TX",
            country = "United States",
            street = "123 Elm Street",
            houseNumber = "123",
            streetTokens = listOf("elm"),
        )
        // An address-only hit: the label starts with a street, not a venue name.
        policy.confirm(mainStreet, place("1 Main Street, Springfield, Illinois, United States"))
            .shouldBeInstanceOf<MatchDecision.Rejected>()
    }

    test("a street feature's name cannot be confirmed as a venue name") {
        val mainStreet = MatchQuery(
            name = "Main Street Grill",
            city = "Dallas",
            region = "TX",
            country = "United States",
            street = "123 Elm Street",
            houseNumber = "123",
            streetTokens = listOf("elm"),
        )
        // Photon can return a road as a hit, with the road name in `name`.
        policy.confirm(
            mainStreet,
            place("Elm Street, Dallas, TX, United States", name = "Elm Street"),
        ).shouldBeInstanceOf<MatchDecision.Rejected>()
    }

    test("a road word alone is not a name match") {
        val roadHouse = MatchQuery(
            name = "Road House Halal",
            city = "Dallas",
            region = "TX",
            country = "United States",
            street = "5 Runner Road",
            houseNumber = "5",
            streetTokens = listOf("runner"),
        )
        // Every distinctive word in the row's name is a generic road word.
        policy.confirm(
            roadHouse,
            place("Runner Road, Dallas, TX, United States", name = "Runner Road"),
        ).shouldBeInstanceOf<MatchDecision.Rejected>()
    }

    test("a name token that is just the city is not a name match") {
        val dallas = MatchQuery(
            name = "Dallas Grill",
            city = "Dallas",
            region = "TX",
            country = "United States",
            street = "1200 Main Street",
            houseNumber = "1200",
            streetTokens = listOf("main"),
        )
        // The label names the city but not the venue.
        policy.confirm(dallas, place("Some Other Restaurant, 900 Commerce Street, Dallas, TX"))
            .shouldBeInstanceOf<MatchDecision.Rejected>()
    }

    test("a name token that is just the state is not a name match") {
        // The label's state field is "Texas"; a venue called "Texas King" must
        // not be confirmed by it, even though region is the code "TX".
        val texas = MatchQuery(
            name = "Texas King Irving",
            city = "Irving",
            region = "TX",
            country = "United States",
            street = "3455 North Belt Line Road",
            houseNumber = "3455",
            streetTokens = listOf("belt", "line"),
        )
        policy.confirm(texas, place("Some Market, 123 Main Street, Plano, Texas, United States"))
            .shouldBeInstanceOf<MatchDecision.Rejected>()
    }

    test("a Canadian postal code written as two tokens still confirms the address") {
        val toronto = MatchQuery(
            name = "Paramount Fine Foods",
            city = "Toronto",
            region = "ON",
            country = "Canada",
            street = "253 Yonge Street",
            houseNumber = "253",
            streetTokens = listOf("yonge"),
            postalCode = "M5V 2T6",
        )
        policy.confirm(toronto, place("Some Place, 253 Yonge Street, M5V 2T6 Toronto, ON, Canada"))
            .shouldBeInstanceOf<MatchDecision.Confirmed>()
    }

    test("generic words alone never confirm a match") {
        policy.confirm(
            MatchQuery("The Halal Grill", "Toronto", "ON", "Canada"),
            place("Some Other Restaurant, Toronto"),
        ).shouldBeInstanceOf<MatchDecision.Rejected>()
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
        val montreal = FakeGeocoder { place("Paramount Fine Foods, Toronto, Ontario", 45.5019, -73.5674, name = "Paramount Fine Foods") }
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

    test("a row is asked for by name first, then by address, with unit noise stripped") {
        val geocoder = FakeGeocoder { query ->
            if (query.startsWith("Paramount")) null else place("253 Yonge Street, Toronto, ON, Canada")
        }

        CandidateFactory(geocoder)
            .build(sourceListing(address = "253 Yonge St, Suite 500"), "Toronto")
            .shouldBeInstanceOf<CandidateBuild.Built>()

        geocoder.queries shouldBe listOf(
            "Paramount Fine Foods, 253 Yonge Street, Toronto, ON",
            "253 Yonge Street, Toronto, ON",
        )
    }

    test("an unconfirmed geocoder answer is a reject, not an accepted location") {
        // Photon answered, but only with a street-level point somewhere on it.
        val geocoder = FakeGeocoder { place("Yonge Street, Toronto, ON, Canada") }
        val reject = CandidateFactory(geocoder)
            .build(sourceListing(), "Toronto")
            .shouldBeInstanceOf<CandidateBuild.Rejected>()
            .reject

        reject.reason shouldBe RejectReason.AMBIGUOUS_GEOCODER_MATCH
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
