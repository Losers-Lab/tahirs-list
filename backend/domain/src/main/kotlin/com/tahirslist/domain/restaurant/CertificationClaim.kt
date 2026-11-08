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
        require(source.length <= SOURCE_MAX_LENGTH) { "claim source must be $SOURCE_MAX_LENGTH characters or fewer" }
        require(source == source.trim().lowercase()) { "claim source must be trimmed and lowercase" }
        require(SOURCE_PATTERN.matches(source)) { "claim source must match $SOURCE_PATTERN" }
        require(sourceRecordRef.isNotBlank()) { "claim source record must not be blank" }
        require(sourceRecordRef.length <= RECORD_MAX_LENGTH) {
            "claim source record must be $RECORD_MAX_LENGTH characters or fewer"
        }
        require(sourceRecordRef == sourceRecordRef.trim()) { "claim source record must be trimmed" }
        require(certifier.isNotBlank()) { "claim certifier must not be blank" }
        require(certifier.length <= CERTIFIER_MAX_LENGTH) {
            "claim certifier must be $CERTIFIER_MAX_LENGTH characters or fewer"
        }
        require(isHttpsCitation(sourceUrl)) { "claim source URL must be an https URL" }
        require(firstSeen == null || lastSeen == null || lastSeen >= firstSeen) {
            "claim lastSeen must not be before firstSeen"
        }
    }

    /** The globally unique source-record identity. */
    val key: String get() = "$source::$sourceRecordRef"

    val isHandCut: Boolean get() = declaredHandCut.isHandCut

    companion object {
        /** Mirror the V22 column widths so persistence can never truncate silently. */
        const val SOURCE_MAX_LENGTH: Int = 64
        const val RECORD_MAX_LENGTH: Int = 255
        const val URL_MAX_LENGTH: Int = 1024
        const val CERTIFIER_MAX_LENGTH: Int = 255

        private val SOURCE_PATTERN = Regex("^[a-z0-9-]+$")

        /** `https://` + a non-empty host/path and no embedded whitespace. */
        private fun isHttpsCitation(url: String): Boolean =
            url.length <= URL_MAX_LENGTH &&
                url.startsWith("https://") &&
                url.length > "https://".length &&
                url.none { it.isWhitespace() }
    }
}
