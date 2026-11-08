package com.tahirslist.certifiers.core

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest

private const val TEST_UA = "TahirsListBot/0.1"

class MutableClock(private var current: Instant = Instant.parse("2026-11-08T00:00:00Z")) : Clock() {
    override fun instant(): Instant = current
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = this

    fun advance(duration: Duration) {
        current = current.plusMillis(duration.inWholeMilliseconds)
    }
}

private class RecordingSleeper(private val clock: MutableClock) : Suspender {
    val sleeps = mutableListOf<Duration>()

    override suspend fun sleep(duration: Duration) {
        sleeps += duration
        clock.advance(duration)
    }
}

private class ScriptedTransport(private val handler: suspend (HttpRequest) -> HttpResponse) : HttpTransport {
    val requests = mutableListOf<HttpRequest>()

    override suspend fun execute(request: HttpRequest): HttpResponse {
        requests += request
        return handler(request)
    }
}

private fun policy(
    minInterval: Duration = Duration.ZERO,
    maxAttempts: Int = 3,
    maxRequests: Int = 10,
    cacheTtl: Duration = Duration.ZERO,
    obeyRobots: Boolean = false,
    maxRedirects: Int = 3,
    maxResponseBytes: Int = 16 * 1024 * 1024,
) = FetchPolicy(
    userAgent = TEST_UA,
    allowedHosts = setOf("example.test", "one.test", "two.test"),
    minInterval = minInterval,
    maxAttempts = maxAttempts,
    maxRequestsPerRun = maxRequests,
    cacheTtl = cacheTtl,
    obeyRobots = obeyRobots,
    maxRedirects = maxRedirects,
    maxResponseBytes = maxResponseBytes,
)

