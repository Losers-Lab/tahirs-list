package com.tahirslist.certifiers.hfsaa

import com.tahirslist.certifiers.core.CertifierProfile
import com.tahirslist.certifiers.core.HandCutDeclaration
import java.time.Instant

enum class HfsaaEntryType {
    RESTAURANT,
    MEAT_MARKET,
    DINING_HALL,
    UNKNOWN;

    companion object {
        fun fromLabel(label: String?): HfsaaEntryType = when (label?.trim()?.lowercase()) {
            "restaurant" -> RESTAURANT
            "meat_market" -> MEAT_MARKET
            "dining_hall" -> DINING_HALL
            else -> UNKNOWN
        }
    }
}

data class HfsaaAddress(
    val line: String?,
    val city: String?,
    val state: String?,
    val postalCode: String?,
)

data class HfsaaLocation(val latitude: Double, val longitude: Double) {
    init {
        require(latitude in -90.0..90.0) { "latitude out of range: $latitude" }
        require(longitude in -180.0..180.0) { "longitude out of range: $longitude" }
    }

    companion object {
        fun of(latitude: Double?, longitude: Double?): HfsaaLocation? {
            if (latitude == null || longitude == null) return null
            return runCatching { HfsaaLocation(latitude, longitude) }.getOrNull()
        }
    }
}

/**
 * One entry in HFSAA's public locator. The certificate id is HFSAA's own
 * identifier (`R-…` restaurant, `M-…` meat market) and is retained verbatim so a
 * claim can cite it.
 */
data class HfsaaEntry(
    val id: String,
    val type: HfsaaEntryType,
    val name: String,
    val certificateId: String?,
    val certificationStatus: String?,
    val chapter: String?,
    val cuisine: String?,
    val address: HfsaaAddress,
    val location: HfsaaLocation?,
    val phone: String?,
    val websiteUrl: String?,
    val deliveryUrl: String?,
    val businessHours: String?,
    val googlePlaceId: String?,
    val createdAt: Instant?,
    val updatedAt: Instant?,
)

val HfsaaProfile: CertifierProfile = CertifierProfile(
    id = "hfsaa",
    name = "Halal Food Standards Alliance of America (HFSAA)",
    sourceUrl = "https://www.hfsaa.org/newyork",
    declaredHandCut = HandCutDeclaration.HAND_CUT,
    handCutBasis = "HFSAA certification standard requires hand-cut (founder-supplied research, 2026-11-07)",
    allowedHosts = setOf(
        "www.hfsaa.org",
        "hfsaa.org",
        "hfsaa-public-locator.developer-907.workers.dev",
        "pmolfcjxryccsisyhhwm.supabase.co",
    ),
)

/**
 * The public locator project. The publishable key is not a secret: HFSAA ships
 * it inside the browser bundle of its own locator and the key only grants the
 * row-level-security-scoped read RPC.
 */
data class HfsaaProject(
    val supabaseUrl: String,
    val publishableKey: String,
) {
    val locatorUrl: String get() = "$supabaseUrl/rest/v1/rpc/get_public_locator_entries"

    companion object {
        const val LOCATOR_HOME: String = "https://hfsaa-public-locator.developer-907.workers.dev/"

        val Default = HfsaaProject(
            supabaseUrl = "https://pmolfcjxryccsisyhhwm.supabase.co",
            publishableKey = "sb_publishable_D9EXV4Sip6WURhiClGHZCA_YaGOBY-2",
        )
    }
}
