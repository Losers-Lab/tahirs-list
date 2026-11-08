package com.tahirslist.certifiers.core

import java.security.MessageDigest
import java.time.Instant

private const val HEX = "0123456789abcdef"

internal fun sha256Hex(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
    val out = StringBuilder(digest.size * 2)
    for (byte in digest) {
        val v = byte.toInt() and 0xff
        out.append(HEX[v ushr 4]).append(HEX[v and 0x0f])
    }
    return out.toString()
}

/**
 * The exact bytes one directory request returned, plus the request metadata
 * needed to audit it later. Snapshots are the provenance record: candidates
 * cite [url] + [fetchedAt] + [sha256].
 *
 * [url] is the URL that finally served the bytes (after any redirects);
 * [requestedUrl] is what we asked for. They differ when a directory redirects.
 */
class SourceSnapshot(
    val certifierId: String,
    val url: String,
    val requestedUrl: String = url,
    val status: Int,
    val contentType: String?,
    val etag: String?,
    val lastModified: String?,
    val headers: Map<String, String>,
    val fetchedAt: Instant,
    val fromCache: Boolean,
    val body: String,
) {
    val sha256: String by lazy(LazyThreadSafetyMode.NONE) { sha256Hex(body) }
    val byteSize: Int get() = body.toByteArray(Charsets.UTF_8).size

    fun header(name: String): String? =
        headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

    /** Never dump a multi-megabyte body into logs. */
    override fun toString(): String =
        "SourceSnapshot(certifier=$certifierId, url=$url, status=$status, " +
            "bytes=$byteSize, fromCache=$fromCache, fetchedAt=$fetchedAt)"
}

/**
 * One successful read of a certifier directory: the library's own records plus
 * the snapshots they were parsed from. Warnings are honest, non-fatal gaps
 * (e.g. a record without directory coordinates).
 */
data class CertifierPage<T>(
    val profile: CertifierProfile,
    val records: List<T>,
    val snapshots: List<SourceSnapshot>,
    val warnings: List<String> = emptyList(),
) {
    init {
        require(snapshots.isNotEmpty()) { "a page must carry at least one snapshot" }
    }

    val snapshot: SourceSnapshot get() = snapshots.first()
}
