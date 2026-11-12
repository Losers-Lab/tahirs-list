package com.tahirslist.domain.restaurant

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.time.Instant

/**
 * SC-231: a cover photo knows where it came from and what that source allows.
 * The refused cases are the point — an illegal photo must not reach persistence
 * because an adapter got it wrong.
 */
class CoverPhotoTest : FunSpec({

    val now: Instant = Instant.parse("2026-11-10T12:00:00Z")

    fun ownerPhoto() = CoverPhoto(
        sourceKind = PhotoSourceKind.OWNER_UPLOAD,
        storageClass = PhotoStorage.PERMANENT,
        attribution = "Photo by the business",
    )

    fun commonsPhoto(licence: PhotoLicence = PhotoLicence.CC_BY) = CoverPhoto(
        sourceKind = PhotoSourceKind.WIKIMEDIA,
        storageClass = PhotoStorage.PERMANENT,
        attribution = "Photo by A. Person, CC BY 2.0",
        licence = licence,
        author = "A. Person",
        sourceUrl = "https://commons.wikimedia.org/wiki/File:Example.jpg",
    )

    fun placesPhoto(expiresAt: Instant? = now.plusSeconds(86_400)) = CoverPhoto(
        sourceKind = PhotoSourceKind.GOOGLE_PLACES,
        storageClass = PhotoStorage.CACHE,
        attribution = "Photo: Google, A. Reviewer",
        placeId = "ChIJ9dnOL__b1IkR8cQEQ0OEczk",
        photoName = "places/abc/photos/def",
        expiresAt = expiresAt,
    )

    test("an owner photo is permanent and carries no licence") {
        ownerPhoto().licence shouldBe null
        ownerPhoto().isServableAt(now.plusSeconds(10_000_000)) shouldBe true
    }

    test("a free-licensed photo is permanent and keeps its licence") {
        commonsPhoto().licence shouldBe PhotoLicence.CC_BY
        commonsPhoto().isServableAt(now.plusSeconds(10_000_000)) shouldBe true
    }

    test("a Places photo is a cache and stops being servable at its expiry") {
        val photo = placesPhoto()

        photo.storageClass shouldBe PhotoStorage.CACHE
        photo.isServableAt(now) shouldBe true
        photo.isServableAt(now.plusSeconds(86_401)) shouldBe false
    }

    test("a permanent Places photo is refused") {
        shouldThrow<IllegalArgumentException> {
            CoverPhoto(
                sourceKind = PhotoSourceKind.GOOGLE_PLACES,
                storageClass = PhotoStorage.PERMANENT,
                attribution = "Photo: Google",
                placeId = "ChIJ",
            )
        }
    }

    test("a cache with no expiry is refused") {
        shouldThrow<IllegalArgumentException> { placesPhoto(expiresAt = null) }
    }

    test("a free-licensed photo with no licence is refused") {
        shouldThrow<IllegalArgumentException> {
            CoverPhoto(
                sourceKind = PhotoSourceKind.OSM,
                storageClass = PhotoStorage.PERMANENT,
                attribution = "Photo credit",
            )
        }
    }

    test("an owner photo that carries a licence is refused") {
        shouldThrow<IllegalArgumentException> {
            CoverPhoto(
                sourceKind = PhotoSourceKind.OWNER_UPLOAD,
                storageClass = PhotoStorage.PERMANENT,
                attribution = "Photo by the business",
                licence = PhotoLicence.CC_BY,
                author = "Someone",
            )
        }
    }

    test("CC-BY without an author is refused") {
        val thrown = shouldThrow<IllegalArgumentException> {
            commonsPhoto().copy(author = null)
        }
        thrown.message.orEmpty() shouldContain "author"
    }

    test("CC0 needs no author") {
        val photo = commonsPhoto(licence = PhotoLicence.CC0).copy(author = null)
        photo.licence shouldBe PhotoLicence.CC0
        photo.author shouldBe null
    }

    test("a blank attribution is refused for every source") {
        shouldThrow<IllegalArgumentException> { ownerPhoto().copy(attribution = "  ") }
        shouldThrow<IllegalArgumentException> { commonsPhoto().copy(attribution = "") }
        shouldThrow<IllegalArgumentException> { placesPhoto().copy(attribution = " ") }
    }

    test("a Places photo without its place id is refused") {
        shouldThrow<IllegalArgumentException> { placesPhoto().copy(placeId = null) }
    }
})
