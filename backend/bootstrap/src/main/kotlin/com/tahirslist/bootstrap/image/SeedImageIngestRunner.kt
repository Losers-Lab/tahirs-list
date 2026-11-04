package com.tahirslist.bootstrap.image

import com.tahirslist.application.image.IngestSeedHeroPhotos
import com.tahirslist.application.image.SeedPhotoIngestResult
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File

/**
 * Runs the seed hero-photo ingest when `APP_SEED_INGEST_ENABLED=true`.
 *
 * Replaces the Spring `ApplicationRunner`: the Ktor application launches it in
 * its coroutine scope after startup, so boot never depends on a licensed photo
 * CDN being reachable. Per-row failures are reported, not fatal.
 */
class SeedImageIngestRunner(
    private val parser: SeedPhotoManifestParser,
    private val ingest: IngestSeedHeroPhotos,
    private val manifestLocation: String,
) {

    private val log = KotlinLogging.logger {}

    suspend fun run() {
        val photos = parser.parse(loadManifest(manifestLocation))
        val results = ingest.ingestAll(photos)
        results.groupBy { it.status }.forEach { (status, rows) ->
            log.info { "seed image ingest: $status -> ${rows.size} row(s) ${rows.map { it.photoName }}" }
        }
        results.filter { it.status != SeedPhotoIngestResult.Status.INGESTED }.forEach {
            log.warn { "${it.status} seed image: ${it.photoName} — ${it.message}" }
        }
    }

    private fun loadManifest(location: String): String {
        if (location.startsWith("classpath:")) {
            val path = location.removePrefix("classpath:")
            return javaClass.classLoader.getResourceAsStream(path)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: error("seed manifest not found on classpath: $path")
        }
        return File(location).readText()
    }
}
