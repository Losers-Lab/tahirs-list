package com.tahirslist.certifiers.core

import java.net.URI
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Injectable sleeping so unit tests never actually wait. */
fun interface Suspender {
    suspend fun sleep(duration: Duration)

    companion object {
        val Real = Suspender { delay(it) }
    }
}

/** A response plus the snapshot that proves where it came from. */
data class FetchResult(val snapshot: SourceSnapshot)

/**
 * The shared, polite HTTP client used by every certifier library.
 *
 * Guarantees, in order: a fresh snapshot short-circuits the network; robots.txt
 * is obeyed before any real fetch; one request at a time per host; a minimum gap
 * between requests; bounded retries with exponential backoff and `Retry-After`;
 * a hard per-run request budget; and a snapshot cache that turns a repeat run
 * into zero network calls.
 */
class CertifierHttp(
    val policy: FetchPolicy,
    private val transport: HttpTransport,
    private val store: SnapshotStore,
    private val clock: Clock,
    private val sleeper: Suspender,
    private val log: (String) -> Unit = {},
) {
    private val stateMutex = Mutex()
    private val robotsMutex = Mutex()
    private val lastRequestAt = mutableMapOf<String, Instant>()
    private val robots = mutableMapOf<String, RobotsPolicy>()
    private var requests = 0

    suspend fun get(
        url: String,
        accept: String = "*/*",
        refresh: Boolean = false,
    ): FetchResult = execute(
        request = HttpRequest(method = "GET", url = url),
        accept = accept,
        refresh = refresh,
    )

    suspend fun postJson(
        url: String,
        json: String,
        headers: Map<String, String> = emptyMap(),
        refresh: Boolean = false,
    ): FetchResult = execute(
        request = HttpRequest(
            method = "POST",
            url = url,
            headers = headers + ("Content-Type" to "application/json"),
            body = json,
        ),
        accept = "application/json",
        refresh = refresh,
    )

    private suspend fun execute(
        request: HttpRequest,
        accept: String,
        refresh: Boolean,
    ): FetchResult {
        val uri = URI(request.url)
        val key = sha256Hex("${request.method}\n${request.url}\n${request.body.orEmpty()}")
        val cached = store.read(key)
        val now = clock.instant()

        val ageMillis = cached?.let { java.time.Duration.between(it.fetchedAt, now).toMillis() }
        if (!refresh && cached != null && ageMillis != null &&
            ageMillis < policy.cacheTtl.inWholeMilliseconds
        ) {
            log("cache hit: ${request.method} ${request.url}")
            return FetchResult(cached.toSnapshot(certifierId = certifierId(uri), fromCache = true))
        }

        // Only a request that will actually hit the network needs a robots check.
        if (policy.obeyRobots) {
            val robotsPolicy = robotsPolicy(uri)
            val path = buildString {
                append(uri.rawPath.ifEmpty { "/" })
                uri.rawQuery?.let { append('?').append(it) }
            }
            if (!robotsPolicy.allows(path)) {
                throw SourceBlockedException(request.url, "robots.txt disallows $path")
            }
        }

        val headers = request.headers.toMutableMap()
        headers["Accept"] = accept
        headers["User-Agent"] = policy.userAgent
        cached?.etag?.let { headers["If-None-Match"] = it }
        cached?.lastModified?.let { headers["If-Modified-Since"] = it }

        val response = fetchRaw(request.copy(headers = headers))

        if (response.status == 304 && cached != null) {
            val refreshed = cached.copy(fetchedAt = now)
            store.write(key, refreshed)
            log("revalidated (304): ${request.url}")
            return FetchResult(refreshed.toSnapshot(certifierId(uri), fromCache = true))
        }

        val stored = StoredPage(
            url = request.url,
            status = response.status,
            contentType = response.header("Content-Type"),
            etag = response.header("ETag"),
            lastModified = response.header("Last-Modified"),
            headers = response.headers,
            fetchedAt = now,
            body = response.body,
        )
        store.write(key, stored)
        log("fetched ${response.status}: ${request.method} ${request.url} (${stored.body.length} chars)")
        return FetchResult(stored.toSnapshot(certifierId(uri), fromCache = false))
    }

    private suspend fun fetchRaw(request: HttpRequest): HttpResponse {
        reserveBudget()
        throttle(request.url)

        var attempt = 1
        while (true) {
            val response = try {
                transport.execute(request)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (attempt >= policy.maxAttempts) {
                    throw SourceUnavailableException(
                        request.url,
                        "request failed after $attempt attempt(s): ${e.message}",
                        e,
                    )
                }
                sleeper.sleep(backoffFor(attempt, retryAfter = null))
                attempt++
                continue
            }

            when {
                response.status in 200..299 -> return response
                response.status == 304 -> return response
                response.status == 429 || response.status in 500..599 -> {
                    if (attempt >= policy.maxAttempts) {
                        throw SourceUnavailableException(
                            request.url,
                            "HTTP ${response.status} after $attempt attempt(s)",
                        )
                    }
                    sleeper.sleep(backoffFor(attempt, response.header("Retry-After")))
                    attempt++
                }

                response.status == 401 || response.status == 403 ->
                    throw SourceBlockedException(request.url, "HTTP ${response.status}")

                else -> throw SourceUnavailableException(request.url, "HTTP ${response.status}")
            }
        }
    }

    private suspend fun reserveBudget() {
        stateMutex.withLock {
            if (requests >= policy.maxRequestsPerRun) {
                throw SourceBudgetExceededException(policy.maxRequestsPerRun)
            }
            requests++
        }
    }

    private suspend fun throttle(url: String) {
        val host = URI(url).host ?: return
        stateMutex.withLock {
            val now = clock.instant()
            val last = lastRequestAt[host]
            if (last != null) {
                val elapsedMillis = java.time.Duration.between(last, now).toMillis()
                val waitMillis = policy.minInterval.inWholeMilliseconds - elapsedMillis
                if (waitMillis > 0) sleeper.sleep(waitMillis.milliseconds)
            }
            lastRequestAt[host] = clock.instant()
        }
    }

    private suspend fun robotsPolicy(uri: URI): RobotsPolicy {
        val origin = originOf(uri)
        robotsMutex.withLock { robots[origin] }?.let { return it }

        val policy = try {
            val response = fetchRaw(
                HttpRequest(
                    method = "GET",
                    url = "$origin/robots.txt",
                    headers = mapOf(
                        "Accept" to "text/plain",
                        "User-Agent" to this.policy.userAgent,
                    ),
                ),
            )
            if (response.status in 200..299) {
                RobotsPolicy.parse(response.body, this.policy.userAgent)
            } else {
                RobotsPolicy.allowAll
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("robots.txt unavailable for $origin (${e.message}); allowing")
            RobotsPolicy.allowAll
        }

        robotsMutex.withLock { robots[origin] = policy }
        return policy
    }

    private fun backoffFor(attempt: Int, retryAfter: String?): Duration {
        val fromHeader = retryAfter?.trim()?.toLongOrNull()?.seconds
        val exponential = 2.seconds * (1 shl (attempt - 1))
        return minOf(fromHeader ?: exponential, policy.maxBackoff)
    }

    private fun originOf(uri: URI): String {
        val port = if (uri.port >= 0) ":${uri.port}" else ""
        return "${uri.scheme}://${uri.host}$port"
    }

    private fun certifierId(uri: URI): String = uri.host ?: "unknown"

    private fun StoredPage.toSnapshot(certifierId: String, fromCache: Boolean) = SourceSnapshot(
        certifierId = certifierId,
        url = url,
        status = status,
        contentType = contentType,
        etag = etag,
        lastModified = lastModified,
        headers = headers,
        fetchedAt = fetchedAt,
        fromCache = fromCache,
        body = body,
    )

    companion object {
        fun create(
            userAgent: String = DEFAULT_USER_AGENT,
            cacheDir: Path = Path.of(System.getProperty("java.io.tmpdir"), "tahirs-list-certifier-cache"),
            policy: FetchPolicy = FetchPolicy(userAgent = userAgent),
            transport: HttpTransport = KtorHttpTransport(policy.requestTimeout),
            store: SnapshotStore = FileSnapshotStore(cacheDir),
            clock: Clock = Clock.systemUTC(),
            sleeper: Suspender = Suspender.Real,
            log: (String) -> Unit = {},
        ): CertifierHttp = CertifierHttp(policy, transport, store, clock, sleeper, log)
    }
}
