package com.tahirslist.certifiers.hfsaa

import com.tahirslist.certifiers.core.CertifierHttp
import com.tahirslist.certifiers.core.FetchPolicy
import com.tahirslist.certifiers.core.HttpRequest
import com.tahirslist.certifiers.core.HttpResponse
import com.tahirslist.certifiers.core.HttpTransport
import com.tahirslist.certifiers.core.InMemorySnapshotStore
import com.tahirslist.certifiers.core.SourceBlockedException
import com.tahirslist.certifiers.core.SourceParseException
import com.tahirslist.certifiers.core.Suspender
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.time.Duration
import kotlinx.coroutines.test.runTest

private fun fixture(name: String): String =
    checkNotNull(HfsaaParserTest::class.java.getResourceAsStream(name)) { "missing fixture $name" }
        .bufferedReader(Charsets.UTF_8)
        .use { it.readText() }

private object NoWait : Suspender {
    override suspend fun sleep(duration: Duration) = Unit
}

private fun http(transport: HttpTransport) = CertifierHttp(
    policy = FetchPolicy(
        userAgent = "TahirsListBot/0.1",
        allowedHosts = setOf(
            "www.hfsaa.org",
            "hfsaa.org",
            "hfsaa-public-locator.developer-907.workers.dev",
            "pmolfcjxryccsisyhhwm.supabase.co",
        ),
        obeyRobots = false,
    ),
    transport = transport,
    store = InMemorySnapshotStore(),
    clock = Clock.fixed(Instant.parse("2026-11-08T00:00:00Z"), ZoneOffset.UTC),
    sleeper = NoWait,
)

private class RoutingTransport(
    private val handler: (HttpRequest) -> HttpResponse,
) : HttpTransport {
    val requests = mutableListOf<HttpRequest>()
    override suspend fun execute(request: HttpRequest): HttpResponse {
        requests += request
        return handler(request)
    }
}

class HfsaaParserTest : FunSpec({

    test("parses every entry in the recorded response") {
        val result = HfsaaParser.parse(fixture("/hfsaa-locator.json"))

        result.entries shouldHaveSize 13
        result.skipped shouldBe 0
    }

    test("keeps certificate ids, chapters and coordinates") {
        val entries = HfsaaParser.parse(fixture("/hfsaa-locator.json")).entries
        val newYork = entries.first { it.chapter == "New York" && it.type == HfsaaEntryType.RESTAURANT }

        newYork.certificateId shouldNotBe null
        newYork.certificateId!! shouldStartWith "R-"
        newYork.location shouldNotBe null
        newYork.googlePlaceId shouldNotBe null
        newYork.address.city shouldNotBe null
        newYork.address.state shouldBe "NY"
    }

    test("classifies meat markets separately") {
        val entries = HfsaaParser.parse(fixture("/hfsaa-locator.json")).entries

        entries.any { it.type == HfsaaEntryType.MEAT_MARKET } shouldBe true
    }

    test("tolerates a null cuisine") {
        val entries = HfsaaParser.parse(fixture("/hfsaa-locator.json")).entries

        entries.any { it.cuisine == null } shouldBe true
    }

    test("skips rows without an id or name and counts them") {
        val body = """
            [
              {"id":"a","name":"Good","type":"restaurant","chapter_name":"New York"},
              {"name":"No id","type":"restaurant"},
              {"id":"c","type":"restaurant"}
            ]
        """.trimIndent()

        val result = HfsaaParser.parse(body)

        result.entries shouldHaveSize 1
        result.skipped shouldBe 2
    }

    test("malformed JSON is a parse failure") {
        shouldThrow<SourceParseException> {
            HfsaaParser.parse("{ not json")
        }
    }
})

class HfsaaLocatorTest : FunSpec({

    test("reads the locator and filters by chapter") {
        runTest {
            val body = fixture("/hfsaa-locator.json")
            val transport = RoutingTransport { HttpResponse(200, body = body) }
            val locator = HfsaaLocator(http(transport))

            val page = locator.entries(chapter = "New York")

            page.records shouldHaveSize 6
            page.records.all { it.chapter == "New York" } shouldBe true
            page.snapshot.body.length shouldBe body.length
        }
    }

    test("sends the publishable key on both Supabase headers") {
        runTest {
            val transport = RoutingTransport { HttpResponse(200, body = "[]") }
            HfsaaLocator(http(transport)).entries()

            val request = transport.requests.single()
            request.url shouldContain "/rest/v1/rpc/get_public_locator_entries"
            request.headers["apikey"] shouldBe HfsaaProject.Default.publishableKey
            request.headers["Authorization"] shouldBe "Bearer ${HfsaaProject.Default.publishableKey}"
            request.headers["Content-Type"] shouldBe "application/json"
        }
    }

    test("re-discovers a rotated publishable key once and retries") {
        runTest {
            val body = fixture("/hfsaa-locator.json")
            val transport = RoutingTransport { request ->
                when {
                    request.url.contains("get_public_locator_entries") ->
                        if (request.headers["apikey"] == "sb_publishable_ROTATED") {
                            HttpResponse(200, body = body)
                        } else {
                            HttpResponse(401)
                        }

                    request.url.endsWith("/") ->
                        HttpResponse(200, body = """<script src="/assets/index-abc.js"></script>""")

                    request.url.endsWith("/assets/index-abc.js") ->
                        HttpResponse(200, body = """const k="sb_publishable_ROTATED";""")

                    else -> HttpResponse(404)
                }
            }
            val locator = HfsaaLocator(http(transport))

            val page = locator.entries()

            page.records shouldHaveSize 13
            val rpcRequests = transport.requests.filter { it.url.contains("get_public_locator_entries") }
            rpcRequests shouldHaveSize 2
            rpcRequests.last().headers["apikey"] shouldBe "sb_publishable_ROTATED"
        }
    }

    test("fails honestly when the key cannot be re-discovered") {
        runTest {
            val transport = RoutingTransport { request ->
                if (request.url.contains("get_public_locator_entries")) HttpResponse(401) else HttpResponse(404)
            }
            val locator = HfsaaLocator(http(transport), keyDiscovery = { null })

            shouldThrow<SourceBlockedException> {
                locator.entries()
            }
        }
    }

    test("does not re-discover a key after a 403") {
        runTest {
            var discoveries = 0
            val transport = RoutingTransport { HttpResponse(403) }
            val locator = HfsaaLocator(
                http(transport),
                keyDiscovery = {
                    discoveries++
                    "sb_publishable_ROTATED"
                },
            )

            shouldThrow<SourceBlockedException> {
                locator.entries()
            }
            discoveries shouldBe 0
            transport.requests shouldHaveSize 1
        }
    }

    test("never fetches a key bundle from a host outside the allow-list") {
        runTest {
            val transport = RoutingTransport { request ->
                when {
                    request.url.contains("get_public_locator_entries") -> HttpResponse(401)
                    request.url.endsWith("/") ->
                        HttpResponse(200, body = """<script src="https://evil.test/assets/x.js"></script>""")
                    else -> HttpResponse(200, body = """const k="sb_publishable_STOLEN";""")
                }
            }

            shouldThrow<SourceBlockedException> {
                HfsaaLocator(http(transport)).entries()
            }
            transport.requests.none { it.url.contains("evil.test") } shouldBe true
        }
    }
})
