package com.tahirslist.application.listing

import com.tahirslist.application.audit.AuditRecorder
import com.tahirslist.application.favorite.ListingNotFoundException
import com.tahirslist.application.image.ImagePort
import com.tahirslist.application.image.ImageVariant
import com.tahirslist.application.image.InMemoryImagePort
import com.tahirslist.application.image.StoredImage
import com.tahirslist.application.verification.NotListingOwnerException
import com.tahirslist.domain.account.Role
import com.tahirslist.domain.restaurant.Cuisine
import com.tahirslist.domain.restaurant.CrossContamination
import com.tahirslist.domain.restaurant.HalalScope
import com.tahirslist.domain.restaurant.LatLng
import com.tahirslist.domain.restaurant.RestaurantListing
import com.tahirslist.domain.restaurant.VerificationStatus
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runTest

/**
 * SC-205: [AddListingImage] authorizes the **actor** (owner or admin, D-2),
 * persists every variant, stamps the photo version (WI-07) and records an
 * admin override (INV-12).
 */
class AddListingImageTest : FunSpec({

    lateinit var listings: RestaurantListingRepository
    lateinit var images: InMemoryImagePort
    lateinit var audit: AuditRecorder
    lateinit var useCase: AddListingImage

    beforeTest {
        listings = mockk()
        images = InMemoryImagePort()
        audit = mockk(relaxed = true)
        useCase = AddListingImage(listings = listings, images = images, audit = audit)
    }

    fun owner(role: Role = Role.RESTAURANT_OWNER): Actor = Actor(Uuid.random(), role)

    fun existingListing(
        id: Uuid = Uuid.random(),
        ownerId: Uuid? = Uuid.random(),
        imageVersion: Long = 0,
    ): RestaurantListing = RestaurantListing.fromStorage(
        id = id,
        name = "Halal Grill",
        address = "123 Main St",
        location = LatLng(40.7128, -74.0060),
        cuisine = Cuisine("mediterranean"),
        isHandCut = true,
        isDelivery = true,
        ownerId = ownerId,
        brandId = null,
        provenance = null,
        verificationStatus = VerificationStatus.UNVERIFIED,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        halalScope = HalalScope.NOT_DISCLOSED,
        crossContamination = CrossContamination.UNCERTAIN,
        imageVersion = imageVersion,
    )

    fun jpeg(width: Int = 3000, height: Int = 2000): ByteArray {
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.color = Color(40, 90, 180)
        g.fillRect(0, 0, width, height)
        g.dispose()
        val out = ByteArrayOutputStream()
        ImageIO.write(img, "jpeg", out)
        return out.toByteArray()
    }

    test("owner can set the listing's hero image; FULL + every thumb is stored") {
        runTest {
            val actor = owner()
            val listing = existingListing(ownerId = actor.accountId)
            coEvery { listings.findById(listing.id) } returns listing
            coEvery { listings.bumpImageVersion(listing.id) } returns 1_760_000_000_001L

            useCase.execute(listing.id, actor, StoredImage(jpeg(), "image/jpeg"))

            val full = images.load(listing.id, ImageVariant.FULL).shouldNotBeNull()
            full.contentType shouldBe "image/jpeg"
            ImageVariant.thumbnailVariants.forEach { images.load(listing.id, it).shouldNotBeNull() }
            coVerify { listings.bumpImageVersion(listing.id) }
            verify(exactly = 0) { audit.imageSet(any(), any(), any(), any()) }
        }
    }

    test("a client-supplied upload label never reaches storage (the stored type is derived)") {
        runTest {
            val actor = owner()
            val listing = existingListing(ownerId = actor.accountId)
            coEvery { listings.findById(listing.id) } returns listing
            coEvery { listings.bumpImageVersion(listing.id) } returns 2L

            useCase.execute(listing.id, actor, StoredImage(jpeg(), "text/html"))

            images.load(listing.id, ImageVariant.FULL).shouldNotBeNull().contentType shouldBe "image/jpeg"
        }
    }

    test("re-uploading overwrites the earlier hero (last-write-wins)") {
        runTest {
            val actor = owner()
            val listing = existingListing(ownerId = actor.accountId)
            coEvery { listings.findById(listing.id) } returns listing
            coEvery { listings.bumpImageVersion(listing.id) } returns 1_760_000_000_002L

            useCase.execute(listing.id, actor, StoredImage(jpeg(400, 300), "image/jpeg"))
            val second = jpeg(2000, 1500)
            useCase.execute(listing.id, actor, StoredImage(second, "image/jpeg"))

            images.load(listing.id, ImageVariant.FULL)!!.bytes.contentEquals(second) shouldBe true
        }
    }

    test("a non-owner who is not an admin is unauthorized (403) and never persists or bumps") {
        runTest {
            val listing = existingListing(ownerId = Uuid.random())
            coEvery { listings.findById(listing.id) } returns listing

            val ex = shouldThrow<NotListingOwnerException> {
                useCase.execute(listing.id, Actor(Uuid.random(), Role.USER), StoredImage(jpeg(), "image/jpeg"))
            }
            ex.listingId shouldBe listing.id
            images.load(listing.id, ImageVariant.FULL) shouldBe null
            coVerify(exactly = 0) { listings.bumpImageVersion(any()) }
            verify(exactly = 0) { audit.imageSet(any(), any(), any(), any()) }
        }
    }

    test("an admin may replace the photo of a listing they do not own, and the override is audited") {
        runTest {
            val admin = Actor(Uuid.random(), Role.ADMIN)
            val listing = existingListing(ownerId = Uuid.random(), imageVersion = 7)
            coEvery { listings.findById(listing.id) } returns listing
            coEvery { listings.bumpImageVersion(listing.id) } returns 99L

            useCase.execute(listing.id, admin, StoredImage(jpeg(), "image/jpeg"))

            images.load(listing.id, ImageVariant.FULL).shouldNotBeNull()
            verify(exactly = 1) { audit.imageSet(listing.id, admin, 7L, 99L) }
        }
    }

    test("an admin may add the photo of an ownerless listing") {
        runTest {
            val admin = Actor(Uuid.random(), Role.ADMIN)
            val listing = existingListing(ownerId = null, imageVersion = 0)
            coEvery { listings.findById(listing.id) } returns listing
            coEvery { listings.bumpImageVersion(listing.id) } returns 1_760_000_000_003L

            useCase.execute(listing.id, admin, StoredImage(jpeg(), "image/jpeg"))

            images.load(listing.id, ImageVariant.FULL).shouldNotBeNull()
            verify(exactly = 1) { audit.imageSet(listing.id, admin, 0L, 1_760_000_000_003L) }
        }
    }

    test("unknown listing is not found (404)") {
        runTest {
            val id = Uuid.random()
            coEvery { listings.findById(id) } returns null

            val ex = shouldThrow<ListingNotFoundException> {
                useCase.execute(id, Actor(Uuid.random(), Role.ADMIN), StoredImage(jpeg(), "image/jpeg"))
            }
            ex.listingId shouldBe id
            images.load(id, ImageVariant.FULL) shouldBe null
        }
    }

    test("undecodable upload throws before anything is persisted or bumped") {
        runTest {
            val actor = owner()
            val listing = existingListing(ownerId = actor.accountId)
            coEvery { listings.findById(listing.id) } returns listing

            shouldThrow<IllegalArgumentException> {
                useCase.execute(listing.id, actor, StoredImage("garbage".toByteArray(), "image/jpeg"))
            }
            images.load(listing.id, ImageVariant.FULL) shouldBe null
            ImageVariant.thumbnailVariants.forEach { images.load(listing.id, it) shouldBe null }
            coVerify(exactly = 0) { listings.bumpImageVersion(any()) }
        }
    }
})
