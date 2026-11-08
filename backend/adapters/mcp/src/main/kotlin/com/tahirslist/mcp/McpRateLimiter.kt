package com.tahirslist.mcp

import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import io.github.bucket4j.Refill
import io.github.bucket4j.TimeMeter
import java.util.Collections
import java.util.LinkedHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** The rate-limit seam: `true` = a token was available and the call may proceed. */
fun interface McpRateLimiter {
    fun tryAcquire(key: String): Boolean
}

/**
 * In-process token bucket per client, bounded by [maxKeys] with LRU eviction.
 *
 * Mirrors the ratified auth limiter (sc-136): $0, no external service, and the
 * conservative direction on eviction (a client whose bucket was evicted simply
 * starts fresh). Single-node MVP posture; a shared counter is a scaling change.
 */
class Bucket4jMcpRateLimiter(
    capacity: Long,
    refillTokensPerWindow: Long,
    refillWindow: Duration,
    private val meter: TimeMeter = TimeMeter.SYSTEM_NANOTIME,
    val maxKeys: Long = 100_000L,
) : McpRateLimiter {

    init {
        require(capacity > 0) { "capacity must be positive" }
        require(refillTokensPerWindow > 0) { "refillTokens must be positive" }
        require(refillWindow > Duration.ZERO) { "refillWindow must be positive" }
        require(maxKeys > 0) { "maxKeys must be positive" }
    }

    private val limit = Bandwidth.classic(
        capacity,
        Refill.greedy(refillTokensPerWindow, java.time.Duration.ofNanos(refillWindow.inWholeNanoseconds)),
    )

    private val buckets: MutableMap<String, Bucket> = Collections.synchronizedMap(
        object : LinkedHashMap<String, Bucket>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bucket>): Boolean =
                size > maxKeys
        },
    )

    /** Current number of tracked client buckets (tests/ops). */
    val trackedKeyCount: Int get() = buckets.size

    override fun tryAcquire(key: String): Boolean =
        buckets.computeIfAbsent(key) { newBucket() }.tryConsume(1)

    private fun newBucket(): Bucket =
        Bucket.builder().withCustomTimePrecision(meter).addLimit(limit).build()
}

/** Convenience factory from the settings block. */
fun McpRateLimitSettings.buildLimiter(): McpRateLimiter = Bucket4jMcpRateLimiter(
    capacity = capacity,
    refillTokensPerWindow = refillTokens,
    refillWindow = refillWindowSeconds.seconds,
    maxKeys = maxKeys.toLong(),
)
