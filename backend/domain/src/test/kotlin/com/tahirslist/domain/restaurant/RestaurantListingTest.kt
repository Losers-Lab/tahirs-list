package com.tahirslist.domain.restaurant

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.math.BigDecimal
import kotlin.time.Clock
import kotlin.uuid.Uuid

class RestaurantListingTest : FunSpec({

    test("new() creates an unverified listing with the given fields") {
        val owner = Uuid.random()
        val listing = RestaurantListing.new(
            name = "  Halal Grill  ",
            address = " 123 Main St  ",
            location = LatLng(40.7128, -74.0060),
            cuisine = Cuisine("Mediterranean"),
            isHandCut = true,
            ownerId = owner,
        )

        listing.name shouldBe "Halal Grill"                // trimmed
        listing.address shouldBe "123 Main St"             // trimmed
        listing.location shouldBe LatLng(40.7128, -74.0060)
        listing.cuisine shouldBe Cuisine("Mediterranean")
        listing.isHandCut shouldBe true
        listing.ownerId shouldBe owner
        listing.verificationStatus shouldBe VerificationStatus.UNVERIFIED // listing-first model
    }

    test("new() defaults isHandCut to null (unknown / not claimed)") {
        // sc-42: hand-cut is an EXTRA boolean, not an either/or choice. A new
        // listing that does not claim hand-cut records null (unknown) by default.
        val listing = RestaurantListing.new(
            name = "Plain Grill",
            address = "9 St",
            location = LatLng(0.0, 0.0),
            cuisine = Cuisine("x"),
            ownerId = Uuid.random(),
        )
        listing.isHandCut shouldBe null
    }

    test("isHandCut is a plain boolean tri-state, not a cutting-method enum") {
        // sc-42: there is no machine-cut concept — only hand-cut, not-hand-cut,
        // or unknown(null). Assert all three states on the domain model.
        fun build(handCut: Boolean?) = RestaurantListing.new(
            name = "X",
            address = "1 St",
            location = LatLng(0.0, 0.0),
            cuisine = Cuisine("x"),
            isHandCut = handCut,
            ownerId = Uuid.random(),
        )
        build(true).isHandCut shouldBe true
        build(false).isHandCut shouldBe false
        build(null).isHandCut shouldBe null
    }

    test("new() assigns a random id and a createdAt timestamp") {
        val a = RestaurantListing.new(
            name = "A", address = "1 St", location = LatLng(0.0, 0.0),
            cuisine = Cuisine("x"), isHandCut = false, ownerId = Uuid.random(),
        )
        val b = RestaurantListing.new(
            name = "B", address = "2 St", location = LatLng(1.0, 1.0),
            cuisine = Cuisine("x"), isHandCut = false, ownerId = Uuid.random(),
        )

        a.id shouldNotBe b.id
        a.createdAt shouldNotBe null
    }

    test("new() carries an optional price") {
        val listing = RestaurantListing.new(
            name = "X",
            address = "1 St",
            location = LatLng(0.0, 0.0),
            cuisine = Cuisine("x"),
            isHandCut = true,
            ownerId = Uuid.random(),
            price = Price(BigDecimal("9.99")),
        )

        listing.price?.value shouldBe BigDecimal("9.99")
    }

    test("new() carries an optional rating") {
        val listing = RestaurantListing.new(
            name = "X",
            address = "1 St",
            location = LatLng(0.0, 0.0),
            cuisine = Cuisine("x"),
            isHandCut = true,
            ownerId = Uuid.random(),
            rating = Rating(BigDecimal("4.5")),
        )

        listing.rating?.value shouldBe BigDecimal("4.5")
        // Default is no rating (null).
        RestaurantListing.new(
            name = "Y", address = "2 St", location = LatLng(0.0, 0.0),
            cuisine = Cuisine("x"), isHandCut = false, ownerId = Uuid.random(),
        ).rating shouldBe null
    }

    test("new() defaults isDelivery to null (unknown / not claimed)") {
        // sc-184: delivery is an EXTRA boolean, not an either/or choice. A new
        // listing that does not claim delivery records null (unknown) by default,
        // mirroring the sc-42 hand-cut tri-state.
        val listing = RestaurantListing.new(
            name = "Plain Grill",
            address = "9 St",
            location = LatLng(0.0, 0.0),
            cuisine = Cuisine("x"),
            ownerId = Uuid.random(),
        )
        listing.isDelivery shouldBe null
    }

    test("new() carries an explicit isDelivery flag (sc-184)") {
        val listing = RestaurantListing.new(
            name = "X",
            address = "1 St",
            location = LatLng(0.0, 0.0),
            cuisine = Cuisine("x"),
            isHandCut = true,
            isDelivery = true,
            ownerId = Uuid.random(),
        )
        listing.isDelivery shouldBe true
    }

    test("isDelivery is a plain boolean tri-state, like isHandCut (sc-184)") {
        // sc-184 mirrors the sc-42 pattern: delivery is true / false / null
        // (unknown/not claimed) — never an enum of service modes.
        fun build(delivery: Boolean?) = RestaurantListing.new(
            name = "X",
            address = "1 St",
            location = LatLng(0.0, 0.0),
            cuisine = Cuisine("x"),
            isHandCut = true,
            isDelivery = delivery,
            ownerId = Uuid.random(),
        )
        build(true).isDelivery shouldBe true
        build(false).isDelivery shouldBe false
        build(null).isDelivery shouldBe null
    }

    test("new() defaults alcoholServed to false") {
        val listing = RestaurantListing.new(
            name = "Y", address = "2 St", location = LatLng(0.0, 0.0),
            cuisine = Cuisine("x"), isHandCut = false, ownerId = Uuid.random(),
        )
        listing.alcoholServed shouldBe false
    }

    test("new() carries an explicit alcoholServed flag") {
        val listing = RestaurantListing.new(
            name = "X",
            address = "1 St",
            location = LatLng(0.0, 0.0),
            cuisine = Cuisine("x"),
            isHandCut = true,
            ownerId = Uuid.random(),
            alcoholServed = true,
        )
        listing.alcoholServed shouldBe true
    }

    test("new() rejects a blank name") {
        shouldThrow<IllegalArgumentException> {
            RestaurantListing.new(
                name = "   ",
                address = "1 St",
                location = LatLng(0.0, 0.0),
                cuisine = Cuisine("x"),
                isHandCut = true,
                ownerId = Uuid.random(),
            )
        }
    }

    test("new() rejects a blank address") {
        shouldThrow<IllegalArgumentException> {
            RestaurantListing.new(
                name = "Name",
                address = "",
                location = LatLng(0.0, 0.0),
                cuisine = Cuisine("x"),
                isHandCut = true,
                ownerId = Uuid.random(),
            )
        }
    }

    test("Cuisine rejects blank and over-long values") {
        shouldThrow<IllegalArgumentException> { Cuisine("   ") }
        shouldThrow<IllegalArgumentException> { Cuisine("c".repeat(65)) }
    }

    test("LatLng validates latitude and longitude bounds") {
        shouldThrow<IllegalArgumentException> { LatLng(91.0, 0.0) }
        shouldThrow<IllegalArgumentException> { LatLng(-91.0, 0.0) }
        shouldThrow<IllegalArgumentException> { LatLng(0.0, 181.0) }
        shouldThrow<IllegalArgumentException> { LatLng(0.0, -181.0) }
        // Boundaries are permitted.
        LatLng(90.0, -180.0).lat shouldBe 90.0
    }

    test("Cuisine normalises to trimmed, lowercase value") {
        Cuisine(" Mediterranean ").value shouldBe "mediterranean"
    }

    test("Provenance accepts the closed seed vocabulary") {
        Provenance(" research-seed ").value shouldBe "research-seed"
        Provenance("photon-geocode") shouldBe Provenance.PHOTON_GEOCODE
        Provenance("research-seed / photon-geocode") shouldBe Provenance.RESEARCH_SEED_PHOTON_GEOCODE
    }

    test("Provenance rejects an out-of-vocabulary value") {
        shouldThrow<IllegalArgumentException> { Provenance("some-other-source") }
        shouldThrow<IllegalArgumentException> { Provenance("") }
    }

    test("fromStorage materialises a seed-style row with nullable cuisine/owner and provenance") {
        val seedId = Uuid.random()
        val brandId = Uuid.random()
        val listing = RestaurantListing.fromStorage(
            id = seedId,
            name = "The Halal Guys",
            address = "307 E 14th St",
            location = LatLng(40.732288, -73.984423),
            cuisine = null,
            isHandCut = null,
            ownerId = null,
            brandId = brandId,
            provenance = Provenance.RESEARCH_SEED_PHOTON_GEOCODE,
            verificationStatus = VerificationStatus.UNVERIFIED,
            createdAt = Clock.System.now(),
        )

        listing.id shouldBe seedId
        listing.cuisine shouldBe null
        listing.ownerId shouldBe null
        listing.brandId shouldBe brandId
        listing.provenance shouldBe Provenance.RESEARCH_SEED_PHOTON_GEOCODE
    }

    test("imageVersion defaults to 0 (no photo) on every factory (SC-205)") {
        val created = RestaurantListing.new(
            name = "Halal Grill",
            address = "123 Main St",
            location = LatLng(40.0, -74.0),
            cuisine = Cuisine("mediterranean"),
            ownerId = Uuid.random(),
        )
        created.imageVersion shouldBe 0L

        val stored = RestaurantListing.fromStorage(
            id = Uuid.random(),
            name = "Seed Row",
            address = "1 Seed St",
            location = LatLng(40.0, -74.0),
            cuisine = null,
            ownerId = null,
            brandId = null,
            provenance = null,
            verificationStatus = VerificationStatus.UNVERIFIED,
            createdAt = Clock.System.now(),
        )
        stored.imageVersion shouldBe 0L
    }

    test("fromStorage reconstitutes the photo version (SC-205)") {
        val listing = RestaurantListing.fromStorage(
            id = Uuid.random(),
            name = "Seed Row",
            address = "1 Seed St",
            location = LatLng(40.0, -74.0),
            cuisine = null,
            ownerId = null,
            brandId = null,
            provenance = null,
            verificationStatus = VerificationStatus.UNVERIFIED,
            createdAt = Clock.System.now(),
            imageVersion = 1_760_000_000_123L,
        )

        listing.imageVersion shouldBe 1_760_000_000_123L
    }

    test("fromStorage reconstitutes the alcoholServed flag") {
        val listing = RestaurantListing.fromStorage(
            id = Uuid.random(),
            name = "Steak House",
            address = "1 Grill Ave",
            location = LatLng(40.0, -74.0),
            cuisine = null,
            isHandCut = true,
            isDelivery = true,
            ownerId = null,
            brandId = null,
            provenance = null,
            verificationStatus = VerificationStatus.UNVERIFIED,
            createdAt = Clock.System.now(),
            alcoholServed = true,
        )

        listing.alcoholServed shouldBe true
        listing.isDelivery shouldBe true
    }

    test("new() carries and trims the structured address fields (sc-187)") {
        val listing = RestaurantListing.new(
            name = "Al-Amir",
            address = " 3885 Belt Line Rd ",
            city = " Addison ",
            province = " TX ",
            postal = " 75001 ",
            country = " US ",
            location = LatLng(32.953530, -96.849844),
            cuisine = Cuisine("lebanese"),
            ownerId = Uuid.random(),
        )

        listing.address shouldBe "3885 Belt Line Rd"      // street line untouched (backward compat)
        listing.city shouldBe "Addison"                   // trimmed
        listing.province shouldBe "TX"
        listing.postal shouldBe "75001"
        listing.country shouldBe "US"
    }

    test("new() defaults the structured address fields to null (sc-187)") {
        val listing = RestaurantListing.new(
            name = "Plain Grill",
            address = "9 St",
            location = LatLng(0.0, 0.0),
            cuisine = Cuisine("x"),
            ownerId = Uuid.random(),
        )
        listing.city shouldBe null
        listing.province shouldBe null
        listing.postal shouldBe null
        listing.country shouldBe null
    }

    test("new() keeps the Canadian seed shape with a Toronto / ON / Canadian-postal country (sc-187 non-US)") {
        // Province must be country-agnostic — CA rows use a Canadian postal string and CA country.
        val listing = RestaurantListing.new(
            name = "Osmow's",
            address = "505 St. Clair Ave W",
            city = "Toronto",
            province = "ON",
            postal = "M6C 1A1",
            country = "CA",
            location = LatLng(43.682921, -79.418493),
            cuisine = Cuisine("shwarma"),
            ownerId = Uuid.random(),
        )
        listing.province shouldBe "ON"
        listing.postal shouldBe "M6C 1A1"   // NOT a US 5-digit zip — no US-only assumption
        listing.country shouldBe "CA"
    }

    test("new() rejects a blank structured address field (sc-187)") {
        shouldThrow<IllegalArgumentException> {
            RestaurantListing.new(
                name = "X", address = "1 St", location = LatLng(0.0, 0.0), cuisine = Cuisine("x"),
                city = "   ", ownerId = Uuid.random(),
            )
        }
        shouldThrow<IllegalArgumentException> {
            RestaurantListing.new(
                name = "X", address = "1 St", location = LatLng(0.0, 0.0), cuisine = Cuisine("x"),
                country = " ", ownerId = Uuid.random(),
            )
        }
    }

    test("new() rejects an over-long structured address field (sc-187)") {
        shouldThrow<IllegalArgumentException> {
            RestaurantListing.new(
                name = "X", address = "1 St", location = LatLng(0.0, 0.0), cuisine = Cuisine("x"),
                city = "c".repeat(RestaurantListing.CITY_MAX_LENGTH + 1), ownerId = Uuid.random(),
            )
        }
        shouldThrow<IllegalArgumentException> {
            RestaurantListing.new(
                name = "X", address = "1 St", location = LatLng(0.0, 0.0), cuisine = Cuisine("x"),
                postal = "p".repeat(RestaurantListing.POSTAL_MAX_LENGTH + 1), ownerId = Uuid.random(),
            )
        }
    }

    test("fromStorage reconstitutes the structured address fields (sc-187)") {
        val listing = RestaurantListing.fromStorage(
            id = Uuid.random(),
            name = "Caspian Grill",
            address = "12518 Research Blvd",
            city = "Austin",
            province = "TX",
            postal = "78759",
            country = "US",
            location = LatLng(30.428596, -97.760465),
            cuisine = null,
            ownerId = null,
            brandId = null,
            provenance = null,
            verificationStatus = VerificationStatus.UNVERIFIED,
            createdAt = Clock.System.now(),
        )
        listing.city shouldBe "Austin"
        listing.province shouldBe "TX"
        listing.postal shouldBe "78759"
        listing.country shouldBe "US"
    }
})