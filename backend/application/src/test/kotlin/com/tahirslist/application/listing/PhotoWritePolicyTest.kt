package com.tahirslist.application.listing

import com.tahirslist.domain.account.Role
import com.tahirslist.domain.restaurant.Cuisine
import com.tahirslist.domain.restaurant.LatLng
import com.tahirslist.domain.restaurant.RestaurantListing
import com.tahirslist.domain.restaurant.VerificationStatus
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * SC-205 AC-6: the one place that decides who may write a listing photo.
 *
 * `role == ADMIN || accountId == ownerId` — the admin case must not depend on
 * ownership, and the ownership case must not depend on the role.
 */
class PhotoWritePolicyTest : FunSpec({

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

    test("the owner may manage their own listing") {
        val ownerId = Uuid.random()
        PhotoWritePolicy.mayManage(Actor(ownerId, Role.RESTAURANT_OWNER), listing(ownerId)) shouldBe true
    }

    test("a signed-in user who is not the owner may not manage it") {
        PhotoWritePolicy.mayManage(Actor(Uuid.random(), Role.USER), listing(Uuid.random())) shouldBe false
    }

    test("an admin may manage a listing they do not own") {
        PhotoWritePolicy.mayManage(Actor(Uuid.random(), Role.ADMIN), listing(Uuid.random())) shouldBe true
    }

    test("an admin may manage an ownerless listing") {
        PhotoWritePolicy.mayManage(Actor(Uuid.random(), Role.ADMIN), listing(null)) shouldBe true
    }

    test("a non-admin may not manage an ownerless listing") {
        PhotoWritePolicy.mayManage(Actor(Uuid.random(), Role.USER), listing(null)) shouldBe false
        PhotoWritePolicy.mayManage(Actor(Uuid.random(), Role.VERIFICATION_COMMITTEE), listing(null)) shouldBe false
    }

    test("an admin who also owns the listing is still allowed") {
        val id = Uuid.random()
        PhotoWritePolicy.mayManage(Actor(id, Role.ADMIN), listing(id)) shouldBe true
    }
})
