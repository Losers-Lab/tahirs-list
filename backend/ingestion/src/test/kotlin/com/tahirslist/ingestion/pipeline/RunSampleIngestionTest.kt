package com.tahirslist.ingestion.pipeline

import com.tahirslist.application.geo.CheckGeocoderHealth
import com.tahirslist.application.ingestion.CandidateBatch
import com.tahirslist.application.ingestion.CandidateBatchJson
import com.tahirslist.application.ingestion.RejectReason
import com.tahirslist.certifiers.core.SourceUnavailableException
import com.tahirslist.ingestion.artifacts.ArtifactStore
import com.tahirslist.ingestion.artifacts.PendingRun
import com.tahirslist.ingestion.artifacts.PendingRunPolicy
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.nio.file.Path
import java.time.Instant
import kotlinx.coroutines.test.runTest

class FakeArtifacts : ArtifactStore {
    var begun: String? = null
    var completedRun: String? = null
    var candidatesJson: String? = null
    var reportMarkdown: String? = null
    var snapshots: Map<String, ByteArray> = emptyMap()

    override fun begin(runId: String, onPending: PendingRunPolicy): Path {
        begun = runId
        return Path.of("/tmp", runId)
    }

    override fun complete(
        runId: String,
        candidatesJson: String,
        reportMarkdown: String,
        snapshots: Map<String, ByteArray>,
    ): PendingRun {
        completedRun = runId
        this.candidatesJson = candidatesJson
        this.reportMarkdown = reportMarkdown
        this.snapshots = snapshots
        return PendingRun(runId, Path.of("/tmp", runId), Instant.EPOCH, "x", "y")
    }
}

class FakeFeed(
    override val sourceId: String,
    private val listings: List<com.tahirslist.ingestion.SourceListing>,
    private val failure: Throwable? = null,
) : DirectoryFeed {
    var fetches: Int = 0

    override suspend fun fetch(refresh: Boolean): DirectoryPage {
        fetches++
        failure?.let { throw it }
        return DirectoryPage(sourceId, listings, emptyList())
    }
}

fun namedGeocoder() = FakeGeocoder { address ->
    val name = address.substringBefore(',')
    place("$name, Toronto, Ontario, Canada", name = name)
}

