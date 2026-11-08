package com.tahirslist.domain.restaurant

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.time.Instant
import kotlin.uuid.Uuid

private fun location() = LatLng(43.665206, -79.384523)

private fun cuisine() = Cuisine("pakistani")

class RestaurantListingImportedTest : FunSpec({

    fun imported(
        name: String = "  Paramount Fine Foods  ",
        address: String = "  253 Yonge St  ",
        venueKey: String? = "a".repeat(64),
        verificationStatus: VerificationStatus = VerificationStatus.VERIFIED,
        verificationBasis: VerificationBasis? = VerificationBasis.CERTIFIER_REGISTRY,
    ) = RestaurantListing.imported(
        name = name,
        address = address,
        city = "Toronto",
        province = "ON",
        postal = "M5B 1N8",
        country = "CA",
        location = location(),
        cuisine = null,
        isHandCut = false,
        isDelivery = null,
        provenance = Provenance.CERTIFIER_HMA,
        venueKey = venueKey,
        verificationStatus = verificationStatus,
        verificationBasis = verificationBasis,
    )

    test("imports a certifier row with no owner and no cuisine") {
        val listing = imported()

        listing.name shouldBe "Paramount Fine Foods"
        listing.address shouldBe "253 Yonge St"
        listing.ownerId shouldBe null
        listing.cuisine shouldBe null
        listing.verificationStatus shouldBe VerificationStatus.VERIFIED
        listing.provenance shouldBe Provenance.CERTIFIER_HMA
        listing.venueKey shouldBe "a".repeat(64)
        listing.isHandCut shouldBe false
        listing.brandId shouldBe null
    }

    test("refuses VERIFIED without a verification basis") {
        shouldThrow<IllegalArgumentException> {
            imported(verificationBasis = null)
        }
    }

    test("refuses a basis on an UNVERIFIED row") {
        shouldThrow<IllegalArgumentException> {
            imported(
                verificationStatus = VerificationStatus.UNVERIFIED,
                verificationBasis = VerificationBasis.CERTIFIER_REGISTRY,
            )
        }
    }

    test("allows an UNVERIFIED imported row with no basis") {
        val listing = imported(
            verificationStatus = VerificationStatus.UNVERIFIED,
            verificationBasis = null,
        )

        listing.verificationStatus shouldBe VerificationStatus.UNVERIFIED
        listing.provenance shouldBe Provenance.CERTIFIER_HMA
    }

    test("requires a provenance stamp for an imported row") {
        shouldThrow<IllegalArgumentException> {
            RestaurantListing.imported(
                name = "X",
                address = "1 St",
                location = location(),
                provenance = null,
            )
        }
    }

    test("trims and validates the address fields like new/fromStorage") {
        shouldThrow<IllegalArgumentException> {
            RestaurantListing.imported(
                name = "X",
                address = " ",
                location = location(),
                provenance = Provenance.CERTIFIER_HMA,
            )
        }
        shouldThrow<IllegalArgumentException> {
            RestaurantListing.imported(
                name = "X",
                address = "1 St",
                city = " ",
                location = location(),
                provenance = Provenance.CERTIFIER_HMA,
            )
        }
    }

    test("the existing new() factory still creates an unverified user row") {
        val listing = RestaurantListing.new(
            name = "User Place",
            address = "1 Main St",
            location = location(),
            cuisine = cuisine(),
            ownerId = Uuid.random(),
        )

        listing.verificationStatus shouldBe VerificationStatus.UNVERIFIED
        listing.ownerId shouldNotBe null
        listing.venueKey shouldBe null
    }

    test("fromStorage preserves the venue key") {
        val listing = RestaurantListing.fromStorage(
            id = Uuid.random(),
            name = "X",
            address = "1 St",
            location = location(),
            cuisine = null,
            ownerId = null,
            brandId = null,
            provenance = Provenance.CERTIFIER_HMA,
            verificationStatus = VerificationStatus.VERIFIED,
            createdAt = Instant.parse("2026-11-08T00:00:00Z"),
            venueKey = "b".repeat(64),
        )

        listing.venueKey shouldBe "b".repeat(64)
    }

    test("an owner edit preserves governance fields including venueKey") {
        val original = imported()
        val edited = original.withUpdatedFields(
            name = "New Name",
            address = "2 St",
            location = location(),
            cuisine = null,
            isHandCut = null,
            isDelivery = null,
        )

        edited.venueKey shouldBe original.venueKey
        edited.provenance shouldBe original.provenance
        edited.verificationStatus shouldBe original.verificationStatus
    }
})
