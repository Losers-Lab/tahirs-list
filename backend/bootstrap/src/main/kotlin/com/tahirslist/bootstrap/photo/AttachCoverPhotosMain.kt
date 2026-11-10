package com.tahirslist.bootstrap.photo

import com.tahirslist.application.image.ImagePort
import com.tahirslist.application.image.ImageVariant
import com.tahirslist.application.image.IngestHeroImage
import com.tahirslist.application.photo.AttachCoverPhoto
import com.tahirslist.application.photo.PhotoOutcome
import com.tahirslist.application.photo.PhotoRow
import com.tahirslist.application.photo.PhotoCandidateSource
import com.tahirslist.bootstrap.config.loadConfig
import com.tahirslist.bootstrap.image.HttpImageFetcher
import com.tahirslist.persistence.db.DatabaseFactory
import com.tahirslist.persistence.db.Migrations
import com.tahirslist.persistence.listing.ExposedRestaurantListingRepository
import com.tahirslist.persistence.photo.ExposedCoverPhotoRepository
import com.tahirslist.storage.s3.S3StorageConfig
import com.tahirslist.storage.s3.createImagePort
import com.tahirslist.storage.s3.createS3Client
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.uuid.Uuid
import kotlinx.coroutines.runBlocking

/**
 * UC-03 CLI: the cover-photo pass (SC-231).
 *
 * Usage:
 * ```
 * :bootstrap:attachCoverPhotos -PingestArgs="--out-dir=/artifacts"
 * :bootstrap:attachCoverPhotos -PingestArgs="--out-dir=/artifacts --execute"
 * ```
 *
 * Flags: `--out-dir` (default `ingest`), `--limit`, `--listing-id`, `--provenance`
 * (`certifier-hms`, `certifier-hfsaa`, …), `--execute`, `--replace`.
 *
 * Without `--execute` it only reads: it reports what the pass *would* do, per
 * row, and writes nothing. The photo pass is a separate step from the load on
 * purpose — a Places outage must not fail an ingestion batch.
 *
 * The Places key comes from `GOOGLE_MAPS_API_KEY` and is never printed. When it
 * is absent, Places is simply not consulted and the report says so.
 */
