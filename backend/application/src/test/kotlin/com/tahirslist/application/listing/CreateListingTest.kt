package com.tahirslist.application.listing

import com.tahirslist.application.account.AccountRepository
import com.tahirslist.domain.account.Account
import com.tahirslist.domain.account.Email
import com.tahirslist.domain.restaurant.CrossContamination
import com.tahirslist.domain.restaurant.Cuisine
import com.tahirslist.domain.restaurant.HalalItem
import com.tahirslist.domain.restaurant.HalalScope
import com.tahirslist.domain.restaurant.LatLng
import com.tahirslist.domain.restaurant.VerificationStatus
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runTest

class CreateListingTest : FunSpec({

    // Fresh mocks per test: mockk call counts are cumulative across a spec, so a
    // shared instance would make `verify(exactly = 0)` order-dependent.
    lateinit var listings: RestaurantListingRepository
    lateinit var accounts: AccountRepository
    lateinit var createListing: CreateListing

    beforeTest {
        listings = mockk()
        accounts = mockk()
        createListing = CreateListing(listings = listings, accounts = accounts)
    }

    fun registeredOwner(ownerId: Uuid = Uuid.random()): Uuid {
        coEvery { accounts.findById(ownerId) } returns
            Account.new(email = Email("owner@example.com"), passwordHash = "argon2id\$hash")
        return ownerId
    }

    test("creates and persists an unverified listing") {
        runTest {
            val ownerId = registeredOwner()
            coEvery { listings.save(any()) } coAnswers { firstArg() }

            val listing = createListing.execute(
                name = "Halal Grill",
                address = "123 Main St",
                location = LatLng(40.7128, -74.0060),
                cuisine = Cuisine("mediterranean"),
                isHandCut = true,
                ownerId = ownerId,
            )

            listing.name shouldBe "Halal Grill"
            listing.verificationStatus shouldBe VerificationStatus.UNVERIFIED
            coVerify { listings.save(match { it.ownerId == ownerId && it.location == LatLng(40.7128, -74.0060) }) }
            // Exactly one owner lookup and one save — no re-checking, no double-writes.
            coVerify(exactly = 1) { accounts.findById(ownerId) }
            coVerify(exactly = 1) { listings.save(any()) }
        }
    }

    test("trims name and address before persistence") {
        runTest {
            val ownerId = registeredOwner()
            coEvery { listings.save(any()) } coAnswers { firstArg() }

            createListing.execute(
                name = "  Halal Grill  ",
                address = "  123 Main St  ",
                location = LatLng(1.0, 2.0),
                cuisine = Cuisine("x"),
                isHandCut = false,
                ownerId = ownerId,
            )

            coVerify { listings.save(match { it.name == "Halal Grill" && it.address == "123 Main St" }) }
        }
    }

    test("passes the structured address fields to the saved listing (sc-187)") {
        runTest {
            val ownerId = registeredOwner()
            coEvery { listings.save(any()) } coAnswers { firstArg() }

            val listing = createListing.execute(
                name = "Al-Amir",
                address = "3885 Belt Line Rd",
                city = "Addison",
                province = "TX",
                postal = "75001",
                country = "US",
                location = LatLng(32.953530, -96.849844),
                cuisine = Cuisine("lebanese"),
                isHandCut = true,
                ownerId = ownerId,
            )

            listing.city shouldBe "Addison"
            listing.province shouldBe "TX"
            listing.postal shouldBe "75001"
            listing.country shouldBe "US"
            coVerify { listings.save(match { it.city == "Addison" && it.province == "TX" && it.postal == "75001" && it.country == "US" }) }
        }
    }

    test("passes an explicit alcoholServed flag to the saved listing") {
        runTest {
            val ownerId = registeredOwner()
            coEvery { listings.save(any()) } coAnswers { firstArg() }

            val listing = createListing.execute(
                name = "Halal Grill",
                address = "123 Main St",
                location = LatLng(1.0, 2.0),
                cuisine = Cuisine("x"),
                isHandCut = true,
                ownerId = ownerId,
                alcoholServed = true,
            )

            listing.alcoholServed shouldBe true
            coVerify { listings.save(match { it.alcoholServed }) }
        }
    }

    test("passes an explicit isHandCut flag to the saved listing (sc-42)") {
        runTest {
            val ownerId = registeredOwner()
            coEvery { listings.save(any()) } coAnswers { firstArg() }

            val listing = createListing.execute(
                name = "Halal Grill",
                address = "123 Main St",
                location = LatLng(1.0, 2.0),
                cuisine = Cuisine("x"),
                isHandCut = true,
                ownerId = ownerId,
            )

            listing.isHandCut shouldBe true
            coVerify { listings.save(match { it.isHandCut == true }) }
        }
    }

    test("defaults isHandCut to null (unknown) when not supplied (sc-42)") {
        runTest {
            val ownerId = registeredOwner()
            coEvery { listings.save(any()) } coAnswers { firstArg() }

            val listing = createListing.execute(
                name = "Halal Grill",
                address = "123 Main St",
                location = LatLng(1.0, 2.0),
                cuisine = Cuisine("x"),
                ownerId = ownerId,
            )

            listing.isHandCut shouldBe null
            coVerify { listings.save(match { it.isHandCut == null }) }
        }
    }

    test("passes an explicit isDelivery flag to the saved listing (sc-184)") {
        runTest {
            val ownerId = registeredOwner()
            coEvery { listings.save(any()) } coAnswers { firstArg() }

            val listing = createListing.execute(
                name = "Halal Grill",
                address = "123 Main St",
                location = LatLng(1.0, 2.0),
                cuisine = Cuisine("x"),
                isHandCut = true,
                isDelivery = true,
                ownerId = ownerId,
            )

            listing.isDelivery shouldBe true
            coVerify { listings.save(match { it.isDelivery == true }) }
        }
    }

    test("defaults isDelivery to null (unknown) when not supplied (sc-184)") {
        runTest {
            val ownerId = registeredOwner()
            coEvery { listings.save(any()) } coAnswers { firstArg() }

            val listing = createListing.execute(
                name = "Halal Grill",
                address = "123 Main St",
                location = LatLng(1.0, 2.0),
                cuisine = Cuisine("x"),
                ownerId = ownerId,
            )

            listing.isDelivery shouldBe null
            coVerify { listings.save(match { it.isDelivery == null }) }
        }
    }

    test("defaults alcoholServed to false when not supplied") {
        runTest {
            val ownerId = registeredOwner()
            coEvery { listings.save(any()) } coAnswers { firstArg() }

            val listing = createListing.execute(
                name = "Halal Grill",
                address = "123 Main St",
                location = LatLng(1.0, 2.0),
                cuisine = Cuisine("x"),
                isHandCut = true,
                ownerId = ownerId,
            )

            listing.alcoholServed shouldBe false
            coVerify { listings.save(match { !it.alcoholServed }) }
        }
    }

    test("passes an explicit partial-halal scope, halal items and cross-contamination to the saved listing") {
        runTest {
            val ownerId = registeredOwner()
            coEvery { listings.save(any()) } coAnswers { firstArg() }

            val listing = createListing.execute(
                name = "Halal Grill",
                address = "123 Main St",
                location = LatLng(1.0, 2.0),
                cuisine = Cuisine("x"),
                ownerId = ownerId,
                isHandCut = true,
                halalScope = HalalScope.PARTIALLY_HALAL,
                halalItems = setOf(HalalItem("chicken", true), HalalItem("beef", false)),
                crossContamination = CrossContamination.NO_CROSS_CONTAMINATION,
            )

            listing.isHandCut shouldBe true
            listing.halalScope shouldBe HalalScope.PARTIALLY_HALAL
            listing.halalItems shouldBe setOf(HalalItem("chicken", true), HalalItem("beef", false))
            listing.crossContamination shouldBe CrossContamination.NO_CROSS_CONTAMINATION
            coVerify {
                listings.save(match {
                    it.isHandCut == true &&
                        it.halalScope == HalalScope.PARTIALLY_HALAL &&
                        it.halalItems == setOf(HalalItem("chicken", true), HalalItem("beef", false)) &&
                        it.crossContamination == CrossContamination.NO_CROSS_CONTAMINATION
                })
            }
        }
    }

    test("defaults isHandCut, halalScope, halalItems and crossContamination when not supplied") {
        runTest {
            val ownerId = registeredOwner()
            coEvery { listings.save(any()) } coAnswers { firstArg() }

            val listing = createListing.execute(
                name = "Halal Grill",
                address = "123 Main St",
                location = LatLng(1.0, 2.0),
                cuisine = Cuisine("x"),
                ownerId = ownerId,
            )

            listing.isHandCut shouldBe null
            listing.halalScope shouldBe HalalScope.NOT_DISCLOSED
            listing.halalItems shouldBe emptySet()
            listing.crossContamination shouldBe CrossContamination.UNCERTAIN
            coVerify { listings.save(match { it.crossContamination == CrossContamination.UNCERTAIN }) }
        }
    }

    test("rejects a blank name before touching the owner or repository") {
        runTest {
            coEvery { accounts.findById(any()) } returns null
            coEvery { listings.save(any()) } coAnswers { firstArg() }

            shouldThrow<IllegalArgumentException> {
                createListing.execute(
                    name = "   ",
                    address = "123 Main St",
                    location = LatLng(1.0, 2.0),
                    cuisine = Cuisine("x"),
                    isHandCut = true,
                    ownerId = Uuid.random(),
                )
            }
            coVerify(exactly = 0) { listings.save(any()) }
            coVerify(exactly = 0) { accounts.findById(any()) }
        }
    }

    test("rejects a blank address before any owner lookup or save") {
        runTest {
            coEvery { listings.save(any()) } coAnswers { firstArg() }

            shouldThrow<IllegalArgumentException> {
                createListing.execute(
                    name = "Halal Grill",
                    address = "   ",
                    location = LatLng(1.0, 2.0),
                    cuisine = Cuisine("x"),
                    isHandCut = true,
                    ownerId = Uuid.random(),
                )
            }
            coVerify(exactly = 0) { listings.save(any()) }
            coVerify(exactly = 0) { accounts.findById(any()) }
        }
    }

    test("requires the owning account to exist") {
        runTest {
            val ownerId = Uuid.random()
            coEvery { accounts.findById(ownerId) } returns null // unknown owner
            coEvery { listings.save(any()) } coAnswers { firstArg() }

            val ex = shouldThrow<ListingOwnerNotFoundException> {
                createListing.execute(
                    name = "Ghost Kitchen",
                    address = "123 Main St",
                    location = LatLng(1.0, 2.0),
                    cuisine = Cuisine("x"),
                    isHandCut = true,
                    ownerId = ownerId,
                )
            }

            ex.ownerId shouldBe ownerId
            coVerify(exactly = 0) { listings.save(any()) }
        }
    }
})
