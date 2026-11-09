package com.tahirslist.bootstrap.ingestion

import com.tahirslist.application.ingestion.CandidateBatchJson
import com.tahirslist.application.ingestion.LoadIngestionBatch
import com.tahirslist.application.ingestion.Resolutions
import com.tahirslist.application.ingestion.ResolutionsJson
import com.tahirslist.bootstrap.config.loadConfig
import com.tahirslist.ingestion.artifacts.FileRunArtifacts
import com.tahirslist.persistence.brand.ExposedBrandRepository
import com.tahirslist.persistence.db.DatabaseFactory
import com.tahirslist.persistence.db.Migrations
import com.tahirslist.persistence.listing.ExposedRestaurantListingRepository
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.runBlocking

/**
 * UC-03 CLI: review a candidate batch, resolve its findings, and — with
 * `--execute` — apply it in one transaction.
 *
 * Usage:
 * ```
 * :bootstrap:loadIngestBatch -PingestArgs="--run-id=sample-..."
 * :bootstrap:loadIngestBatch -PingestArgs="--run-id=sample-... --execute"
 * ```
 * Flags: `--run-id` (required), `--out-dir`, `--resolutions`, `--execute`,
 * `--load-run-id`, `--interactive`, `--no-interactive`.
 *
 * At preview time every conflict or held duplicate is turned into an entry in
 * the run's `resolutions.json` (generated, never hand-authored). With
 * `--interactive` (the shell script passes it on a terminal) the CLI asks one
 * question per finding and writes the answer immediately; otherwise it prints
 * what is undecided and where to answer it. `--execute` refuses while any
 * finding is unresolved.
 */
fun main(args: Array<String>) = runBlocking {
    val options = IngestOptions.parse(args)
    val runId = options["run-id"] ?: error("usage: loadIngestBatch --run-id=<id> [--execute]")
    val outDir = Path.of(options["out-dir"] ?: "ingest")
    val execute = options.flag("execute")
    val loadRunId = options["load-run-id"] ?: "load-" + DateTimeFormatter
        .ofPattern("yyyyMMdd'T'HHmmss'Z'")
        .withZone(ZoneOffset.UTC)
        .format(Clock.systemUTC().instant())

    val config = loadConfig()
    val dataSource = DatabaseFactory.dataSource(
        jdbcUrl = config.database.jdbcUrl,
        user = config.database.user,
        password = config.database.password,
        maxPoolSize = 2,
    )

    try {
        Migrations.migrate(
            jdbcUrl = config.database.jdbcUrl,
            user = config.database.user,
            password = config.database.password,
        )
        val database = DatabaseFactory.connect(dataSource)
        val artifacts = FileRunArtifacts(outDir)
        artifacts.refuseIfLoaded(runId)
        val batch = CandidateBatchJson.decode(artifacts.readCandidates(runId))

        val decisionsPath = options["resolutions"]?.let(Path::of) ?: artifacts.resolutionsPath(runId)
        var resolutions = read(decisionsPath, runId)

        val load = LoadIngestionBatch(
            listings = ExposedRestaurantListingRepository(database),
            brands = ExposedBrandRepository(database),
        )

        fun save(updated: Resolutions) {
            resolutions = updated
            if (decisionsPath == artifacts.resolutionsPath(runId)) {
                artifacts.writeResolutions(runId, updated)
            } else {
                Files.createDirectories(decisionsPath.parent ?: Path.of("."))
                Files.writeString(decisionsPath, ResolutionsJson.encode(updated), StandardCharsets.UTF_8)
            }
        }

        var review = load.preview(batch, resolutions)
        if (review.findings.isNotEmpty()) {
            // The sheet is generated from the preview: ids, evidence and the
            // question come from the tool, so nobody hand-authors a decision.
            save(resolutions.completeWith(review.findings))
            println("decision sheet: $decisionsPath")
        }

        // Explicit, never auto-detected: a CI run must fail rather than block on
        // a prompt. The shell script passes --interactive only on a terminal.
        val interactive = options.flag("interactive") && !options.flag("no-interactive")
        var rounds = 0
        while (interactive && review.findings.isNotEmpty() && rounds++ < 10) {
            resolutions = DecisionPrompts().answer(review.findings, resolutions) { save(it) }
            review = load.preview(batch, resolutions)
        }

        println(review.diffSummary())
        if (review.findings.isNotEmpty()) {
            println(ResolutionsJson.describe(review))
            println("No catalogue write: ${review.unresolved} decision(s) are unresolved.")
            println("Answer them interactively (attach a terminal), or edit $decisionsPath and re-run.")
            if (execute) error("refusing --execute: ${review.unresolved} unresolved decision(s) for run $runId")
            return@runBlocking
        }

        if (resolutions.claims.isNotEmpty() || resolutions.duplicates.isNotEmpty()) {
            // Always keep the applied decisions inside the run directory, even
            // when they were read from a custom path.
            artifacts.writeResolutions(runId, resolutions)
        }

        if (!execute) {
            println("Preview only — pass --execute to apply this diff in one transaction.")
            return@runBlocking
        }

        val result = load.execute(review)
        artifacts.markLoaded(runId, loadRunId)
        println("Load $loadRunId committed: $result")
        println("Run $runId is now marked loaded; it will not be loadable again.")
    } finally {
        dataSource.close()
    }
}

private fun read(path: Path, runId: String): Resolutions =
    if (Files.isRegularFile(path)) {
        ResolutionsJson.decode(Files.readString(path, StandardCharsets.UTF_8)).also {
            it.requireRun(runId)
        }
    } else {
        Resolutions.empty(runId)
    }
