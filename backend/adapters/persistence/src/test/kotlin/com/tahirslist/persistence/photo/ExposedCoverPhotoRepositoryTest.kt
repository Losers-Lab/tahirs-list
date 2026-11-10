package com.tahirslist.persistence.photo

import com.tahirslist.domain.restaurant.CoverPhoto
import com.tahirslist.domain.restaurant.LatLng
import com.tahirslist.domain.restaurant.PhotoLicence
import com.tahirslist.domain.restaurant.PhotoSourceKind
import com.tahirslist.domain.restaurant.PhotoStorage
import com.tahirslist.domain.restaurant.RestaurantListing
import com.tahirslist.persistence.PostgisTestDatabase
import com.tahirslist.persistence.account.ExposedAccountRepository
import com.tahirslist.persistence.count
import com.tahirslist.persistence.execute
import com.tahirslist.persistence.listing.ExposedRestaurantListingRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.time.Instant
import kotlinx.coroutines.test.runTest
import kotlin.uuid.Uuid

/**
 * SC-231 WI-15: the cover-photo row round-trips, and a refreshed cache replaces
 * the row instead of accumulating history. The listing read attaches it, which is
 * what lets the API carry credit without a second query.
 */
class ExposedCoverPhotoRepositoryTest : FunSpec() {

    private val postgis = PostgisTestDatabase()
    private lateinit var photos: ExposedCoverPhotoRepository
    private lateinit var listings: ExposedRestaurantListingRepository
    private lateinit var accounts: ExposedAccountRepository

    init {
        beforeSpec {
            postgis.start()
            photos = ExposedCoverPhotoRepository(postgis.database)
            listings = ExposedRestaurantListingRepository(postgis.database)
            accounts = ExposedAccountRepository(postgis.database)
        }
        afterSpec { postgis.stop() }

        beforeTest {
            postgis.execute("TRUNCATE listing_cover_photos, restaurant_listings CASCADE")
        }

        suspend fun listing(name: String = "Noor Grill"): RestaurantListing = RestaurantListing.new(
            name = name,
            address = "1 Test St",
            location = LatLng(43.7, -79.4),
            cuisine = com.tahirslist.domain.restaurant.Cuisine("middle eastern"),
            ownerId = accounts.save(
                com.tahirslist.domain.account.Account.new(
                    com.tahirslist.domain.account.Email("photo-${Uuid.random()}@example.com"),
                    "argon2id\$h",
                ),
            ).id,
        )

        fun places(expiresAt: Instant = Instant.parse("2026-12-10T12:00:00Z")) = CoverPhoto(
            sourceKind = PhotoSourceKind.GOOGLE_PLACES,
            storageClass = PhotoStorage.CACHE,
            attribution = "Google Maps, A. Reviewer",
            author = "A. Reviewer",
            sourceUrl = "https://www.google.com/maps/place/?q=place_id:ChIJ",
            placeId = "ChIJ",
            photoName = "places/abc/photos/def",
            expiresAt = expiresAt,
            contentHash = "a".repeat(64),
        )

        test("a Places cache round-trips with its expiry and place id") {
            runTest {
                val saved = listings.save(listing())

                photos.save(saved.id, places())

                val found = photos.find(saved.id).shouldBeInstanceOf<CoverPhoto>()
                found.storageClass shouldBe PhotoStorage.CACHE
                found.expiresAt shouldBe Instant.parse("2026-12-10T12:00:00Z")
                found.placeId shouldBe "ChIJ"
                found.licence shouldBe null
            }
        }

        test("a free-licensed photo keeps its licence and needs no expiry") {
            runTest {
                val saved = listings.save(listing())

                photos.save(
                    saved.id,
                    CoverPhoto(
                        sourceKind = PhotoSourceKind.WIKIMEDIA,
                        storageClass = PhotoStorage.PERMANENT,
                        attribution = "A. Person / Wikimedia Commons (CC BY 4.0)",
                        licence = PhotoLicence.CC_BY,
                        author = "A. Person",
                        sourceUrl = "https://commons.wikimedia.org/wiki/File:X.jpg",
                    ),
                )

                val found = photos.find(saved.id).shouldBeInstanceOf<CoverPhoto>()
                found.licence shouldBe PhotoLicence.CC_BY
                found.expiresAt shouldBe null
            }
        }

        test("saving again replaces the row — one photo per listing") {
            runTest {
                val saved = listings.save(listing())
                photos.save(saved.id, places())

                photos.save(
                    saved.id,
                    CoverPhoto(
                        sourceKind = PhotoSourceKind.OWNER_UPLOAD,
                        storageClass = PhotoStorage.PERMANENT,
                        attribution = "Photo by the business",
                    ),
                )

                postgis.count("SELECT count(*) FROM listing_cover_photos") shouldBe 1
                photos.find(saved.id)!!.sourceKind shouldBe PhotoSourceKind.OWNER_UPLOAD
            }
        }

        test("the listing read carries the cover photo") {
            runTest {
                val saved = listings.save(listing())
                photos.save(saved.id, places())

                val read = listings.findById(saved.id).shouldBeInstanceOf<RestaurantListing>()
                read.coverPhoto.shouldBeInstanceOf<CoverPhoto>().placeId shouldBe "ChIJ"
            }
        }

        test("deleting a listing deletes its photo") {
            runTest {
                val saved = listings.save(listing())
                photos.save(saved.id, places())

                postgis.execute("DELETE FROM restaurant_listings WHERE id = '${saved.id}'")

                postgis.count("SELECT count(*) FROM listing_cover_photos") shouldBe 0
            }
        }
    }
}
