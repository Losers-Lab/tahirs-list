package com.tahirslist.ingestion

import com.tahirslist.certifiers.core.CertifierPage
import com.tahirslist.certifiers.hfsaa.HfsaaEntry
import com.tahirslist.certifiers.hfsaa.HfsaaProfile

/**
 * HFSAA -> Tahir's List. The locator's certificate id is carried into the
 * claim; the chapter page is the human-citable URL.
 */
class HfsaaTranslator(
    override val standard: DirectoryStandard = DirectoryStandard(
        tier = SourceTier.TIER_1,
        certifier = HfsaaProfile.name,
        handCut = HfsaaProfile.declaredHandCut,
    ),
) : SourceTranslator<HfsaaEntry> {

    override fun translate(page: CertifierPage<HfsaaEntry>): List<SourceListing> =
        page.records.map { record ->
            val warnings = buildList {
                if (record.address.line == null) add("HFSAA entry has no street address")
                if (record.location == null) add("HFSAA entry has no coordinates; geocoding required")
            }
            SourceListing(
                source = SourceRef(
                    certifierId = "hfsaa",
                    recordId = record.certificateId ?: record.id,
                    url = chapterUrl(record.chapter),
                ),
                standard = standard,
                name = record.name,
                addressLines = listOfNotNull(record.address.line),
                city = record.address.city,
                region = record.address.state,
                postalCode = record.address.postalCode,
                country = "United States",
                latitude = record.location?.latitude,
                longitude = record.location?.longitude,
                phone = record.phone,
                website = record.websiteUrl,
                certificateRef = record.certificateId,
                certificationStatus = record.certificationStatus,
                expiresOn = null,
                providerPlaceId = record.googlePlaceId,
                category = record.type.name,
                warnings = warnings,
            )
        }

    private fun chapterUrl(chapter: String?): String {
        val slug = chapter?.trim()?.lowercase()?.replace(" ", "").orEmpty()
        return if (slug.isEmpty()) {
            "https://www.hfsaa.org/"
        } else {
            "https://www.hfsaa.org/$slug"
        }
    }
}
