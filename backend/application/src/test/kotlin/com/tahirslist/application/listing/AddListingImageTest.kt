package com.tahirslist.application.listing

import com.tahirslist.application.favorite.ListingNotFoundException
import com.tahirslist.application.image.ImagePort
import com.tahirslist.application.image.ImageVariant
import com.tahirslist.application.image.InMemoryImagePort
import com.tahirslist.application.image.StoredImage
import com.tahirslist.application.verification.NotListingOwnerException
import com.tahirslist.domain.restaurant.CrossContamination
import com.tahirslist.domain.restaurant.Cuisine
import com.tahirslist.domain.restaurant.HalalScope
import com.tahirslist.domain.restaurant.LatLng
import com.tahirslist.domain.restaurant.RestaurantListing
import com.tahirslist.domain.restaurant.VerificationStatus
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runTest

/**
 * sc-53 owner add/replace hero image: [AddListingImage] owner-guards an upload
 * and persists every variant (FULL + sc-183 thumbs) via the [ImagePort].
 * Non-owners get 403 semantics, missing listings 404. The underlying variant
 * generation is already pinned by IngestHeroImageTest; here we pin the
 * authorization flow + that a successful upload overwrites (last-write-wins).
 */
class AddListingImageTest : FunSpec({

    class FakePhotos : com.tahirslist.application.photo.CoverPhotoRepository {
        val saved = mutableMapOf<Uuid, com.tahirslist.domain.restaurant.CoverPhoto>()
        override suspend fun find(listingId: Uuid) = saved[listingId]
        override suspend fun save(listingId: Uuid, photo: com.tahirslist.domain.restaurant.CoverPhoto) {
            saved[listingId] = photo
        }
        override suspend fun delete(listingId: Uuid) {
            saved.remove(listingId)
        }
    }

    lateinit var listings: RestaurantListingRepository
    lateinit var images: InMemoryImagePort
    lateinit var photos: FakePhotos
    lateinit var useCase: AddListingImage

    beforeTest {
        listings = mockk()
        images = InMemoryImagePort()
        photos = FakePhotos()
        useCase = AddListingImage(listings = listings, images = images, photos = photos)
    }

    fun existingListing(
        id: Uuid = Uuid.random(),
        ownerId: Uuid = Uuid.random(),
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
            val ownerId = Uuid.random()
            val listing = existingListing(ownerId = ownerId)
            coEvery { listings.findById(listing.id) } returns listing

            useCase.execute(listing.id, ownerId, StoredImage(jpeg(), "image/jpeg"))

            val full = images.load(listing.id, ImageVariant.FULL).shouldNotBeNull()
            full.contentType shouldBe "image/jpeg"
            ImageVariant.thumbnailVariants.forEach { images.load(listing.id, it).shouldNotBeNull() }
        }
    }

    test("an owner upload writes the OWNER_UPLOAD credit row (INV-14 depends on it)") {
        runTest {
            val ownerId = Uuid.random()
            val listing = existingListing(ownerId = ownerId)
            coEvery { listings.findById(listing.id) } returns listing

            useCase.execute(listing.id, ownerId, StoredImage(jpeg(), "image/jpeg"))

            val photo = photos.saved[listing.id].shouldNotBeNull()
            photo.sourceKind shouldBe com.tahirslist.domain.restaurant.PhotoSourceKind.OWNER_UPLOAD
            photo.storageClass shouldBe com.tahirslist.domain.restaurant.PhotoStorage.PERMANENT
            photo.licence shouldBe null
        }
    }

    test("a client-supplied upload label never reaches storage (the stored type is derived)") {
        runTest {
            val ownerId = Uuid.random()
            val listing = existingListing(ownerId = ownerId)
            coEvery { listings.findById(listing.id) } returns listing

            useCase.execute(listing.id, ownerId, StoredImage(jpeg(), "text/html"))

            images.load(listing.id, ImageVariant.FULL).shouldNotBeNull().contentType shouldBe "image/jpeg"
        }
    }

    test("re-uploading overwrites the earlier hero (last-write-wins)") {
        runTest {
            val ownerId = Uuid.random()
            val listing = existingListing(ownerId = ownerId)
            coEvery { listings.findById(listing.id) } returns listing

            useCase.execute(listing.id, ownerId, StoredImage(jpeg(400, 300), "image/jpeg"))
            val second = jpeg(2000, 1500)
            useCase.execute(listing.id, ownerId, StoredImage(second, "image/jpeg"))

            images.load(listing.id, ImageVariant.FULL)!!.bytes.contentEquals(second) shouldBe true
        }
    }

    test("a non-owner is unauthorized (403) and never persists") {
        runTest {
            val listing = existingListing(ownerId = Uuid.random()) // owner is someone else
            coEvery { listings.findById(listing.id) } returns listing

            val ex = shouldThrow<NotListingOwnerException> {
                useCase.execute(listing.id, Uuid.random(), StoredImage(jpeg(), "image/jpeg"))
            }
            ex.listingId shouldBe listing.id
            images.load(listing.id, ImageVariant.FULL) shouldBe null
        }
    }

    test("unknown listing is not found (404)") {
        runTest {
            val id = Uuid.random()
            coEvery { listings.findById(id) } returns null

            val ex = shouldThrow<ListingNotFoundException> {
                useCase.execute(id, Uuid.random(), StoredImage(jpeg(), "image/jpeg"))
            }
            ex.listingId shouldBe id
            images.load(id, ImageVariant.FULL) shouldBe null
        }
    }

    test("undecodable upload throws before anything is persisted") {
        runTest {
            val ownerId = Uuid.random()
            val listing = existingListing(ownerId = ownerId)
            coEvery { listings.findById(listing.id) } returns listing

            shouldThrow<IllegalArgumentException> {
                useCase.execute(listing.id, ownerId, StoredImage("garbage".toByteArray(), "image/jpeg"))
            }
            images.load(listing.id, ImageVariant.FULL) shouldBe null
            ImageVariant.thumbnailVariants.forEach { images.load(listing.id, it) shouldBe null }
        }
    }
})
