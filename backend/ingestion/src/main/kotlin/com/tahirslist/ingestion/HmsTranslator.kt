package com.tahirslist.ingestion

import com.tahirslist.certifiers.core.CertifierPage
import com.tahirslist.certifiers.hms.HmsDirectory
import com.tahirslist.certifiers.hms.HmsEstablishment
import com.tahirslist.certifiers.hms.HmsProfile

/**
 * HMS -> Tahir's List. Verification comes from the Tier-1 registry entry;
 * hand-cut comes from HMS's declared standard, never from the tier.
 */
class HmsTranslator(
    override val standard: DirectoryStandard = DirectoryStandard(
        tier = SourceTier.TIER_1,
        certifier = HmsProfile.name,
        handCut = HmsProfile.declaredHandCut,
    ),
) : SourceTranslator<HmsEstablishment> {

    override fun translate(page: CertifierPage<HmsEstablishment>): List<SourceListing> =
        page.records.map { record ->
            val warnings = buildList {
                if (record.address.line == null) add("HMS record has no street address")
                if (record.address.city == null) add("HMS record has no city")
                if (record.location == null) add("HMS record has no directory coordinates; geocoding required")
            }
            SourceListing(
                source = SourceRef(certifierId = "hms", recordId = record.id, url = HmsDirectory.LISTING_URL),
                standard = standard,
                name = record.name,
                addressLines = listOfNotNull(record.address.line),
                city = record.address.city,
                region = record.address.state,
                postalCode = null,
                country = record.address.country,
                latitude = record.location?.latitude,
                longitude = record.location?.longitude,
                phone = record.phone,
                website = null,
                certificateRef = null,
                certificationStatus = record.statusLabel,
                expiresOn = record.expiresOn,
                providerPlaceId = null,
                category = record.category.label,
                warnings = warnings,
            )
        }
}
