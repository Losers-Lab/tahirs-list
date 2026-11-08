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
) = FetchPolicy(
    userAgent = TEST_UA,
    minInterval = minInterval,
    maxAttempts = maxAttempts,
    maxRequestsPerRun = maxRequests,
    cacheTtl = cacheTtl,
    obeyRobots = obeyRobots,
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
            FetchPolicy(userAgent = " ")
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
})
