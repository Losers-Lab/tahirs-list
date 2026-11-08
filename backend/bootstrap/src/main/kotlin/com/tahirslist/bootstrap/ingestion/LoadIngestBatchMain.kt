package com.tahirslist.bootstrap.ingestion

import com.tahirslist.application.ingestion.CandidateBatchJson
import com.tahirslist.application.ingestion.LoadIngestionBatch
import com.tahirslist.bootstrap.config.loadConfig
import com.tahirslist.ingestion.artifacts.FileRunArtifacts
import com.tahirslist.persistence.brand.ExposedBrandRepository
import com.tahirslist.persistence.db.DatabaseFactory
import com.tahirslist.persistence.db.Migrations
import com.tahirslist.persistence.listing.ExposedRestaurantListingRepository
import java.nio.file.Path
import java.time.Clock
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.runBlocking

/**
 * UC-03 CLI: preview a reviewed candidate batch and, with `--execute`, apply it
 * in one transaction and mark the run loaded.
 *
 * Usage:
 * ```
 * :bootstrap:loadIngestBatch -PingestArgs="--run-id=sample-... "
 * :bootstrap:loadIngestBatch -PingestArgs="--run-id=sample-... --execute"
 * ```
 * Flags: `--run-id` (required), `--out-dir`, `--execute`, `--load-run-id`.
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
        val batch = CandidateBatchJson.decode(artifacts.readCandidates(runId))
        val load = LoadIngestionBatch(
            listings = ExposedRestaurantListingRepository(database),
            brands = ExposedBrandRepository(database),
        )

        val review = load.preview(batch)
        println(review.diffSummary())

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
