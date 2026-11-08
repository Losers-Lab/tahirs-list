package com.tahirslist.ingestion

import com.tahirslist.certifiers.core.CertifierPage
import com.tahirslist.certifiers.hma.HmaCertifiedOutlet
import com.tahirslist.certifiers.hma.HmaProfile

/**
 * HMA -> Tahir's List. HMA publishes no coordinates, so the listing keeps null
 * coordinates and is geocoded downstream; province names are normalized to
 * Canadian two-letter codes for consistency with the catalogue.
 */
class HmaTranslator(
    override val standard: DirectoryStandard = DirectoryStandard(
        tier = SourceTier.TIER_1,
        certifier = HmaProfile.name,
        handCut = HmaProfile.declaredHandCut,
    ),
) : SourceTranslator<HmaCertifiedOutlet> {

    override fun translate(page: CertifierPage<HmaCertifiedOutlet>): List<SourceListing> =
        page.records.map { record ->
            val warnings = buildList {
                if (record.address.city == null) add("HMA outlet has no city")
                add("HMA publishes no coordinates; geocoding required")
            }
            SourceListing(
                source = SourceRef(
                    certifierId = "hma",
                    recordId = record.id.toString(),
                    url = record.url.ifBlank { HmaProfile.sourceUrl },
                ),
                standard = standard,
                name = record.name,
                addressLines = listOfNotNull(record.address.line),
                city = record.address.city,
                region = normalizeProvince(record.address.region),
                postalCode = record.address.postalCode,
                country = "Canada",
                latitude = null,
                longitude = null,
                phone = record.phone,
                website = record.website,
                certificateRef = null,
                certificationStatus = record.certificationStatement,
                expiresOn = null,
                providerPlaceId = null,
                category = "Restaurant",
                warnings = warnings,
            )
        }

    private fun normalizeProvince(region: String?): String? {
        val key = region?.trim()?.lowercase() ?: return null
        return PROVINCES[key] ?: region
    }

    private companion object {
        val PROVINCES = mapOf(
            "ontario" to "ON",
            "quebec" to "QC",
            "british columbia" to "BC",
            "alberta" to "AB",
            "manitoba" to "MB",
            "saskatchewan" to "SK",
            "nova scotia" to "NS",
            "new brunswick" to "NB",
            "newfoundland and labrador" to "NL",
            "prince edward island" to "PE",
            "yukon" to "YT",
            "northwest territories" to "NT",
            "nunavut" to "NU",
        )
    }
}