class RunSampleIngestionTest : FunSpec({

    test("a failed pre-flight aborts before any directory request and writes nothing") {
        val feed = FakeFeed("hms", listOf(sourceListing()))
        val artifacts = FakeArtifacts()
        val run = RunSampleIngestion(
            feeds = listOf(feed),
            health = CheckGeocoderHealth(FakeGeocoder { null }),
            artifacts = artifacts,
            factory = CandidateFactory(namedGeocoder()),
        )

        shouldThrow<GeocoderUnavailableException> {
            run.execute(IngestSelection(runId = "run-1", metros = setOf("Toronto")))
        }
        feed.fetches shouldBe 0
        artifacts.begun shouldBe "run-1"
        artifacts.completedRun shouldBe null
    }

    test("a run keeps metro rows, merges duplicates and writes candidates + report") {
        val hms = FakeFeed(
            "hms",
            listOf(
                sourceListing(record = "H-1", name = "Paramount Fine Foods", city = "Toronto"),
                sourceListing(
                    record = "H-2",
                    name = "The Halal Guys",
                    city = "Dallas",
                    lat = 32.7767,
                    lng = -96.7970,
                ),
            ),
        )
        val hfsaa = FakeFeed(
            "hfsaa",
            listOf(sourceListing(source = "hfsaa", record = "R-1", name = "Mamoun's Falafel", city = "Toronto")),
        )
        val artifacts = FakeArtifacts()
        val run = RunSampleIngestion(
            feeds = listOf(hms, hfsaa),
            health = CheckGeocoderHealth(namedGeocoder()),
            artifacts = artifacts,
            factory = CandidateFactory(namedGeocoder()),
        )

        val result = run.execute(IngestSelection(runId = "run-1", metros = setOf("Toronto")))

        result.report.candidates.map { it.name }.shouldContainExactly("Paramount Fine Foods", "Mamoun's Falafel")
        result.report.rejects shouldBe emptyList()
        artifacts.completedRun shouldBe "run-1"
        artifacts.reportMarkdown!!.contains("| hms | Toronto |") shouldBe true
        // The reviewer must be able to see each candidate and its citation(s).
        artifacts.reportMarkdown!!.contains("## Candidates and claims") shouldBe true
        artifacts.reportMarkdown!!.contains("hms::H-1") shouldBe true
        artifacts.reportMarkdown!!.contains("hfsaa::R-1") shouldBe true
        CandidateBatchJson.decode(artifacts.candidatesJson!!).candidates.size shouldBe 2
    }

    test("the per-metro cap is filled round-robin across sources") {
        val hms = FakeFeed(
            "hms",
            (1..3).map { sourceListing(record = "H-$it", name = "HMS Place $it") },
        )
        val hfsaa = FakeFeed(
            "hfsaa",
            (1..3).map {
                sourceListing(
                    source = "hfsaa",
                    record = "R-$it",
                    name = "HFSAA Place $it",
                    country = "United States",
                )
            },
        )
        val run = RunSampleIngestion(
            feeds = listOf(hms, hfsaa),
            health = CheckGeocoderHealth(namedGeocoder()),
            artifacts = FakeArtifacts(),
            factory = CandidateFactory(namedGeocoder()),
        )

        val result = run.execute(IngestSelection(runId = "run-1", metros = setOf("Toronto"), capPerMetro = 4))

        result.report.candidates.size shouldBe 4
        result.report.counts.sumOf { it.capped } shouldBe 2
        result.report.candidates.count { it.claims.single().source == "hms" } shouldBe 2
        result.report.candidates.count { it.claims.single().source == "hfsaa" } shouldBe 2
    }

    test("one unreachable source is a reject; the rest of the run continues") {
        val broken = FakeFeed("hms", emptyList(), SourceUnavailableException("https://hmsusa.org", "offline"))
        val healthy = FakeFeed("hma", listOf(sourceListing(source = "hma", record = "M-1", name = "Bamiyan Kabob")))
        val artifacts = FakeArtifacts()
        val run = RunSampleIngestion(
            feeds = listOf(broken, healthy),
            health = CheckGeocoderHealth(namedGeocoder()),
            artifacts = artifacts,
            factory = CandidateFactory(namedGeocoder()),
        )

        val result = run.execute(IngestSelection(runId = "run-1", metros = setOf("Toronto")))

        result.report.rejects.single().reason shouldBe RejectReason.SOURCE_UNAVAILABLE
        result.report.candidates.single().name shouldBe "Bamiyan Kabob"
        artifacts.completedRun shouldBe "run-1"
    }

    test("a row the geocoder cannot match is rejected, not given a location") {
        val feed = FakeFeed("hms", listOf(sourceListing(name = "Unknown Place")))
        val run = RunSampleIngestion(
            feeds = listOf(feed),
            health = CheckGeocoderHealth(namedGeocoder()),
            artifacts = FakeArtifacts(),
            factory = CandidateFactory(FakeGeocoder { if (it.startsWith("New York")) place("New York") else null }),
        )

        val result = run.execute(IngestSelection(runId = "run-1", metros = setOf("Toronto")))

        result.report.rejects.single().reason shouldBe RejectReason.NO_GEOCODER_MATCH
        result.report.candidates shouldBe emptyList()
    }

    test("a row with no coordinates is geocoded once to decide its metro, and the build reuses it") {
        val geocoder = namedGeocoder()
        val row = sourceListing(
            source = "hma",
            record = "M-1",
            name = "Bamiyan Kabob",
            lat = null,
            lng = null,
        )
        val run = RunSampleIngestion(
            feeds = listOf(FakeFeed("hma", listOf(row))),
            health = CheckGeocoderHealth(geocoder),
            artifacts = FakeArtifacts(),
            factory = CandidateFactory(geocoder),
        )

        val result = run.execute(IngestSelection(runId = "run-1", metros = setOf("Toronto")))

        result.report.candidates.single().metro shouldBe "Toronto"
        // one pre-flight + one locate; the candidate build reuses the locate.
        geocoder.queries.size shouldBe 2
    }

    test("a row outside every circle is skipped without a geocoder call and reported as a gap") {
        val geocoder = namedGeocoder()
        val run = RunSampleIngestion(
            feeds = listOf(
                FakeFeed(
                    "hms",
                    listOf(
                        sourceListing(record = "H-1", name = "Inside"),
                        // Vancouver: outside every configured circle.
                        sourceListing(record = "H-2", name = "Outside", lat = 49.2827, lng = -123.1207),
                    ),
                ),
            ),
            health = CheckGeocoderHealth(geocoder),
            artifacts = FakeArtifacts(),
            factory = CandidateFactory(geocoder),
        )

        val result = run.execute(IngestSelection(runId = "run-1", metros = setOf("Toronto")))

        result.report.candidates.single().name shouldBe "Inside"
        // one pre-flight + the selected row; the outside row never reaches the geocoder.
        geocoder.queries.size shouldBe 2
        result.report.gaps.any { it.contains("outside every configured metro") } shouldBe true
    }

    test("an unlocated row is rejected once, not also counted as outside") {
        val geocoder = FakeGeocoder { null }
        val row = sourceListing(record = "M-1", name = "Nowhere", lat = null, lng = null)
        val run = RunSampleIngestion(
            feeds = listOf(FakeFeed("hma", listOf(row))),
            health = CheckGeocoderHealth(FakeGeocoder { place("Toronto") }),
            artifacts = FakeArtifacts(),
            factory = CandidateFactory(geocoder),
        )

        val result = run.execute(IngestSelection(runId = "run-1", metros = setOf("Toronto")))

        result.report.rejects.single().reason shouldBe RejectReason.NO_GEOCODER_MATCH
        result.report.gaps.none { it.contains("outside every configured metro") } shouldBe true
    }

    test("a row in a configured metro that was not selected is reported as a gap") {
        val run = RunSampleIngestion(
            feeds = listOf(
                FakeFeed(
                    "hms",
                    listOf(
                        sourceListing(record = "H-1", name = "Toronto Place"),
                        sourceListing(record = "H-2", name = "Dallas Place", lat = 32.7767, lng = -96.7970),
                    ),
                ),
            ),
            health = CheckGeocoderHealth(namedGeocoder()),
            artifacts = FakeArtifacts(),
            factory = CandidateFactory(namedGeocoder()),
        )

        val result = run.execute(IngestSelection(runId = "run-1", metros = setOf("Toronto")))

        result.report.candidates.single().name shouldBe "Toronto Place"
        result.report.gaps.any { it.contains("configured metro that was not selected") } shouldBe true
    }

    test("a geocoder outage mid-run aborts before the manifest") {
        val artifacts = FakeArtifacts()
        val run = RunSampleIngestion(
            feeds = listOf(FakeFeed("hms", listOf(sourceListing()))),
            health = CheckGeocoderHealth(namedGeocoder()),
            artifacts = artifacts,
            factory = CandidateFactory(
                FakeGeocoder { address ->
                    if (address.startsWith("New York")) place("New York") else
                        throw com.tahirslist.application.geo.GeocodingException("down")
                },
            ),
        )

        shouldThrow<GeocoderUnavailableException> {
            run.execute(IngestSelection(runId = "run-1", metros = setOf("Toronto")))
        }
        artifacts.completedRun shouldBe null
    }
})
