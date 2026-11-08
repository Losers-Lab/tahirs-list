package com.tahirslist.domain.restaurant

import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/**
 * A cited observation from a directory or certifier.
 *
 * The claim is the audit evidence behind registry verification and hand-cut.
 * [source] + [sourceRecordRef] identify the source row globally: the same source
 * record must never attach to two listings, even if a later ingestion run
 * resolves it to slightly different coordinates.
 */
data class CertificationClaim(
    val source: String,
    val sourceRecordRef: String,
    val sourceUrl: String,
    val certifier: String,
    val tier: CertificationTier,
    val declaredHandCut: HandCutDeclaration,
    val expiresOn: LocalDate? = null,
    val firstSeen: Instant? = null,
    val lastSeen: Instant? = null,
) {
    init {
        require(source.isNotBlank()) { "claim source must not be blank" }
        require(source == source.trim().lowercase()) { "claim source must be trimmed and lowercase" }
        require(sourceRecordRef.isNotBlank()) { "claim source record must not be blank" }
        require(sourceRecordRef == sourceRecordRef.trim()) { "claim source record must be trimmed" }
        require(certifier.isNotBlank()) { "claim certifier must not be blank" }
        require(sourceUrl.startsWith("https://")) { "claim source URL must be https" }
        require(firstSeen == null || lastSeen == null || lastSeen >= firstSeen) {
            "claim lastSeen must not be before firstSeen"
        }
    }

    /** The globally unique source-record identity. */
    val key: String get() = "$source::$sourceRecordRef"

    val isHandCut: Boolean get() = declaredHandCut.isHandCut
}
