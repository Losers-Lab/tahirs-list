package com.tahirslist.domain.restaurant

/**
 * Credibility tier of a cited source. Only Tier-1 (an audited certifier
 * registry) confers `CERTIFIER_REGISTRY` verification; lower tiers may add a
 * claim but never verify a listing on their own.
 */
enum class CertificationTier(
    val rank: Int,
    val confersRegistryVerification: Boolean,
) {
    TIER_1(1, true),
    TIER_2(2, false),
    TIER_3(3, false),
    ;
}
