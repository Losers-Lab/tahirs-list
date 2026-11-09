package com.tahirslist.application.listing

import com.tahirslist.domain.restaurant.CoordinatePrecision

/**
 * Search tuning (SC-199). The centre precision is fixed at 3 or 4 decimals by the
 * [CoordinatePrecision] type. The freshness window is the longest a shared cache may
 * keep a search answer, so a delisting can stay visible at most [MAX_STALENESS_SECONDS].
 */
data class SearchSettings(
    val centrePrecision: CoordinatePrecision = CoordinatePrecision.FOUR_DECIMALS,
    val cacheMaxAgeSeconds: Long = 60,
    val sMaxAgeSeconds: Long = 300,
    val staleWhileRevalidateSeconds: Long = 60,
) {
    init {
        require(cacheMaxAgeSeconds >= 0) { "cache max-age must not be negative" }
        require(sMaxAgeSeconds >= 0) { "cache s-maxage must not be negative" }
        require(staleWhileRevalidateSeconds >= 0) { "cache stale-while-revalidate must not be negative" }
        require(sMaxAgeSeconds + staleWhileRevalidateSeconds <= MAX_STALENESS_SECONDS) {
            "s-maxage plus stale-while-revalidate must not exceed $MAX_STALENESS_SECONDS seconds"
        }
    }

    fun cacheControl(): String =
        "public, max-age=$cacheMaxAgeSeconds, s-maxage=$sMaxAgeSeconds, " +
            "stale-while-revalidate=$staleWhileRevalidateSeconds"

    companion object {
        const val MAX_STALENESS_SECONDS: Long = 600
    }
}
