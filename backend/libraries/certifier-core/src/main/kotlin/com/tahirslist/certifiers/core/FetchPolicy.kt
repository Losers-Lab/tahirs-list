package com.tahirslist.certifiers.core

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

/** Identifies the crawler honestly; every certifier library sends it. */
const val DEFAULT_USER_AGENT: String =
    "TahirsListBot/0.1 (+https://github.com/Losers-Lab/tahirs-list; data-curation)"

/**
 * The politeness **and** safety contract shared by every certifier library.
 *
 * Politeness defaults are deliberate: one request per second per host, three
 * attempts, a hard cap of ten wire requests per run, and a day-long snapshot
 * cache. A full SC-211 sample uses three data requests (plus robots.txt).
 *
 * Safety defaults bound what a directory (or a compromised response) can make us
 * do: HTTPS only, an explicit host allow-list per certifier, at most three
 * redirects (each re-checked against the allow-list, robots.txt, throttle and
 * budget), and a 16 MB response cap. [allowedHosts] must be non-empty; there is
 * no "allow everything" mode.
 */
data class FetchPolicy(
    val userAgent: String = DEFAULT_USER_AGENT,
    val allowedHosts: Set<String>,
    val minInterval: Duration = 1.seconds,
    val maxAttempts: Int = 3,
    val maxRequestsPerRun: Int = 10,
    val cacheTtl: Duration = 24.hours,
    val requestTimeout: Duration = 60.seconds,
    val maxBackoff: Duration = 60.seconds,
    val maxRedirects: Int = 3,
    val maxResponseBytes: Int = 16 * 1024 * 1024,
    val obeyRobots: Boolean = true,
) {
    init {
        require(userAgent.isNotBlank()) { "user agent must not be blank" }
        require(allowedHosts.isNotEmpty()) { "at least one allowed host is required" }
        require(allowedHosts.none { it.isBlank() || '/' in it || ':' in it }) {
            "allowed hosts must be bare host names"
        }
        require(minInterval >= Duration.ZERO) { "minInterval must not be negative" }
        require(maxAttempts >= 1) { "maxAttempts must be at least 1" }
        require(maxRequestsPerRun >= 1) { "maxRequestsPerRun must be at least 1" }
        require(cacheTtl >= Duration.ZERO) { "cacheTtl must not be negative" }
        require(requestTimeout > Duration.ZERO) { "requestTimeout must be positive" }
        require(maxBackoff > Duration.ZERO) { "maxBackoff must be positive" }
        require(maxRedirects in 0..10) { "maxRedirects must be between 0 and 10" }
        require(maxResponseBytes > 0) { "maxResponseBytes must be positive" }
    }
}