fun main(args: Array<String>) = runBlocking {
    val options = PhotoOptions.parse(args)
    val outDir = Path.of(options["out-dir"] ?: "ingest")
    val execute = options.flag("execute")
    val replace = options.flag("replace")
    val limit = options["limit"]?.toIntOrNull()

    val config = loadConfig()
    val storage = config.storage
        ?: error("No object storage configured: the photo pass cannot store bytes. Set the APP_STORAGE_* settings.")

    Migrations.migrate(
        jdbcUrl = config.database.jdbcUrl,
        user = config.database.user,
        password = config.database.password,
    )
    val dataSource = DatabaseFactory.dataSource(
        jdbcUrl = config.database.jdbcUrl,
        user = config.database.user,
        password = config.database.password,
        maxPoolSize = 2,
    )
    val s3 = createS3Client(
        S3StorageConfig(
            endpoint = storage.endpoint,
            region = storage.region,
            accessKey = storage.accessKey,
            secretKey = storage.secretKey,
            bucket = storage.bucket,
        ),
    )
    val images: ImagePort = createImagePort(s3, storage.bucket)

    try {
        val database = DatabaseFactory.connect(dataSource)
        val listings = ExposedRestaurantListingRepository(database)
        val photos = ExposedCoverPhotoRepository(database)

        val key = System.getenv("GOOGLE_MAPS_API_KEY")?.takeIf { it.isNotBlank() }
        val sources: List<PhotoCandidateSource> = buildList {
            // OSM's own image tags need the matched feature from SC-227; until that
            // lands, Commons is the only free source and the report states it.
            add(CommonsPhotoSource())
            if (key != null) add(GooglePlacesPhotoSource(key))
        }

        val pass = AttachCoverPhoto(
            photos = photos,
            sources = sources,
            fetcher = HttpImageFetcher(),
            hero = IngestHeroImage(images),
            hasBytes = { images.load(it, ImageVariant.THUMBNAIL_400) != null },
        )

        val scope = listings.findAll()
            .filter { listing ->
                val byId = options["listing-id"]?.let { it == listing.id.toString() } ?: true
                val byProvenance = options["provenance"]?.let { it == listing.provenance?.value } ?: true
                byId && byProvenance
            }
            .let { if (limit != null) it.take(limit) else it }

        val review = pass.preview(scope)
        val counts = review.rows.groupingBy { it.plan }.eachCount()
        println("scope: ${scope.size} listing(s) — $counts")
        println("places: ${if (key != null) "enabled" else "disabled (GOOGLE_MAPS_API_KEY is not set)"}")
        println("osm: unavailable on this build (SC-227 tags not merged)")

        if (!execute) {
            review.rows.forEach { row ->
                println("would ${row.plan}: ${row.listing.name} (${row.listing.id})")
            }
            println("Preview only — pass --execute to fetch and store photos.")
            return@runBlocking
        }

        val result = pass.execute(review, replace = replace)
        result.outcomes.forEach { outcome ->
            println(
                when (outcome) {
                    is PhotoOutcome.Attached ->
                        "attached ${outcome.photo.sourceKind} for ${outcome.listingName}"
                    is PhotoOutcome.Skipped -> "skipped ${outcome.listingName}: ${outcome.reason}"
                    is PhotoOutcome.NoPhoto -> "no photo ${outcome.listingName}: ${outcome.reason}"
                    is PhotoOutcome.Rejected ->
                        "refused ${outcome.listingName}: ${outcome.reasons.joinToString("; ")}"
                },
            )
        }
        Files.createDirectories(outDir)
        val report = outDir.resolve("photo-report.md")
        Files.writeString(report, renderReport(review.rows, result.outcomes), StandardCharsets.UTF_8)
        println(result.summary())
        println("photo report: $report")
    } finally {
        dataSource.close()
    }
}

private fun renderReport(rows: List<PhotoRow>, outcomes: List<PhotoOutcome>): String = buildString {
    appendLine("# Cover photo pass")
    appendLine()
    appendLine("| metric | value |")
    appendLine("|---|---:|")
    appendLine("| rows | ${outcomes.size} |")
    appendLine("| attached | ${outcomes.count { it is PhotoOutcome.Attached }} |")
    appendLine("| skipped | ${outcomes.count { it is PhotoOutcome.Skipped }} |")
    appendLine("| no photo (gap) | ${outcomes.count { it is PhotoOutcome.NoPhoto }} |")
    appendLine("| refused | ${outcomes.count { it is PhotoOutcome.Rejected }} |")
    appendLine()
    appendLine("_OSM photo tags are unavailable on this build (they arrive with SC-227)._")
    appendLine()
    appendLine("| listing | plan | outcome |")
    appendLine("|---|---|---|")
    rows.zip(outcomes).forEach { (row, outcome) ->
        val detail = when (outcome) {
            is PhotoOutcome.Attached -> "attached ${outcome.photo.sourceKind}"
            is PhotoOutcome.Skipped -> "skipped: ${outcome.reason}"
            is PhotoOutcome.NoPhoto -> "gap: ${outcome.reason}"
            is PhotoOutcome.Rejected -> "refused: ${outcome.reasons.joinToString("; ")}"
        }
        appendLine("| ${row.listing.name.replace("|", "\\|")} | ${row.plan} | $detail |")
    }
}

/** Tiny `--key=value` reader, matching the other ingestion CLIs. */
private class PhotoOptions(private val values: Map<String, String>) {
    operator fun get(key: String): String? = values[key]?.takeIf { it.isNotBlank() && it != "true" }

    fun flag(key: String): Boolean = values[key] == "true"

    companion object {
        fun parse(args: Array<String>): PhotoOptions {
            val map = args.filter { it.startsWith("--") }.associate { arg ->
                val body = arg.removePrefix("--")
                val name = body.substringBefore('=')
                val value = if ('=' in body) body.substringAfter('=') else "true"
                name to value
            }
            return PhotoOptions(map)
        }
    }
}
