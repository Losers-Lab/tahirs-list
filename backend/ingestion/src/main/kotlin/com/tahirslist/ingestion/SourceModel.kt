package com.tahirslist.ingestion

import com.tahirslist.certifiers.core.HandCutDeclaration
import java.time.LocalDate

/** Credibility tier, from the founder-supplied research. */
enum class SourceTier(val rank: Int) {
    TIER_1(1),
    TIER_2(2),
    TIER_3(3),
}

/** Where one listing record came from, precisely enough to cite it. */
data class SourceRef(
    val certifierId: String,
    val recordId: String,
    val url: String,
)

/**
 * The certifier's policy as Tahir's List records it: the tier decides
 * verification, the hand-cut declaration decides hand-cut.
 */
data class DirectoryStandard(
    val tier: SourceTier,
    val certifier: String,
    val handCut: HandCutDeclaration,
)

/**
 * Tahir's List's own source-listing model. Library models never cross this
 * boundary: each translator maps one library's types into this shape, keeping
 * the app independent of any directory's markup or JSON.
 */
data class SourceListing(
    val source: SourceRef,
    val standard: DirectoryStandard,
    val name: String,
    val addressLines: List<String>,
    val city: String?,
    val region: String?,
    val postalCode: String?,
    val country: String?,
    val latitude: Double?,
    val longitude: Double?,
    val phone: String?,
    val website: String?,
    val certificateRef: String?,
    val certificationStatus: String?,
    val expiresOn: LocalDate?,
    val providerPlaceId: String?,
    val category: String,
    val warnings: List<String> = emptyList(),
) {
    val hasCoordinates: Boolean get() = latitude != null && longitude != null

    /**
     * TRUE only when a Tier-1 certifier declares hand-cut; FALSE for every
     * other Tier-1 declaration (declared not or silent). Rows without a
     * certifier claim (manual Tier-3) stay NULL.
     */
    val isHandCut: Boolean?
        get() = if (standard.tier == SourceTier.TIER_1) standard.handCut.isHandCut else null
}

/** One library model -> Tahir's List source listings. */
interface SourceTranslator<T> {
    val standard: DirectoryStandard
    fun translate(page: com.tahirslist.certifiers.core.CertifierPage<T>): List<SourceListing>
}