class CertifierHttpTest : FunSpec({

    test("returns the body and records a snapshot") {
        runTest {
            val clock = MutableClock()
            val transport = ScriptedTransport { HttpResponse(200, mapOf("Content-Type" to "text/html"), "hello") }
            val client = CertifierHttp(policy(), transport, InMemorySnapshotStore(), clock, RecordingSleeper(clock))

            val result = client.get("https://example.test/list")

            result.snapshot.body shouldBe "hello"
            result.snapshot.status shouldBe 200
            result.snapshot.contentType shouldBe "text/html"
            result.snapshot.fromCache shouldBe false
            result.snapshot.sha256.length shouldBe 64
            transport.requests.single().headers["User-Agent"] shouldBe TEST_UA
        }
    }

    test("serves a repeat call from the cache without network I/O") {
        runTest {
            val clock = MutableClock()
            val transport = ScriptedTransport { HttpResponse(200, body = "cached body") }
            val store = InMemorySnapshotStore()
            val client = CertifierHttp(policy(cacheTtl = 24.hours), transport, store, clock, RecordingSleeper(clock))

            client.get("https://example.test/list")
            val second = client.get("https://example.test/list")

            transport.requests.size shouldBe 1
            second.snapshot.fromCache shouldBe true
            second.snapshot.body shouldBe "cached body"
        }
    }

    test("refetches once the snapshot is older than the TTL") {
        runTest {
            val clock = MutableClock()
            val transport = ScriptedTransport { HttpResponse(200, body = "body") }
            val client = CertifierHttp(
                policy(cacheTtl = 1.hours),
                transport,
                InMemorySnapshotStore(),
                clock,
                RecordingSleeper(clock),
            )

            client.get("https://example.test/list")
            clock.advance(2.hours)
            client.get("https://example.test/list")

            transport.requests.size shouldBe 2
        }
    }

    test("revalidates with If-None-Match and accepts 304") {
        runTest {
            val clock = MutableClock()
            var call = 0
            val transport = ScriptedTransport {
                call++
                if (call == 1) {
                    HttpResponse(200, mapOf("ETag" to "\"abc\"", "Content-Type" to "application/json"), """{"a":1}""")
                } else {
                    HttpResponse(304)
                }
            }
            val client = CertifierHttp(
                policy(cacheTtl = 1.hours),
                transport,
                InMemorySnapshotStore(),
                clock,
                RecordingSleeper(clock),
            )

            client.get("https://example.test/data")
            clock.advance(2.hours)
            val second = client.get("https://example.test/data")

            transport.requests[1].headers["If-None-Match"] shouldBe "\"abc\""
            second.snapshot.body shouldBe """{"a":1}"""
            second.snapshot.fromCache shouldBe true
        }
    }

    test("retries a 503 with exponential backoff and then succeeds") {
        runTest {
            val clock = MutableClock()
            var call = 0
            val transport = ScriptedTransport {
                call++
                if (call == 1) HttpResponse(503) else HttpResponse(200, body = "ok")
            }
            val sleeper = RecordingSleeper(clock)
            val client = CertifierHttp(policy(maxAttempts = 3), transport, InMemorySnapshotStore(), clock, sleeper)

            client.get("https://example.test/flaky").snapshot.body shouldBe "ok"

            sleeper.sleeps shouldContain 2.seconds
            transport.requests.size shouldBe 2
        }
    }

    test("honours Retry-After on 429") {
        runTest {
            val clock = MutableClock()
            var call = 0
            val transport = ScriptedTransport {
                call++
                if (call == 1) HttpResponse(429, mapOf("Retry-After" to "7")) else HttpResponse(200, body = "ok")
            }
            val sleeper = RecordingSleeper(clock)
            val client = CertifierHttp(policy(), transport, InMemorySnapshotStore(), clock, sleeper)

            client.get("https://example.test/limited")

            sleeper.sleeps shouldContain 7.seconds
        }
    }

    test("surfaces a persistent 503 after maxAttempts") {
        runTest {
            val clock = MutableClock()
            val transport = ScriptedTransport { HttpResponse(503) }
            val client = CertifierHttp(policy(maxAttempts = 3), transport, InMemorySnapshotStore(), clock, RecordingSleeper(clock))

            shouldThrow<SourceUnavailableException> {
                client.get("https://example.test/down")
            }
            transport.requests.size shouldBe 3
        }
    }

    test("blocks immediately on 403") {
        runTest {
            val clock = MutableClock()
            val transport = ScriptedTransport { HttpResponse(403) }
            val client = CertifierHttp(policy(), transport, InMemorySnapshotStore(), clock, RecordingSleeper(clock))

            shouldThrow<SourceBlockedException> {
                client.get("https://example.test/forbidden")
            }
            transport.requests.size shouldBe 1
        }
    }

    test("enforces the per-run request budget") {
        runTest {
            val clock = MutableClock()
            val transport = ScriptedTransport { HttpResponse(200, body = "ok") }
            val client = CertifierHttp(policy(maxRequests = 1), transport, InMemorySnapshotStore(), clock, RecordingSleeper(clock))

            client.get("https://example.test/one")

            shouldThrow<SourceBudgetExceededException> {
                client.get("https://example.test/two")
            }
        }
    }

    test("spaces requests to the same host by minInterval") {
        runTest {
            val clock = MutableClock()
            val transport = ScriptedTransport { HttpResponse(200, body = "ok") }
            val sleeper = RecordingSleeper(clock)
            val client = CertifierHttp(
                policy(minInterval = 1.seconds),
                transport,
                InMemorySnapshotStore(),
                clock,
                sleeper,
            )

            client.get("https://example.test/one")
            client.get("https://example.test/two")

            sleeper.sleeps shouldContain 1.seconds
        }
    }

    test("does not throttle across different hosts") {
        runTest {
            val clock = MutableClock()
            val transport = ScriptedTransport { HttpResponse(200, body = "ok") }
            val sleeper = RecordingSleeper(clock)
            val client = CertifierHttp(
                policy(minInterval = 1.seconds),
                transport,
                InMemorySnapshotStore(),
                clock,
                sleeper,
            )

            client.get("https://one.test/a")
            client.get("https://two.test/b")

            sleeper.sleeps.size shouldBe 0
        }
    }

    test("obeys a robots.txt disallow without fetching the page") {
        runTest {
            val clock = MutableClock()
            val transport = ScriptedTransport { request ->
                if (request.url.endsWith("/robots.txt")) {
                    HttpResponse(200, body = "User-agent: *\nDisallow: /private\n")
                } else {
                    HttpResponse(200, body = "should not be fetched")
                }
            }
            val client = CertifierHttp(
                policy(obeyRobots = true),
                transport,
                InMemorySnapshotStore(),
                clock,
                RecordingSleeper(clock),
            )

            shouldThrow<SourceBlockedException> {
                client.get("https://example.test/private/page")
            }
            transport.requests.none { it.url.endsWith("/private/page") } shouldBe true
        }
    }

    test("caches the robots policy per origin") {
        runTest {
            val clock = MutableClock()
            val transport = ScriptedTransport { request ->
                if (request.url.endsWith("/robots.txt")) {
                    HttpResponse(200, body = "User-agent: *\nAllow: /\n")
                } else {
                    HttpResponse(200, body = "ok")
                }
            }
            val client = CertifierHttp(
                policy(obeyRobots = true, cacheTtl = 24.hours),
                transport,
                InMemorySnapshotStore(),
                clock,
                RecordingSleeper(clock),
            )

            client.get("https://example.test/one")
            client.get("https://example.test/two")

            transport.requests.count { it.url.endsWith("/robots.txt") } shouldBe 1
        }
    }

    test("refuses a malformed policy") {
        shouldThrow<IllegalArgumentException> {
            FetchPolicy(userAgent = " ", allowedHosts = setOf("example.test"))
        }
    }

    test("snapshot toString never includes the body") {
        runTest {
            val clock = MutableClock()
            val transport = ScriptedTransport { HttpResponse(200, body = "SECRET-BODY") }
            val client = CertifierHttp(policy(), transport, InMemorySnapshotStore(), clock, RecordingSleeper(clock))

            val snapshot = client.get("https://example.test/x").snapshot

            snapshot.toString().contains("SECRET-BODY") shouldBe false
            snapshot shouldNotBe null
        }
    }

    test("refuses plain HTTP") {
        runTest {
            val clock = MutableClock()
            val transport = ScriptedTransport { HttpResponse(200, body = "ok") }
            val client = CertifierHttp(policy(), transport, InMemorySnapshotStore(), clock, RecordingSleeper(clock))

            shouldThrow<SourceBlockedException> {
                client.get("http://example.test/list")
            }
            transport.requests.size shouldBe 0
        }
    }

    test("refuses a host outside the certifier allow-list") {
        runTest {
            val clock = MutableClock()
            val transport = ScriptedTransport { HttpResponse(200, body = "ok") }
            val client = CertifierHttp(policy(), transport, InMemorySnapshotStore(), clock, RecordingSleeper(clock))

            shouldThrow<SourceBlockedException> {
                client.get("https://evil.test/list")
            }
            transport.requests.size shouldBe 0
        }
    }

    test("follows an allowed redirect and records the final URL") {
        runTest {
            val clock = MutableClock()
            val transport = ScriptedTransport { request ->
                if (request.url.endsWith("/start")) {
                    HttpResponse(302, mapOf("Location" to "/final"))
                } else {
                    HttpResponse(200, body = "served")
                }
            }
            val client = CertifierHttp(policy(), transport, InMemorySnapshotStore(), clock, RecordingSleeper(clock))

            val snapshot = client.get("https://example.test/start").snapshot

            snapshot.url shouldBe "https://example.test/final"
            snapshot.requestedUrl shouldBe "https://example.test/start"
            snapshot.body shouldBe "served"
            transport.requests.map { it.url } shouldBe listOf(
                "https://example.test/start",
                "https://example.test/final",
            )
        }
    }

    test("refuses a redirect to a host outside the allow-list") {
        runTest {
            val clock = MutableClock()
            val transport = ScriptedTransport { request ->
                if (request.url.endsWith("/start")) {
                    HttpResponse(302, mapOf("Location" to "https://evil.test/final"))
                } else {
                    HttpResponse(200, body = "should not be fetched")
                }
            }
            val client = CertifierHttp(policy(), transport, InMemorySnapshotStore(), clock, RecordingSleeper(clock))

            shouldThrow<SourceBlockedException> {
                client.get("https://example.test/start")
            }
            transport.requests.map { it.url } shouldBe listOf("https://example.test/start")
        }
    }

    test("refuses a redirect to plain HTTP") {
        runTest {
            val clock = MutableClock()
            val transport = ScriptedTransport {
                HttpResponse(302, mapOf("Location" to "http://example.test/final"))
            }
            val client = CertifierHttp(policy(), transport, InMemorySnapshotStore(), clock, RecordingSleeper(clock))

            shouldThrow<SourceBlockedException> {
                client.get("https://example.test/start")
            }
        }
    }

    test("caps the redirect chain") {
        runTest {
            val clock = MutableClock()
            var n = 0
            val transport = ScriptedTransport {
                n++
                HttpResponse(302, mapOf("Location" to "/hop-$n"))
            }
            val client = CertifierHttp(
                policy(maxRedirects = 2),
                transport,
                InMemorySnapshotStore(),
                clock,
                RecordingSleeper(clock),
            )

            shouldThrow<SourceBlockedException> {
                client.get("https://example.test/start")
            }
            transport.requests.size shouldBe 3
        }
    }

    test("counts each redirect hop against the request budget") {
        runTest {
            val clock = MutableClock()
            val transport = ScriptedTransport { request ->
                if (request.url.endsWith("/start")) {
                    HttpResponse(302, mapOf("Location" to "/final"))
                } else {
                    HttpResponse(200, body = "ok")
                }
            }
            val client = CertifierHttp(
                policy(maxRequests = 1),
                transport,
                InMemorySnapshotStore(),
                clock,
                RecordingSleeper(clock),
            )

            shouldThrow<SourceBudgetExceededException> {
                client.get("https://example.test/start")
            }
        }
    }

    test("counts retries against the request budget") {
        runTest {
            val clock = MutableClock()
            val transport = ScriptedTransport { HttpResponse(503) }
            val client = CertifierHttp(
                policy(maxAttempts = 3, maxRequests = 2),
                transport,
                InMemorySnapshotStore(),
                clock,
                RecordingSleeper(clock),
            )

            shouldThrow<SourceBudgetExceededException> {
                client.get("https://example.test/flaky")
            }
            transport.requests.size shouldBe 2
        }
    }

    test("rejects a response over the size cap") {
        runTest {
            val clock = MutableClock()
            val transport = ScriptedTransport { HttpResponse(200, body = "123456") }
            val client = CertifierHttp(
                policy(maxResponseBytes = 5),
                transport,
                InMemorySnapshotStore(),
                clock,
                RecordingSleeper(clock),
            )

            shouldThrow<SourceTooLargeException> {
                client.get("https://example.test/big")
            }
        }
    }

    test("fails closed when robots.txt denies access") {
        runTest {
            val clock = MutableClock()
            val transport = ScriptedTransport { request ->
                if (request.url.endsWith("/robots.txt")) {
                    HttpResponse(403)
                } else {
                    HttpResponse(200, body = "should not be fetched")
                }
            }
            val client = CertifierHttp(
                policy(obeyRobots = true),
                transport,
                InMemorySnapshotStore(),
                clock,
                RecordingSleeper(clock),
            )

            shouldThrow<SourceBlockedException> {
                client.get("https://example.test/page")
            }
            transport.requests.none { it.url.endsWith("/page") } shouldBe true
        }
    }

    test("allows fetching when robots.txt is absent") {
        runTest {
            val clock = MutableClock()
            val transport = ScriptedTransport { request ->
                if (request.url.endsWith("/robots.txt")) HttpResponse(404) else HttpResponse(200, body = "ok")
            }
            val client = CertifierHttp(
                policy(obeyRobots = true),
                transport,
                InMemorySnapshotStore(),
                clock,
                RecordingSleeper(clock),
            )

            client.get("https://example.test/page").snapshot.body shouldBe "ok"
        }
    }

    test("uses a different cache entry per Accept header") {
        runTest {
            val clock = MutableClock()
            val transport = ScriptedTransport { HttpResponse(200, body = "ok") }
            val client = CertifierHttp(
                policy(cacheTtl = 24.hours),
                transport,
                InMemorySnapshotStore(),
                clock,
                RecordingSleeper(clock),
            )

            client.get("https://example.test/list", accept = "text/html")
            client.get("https://example.test/list", accept = "application/json")

            transport.requests.size shouldBe 2
        }
    }

    test("does not re-use a cached 403 as a success") {
        runTest {
            val clock = MutableClock()
            val transport = ScriptedTransport { HttpResponse(403) }
            val client = CertifierHttp(policy(), transport, InMemorySnapshotStore(), clock, RecordingSleeper(clock))

            shouldThrow<SourceBlockedException> { client.get("https://example.test/forbidden") }
            shouldThrow<SourceBlockedException> { client.get("https://example.test/forbidden") }
        }
    }
})
