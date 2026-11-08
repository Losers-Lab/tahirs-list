package com.tahirslist.ingestion

import com.tahirslist.certifiers.core.CertifierPage
import com.tahirslist.certifiers.core.HandCutDeclaration
import com.tahirslist.certifiers.core.SourceSnapshot
import com.tahirslist.certifiers.hfsaa.HfsaaAddress
import com.tahirslist.certifiers.hfsaa.HfsaaEntry
import com.tahirslist.certifiers.hfsaa.HfsaaEntryType
import com.tahirslist.certifiers.hfsaa.HfsaaLocation
import com.tahirslist.certifiers.hfsaa.HfsaaProfile
import com.tahirslist.certifiers.hma.HmaAddress
import com.tahirslist.certifiers.hma.HmaCertifiedOutlet
import com.tahirslist.certifiers.hma.HmaProfile
import com.tahirslist.certifiers.hms.HmsAddress
import com.tahirslist.certifiers.hms.HmsCategory
import com.tahirslist.certifiers.hms.HmsCertificationStatus
import com.tahirslist.certifiers.hms.HmsDirectory
import com.tahirslist.certifiers.hms.HmsEstablishment
import com.tahirslist.certifiers.hms.HmsLocation
import com.tahirslist.certifiers.hms.HmsProfile
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.time.LocalDate

private fun snapshot(certifier: String) = SourceSnapshot(
    certifierId = certifier,
    url = "https://example.test/$certifier",
    status = 200,
    contentType = "application/json",
    etag = null,
    lastModified = null,
    headers = emptyMap(),
    fetchedAt = Instant.parse("2026-11-08T00:00:00Z"),
    fromCache = false,
    body = "{}",
)

private fun hmsRecord(
    id: String = "1",
    name: String = "Test Kitchen",
    city: String? = "Dallas",
    location: HmsLocation? = HmsLocation(32.7767, -96.7970),
) = HmsEstablishment(
    id = id,
    name = name,
    category = HmsCategory.RESTAURANT,
    address = HmsAddress("1 Main St", city, "Dallas County", "Texas", "United States Of America"),
    phone = "+12145550100",
    location = location,
    status = HmsCertificationStatus.CERTIFIED,
    statusLabel = "Certified",
    expiresOn = LocalDate.parse("2026-12-31"),
    listedOn = null,
    featureIds = emptyList(),
)

class HmsTranslatorTest : FunSpec({

    test("maps the library model into a Tier-1 source listing") {
        val page = CertifierPage(HmsProfile, listOf(hmsRecord()), listOf(snapshot("hms")))

        val listing = HmsTranslator().translate(page).single()

        listing.source.certifierId shouldBe "hms"
        listing.source.recordId shouldBe "1"
        listing.source.url shouldBe HmsDirectory.LISTING_URL
        listing.standard.tier shouldBe SourceTier.TIER_1
        listing.standard.handCut shouldBe HandCutDeclaration.HAND_CUT
        listing.name shouldBe "Test Kitchen"
        listing.city shouldBe "Dallas"
        listing.region shouldBe "Texas"
        listing.latitude shouldBe 32.7767
        listing.expiresOn shouldBe LocalDate.parse("2026-12-31")
        listing.isHandCut shouldBe true
    }

    test("carries a not-hand-cut declaration through unchanged") {
        val standard = DirectoryStandard(SourceTier.TIER_1, HmsProfile.name, HandCutDeclaration.NOT_HAND_CUT)
        val page = CertifierPage(HmsProfile, listOf(hmsRecord()), listOf(snapshot("hms")))

        val listing = HmsTranslator(standard).translate(page).single()

        listing.standard.handCut shouldBe HandCutDeclaration.NOT_HAND_CUT
        listing.standard.tier shouldBe SourceTier.TIER_1
        listing.isHandCut shouldBe false
    }

    test("a silent declaration is false, not null") {
        val standard = DirectoryStandard(SourceTier.TIER_1, HmsProfile.name, HandCutDeclaration.UNSPECIFIED)
        val page = CertifierPage(HmsProfile, listOf(hmsRecord()), listOf(snapshot("hms")))

        val listing = HmsTranslator(standard).translate(page).single()

        listing.standard.handCut shouldBe HandCutDeclaration.UNSPECIFIED
        listing.isHandCut shouldBe false
    }

    test("flags a record that still needs geocoding") {
        val page = CertifierPage(
            HmsProfile,
            listOf(hmsRecord(location = null)),
            listOf(snapshot("hms")),
        )

        val listing = HmsTranslator().translate(page).single()

        listing.hasCoordinates shouldBe false
        listing.warnings.any { it.contains("geocoding") } shouldBe true
    }
})

