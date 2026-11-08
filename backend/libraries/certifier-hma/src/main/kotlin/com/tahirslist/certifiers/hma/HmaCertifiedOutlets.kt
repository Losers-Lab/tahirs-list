package com.tahirslist.certifiers.hma

import com.tahirslist.certifiers.core.CertifierHttp
import com.tahirslist.certifiers.core.CertifierPage
import com.tahirslist.certifiers.core.CertifierProfile
import com.tahirslist.certifiers.core.SourceSnapshot

/**
 * HMA Canada certified-restaurants client.
 *
 * ```kotlin
 * val hma = HmaCertifiedOutlets(CertifierHttp.create())
 * val gta = hma.outlets().records
 * ```
 *
 * One request normally returns every outlet because `per_page=100` inlines the
 * page content; pagination is followed if the directory grows. HMA publishes no
 * coordinates, so callers must geocode the address.
 */
class HmaCertifiedOutlets(
    private val http: CertifierHttp,
    val profile: CertifierProfile = HmaProfile,
) {
    suspend fun outlets(refresh: Boolean = false): CertifierPage<HmaCertifiedOutlet> {
        val snapshots = mutableListOf<SourceSnapshot>()
        val records = mutableListOf<HmaCertifiedOutlet>()
        var skipped = 0
        var page = 1
        var totalPages = 1

        do {
            val result = http.get(
                url = pageUrl(page),
                accept = "application/json",
                refresh = refresh && page == 1,
            )
            snapshots += result.snapshot
            val parsed = HmaParser.parse(result.snapshot.body)
            records += parsed.outlets
            skipped += parsed.skipped

            totalPages = result.snapshot.header("X-WP-TotalPages")?.toIntOrNull() ?: 1
            page++
        } while (page <= totalPages && page <= MAX_PAGES)

        val warnings = buildList {
            if (skipped > 0) add("$skipped HMA pages were unusable (missing id/title)")
            val noAddress = records.count { it.address.line == null && it.address.city == null }
            if (noAddress > 0) add("$noAddress HMA outlets have no parsable address")
            if (totalPages > MAX_PAGES) add("HMA has $totalPages pages; only $MAX_PAGES were read")
        }
        return CertifierPage(profile, records, snapshots, warnings)
    }

    private fun pageUrl(page: Int): String =
        "https://hmacanada.org/wp-json/wp/v2/pages" +
            "?parent=$PARENT_PAGE_ID&per_page=100" +
            "&_fields=id,slug,link,title,content&page=$page"

    companion object {
        const val PARENT_PAGE_ID: Long = 21_800
        const val MAX_PAGES: Int = 20
    }
}
