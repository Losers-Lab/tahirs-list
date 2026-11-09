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
        lahore.address.region shouldBe "ON"
        lahore.address.postalCode shouldBe "M4L 1Z3"
        lahore.phone shouldNotBe null
        lahore.website.orEmpty() shouldContain "lahoretikkahouse.com"
        lahore.certificationStatement shouldNotBe null
    }

    test("a city is read only when the address separates one") {
        val outlets = HmaParser.parse(fixture("/hma-outlets.json")).outlets
        val bySlug = outlets.associateBy { it.slug }

        bySlug.getValue("lahore-tikka-house").address.city shouldBe "Toronto"
        bySlug.getValue("d-spot-desserts-ellesmere").address.city shouldBe "Scarborough"
        // These two publish the city glued to the street; storing the whole
        // segment as a city would put a street number in the catalogue.
        bySlug.getValue("passage-to-india").address.city shouldBe null
        bySlug.getValue("chachees-chai-cafe-scarborough").address.city shouldBe null
        // Whatever is read as a city must look like one.
        outlets.mapNotNull { it.address.city }.forEach { city ->
            city.any(Char::isDigit) shouldBe false
            city.equals("Canada", ignoreCase = true) shouldBe false
        }
    }

    test("splits addresses without inventing missing parts") {
        HmaParser.parseAddress("1365 Gerrard St E, Toronto, Ontario, M4L 1Z3") shouldBe
            HmaAddress("1365 Gerrard St E", "Toronto", "ON", "M4L 1Z3")
        HmaParser.parseAddress("2501 Hampshire Gate #7b, Oakville, ON L6H 6C8") shouldBe
            HmaAddress("2501 Hampshire Gate #7b", "Oakville", "ON", "L6H 6C8")
        HmaParser.parseAddress("") shouldBe HmaAddress(null, null, null, null)
    }

    test("a trailing country never becomes the city") {
        HmaParser.parseAddress("55 Mountainash Rd, Brampton, ON L6R 1W4, Canada") shouldBe
            HmaAddress("55 Mountainash Rd", "Brampton", "ON", "L6R 1W4")
        HmaParser.parseAddress("7690 Markham Road, Markham, ON, Canada L3S3K1") shouldBe
            HmaAddress("7690 Markham Road", "Markham", "ON", "L3S3K1")
    }

    test("province abbreviations with a dot are recognised") {
        HmaParser.parseAddress("2535 Appleby Line Unit #3, Burlington, Ont. L7L 0B6") shouldBe
            HmaAddress("2535 Appleby Line Unit #3", "Burlington", "ON", "L7L 0B6")
    }

    test("a postal code written before the city is lifted, not kept as the city") {
        HmaParser.parseAddress("5165 Dixie Road,L4W4G1 Mississauga") shouldBe
            HmaAddress("5165 Dixie Road", "Mississauga", null, "L4W4G1")
    }

    test("a broken postal code is dropped rather than stored as the city") {
        val address = HmaParser.parseAddress("1 Wexford Road, Unit 7A, Brampton, ON, L6Z2WL")

        address.city shouldBe "Brampton"
        address.region shouldBe "ON"
        address.postalCode shouldBe null
        address.line shouldBe "1 Wexford Road, Unit 7A"
    }

    test("a city glued to the street stays in the line and the city is null, not garbage") {
        val address = HmaParser.parseAddress("3417 Derry Road East Mississauga, ON L4T 1A8 Canada")

        address.city shouldBe null
        address.region shouldBe "ON"
        address.postalCode shouldBe "L4T 1A8"
        address.line shouldBe "3417 Derry Road East Mississauga"
    }

    test("the real Scarborough address keeps its city") {
        HmaParser.parseAddress("2058 Ellesmere Rd Unit 6, Scarborough, ON M1H 2V6") shouldBe
            HmaAddress("2058 Ellesmere Rd Unit 6", "Scarborough", "ON", "M1H 2V6")
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
