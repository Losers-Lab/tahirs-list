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

private data class FetchedResponse(val response: HttpResponse, val finalUrl: String)

/**
 * The shared, polite and bounded HTTP client used by every certifier library.
 *
 * Guarantees, in order: a fresh snapshot short-circuits the network; every URL
 * (including redirect targets) is HTTPS and on the certifier's host allow-list;
 * robots.txt is obeyed before any real fetch and fails closed; one wire request
 * at a time; a minimum gap between requests; bounded retries with exponential
 * backoff and `Retry-After`; a hard per-run budget counted per wire request; a
 * response-size cap; and a snapshot cache that turns a repeat run into zero
 * network calls.
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
    private val inFlightMutex = Mutex()
    private val allowedHosts = policy.allowedHosts.map { it.lowercase() }.toSet()
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
        val uri = requireAllowed(request.url)
        val key = cacheKey(request, accept)
        val cached = store.read(key)
        val now = clock.instant()

        val ageMillis = cached?.let { java.time.Duration.between(it.fetchedAt, now).toMillis() }
        if (!refresh && cached != null && ageMillis != null &&
            ageMillis < policy.cacheTtl.inWholeMilliseconds
        ) {
            log("cache hit: ${request.method} ${request.url}")
            return FetchResult(cached.toSnapshot(certifierId(URI(cached.url)), fromCache = true))
        }

        val headers = request.headers.toMutableMap()
        headers["Accept"] = accept
        headers["User-Agent"] = policy.userAgent
        cached?.etag?.let { headers["If-None-Match"] = it }
        cached?.lastModified?.let { headers["If-Modified-Since"] = it }

        val fetched = fetchFollowing(request.copy(headers = headers))
        val response = fetched.response

        if (response.status == 304 && cached != null) {
            val refreshed = cached.copy(fetchedAt = now)
            store.write(key, refreshed)
            log("revalidated (304): ${request.url}")
            return FetchResult(
                refreshed.toSnapshot(certifierId(URI(refreshed.url)), fromCache = true),
            )
        }

        val stored = StoredPage(
            url = fetched.finalUrl,
            status = response.status,
            contentType = response.header("Content-Type"),
            etag = response.header("ETag"),
            lastModified = response.header("Last-Modified"),
            headers = response.headers,
            fetchedAt = now,
            body = response.body,
        )
        store.write(key, stored)
        log(
            "fetched ${response.status}: ${request.method} ${request.url} " +
                "(${stored.body.length} chars, final ${fetched.finalUrl})",
        )
        return FetchResult(
            stored.toSnapshot(certifierId(URI(stored.url)), fromCache = false, requestedUrl = uri.toString()),
        )
    }

    /**
     * Follows redirects manually so each hop is re-validated: allow-list,
     * robots, throttle, budget and size. Ktor itself is configured not to
     * auto-follow (see [KtorHttpTransport]).
     */
    private suspend fun fetchFollowing(
        request: HttpRequest,
        checkRobots: Boolean = true,
    ): FetchedResponse {
        var current = request
        var redirects = 0

        while (true) {
            val uri = requireAllowed(current.url)
            if (checkRobots && policy.obeyRobots) assertRobotsAllowed(uri, current.url)

            val response = fetchWithRetries(current)
            if (response.status !in REDIRECT_STATUSES) {
                return FetchedResponse(response, current.url)
            }

            if (redirects >= policy.maxRedirects) {
                throw SourceBlockedException(
                    current.url,
                    "redirect limit of ${policy.maxRedirects} exceeded",
                    status = response.status,
                )
            }
            val location = response.header("Location")
                ?: throw SourceUnavailableException(current.url, "HTTP ${response.status} without Location")
            val target = try {
                uri.resolve(location).toString()
            } catch (e: IllegalArgumentException) {
                throw SourceUnavailableException(current.url, "unparseable redirect Location '$location'", e)
            }
            current = when (response.status) {
                303 -> current.copy(url = target, method = "GET", body = null)
                else -> current.copy(url = target)
            }
            redirects++
        }
    }

    /** One logical attempt, with bounded retries; every wire call consumes budget. */
    private suspend fun fetchWithRetries(request: HttpRequest): HttpResponse {
        var attempt = 1
        while (true) {
            val response = try {
                reserveBudget()
                throttle(request.url)
                inFlightMutex.withLock { transport.execute(request) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: CertifierException) {
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

            if (response.body.toByteArray(Charsets.UTF_8).size > policy.maxResponseBytes) {
                throw SourceTooLargeException(request.url, policy.maxResponseBytes)
            }

            when {
                response.status in 200..299 -> return response
                response.status == 304 -> return response
                response.status in REDIRECT_STATUSES -> return response
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
                    throw SourceBlockedException(request.url, "HTTP ${response.status}", response.status)

                else -> throw SourceUnavailableException(
                    request.url,
                    "HTTP ${response.status}",
                    status = response.status,
                )
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

    private suspend fun assertRobotsAllowed(uri: URI, url: String) {
        val robotsPolicy = robotsPolicy(uri)
        val path = buildString {
            append(uri.rawPath.ifEmpty { "/" })
            uri.rawQuery?.let { append('?').append(it) }
        }
        if (!robotsPolicy.allows(path)) {
            throw SourceBlockedException(url, "robots.txt disallows $path")
        }
    }

    private suspend fun robotsPolicy(uri: URI): RobotsPolicy {
        val origin = originOf(uri)
        robotsMutex.withLock { robots[origin] }?.let { return it }

        val robotsUrl = "$origin/robots.txt"
        val policy = try {
            val response = fetchFollowing(
                HttpRequest(
                    method = "GET",
                    url = robotsUrl,
                    headers = mapOf(
                        "Accept" to "text/plain",
                        "User-Agent" to this.policy.userAgent,
                    ),
                ),
                checkRobots = false,
            ).response
            when {
                response.status in 200..299 -> RobotsPolicy.parse(response.body, this.policy.userAgent)
                response.status == 404 || response.status == 410 -> RobotsPolicy.allowAll
                response.status == 401 || response.status == 403 -> throw SourceBlockedException(
                    robotsUrl,
                    "robots.txt denied access (HTTP ${response.status})",
                    response.status,
                )
                else -> throw SourceUnavailableException(robotsUrl, "robots.txt returned HTTP ${response.status}")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SourceUnavailableException) {
            if (e.status == 404 || e.status == 410) {
                RobotsPolicy.allowAll
            } else {
                // Fail closed: if we cannot establish permission, we do not fetch.
                log("robots.txt unavailable for $origin (${e.message}); refusing to fetch")
                throw e
            }
        } catch (e: CertifierException) {
            // Fail closed: if we cannot establish permission, we do not fetch.
            log("robots.txt unavailable for $origin (${e.message}); refusing to fetch")
            throw e
        }

        robotsMutex.withLock { robots[origin] = policy }
        return policy
    }

    /** HTTPS-only plus the certifier's explicit host allow-list, on every hop. */
    private fun requireAllowed(raw: String): URI {
        val uri = try {
            URI(raw)
        } catch (e: IllegalArgumentException) {
            throw SourceBlockedException(raw, "unparseable URL", status = 0)
        }
        if (!uri.scheme.equals("https", ignoreCase = true)) {
            throw SourceBlockedException(raw, "HTTPS is required (got '${uri.scheme}')")
        }
        val host = uri.host?.lowercase()
            ?: throw SourceBlockedException(raw, "URL has no host")
        if (host !in allowedHosts) {
            throw SourceBlockedException(raw, "host '$host' is not on the certifier allow-list")
        }
        return uri
    }

    private fun cacheKey(request: HttpRequest, accept: String): String =
        sha256Hex("${request.method}\n${request.url}\n${request.body.orEmpty()}\n$accept")

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

    private fun StoredPage.toSnapshot(
        certifierId: String,
        fromCache: Boolean,
        requestedUrl: String = url,
    ) = SourceSnapshot(
        certifierId = certifierId,
        url = url,
        requestedUrl = requestedUrl,
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
        private val REDIRECT_STATUSES = setOf(301, 302, 303, 307, 308)

        fun create(
            policy: FetchPolicy,
            cacheDir: Path = defaultCacheDir(),
            transport: HttpTransport = KtorHttpTransport(
                requestTimeout = policy.requestTimeout,
                maxResponseBytes = policy.maxResponseBytes,
            ),
            store: SnapshotStore = FileSnapshotStore(cacheDir),
            clock: Clock = Clock.systemUTC(),
            sleeper: Suspender = Suspender.Real,
            log: (String) -> Unit = {},
        ): CertifierHttp = CertifierHttp(policy, transport, store, clock, sleeper, log)

        /** A client whose allow-list is exactly the certifier's own hosts. */
        fun createFor(
            profile: CertifierProfile,
            cacheDir: Path = defaultCacheDir(),
            log: (String) -> Unit = {},
        ): CertifierHttp = create(
            policy = FetchPolicy(userAgent = DEFAULT_USER_AGENT, allowedHosts = profile.allowedHosts),
            cacheDir = cacheDir,
            log = log,
        )

        /** A user-scoped cache dir; never the shared system temp directory. */
        fun defaultCacheDir(): Path {
            val home = System.getProperty("user.home") ?: "."
            return Path.of(home, ".cache", "tahirs-list", "certifiers")
        }
    }
}
