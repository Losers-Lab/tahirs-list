package com.tahirslist.certifiers.hms

import com.tahirslist.certifiers.core.CertifierProfile
import com.tahirslist.certifiers.core.HandCutDeclaration
import java.time.LocalDate

/** The directory's own record categories, exactly as published. */
enum class HmsCategory(val label: String) {
    RESTAURANT("Restaurants"),
    RETAIL_STORE("Retail Store"),
    SLAUGHTER_HOUSE("Slaughter House"),
    DISTRIBUTOR("Distributor"),
    PROCESSOR("Processors"),
    FURTHER_PROCESSOR("Further Processor"),
    CATERER("Caterers"),
    MISC("Misc"),
    UNKNOWN("Unknown");

    companion object {
        fun fromLabel(label: String?): HmsCategory {
            val normalized = label?.trim().orEmpty()
            return entries.firstOrNull { it.label.equals(normalized, ignoreCase = true) } ?: UNKNOWN
        }
    }
}

enum class HmsCertificationStatus {
    CERTIFIED,
    EXPIRED,
    SUSPENDED,
    UNKNOWN;

    companion object {
        fun fromLabel(label: String?): HmsCertificationStatus = when (label?.trim()?.lowercase()) {
            "certified" -> CERTIFIED
            "expired" -> EXPIRED
            "suspended" -> SUSPENDED
            else -> UNKNOWN
        }
    }
}

/** Address as HMS publishes it; any part may be absent. */
data class HmsAddress(
    val line: String?,
    val city: String?,
    val county: String?,
    val state: String?,
    val country: String?,
)

/** Directory-supplied coordinates. Evidence only — callers still geocode. */
data class HmsLocation(val latitude: Double, val longitude: Double) {
    init {
        require(latitude in -90.0..90.0) { "latitude out of range: $latitude" }
        require(longitude in -180.0..180.0) { "longitude out of range: $longitude" }
    }

    companion object {
        fun of(latitude: Double?, longitude: Double?): HmsLocation? {
            if (latitude == null || longitude == null) return null
            return runCatching { HmsLocation(latitude, longitude) }.getOrNull()
        }
    }
}

/** One establishment as HMS publishes it. */
data class HmsEstablishment(
    val id: String,
    val name: String,
    val category: HmsCategory,
    val address: HmsAddress,
    val phone: String?,
    val location: HmsLocation?,
    val status: HmsCertificationStatus,
    val statusLabel: String?,
    val expiresOn: LocalDate?,
    val listedOn: LocalDate?,
    val featureIds: List<String>,
) {
    val isRestaurant: Boolean get() = category == HmsCategory.RESTAURANT
}

/** HMS's published position, recorded once and cited by every claim. */
val HmsProfile: CertifierProfile = CertifierProfile(
    id = "hms",
    name = "Halal Monitoring Services (HMS)",
    sourceUrl = "https://hmsusa.org/certified-listing/",
    declaredHandCut = HandCutDeclaration.HAND_CUT,
    handCutBasis = "HMS certification standard requires hand-cut (founder-supplied research, 2026-11-07)",
    allowedHosts = setOf("hmsusa.org", "www.hmsusa.org"),
)
