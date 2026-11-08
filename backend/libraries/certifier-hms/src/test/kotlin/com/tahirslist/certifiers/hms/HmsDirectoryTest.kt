package com.tahirslist.certifiers.hms

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
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.time.Duration
import kotlinx.coroutines.test.runTest

private fun fixture(name: String): String =
    checkNotNull(HmsParserTest::class.java.getResourceAsStream(name)) { "missing fixture $name" }
        .bufferedReader(Charsets.UTF_8)
        .use { it.readText() }

private class FixedTransport(private val body: String) : HttpTransport {
    val requests = mutableListOf<HttpRequest>()
    override suspend fun execute(request: HttpRequest): HttpResponse {
        requests += request
        return HttpResponse(200, mapOf("Content-Type" to "text/html"), body)
    }
}

private object NoWait : Suspender {
    override suspend fun sleep(duration: Duration) = Unit
}

private fun http(transport: HttpTransport) = CertifierHttp(
    policy = FetchPolicy(userAgent = "TahirsListBot/0.1", obeyRobots = false),
    transport = transport,
    store = InMemorySnapshotStore(),
    clock = Clock.fixed(Instant.parse("2026-11-08T00:00:00Z"), ZoneOffset.UTC),
    sleeper = NoWait,
)

class HmsParserTest : FunSpec({

    test("parses every record in the recorded page") {
        val records = HmsParser.parse(fixture("/hms-listing.html"))

        records shouldHaveSize 20
        records.map { it.id }.toSet() shouldHaveSize 20
    }

    test("decodes names, categories, addresses and expiry") {
        val records = HmsParser.parse(fixture("/hms-listing.html"))
        val sal = records.first { it.name.contains("Nashville Hot Chicken") }

        sal.category shouldBe HmsCategory.RESTAURANT
        sal.address.line.orEmpty() shouldContain "746 S Central Expy"
        sal.address.city shouldBe "Richardson"
        sal.address.state shouldBe "Texas"
        sal.address.county shouldBe "Dallas County"
        sal.status shouldBe HmsCertificationStatus.CERTIFIED
        sal.location shouldNotBe null
        sal.expiresOn shouldNotBe null
        sal.listedOn shouldNotBe null
    }

    test("recovers the city when HMS leaves data-city-name blank") {
        val records = HmsParser.parse(fixture("/hms-listing.html"))
        val flushing = records.first { it.id == "1732" }

        flushing.address.city shouldBe "Flushing"
        flushing.address.line.orEmpty() shouldContain "Kissena Blvd"
    }

    test("keeps restaurants from more than one state") {
        val records = HmsParser.parse(fixture("/hms-listing.html"))

        records.mapNotNull { it.address.state }.toSet() shouldContain "Texas"
        records.mapNotNull { it.address.state }.toSet() shouldContain "New York"
    }

    test("a record without a map entry survives with no coordinates") {
        val records = HmsParser.parse(fixture("/hms-listing.html"))

        records.count { it.location == null } shouldBeGreaterThan 0
    }

    test("classifies non-restaurant categories") {
        val records = HmsParser.parse(fixture("/hms-listing.html"))

        records.any { it.category != HmsCategory.RESTAURANT } shouldBe true
    }

    test("deriveCity never invents a city from a state/zip segment") {
        HmsParser.deriveCity("825 W HIGGINS, SCHAUMBURG, IL 60194", "Illinois") shouldBe "SCHAUMBURG"
        HmsParser.deriveCity("123 Main St, IL 60194", "Illinois") shouldBe null
        HmsParser.deriveCity("", "") shouldBe null
    }

    test("a page with no parsable records is a parse failure") {
        shouldThrow<SourceParseException> {
            HmsParser.parse("<html><body><p>maintenance</p></body></html>")
        }
    }
})

class HmsDirectoryTest : FunSpec({

    test("end-to-end parse from a recorded response") {
        runTest {
            val transport = FixedTransport(fixture("/hms-listing.html"))
            val page = HmsDirectory(http(transport)).certifiedListing()

            page.records shouldHaveSize 20
            page.snapshot.body.length shouldBeGreaterThan 1_000
            page.snapshot.sha256.length shouldBe 64
            page.snapshot.fromCache shouldBe false
            transport.requests.single().url shouldBe HmsDirectory.LISTING_URL
            transport.requests.single().headers["Accept"] shouldBe "text/html"
            page.warnings.any { it.contains("coordinates") } shouldBe true
        }
    }

    test("certifiedRestaurants filters the page") {
        runTest {
            val page = HmsDirectory(http(FixedTransport(fixture("/hms-listing.html")))).certifiedRestaurants()

            page.records.all { it.isRestaurant } shouldBe true
            page.records.size shouldBe 16
        }
    }

    test("declares hand-cut, and the declaration is not the tier") {
        runTest {
            val page = HmsDirectory(http(FixedTransport(fixture("/hms-listing.html")))).certifiedListing()

            page.profile.declaredHandCut shouldBe HandCutDeclaration.HAND_CUT
            page.profile.handCutBasis.isNotBlank() shouldBe true
        }
    }
})
