package com.tahirslist.certifiers.hma

import com.tahirslist.certifiers.core.CertifierHttp
import com.tahirslist.certifiers.core.FetchPolicy
import com.tahirslist.certifiers.core.HandCutDeclaration
import com.tahirslist.certifiers.core.HttpRequest
import com.tahirslist.certifiers.core.HttpResponse
import com.tahirslist.certifiers.core.HttpTransport
import com.tahirslist.certifiers.core.InMemorySnapshotStore
import com.tahirslist.certifiers.core.SourceParseException
import com.tahirslist.certifiers.core.Suspender
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.time.Duration
import kotlinx.coroutines.test.runTest

private fun fixture(name: String): String =
    checkNotNull(HmaParserTest::class.java.getResourceAsStream(name)) { "missing fixture $name" }
        .bufferedReader(Charsets.UTF_8)
        .use { it.readText() }

private object NoWait : Suspender {
    override suspend fun sleep(duration: Duration) = Unit
}

private fun http(transport: HttpTransport) = CertifierHttp(
    policy = FetchPolicy(
        userAgent = "TahirsListBot/0.1",
        allowedHosts = setOf("hmacanada.org", "www.hmacanada.org"),
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

class HmaParserTest : FunSpec({

    test("parses every outlet in the recorded response") {
        val result = HmaParser.parse(fixture("/hma-outlets.json"))

        result.outlets shouldHaveSize 8
        result.skipped shouldBe 0
    }

    test("reads the Elementor address, phone and website blocks") {
        val lahore = HmaParser.parse(fixture("/hma-outlets.json")).outlets
            .first { it.slug == "lahore-tikka-house" }

        lahore.name shouldBe "Lahore Tikka House"
        lahore.address.line shouldBe "1365 Gerrard St E"
        lahore.address.city shouldBe "Toronto"
        lahore.address.region shouldBe "Ontario"
        lahore.address.postalCode shouldBe "M4L 1Z3"
        lahore.phone shouldNotBe null
        lahore.website.orEmpty() shouldContain "lahoretikkahouse.com"
        lahore.certificationStatement shouldNotBe null
    }

    test("every recorded outlet yields a city") {
        val outlets = HmaParser.parse(fixture("/hma-outlets.json")).outlets

        outlets.all { it.address.city != null } shouldBe true
    }

    test("splits addresses without inventing missing parts") {
        HmaParser.parseAddress("1365 Gerrard St E, Toronto, Ontario, M4L 1Z3") shouldBe
            HmaAddress("1365 Gerrard St E", "Toronto", "Ontario", "M4L 1Z3")
        HmaParser.parseAddress("2501 Hampshire Gate #7b, Oakville, ON L6H 6C8") shouldBe
            HmaAddress("2501 Hampshire Gate #7b", "Oakville", "ON", "L6H 6C8")
        HmaParser.parseAddress("") shouldBe HmaAddress(null, null, null, null)
    }

    test("malformed JSON is a parse failure") {
        shouldThrow<SourceParseException> {
            HmaParser.parse("{ not json")
        }
    }
})

class HmaCertifiedOutletsTest : FunSpec({

    test("end-to-end parse from a recorded response") {
        runTest {
            val body = fixture("/hma-outlets.json")
            val transport = RoutingTransport {
                HttpResponse(200, mapOf("X-WP-TotalPages" to "1"), body)
            }
            val page = HmaCertifiedOutlets(http(transport)).outlets()

            page.records shouldHaveSize 8
            page.snapshot.header("X-WP-TotalPages") shouldBe "1"
            transport.requests.single().url shouldContain "parent=21800"
            transport.requests.single().url shouldContain "per_page=100"
        }
    }

    test("follows X-WP-TotalPages") {
        runTest {
            val pageTwo = """
                [{"id":1,"slug":"page-two","link":"https://hmacanada.org/hma-certified-restaurants/page-two/",
                  "title":{"rendered":"Page Two"},
                  "content":{"rendered":"<ul><li><span class='elementor-icon-list-icon'><i class='fas fa-map-marker-alt'></i></span><span class='elementor-icon-list-text'> 1 Queen St W, Toronto, Ontario, M5H 2M5</span></li></ul>"}}]
            """.trimIndent()

            val transport = RoutingTransport { request ->
                if (request.url.contains("&page=2")) {
                    HttpResponse(200, mapOf("X-WP-TotalPages" to "2"), pageTwo)
                } else {
                    HttpResponse(200, mapOf("X-WP-TotalPages" to "2"), fixture("/hma-outlets.json"))
                }
            }

            val page = HmaCertifiedOutlets(http(transport)).outlets()

            page.records shouldHaveSize 9
            page.snapshots shouldHaveSize 2
            transport.requests shouldHaveSize 2
            page.records.last().address.city shouldBe "Toronto"
        }
    }

    test("declares hand-cut") {
        runTest {
            val transport = RoutingTransport { HttpResponse(200, body = "[]") }
            val page = HmaCertifiedOutlets(http(transport)).outlets()

            page.profile.declaredHandCut shouldBe HandCutDeclaration.HAND_CUT
        }
    }
})
