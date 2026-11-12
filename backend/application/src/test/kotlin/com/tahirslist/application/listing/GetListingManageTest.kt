package com.tahirslist.application.listing

import com.tahirslist.domain.account.Role
import com.tahirslist.domain.restaurant.Cuisine
import com.tahirslist.domain.restaurant.LatLng
import com.tahirslist.domain.restaurant.RestaurantListing
import com.tahirslist.domain.restaurant.VerificationStatus
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlin.time.Clock
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runTest

/**
 * SC-205: the read the browser uses to decide whether to show the control.
 * It must agree with the write policy exactly (`owner || ADMIN`), and answer
 * `null` for a listing that does not exist.
 */
class GetListingManageTest : FunSpec({

    lateinit var listings: RestaurantListingRepository
    lateinit var useCase: GetListingManage

    beforeTest {
        listings = mockk()
        useCase = GetListingManage(listings)
    }

    fun listing(ownerId: Uuid?): RestaurantListing = RestaurantListing.fromStorage(
        id = Uuid.random(),
        name = "Halal Grill",
        address = "123 Main St",
        location = LatLng(40.7, -74.0),
        cuisine = Cuisine("mediterranean"),
        ownerId = ownerId,
        brandId = null,
        provenance = null,
        verificationStatus = VerificationStatus.UNVERIFIED,
        createdAt = Clock.System.now(),
    )

    test("the owner gets canManage = true") {
        runTest {
            val ownerId = Uuid.random()
            val listing = listing(ownerId)
            coEvery { listings.findById(listing.id) } returns listing

            useCase.execute(listing.id, Actor(ownerId, Role.RESTAURANT_OWNER))?.canManage shouldBe true
        }
    }

    test("an admin gets canManage = true on a foreign listing") {
        runTest {
            val listing = listing(Uuid.random())
            coEvery { listings.findById(listing.id) } returns listing

            useCase.execute(listing.id, Actor(Uuid.random(), Role.ADMIN))?.canManage shouldBe true
        }
    }

    test("a signed-in non-owner gets canManage = false") {
        runTest {
            val listing = listing(Uuid.random())
            coEvery { listings.findById(listing.id) } returns listing

            useCase.execute(listing.id, Actor(Uuid.random(), Role.USER))?.canManage shouldBe false
        }
    }

    test("an unknown listing is null (404 at the edge)") {
        runTest {
            val id = Uuid.random()
            coEvery { listings.findById(id) } returns null

            useCase.execute(id, Actor(Uuid.random(), Role.ADMIN)) shouldBe null
        }
    }
})
