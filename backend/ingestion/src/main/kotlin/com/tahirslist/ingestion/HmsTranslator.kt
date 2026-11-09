package com.tahirslist.ingestion

import com.tahirslist.certifiers.core.CertifierPage
import com.tahirslist.certifiers.hms.HmsDirectory
import com.tahirslist.certifiers.hms.HmsEstablishment
import com.tahirslist.certifiers.hms.HmsProfile
import com.tahirslist.domain.restaurant.RestaurantListing

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
            val country = canonicalCountry(record.address.country)
            val warnings = buildList {
                if (record.address.line == null) add("HMS record has no street address")
                if (record.address.city == null) add("HMS record has no city")
                if (record.location == null) add("HMS record has no directory coordinates; geocoding required")
                if (record.address.country != null && country == null) {
                    add("HMS country '${record.address.country}' is too long for the catalogue field; not stored")
                }
            }
            SourceListing(
                source = SourceRef(certifierId = "hms", recordId = record.id, url = HmsDirectory.LISTING_URL),
                standard = standard,
                name = record.name,
                addressLines = listOfNotNull(record.address.line),
                city = record.address.city,
                region = canonicalRegion(record.address.state),
                postalCode = null,
                country = country,
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

    /**
     * HMS publishes "United States Of America", which does not fit the
     * catalogue's 16-character country field. The translator maps it to the
     * short form the other sources already use; an unexpected long name is
     * dropped with a warning rather than truncated into nonsense.
     */
    private fun canonicalCountry(raw: String?): String? =
        LocalityNames.country(raw, RestaurantListing.COUNTRY_MAX_LENGTH)

    /**
     * HMS publishes the full state name ("New York"), HFSAA publishes the code
     * ("NY"). The region is part of the venue key, so the same venue seen in
     * both directories would otherwise never merge; map to the code here.
     */
    private fun canonicalRegion(raw: String?): String? = LocalityNames.usStateCode(raw)
}
