package com.tahirslist.ingestion.artifacts

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.paths.shouldExist
import io.kotest.matchers.paths.shouldNotExist
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.io.path.createTempDirectory
import kotlin.io.path.name

private val FIXED = Clock.fixed(Instant.parse("2026-11-08T12:00:00Z"), ZoneOffset.UTC)

class FileRunArtifactsTest : FunSpec({

    fun artifacts(root: Path = createTempDirectory("run-artifacts")) = FileRunArtifacts(root, FIXED)

    fun finish(store: FileRunArtifacts, runId: String = "run-1"): PendingRun = store.complete(
        runId = runId,
        candidatesJson = """{"candidates":[{"name":"A"}]}""",
        reportMarkdown = "# report",
        snapshots = mapOf("hms.html" to "<html>".toByteArray()),
    )

    fun complete(store: FileRunArtifacts, runId: String = "run-1"): PendingRun {
        store.begin(runId, PendingRunPolicy.IGNORE)
        return finish(store, runId)
    }

    test("writes a run under its own directory and reports it pending") {
        val store = artifacts()

        val pending = complete(store)

        pending.runId shouldBe "run-1"
        pending.candidatesSha256.length shouldBe 64
        pending.directory.resolve("run.json").shouldExist()
        pending.directory.resolve("candidates.json").shouldExist()
        pending.directory.resolve("report.md").shouldExist()
        pending.directory.resolve("snapshots/hms.html").shouldExist()
        store.findPending().map { it.runId } shouldBe listOf("run-1")
    }

    test("refuses to reuse a run id, so it cannot overwrite a run") {
        val store = artifacts()
        complete(store)

        shouldThrow<RunArtifactsExistException> {
            store.begin("run-1")
        }
    }

    test("a new run does not overwrite the previous run's artifacts") {
        val store = artifacts()
        complete(store, "run-1")
        val first = store.findPending().single().directory.resolve("candidates.json")
        val firstBody = Files.readString(first)

        complete(store, "run-2")

        Files.readString(first) shouldBe firstBody
        store.findPending().map { it.runId } shouldBe listOf("run-1", "run-2")
    }

    test("aborts when a pending run exists and the policy says so") {
        val store = artifacts()
        complete(store, "run-1")

        val error = shouldThrow<PendingRunsException> {
            store.begin("run-2", PendingRunPolicy.ABORT)
        }

        error.pending.map { it.runId } shouldBe listOf("run-1")
        store.findPending().map { it.runId } shouldBe listOf("run-1")
    }

    test("archives pending runs under unique names before starting a new one") {
        val store = artifacts()
        complete(store, "run-1")
        complete(store, "run-2")

        store.begin("run-3", PendingRunPolicy.ARCHIVE)
        finish(store, "run-3")

        store.findPending().map { it.runId } shouldBe listOf("run-3")
        val archiveRoot = store.archiveRoot()
        val archived = Files.list(archiveRoot).use { it.toList() }
        archived shouldHaveSize 2
        archived.map { it.name }.toSet() shouldHaveSize 2
        archiveRoot.resolve(archived[0].name).resolve("archive.json").shouldExist()
    }

    test("IGNORE starts beside pending runs without touching them") {
        val store = artifacts()
        complete(store, "run-1")

        store.begin("run-2", PendingRunPolicy.IGNORE)
        finish(store, "run-2")

        store.findPending().map { it.runId } shouldBe listOf("run-1", "run-2")
    }

    test("a run without a manifest is incomplete, never pending") {
        val store = artifacts()
        val dir = store.begin("run-1")
        Files.writeString(dir.resolve("candidates.json"), "{}")

        store.findPending() shouldHaveSize 0
        store.findIncomplete().map { it.name } shouldBe listOf("run-1")
    }

    test("loaded runs leave the pending set") {
        val store = artifacts()
        complete(store, "run-1")

        store.markLoaded("run-1", "load-1")

        store.findPending() shouldHaveSize 0
        store.findPending() shouldBe emptyList()
    }

    test("marking the same load twice is idempotent; a different load is refused") {
        val store = artifacts()
        complete(store, "run-1")
        store.markLoaded("run-1", "load-1")

        store.markLoaded("run-1", "load-1")

        shouldThrow<RunAlreadyLoadedException> {
            store.markLoaded("run-1", "load-2")
        }
    }

    test("abandoned runs leave the pending set") {
        val store = artifacts()
        complete(store, "run-1")

        store.abandon("run-1", "wrong metro selection")

        store.findPending() shouldHaveSize 0
    }

    test("detects candidates.json changed after the run completed") {
        val store = artifacts()
        val pending = complete(store)
        Files.writeString(pending.directory.resolve("candidates.json"), "{\"tampered\":true}")

        shouldThrow<ArtifactChecksumMismatchException> {
            store.readCandidates("run-1")
        }
        shouldThrow<ArtifactChecksumMismatchException> {
            store.markLoaded("run-1", "load-1")
        }
    }

    test("run ids cannot escape the artifact root") {
        val store = artifacts()

        shouldThrow<IllegalArgumentException> {
            store.begin("../escape")
        }
        shouldThrow<IllegalArgumentException> {
            store.begin("..")
        }
    }

    test("snapshot names cannot escape the run directory") {
        val store = artifacts()
        store.begin("run-1")

        shouldThrow<IllegalArgumentException> {
            store.complete(
                runId = "run-1",
                candidatesJson = "{}",
                reportMarkdown = "",
                snapshots = mapOf("../escape.html" to ByteArray(0)),
            )
        }
    }

    test("a corrupt manifest is surfaced as pending, not silently hidden") {
        val store = artifacts()
        val pending = complete(store)
        Files.writeString(pending.directory.resolve("run.json"), "{ not json")

        store.findPending().map { it.runId } shouldBe listOf("run-1")
    }
})
