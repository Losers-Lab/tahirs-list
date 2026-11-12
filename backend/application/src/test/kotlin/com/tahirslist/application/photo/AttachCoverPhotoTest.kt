package com.tahirslist.application.photo

import com.tahirslist.application.image.ImageFetchException
import com.tahirslist.application.image.ImageFetcher
import com.tahirslist.application.image.InMemoryImagePort
import com.tahirslist.application.image.IngestHeroImage
import com.tahirslist.domain.restaurant.CoverPhoto
import com.tahirslist.domain.restaurant.Cuisine
import com.tahirslist.domain.restaurant.LatLng
import com.tahirslist.domain.restaurant.PhotoLicence
import com.tahirslist.domain.restaurant.PhotoSourceKind
import com.tahirslist.domain.restaurant.PhotoStorage
import com.tahirslist.domain.restaurant.RestaurantListing
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.time.Instant
import javax.imageio.ImageIO
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * SC-231 UC-03: precedence, licences and the cache rule, proven without a network.
 *
 * The fetch is counted on purpose: "refused before bytes" is only true if the
 * fetcher was never called.
 */
class AttachCoverPhotoTest : FunSpec({

    fun listing(name: String = "Noor Grill"): RestaurantListing = RestaurantListing.new(
        name = name,
        address = "1 Test St",
        location = LatLng(43.7, -79.4),
        cuisine = Cuisine("middle eastern"),
        ownerId = Uuid.random(),
    )

    fun png(): ByteArray {
        val image = BufferedImage(500, 400, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.color = Color(120, 40, 40)
        g.fillRect(0, 0, 500, 400)
        g.dispose()
        return ByteArrayOutputStream().use { out ->
            ImageIO.write(image, "png", out)
            out.toByteArray()
        }
    }

    class FakePhotos(private val existing: MutableMap<Uuid, CoverPhoto> = mutableMapOf()) : CoverPhotoRepository {
        override suspend fun find(listingId: Uuid): CoverPhoto? = existing[listingId]

        override suspend fun save(listingId: Uuid, photo: CoverPhoto) {
            existing[listingId] = photo
        }

        override suspend fun delete(listingId: Uuid) {
            existing.remove(listingId)
        }
    }

    class FakeSource(
        private val free: List<PhotoCandidate> = emptyList(),
        private val places: PhotoCandidate? = null,
    ) : PhotoCandidateSource {
        override suspend fun freeCandidates(listing: RestaurantListing): List<PhotoCandidate> = free

        override suspend fun placesCandidate(listing: RestaurantListing, placeId: String?): PhotoCandidate? =
            places

        var asked = 0
            private set
    }

    fun freeCandidate(
        kind: PhotoSourceKind = PhotoSourceKind.WIKIMEDIA,
        licence: PhotoLicence? = PhotoLicence.CC_BY,
        author: String? = "A. Person",
        url: String? = "https://commons.example/photo.jpg",
    ) = PhotoCandidate(
        sourceKind = kind,
        fetchUrl = url,
        attribution = "A. Person / Wikimedia Commons",
        licence = licence,
        author = author,
        sourceUrl = "https://commons.example/File:Photo.jpg",
    )

    fun placesCandidate(placeId: String = "ChIJ9dnOL__b1IkR8cQEQ0OEczk") = PhotoCandidate(
        sourceKind = PhotoSourceKind.GOOGLE_PLACES,
        fetchUrl = "https://places.example/media?key=secret",
        attribution = "Google Maps, Md Abu Rihan",
        author = "Md Abu Rihan",
        sourceUrl = "https://www.google.com/maps/place/?q=place_id:$placeId",
        placeId = placeId,
        photoName = "places/abc/photos/def",
    )

    val now = Instant.parse("2026-11-10T12:00:00Z")
    val clock = object : Clock {
        override fun now(): kotlin.time.Instant =
            kotlin.time.Instant.fromEpochMilliseconds(now.toEpochMilli())
    }

    val bytes = png()
    var fetches = 0
    val fetcher = ImageFetcher { fetches++ ; bytes }

    fun pass(
        repo: FakePhotos,
        vararg sources: PhotoCandidateSource,
        fetcher: ImageFetcher = ImageFetcher { fetches++ ; bytes },
    ) = AttachCoverPhoto(
        photos = repo,
        sources = sources.toList(),
        fetcher = fetcher,
        hero = IngestHeroImage(InMemoryImagePort()),
        clock = clock,
    )

    test("an owner photo is never touched, even when a replace is asked for") {
        val target = listing()
        val repo = FakePhotos(mutableMapOf(target.id to CoverPhoto(
            sourceKind = PhotoSourceKind.OWNER_UPLOAD,
            storageClass = PhotoStorage.PERMANENT,
            attribution = "Photo by the business",
        )))
        var calls = 0
        val network = object : PhotoCandidateSource {
            override suspend fun freeCandidates(listing: RestaurantListing): List<PhotoCandidate> {
                calls++
                return listOf(freeCandidate())
            }
            override suspend fun placesCandidate(listing: RestaurantListing, placeId: String?) = placesCandidate()
        }

        val result = pass(repo, network).execute(pass(repo, network).preview(listOf(target)), replace = true)

        result.outcomes.single().shouldBeInstanceOf<PhotoOutcome.Skipped>()
        result.skipped shouldBe 1
        calls shouldBe 0
        repo.find(target.id)!!.sourceKind shouldBe PhotoSourceKind.OWNER_UPLOAD
    }

    test("a free-licensed photo wins over Places and is stored permanently") {
        val target = listing()
        val repo = FakePhotos()
        val source = FakeSource(free = listOf(freeCandidate()), places = placesCandidate())

        val result = pass(repo, source).execute(pass(repo, source).preview(listOf(target)))

        val attached = result.outcomes.single().shouldBeInstanceOf<PhotoOutcome.Attached>()
        attached.photo.sourceKind shouldBe PhotoSourceKind.WIKIMEDIA
        attached.photo.storageClass shouldBe PhotoStorage.PERMANENT
        attached.photo.licence shouldBe PhotoLicence.CC_BY
        attached.photo.expiresAt shouldBe null
        repo.find(target.id)!!.sourceKind shouldBe PhotoSourceKind.WIKIMEDIA
    }

    test("a candidate with no licence is refused before its bytes are fetched") {
        val target = listing()
        val repo = FakePhotos()
        val source = FakeSource(free = emptyList(), places = null)
        val unlicensed = object : PhotoCandidateSource {
            override suspend fun freeCandidates(listing: RestaurantListing) =
                listOf(freeCandidate(kind = PhotoSourceKind.OSM, licence = null))
        }
        var count = 0
        val result = pass(repo, unlicensed, fetcher = ImageFetcher { count++; bytes })
            .execute(pass(repo, unlicensed).preview(listOf(target)))

        result.outcomes.single().shouldBeInstanceOf<PhotoOutcome.Rejected>()
        count shouldBe 0
        repo.find(target.id) shouldBe null
    }

    test("CC-BY without an author is refused") {
        val target = listing()
        val source = object : PhotoCandidateSource {
            override suspend fun freeCandidates(listing: RestaurantListing) =
                listOf(freeCandidate(author = " "))
        }
        val result = pass(FakePhotos(), source).execute(pass(FakePhotos(), source).preview(listOf(target)))

        result.outcomes.single().shouldBeInstanceOf<PhotoOutcome.Rejected>()
            .reasons.single() shouldBe "WIKIMEDIA: attribution"
    }

    test("a Places photo is a cache with a thirty-day expiry and keeps its place id") {
        val target = listing()
        val source = FakeSource(places = placesCandidate())
        val result = pass(FakePhotos(), source).execute(pass(FakePhotos(), source).preview(listOf(target)))

        val photo = result.outcomes.single().shouldBeInstanceOf<PhotoOutcome.Attached>().photo
        photo.sourceKind shouldBe PhotoSourceKind.GOOGLE_PLACES
        photo.storageClass shouldBe PhotoStorage.CACHE
        photo.expiresAt shouldBe now.plusSeconds(30 * 86_400)
        photo.placeId shouldBe "ChIJ9dnOL__b1IkR8cQEQ0OEczk"
        photo.licence shouldBe null
    }

    test("nothing anywhere is a gap, not a fabricated photo") {
        val target = listing()
        val result = pass(FakePhotos(), FakeSource()).execute(pass(FakePhotos(), FakeSource()).preview(listOf(target)))

        result.outcomes.single().shouldBeInstanceOf<PhotoOutcome.NoPhoto>()
        result.missing shouldBe 1
    }

    test("an unexpired cache is skipped; an expired one is refreshed") {
        val target = listing()
        val fresh = CoverPhoto(
            sourceKind = PhotoSourceKind.GOOGLE_PLACES,
            storageClass = PhotoStorage.CACHE,
            attribution = "Google Maps",
            placeId = "ChIJ",
            photoName = "places/abc/photos/def",
            expiresAt = now.plusSeconds(86_400),
        )
        val stale = fresh.copy(expiresAt = now.minusSeconds(60))

        val freshRepo = FakePhotos(mutableMapOf(target.id to fresh))
        val freshSource = FakeSource(places = placesCandidate())
        val freshRun = pass(freshRepo, freshSource).execute(pass(freshRepo, freshSource).preview(listOf(target)))
        freshRun.outcomes.single().shouldBeInstanceOf<PhotoOutcome.Skipped>()

        val staleRepo = FakePhotos(mutableMapOf(target.id to stale))
        val staleSource = FakeSource(places = placesCandidate())
        val staleRun = pass(staleRepo, staleSource).execute(pass(staleRepo, staleSource).preview(listOf(target)))
        val refreshed = staleRun.outcomes.single().shouldBeInstanceOf<PhotoOutcome.Attached>()
        refreshed.photo.expiresAt shouldBe now.plusSeconds(30 * 86_400)
    }

    test("a fetch failure is reported and the old photo is left in place") {
        val target = listing()
        val current = CoverPhoto(
            sourceKind = PhotoSourceKind.GOOGLE_PLACES,
            storageClass = PhotoStorage.CACHE,
            attribution = "Google Maps",
            placeId = "ChIJ",
            expiresAt = now.minusSeconds(60),
        )
        val repo = FakePhotos(mutableMapOf(target.id to current))
        val source = FakeSource(places = placesCandidate())
        val failing = ImageFetcher { throw ImageFetchException("404") }

        val result = pass(repo, source, fetcher = failing)
            .execute(pass(repo, source, fetcher = failing).preview(listOf(target)))

        result.outcomes.single().shouldBeInstanceOf<PhotoOutcome.Rejected>()
        repo.find(target.id) shouldBe current
    }

    test("a preview of an empty catalogue is a review of no rows") {
        val review = pass(FakePhotos()).preview(emptyList())
        review.rows shouldHaveSize 0
    }

    test("a failing fetch never puts the keyed url in the reported reason") {
        val target = listing()
        val source = FakeSource(places = placesCandidate())
        val leaking = ImageFetcher {
            throw ImageFetchException(
                "non-2xx https://places.example/media?maxWidthPx=1600&key=AIzaSECRET: HTTP 429",
            )
        }

        val result = pass(FakePhotos(), source, fetcher = leaking)
            .execute(pass(FakePhotos(), source, fetcher = leaking).preview(listOf(target)))

        val rejected = result.outcomes.single().shouldBeInstanceOf<PhotoOutcome.Rejected>()
        val text = rejected.reasons.joinToString(" ")
        text shouldNotContain "AIzaSECRET"
        text shouldContain "places.example/media"
    }

    test("a replace that cannot credit a photo removes the uncredited bytes") {
        val target = listing()
        var deleted = false
        val pass = AttachCoverPhoto(
            photos = FakePhotos(),
            sources = listOf(FakeSource()),
            fetcher = ImageFetcher { bytes },
            hero = IngestHeroImage(InMemoryImagePort()),
            clock = clock,
            hasBytes = { true },
            deleteBytes = { deleted = true },
        )

        val result = pass.execute(pass.preview(listOf(target)), replace = true)

        deleted shouldBe true
        val rejected = result.outcomes.single().shouldBeInstanceOf<PhotoOutcome.Rejected>()
        rejected.reasons.joinToString(" ") shouldContain "uncredited image already stored was removed"
    }
})
