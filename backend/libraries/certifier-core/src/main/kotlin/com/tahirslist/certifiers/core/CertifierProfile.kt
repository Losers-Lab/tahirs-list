package com.tahirslist.certifiers.core

/**
 * What a certifier declares about hand-cutting for the supply it certifies.
 *
 * This is a property of the certifier's published policy, not of its credibility
 * tier. A recognized certifier may be authoritative for halal (and therefore
 * confer verification) while not requiring hand-cutting; callers must derive
 * hand-cut from this value, never from the tier.
 *
 * Tahir's List has exactly one axis — hand-cut — with three states: declared,
 * declared not, and silent. There is no counter-category.
 */
enum class HandCutDeclaration {
    HAND_CUT,
    NOT_HAND_CUT,
    UNSPECIFIED;

    /**
     * The product boolean for a certifier-backed row: TRUE only when hand-cut
     * is declared; FALSE for every other declaration, including silence.
     * Callers reserve NULL for rows with no certifier claim at all.
     */
    val isHandCut: Boolean get() = this == HAND_CUT
}

/**
 * Identity and declared policy of one certifier source.
 *
 * [handCutBasis] records where the declaration comes from, so a claim written
 * downstream can cite the policy rather than an assumption. [allowedHosts] is
 * the network allow-list every fetch for this certifier must satisfy.
 */
data class CertifierProfile(
    val id: String,
    val name: String,
    val sourceUrl: String,
    val declaredHandCut: HandCutDeclaration,
    val handCutBasis: String,
    val allowedHosts: Set<String>,
) {
    init {
        require(id.isNotBlank()) { "certifier id must not be blank" }
        require(name.isNotBlank()) { "certifier name must not be blank" }
        require(sourceUrl.startsWith("https://")) { "certifier source URL must be https" }
        require(handCutBasis.isNotBlank()) { "hand-cut basis must cite its source" }
        require(allowedHosts.isNotEmpty()) { "a certifier must declare at least one allowed host" }
        require(allowedHosts.none { it.isBlank() || '/' in it || ':' in it }) {
            "allowed hosts must be bare host names"
        }
    }
}
