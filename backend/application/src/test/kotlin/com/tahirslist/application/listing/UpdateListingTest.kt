package com.tahirslist.application.listing

import com.tahirslist.application.favorite.ListingNotFoundException
import com.tahirslist.application.verification.NotListingOwnerException
import com.tahirslist.domain.restaurant.CrossContamination
import com.tahirslist.domain.restaurant.Cuisine
import com.tahirslist.domain.restaurant.HalalItem
import com.tahirslist.domain.restaurant.HalalScope
import com.tahirslist.domain.restaurant.LatLng
import com.tahirslist.domain.restaurant.Price
import com.tahirslist.domain.restaurant.RestaurantListing
import com.tahirslist.domain.restaurant.VerificationStatus
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.math.BigDecimal
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runTest

/**
 * Owner listing edit (sc-23/47/48): [UpdateListing] owner-authorizes a full
 * replace of the listing's *editable content fields* (name / address /
 * location / cuisine / cutting / delivery / partial-halal / alcohol). Only the
 * recorded owner may edit; non-owners get 403 semantics; missing listings 404;
 * identity + governance fields are preserved across the edit.
 */
class UpdateListingTest : FunSpec({

    lateinit var listings: RestaurantListingRepository
    lateinit var updateListing: UpdateListing

    beforeTest {
        listings = mockk()
        updateListing = UpdateListing(listings = listings)
    }

    fun existingListing(
        id: Uuid = Uuid.random(),
        ownerId: Uuid = Uuid.random(),
        status: VerificationStatus = VerificationStatus.UNVERIFIED,
    ): RestaurantListing = RestaurantListing.fromStorage(
        id = id,
        name = "Halal Grill",
        address = "123 Main St",
        location = LatLng(40.7128, -74.0060),
        cuisine = Cuisine("mediterranean"),
        isHandCut = true,
        isDelivery = true,
        price = Price(BigDecimal("12.00")),
        ownerId = ownerId,
        brandId = null,
        provenance = null,
        verificationStatus = status,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        halalScope = HalalScope.NOT_DISCLOSED,
        crossContamination = CrossContamination.UNCERTAIN,
    )

    fun stubFindById(listing: RestaurantListing) {
        coEvery { listings.findById(listing.id) } returns listing
    }

    test("owner can update own listing's editable fields") {
        runTest {
            val ownerId = Uuid.random()
            val current = existingListing(ownerId = ownerId)
            stubFindById(current)
            coEvery { listings.update(any()) } coAnswers { firstArg() }

            val updated = updateListing.execute(
                listingId = current.id,
                ownerId = ownerId,
                name = "Halal Grill Updated",
                address = "456 New Ave",
                location = LatLng(41.0, -73.0),
                cuisine = Cuisine("turkish"),
                isHandCut = false,
                isDelivery = false,
                halalScope = HalalScope.PARTIALLY_HALAL,
                halalItems = setOf(HalalItem("kebab", true)),
                crossContamination = CrossContamination.NO_CROSS_CONTAMINATION,
                alcoholServed = true,
            )

            updated.name shouldBe "Halal Grill Updated"
            updated.address shouldBe "456 New Ave"
            updated.location shouldBe LatLng(41.0, -73.0)
            updated.cuisine shouldBe Cuisine("turkish")
            updated.isHandCut shouldBe false
            updated.isDelivery shouldBe false
            updated.halalScope shouldBe HalalScope.PARTIALLY_HALAL
            updated.halalItems shouldBe setOf(HalalItem("kebab", true))
            updated.crossContamination shouldBe CrossContamination.NO_CROSS_CONTAMINATION
            updated.alcoholServed shouldBe true

            // Verify the edited fields actually flow to persistence.
            coVerify {
                listings.update(match {
                    it.id == current.id &&
                        it.name == "Halal Grill Updated" &&
                        it.isHandCut == false &&
                        it.crossContamination == CrossContamination.NO_CROSS_CONTAMINATION
                })
            }
            coVerify(exactly = 1) { listings.update(any()) }
        }
    }

    test("editing preserves identity and governance fields (id, owner, verification, created, price, rating)") {
        runTest {
            val ownerId = Uuid.random()
            val id = Uuid.random()
            val current = existingListing(id = id, ownerId = ownerId, status = VerificationStatus.VERIFIED)
            stubFindById(current)
            coEvery { listings.update(any()) } coAnswers { firstArg() }

            val updated = updateListing.execute(
                listingId = current.id,
                ownerId = ownerId,
                name = "Renamed Grill",
                address = "123 Main St",
                location = LatLng(40.7128, -74.0060),
                cuisine = Cuisine("mediterranean"),
                isHandCut = true,
                isDelivery = true,
            )

            updated.id shouldBe id
            updated.ownerId shouldBe ownerId
            updated.verificationStatus shouldBe VerificationStatus.VERIFIED
            updated.createdAt shouldBe current.createdAt
            updated.price shouldBe Price(BigDecimal("12.00"))
            updated.rating shouldBe current.rating
            updated.brandId shouldBe null
            updated.provenance shouldBe null
        }
    }

    test("trims name and address before persisting the update") {
        runTest {
            val ownerId = Uuid.random()
            val current = existingListing(ownerId = ownerId)
            stubFindById(current)
            coEvery { listings.update(any()) } coAnswers { firstArg() }

            updateListing.execute(
                listingId = current.id,
                ownerId = ownerId,
                name = "  Padded Name  ",
                address = "  123 Main St  ",
                location = LatLng(40.7128, -74.0060),
                cuisine = Cuisine("mediterranean"),
                isHandCut = true,
                isDelivery = true,
            )

            coVerify { listings.update(match { it.name == "Padded Name" && it.address == "123 Main St" }) }
        }
    }

    test("rejects a blank name before any repository write") {
        runTest {
            val ownerId = Uuid.random()
            val current = existingListing(ownerId = ownerId)
            stubFindById(current)

            shouldThrow<IllegalArgumentException> {
                updateListing.execute(
                    listingId = current.id,
                    ownerId = ownerId,
                    name = "   ",
                    address = "123 Main St",
                    location = LatLng(40.7128, -74.0060),
                    cuisine = Cuisine("mediterranean"),
                    isHandCut = true,
                    isDelivery = true,
                )
            }
            coVerify(exactly = 0) { listings.update(any()) }
        }
    }

    test("rejects a blank address before any repository write") {
        runTest {
            val ownerId = Uuid.random()
            val current = existingListing(ownerId = ownerId)
            stubFindById(current)

            shouldThrow<IllegalArgumentException> {
                updateListing.execute(
                    listingId = current.id,
                    ownerId = ownerId,
                    name = "Halal Grill",
                    address = "  ",
                    location = LatLng(40.7128, -74.0060),
                    cuisine = Cuisine("mediterranean"),
                    isHandCut = true,
                    isDelivery = true,
                )
            }
            coVerify(exactly = 0) { listings.update(any()) }
        }
    }

    test("a non-owner is unauthorized (403) and never edits") {
        runTest {
            val current = existingListing(ownerId = Uuid.random()) // owner is someone else
            stubFindById(current)

            val ex = shouldThrow<NotListingOwnerException> {
                updateListing.execute(
                    listingId = current.id,
                    ownerId = Uuid.random(), // different account
                    name = "Hijack Grill",
                    address = "123 Main St",
                    location = LatLng(40.7128, -74.0060),
                    cuisine = Cuisine("mediterranean"),
                    isHandCut = true,
                    isDelivery = true,
                )
            }
            ex.listingId shouldBe current.id
            coVerify(exactly = 0) { listings.update(any()) }
        }
    }

    test("unknown listing is not found (404)") {
        runTest {
            val id = Uuid.random()
            coEvery { listings.findById(id) } returns null

            val ex = shouldThrow<ListingNotFoundException> {
                updateListing.execute(
                    listingId = id,
                    ownerId = Uuid.random(),
                    name = "Ghost Grill",
                    address = "123 Main St",
                    location = LatLng(40.7128, -74.0060),
                    cuisine = Cuisine("mediterranean"),
                    isHandCut = true,
                    isDelivery = true,
                )
            }
            ex.listingId shouldBe id
            coVerify(exactly = 0) { listings.update(any()) }
        }
    }
})