class HfsaaTranslatorTest : FunSpec({

    test("carries certificate id, place id and the chapter page URL") {
        val entry = HfsaaEntry(
            id = "uuid",
            type = HfsaaEntryType.RESTAURANT,
            name = "Test Grill",
            certificateId = "R-ABC123",
            certificationStatus = "certified",
            chapter = "New York",
            cuisine = "Pakistani",
            address = HfsaaAddress("1 Main St", "Brooklyn", "NY", "11201"),
            location = HfsaaLocation(40.7, -73.9),
            phone = "(718) 555-0100",
            websiteUrl = "https://example.test",
            deliveryUrl = null,
            businessHours = null,
            googlePlaceId = "ChIJ123",
            createdAt = null,
            updatedAt = null,
        )
        val page = CertifierPage(HfsaaProfile, listOf(entry), listOf(snapshot("hfsaa")))

        val listing = HfsaaTranslator().translate(page).single()

        listing.standard.tier shouldBe SourceTier.TIER_1
        listing.standard.handCut shouldBe HandCutDeclaration.HAND_CUT
        listing.source.url shouldBe "https://www.hfsaa.org/newyork"
        listing.certificateRef shouldBe "R-ABC123"
        listing.providerPlaceId shouldBe "ChIJ123"
        listing.city shouldBe "Brooklyn"
        listing.postalCode shouldBe "11201"
        listing.country shouldBe "United States"
    }
})

class HmaTranslatorTest : FunSpec({

    test("normalizes the province and leaves coordinates for geocoding") {
        val outlet = HmaCertifiedOutlet(
            id = 1,
            slug = "test-outlet",
            name = "Test Outlet",
            url = "https://hmacanada.org/hma-certified-restaurants/test-outlet/",
            address = HmaAddress("1 Queen St W", "Toronto", "Ontario", "M5H 2M5"),
            phone = "(416) 555-0100",
            website = null,
            certificationStatement = "All menu items are HMA Certified.",
        )
        val page = CertifierPage(HmaProfile, listOf(outlet), listOf(snapshot("hma")))

        val listing = HmaTranslator().translate(page).single()

        listing.standard.tier shouldBe SourceTier.TIER_1
        listing.standard.handCut shouldBe HandCutDeclaration.HAND_CUT
        listing.region shouldBe "ON"
        listing.country shouldBe "Canada"
        listing.hasCoordinates shouldBe false
        listing.website shouldBe null
        listing.certificationStatus!!.contains("HMA Certified") shouldBe true
        listing.warnings.any { it.contains("geocoding") } shouldBe true
    }

    test("keeps an unknown region as published") {
        val outlet = HmaCertifiedOutlet(
            id = 2,
            slug = "other",
            name = "Other",
            url = "https://hmacanada.org/x/",
            address = HmaAddress("1 Main St", "Somewhere", "Some Province", null),
            phone = null,
            website = null,
            certificationStatement = null,
        )
        val page = CertifierPage(HmaProfile, listOf(outlet), listOf(snapshot("hma")))

        HmaTranslator().translate(page).single().region shouldBe "Some Province"
    }
})

class SourceListingHandCutTest : FunSpec({

    test("a manual row with no certifier claim stays null") {
        val listing = listing(
            DirectoryStandard(SourceTier.TIER_3, "Zabihah", HandCutDeclaration.UNSPECIFIED),
        )

        listing.isHandCut shouldBe null
    }
})

private fun listing(standard: DirectoryStandard) = SourceListing(
    source = SourceRef("manual", "1", "https://example.test/1"),
    standard = standard,
    name = "Manual entry",
    addressLines = listOf("1 Main St"),
    city = "Dallas",
    region = "Texas",
    postalCode = null,
    country = "United States",
    latitude = null,
    longitude = null,
    phone = null,
    website = null,
    certificateRef = null,
    certificationStatus = null,
    expiresOn = null,
    providerPlaceId = null,
    category = "Restaurant",
)
