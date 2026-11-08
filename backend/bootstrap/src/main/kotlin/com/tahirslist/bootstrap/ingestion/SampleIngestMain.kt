package com.tahirslist.bootstrap.ingestion

import com.tahirslist.application.geo.CheckGeocoderHealth
import com.tahirslist.geocoding.PhotonGeocoder
import com.tahirslist.ingestion.artifacts.FileRunArtifacts
import com.tahirslist.ingestion.artifacts.PendingRunPolicy
import com.tahirslist.ingestion.pipeline.CandidateFactory
import com.tahirslist.ingestion.pipeline.IngestSelection
import com.tahirslist.ingestion.pipeline.RunSampleIngestion
import com.tahirslist.ingestion.pipeline.tierOneFeeds
import java.nio.file.Path
import java.time.Clock
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.runBlocking

/**
 * UC-01 CLI: fetch the three Tier-1 directories, geocode a sample, and write a
 * reviewed run directory. Never touches the catalogue; the load is a separate
 * command over the reviewed artifacts.
 *
 * Usage:
 * ```
 * :bootstrap:ingestSample -PingestArgs="--metros=Toronto --caps=25 --out-dir=ingest"
 * ```
 * Flags: `--metros`, `--caps`, `--sources`, `--cache-dir`, `--out-dir`,
 * `--run-id`, `--on-pending=abort|archive|ignore`, `--refresh`.
 */
fun main(args: Array<String>) = runBlocking {
    val options = IngestOptions.parse(args)
    val runId = options["run-id"] ?: "sample-" + DateTimeFormatter
        .ofPattern("yyyyMMdd'T'HHmmss'Z'")
        .withZone(ZoneOffset.UTC)
        .format(Clock.systemUTC().instant())
    val outDir = Path.of(options["out-dir"] ?: "ingest")
    val cacheDir = options["cache-dir"]?.let(Path::of)
        ?: Path.of(System.getProperty("user.home"), ".cache", "tahirs-list", "certifiers")

    val selection = IngestSelection(
        runId = runId,
        metros = options.csv("metros"),
        capPerMetro = options["caps"]?.toIntOrNull() ?: 25,
        sources = options.csv("sources"),
        refresh = options.flag("refresh"),
        onPending = when ((options["on-pending"] ?: "abort").lowercase()) {
            "archive" -> PendingRunPolicy.ARCHIVE
            "ignore" -> PendingRunPolicy.IGNORE
            else -> PendingRunPolicy.ABORT
        },
    )

    val geocoder = PhotonGeocoder()
    val feeds = tierOneFeeds(cacheDir)
    val run = RunSampleIngestion(
        feeds = feeds,
        health = CheckGeocoderHealth(geocoder),
        artifacts = FileRunArtifacts(outDir),
        factory = CandidateFactory(geocoder),
    )

    val result = run.execute(selection)

    println("run ${result.report.runId} complete — awaiting review")
    println("  candidates: ${result.report.totalCandidates}")
    println("  rejected:   ${result.report.totalRejected}")
    println("  capped:     ${result.report.totalCapped}")
    result.report.gaps.forEach { println("  gap: $it") }
    println("  artifacts:  ${result.pending.directory}")
    println("  report:     ${result.pending.directory.resolve("report.md")}")
    println("Review the report, then run :bootstrap:loadIngestBatch -PingestArgs=\"--run-id=$runId --execute\"")
}

/** `--key=value`, `--key value`, and bare `--flag`. */
internal class IngestOptions(private val values: Map<String, String>) {

    operator fun get(key: String): String? = values[key]

    fun flag(key: String): Boolean = values.containsKey(key)

    /** Comma-separated list; blank or absent means "the default set". */
    fun csv(key: String): Set<String> =
        values[key]?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet().orEmpty()

    companion object {
        fun parse(args: Array<String>): IngestOptions {
            val values = linkedMapOf<String, String>()
            var index = 0
            while (index < args.size) {
                val arg = args[index]
                if (!arg.startsWith("--")) {
                    index++
                    continue
                }
                val body = arg.removePrefix("--")
                val separator = body.indexOf('=')
                if (separator >= 0) {
                    values[body.substring(0, separator)] = body.substring(separator + 1)
                } else if (index + 1 < args.size && !args[index + 1].startsWith("--")) {
                    values[body] = args[index + 1]
                    index++
                } else {
                    values[body] = "true"
                }
                index++
            }
            return IngestOptions(values)
        }
    }
}
