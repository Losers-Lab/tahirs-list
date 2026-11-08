package com.tahirslist.certifiers.core

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

/** Identifies the crawler honestly; every certifier library sends it. */
const val DEFAULT_USER_AGENT: String =
    "TahirsListBot/0.1 (+https://github.com/Losers-Lab/tahirs-list; data-curation)"

/**
 * The politeness contract shared by every certifier library.
 *
 * Defaults are deliberately conservative: one request per second per host,
 * three attempts, a hard cap of ten requests per run, and a day-long snapshot
 * cache. A full SC-211 sample uses three data requests (plus robots.txt).
 */
data class FetchPolicy(
    val userAgent: String = DEFAULT_USER_AGENT,
    val minInterval: Duration = 1.seconds,
    val maxAttempts: Int = 3,
    val maxRequestsPerRun: Int = 10,
    val cacheTtl: Duration = 24.hours,
    val requestTimeout: Duration = 60.seconds,
    val maxBackoff: Duration = 60.seconds,
    val obeyRobots: Boolean = true,
) {
    init {
        require(userAgent.isNotBlank()) { "user agent must not be blank" }
        require(minInterval >= Duration.ZERO) { "minInterval must not be negative" }
        require(maxAttempts >= 1) { "maxAttempts must be at least 1" }
        require(maxRequestsPerRun >= 1) { "maxRequestsPerRun must be at least 1" }
        require(cacheTtl >= Duration.ZERO) { "cacheTtl must not be negative" }
        require(requestTimeout > Duration.ZERO) { "requestTimeout must be positive" }
        require(maxBackoff > Duration.ZERO) { "maxBackoff must be positive" }
    }
}
