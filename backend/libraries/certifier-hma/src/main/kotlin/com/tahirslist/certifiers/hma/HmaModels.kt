package com.tahirslist.certifiers.hma

import com.tahirslist.certifiers.core.CertifierProfile
import com.tahirslist.certifiers.core.HandCutDeclaration

/** Address as HMA publishes it, split into its published parts. */
data class HmaAddress(
    val line: String?,
    val city: String?,
    val region: String?,
    val postalCode: String?,
)

/** One HMA-certified outlet page. HMA publishes no coordinates. */
data class HmaCertifiedOutlet(
    val id: Long,
    val slug: String,
    val name: String,
    val url: String,
    val address: HmaAddress,
    val phone: String?,
    val website: String?,
    val certificationStatement: String?,
)

val HmaProfile: CertifierProfile = CertifierProfile(
    id = "hma",
    name = "Halal Monitoring Authority (HMA), Canada",
    sourceUrl = "https://hmacanada.org/hma-certified-restaurants/",
    declaredHandCut = HandCutDeclaration.HAND_CUT,
    handCutBasis = "HMA certification standard requires hand-cut (founder-supplied research, 2026-11-07)",
)
