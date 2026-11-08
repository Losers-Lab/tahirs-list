package com.tahirslist.ingestion.artifacts

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** What to do when a previous run is still awaiting review. */
enum class PendingRunPolicy {
    /** Refuse to start; the caller must review/load or archive the pending run. */
    ABORT,

    /** Move every pending run to `archive/` under a unique name, then start. */
    ARCHIVE,

    /** Leave pending runs in place and start a new run beside them. */
    IGNORE,
}

/** A completed run that has not been loaded or explicitly abandoned. */
data class PendingRun(
    val runId: String,
    val directory: Path,
    val createdAt: Instant,
    val candidatesSha256: String,
    val reportSha256: String,
)

class PendingRunsException(
    val pending: List<PendingRun>,
) : RuntimeException(
    "There are ${pending.size} ingestion run(s) awaiting review: " +
        pending.joinToString { it.runId } +
        ". Load them, archive them (--on-pending=archive), or pass --on-pending=ignore.",
)

class RunArtifactsExistException(val directory: Path) : RuntimeException(
    "Run artifacts already exist at $directory; refusing to overwrite them.",
)

class ArtifactChecksumMismatchException(
    val runId: String,
    val expected: String,
    val actual: String,
) : RuntimeException(
    "candidates.json for run $runId changed after the run completed " +
        "(expected sha256 $expected, got $actual); refusing to load it.",
)

class RunAlreadyLoadedException(val runId: String) : RuntimeException(
    "Run $runId already has a loaded marker.",
)

/**
 * Immutable per-run artifact store.
 *
 * Layout:
 * ```
 * <root>/runs/<runId>/run.json          manifest written last (commit marker)
 * <root>/runs/<runId>/candidates.json
 * <root>/runs/<runId>/report.md
 * <root>/runs/<runId>/snapshots/<files>
 * <root>/runs/<runId>/loaded.json       written by the load, never by the run
 * <root>/runs/<runId>/abandoned.json    written when a curator discards the run
 * <root>/archive/<runId>-<timestamp>-<token>/  archived pending runs
 * ```
 *
 * A run never writes into another run's directory. Starting a run checks for
 * pending runs first ([PendingRunPolicy]); the default is to refuse. Archiving
 * creates a new archive directory with a unique suffix, so two archives can
 * never overwrite each other.
 */
class FileRunArtifacts(
    private val root: Path,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    fun begin(runId: String, onPending: PendingRunPolicy = PendingRunPolicy.ABORT): Path {
        requireValidRunId(runId)
        val runsDir = root.resolve(RUNS)
        Files.createDirectories(runsDir)

        // Reusing an existing run id is always an error, even if it is pending.
        val directory = runsDir.resolve(runId)
        if (Files.exists(directory)) {
            throw RunArtifactsExistException(directory)
        }

        val pending = findPending()
        if (pending.isNotEmpty()) {
            when (onPending) {
                PendingRunPolicy.ABORT -> throw PendingRunsException(pending)
                PendingRunPolicy.ARCHIVE -> pending.forEach { archive(it.runId, reason = "superseded by $runId") }
                PendingRunPolicy.IGNORE -> Unit
            }
        }

        Files.createDirectories(directory)
        return directory
    }

    /**
     * Writes the run's artifacts and, last, the manifest that marks the run
     * complete and awaiting review. A directory without `run.json` is
     * incomplete and is never reported as pending.
     */
    fun complete(
        runId: String,
        candidatesJson: String,
        reportMarkdown: String,
        snapshots: Map<String, ByteArray> = emptyMap(),
    ): PendingRun {
        val directory = runDirectory(runId)
        require(Files.isDirectory(directory)) { "run $runId was not started" }
        if (Files.exists(directory.resolve(MANIFEST))) {
            throw RunArtifactsExistException(directory)
        }

        snapshots.forEach { (name, bytes) ->
            requireSafeFileName(name)
            writeAtomically(directory.resolve(SNAPSHOTS).resolve(name), bytes)
        }
        writeAtomically(directory.resolve(CANDIDATES), candidatesJson.toByteArray(StandardCharsets.UTF_8))
        writeAtomically(directory.resolve(REPORT), reportMarkdown.toByteArray(StandardCharsets.UTF_8))

        val candidatesHash = sha256(candidatesJson.toByteArray(StandardCharsets.UTF_8))
        val reportHash = sha256(reportMarkdown.toByteArray(StandardCharsets.UTF_8))
        val now = clock.instant()
        val manifest = RunManifest(
            runId = runId,
            createdAt = now.toString(),
            status = "AWAITING_REVIEW",
            candidatesSha256 = candidatesHash,
            reportSha256 = reportHash,
        )
        // The manifest is the commit marker: write it last.
        writeAtomically(directory.resolve(MANIFEST), json.encodeToString(manifest).toByteArray(StandardCharsets.UTF_8))

        return PendingRun(runId, directory, now, candidatesHash, reportHash)
    }

    fun findPending(): List<PendingRun> =
        listRunDirectories()
            .filter { Files.isRegularFile(it.resolve(MANIFEST)) }
            .filter { !Files.exists(it.resolve(LOADED)) }
            .filter { !Files.exists(it.resolve(ABANDONED)) }
            .map { readManifest(it) ?: unreadablePending(it) }
            .sortedWith(compareBy({ it.createdAt }, { it.runId }))

    /** Where archived runs live; each child is immutable. */
    fun archiveRoot(): Path = root.resolve(ARCHIVE)

    /** Run directories that were started but never reached `complete`. */
    fun findIncomplete(): List<Path> =
        listRunDirectories().filter { !Files.isRegularFile(it.resolve(MANIFEST)) }

    fun readCandidates(runId: String): String {
        val directory = runDirectory(runId)
        val manifest = readManifest(directory) ?: error("run $runId has no manifest")
        val bytes = Files.readAllBytes(directory.resolve(CANDIDATES))
        val actual = sha256(bytes)
        if (actual != manifest.candidatesSha256) {
            throw ArtifactChecksumMismatchException(runId, manifest.candidatesSha256, actual)
        }
        return String(bytes, StandardCharsets.UTF_8)
    }

    /** Marks a run loaded after the catalogue transaction committed. */
    fun markLoaded(runId: String, loadRunId: String) {
        requireValidRunId(loadRunId)
        val directory = runDirectory(runId)
        val manifest = readManifest(directory) ?: error("run $runId has no manifest")
        val marker = directory.resolve(LOADED)
        if (Files.exists(marker)) {
            val existing = runCatching {
                json.decodeFromString<LoadMarker>(Files.readString(marker, StandardCharsets.UTF_8))
            }.getOrNull()
            if (existing?.loadRunId == loadRunId) return
            throw RunAlreadyLoadedException(runId)
        }
        val candidates = Files.readAllBytes(directory.resolve(CANDIDATES))
        val actual = sha256(candidates)
        if (actual != manifest.candidatesSha256) {
            throw ArtifactChecksumMismatchException(runId, manifest.candidatesSha256, actual)
        }
        val body = json.encodeToString(
            LoadMarker(
                runId = runId,
                loadRunId = loadRunId,
                loadedAt = clock.instant().toString(),
                candidatesSha256 = manifest.candidatesSha256,
            ),
        )
        createNew(marker, body.toByteArray(StandardCharsets.UTF_8))
    }

    fun abandon(runId: String, reason: String) {
        val directory = runDirectory(runId)
        createNew(
            directory.resolve(ABANDONED),
            json.encodeToString(
                AbandonMarker(runId = runId, abandonedAt = clock.instant().toString(), reason = reason),
            ).toByteArray(StandardCharsets.UTF_8),
        )
    }

    /**
     * Moves a run to `archive/<runId>-<timestamp>-<token>`. The target name is
     * unique (loop on the rare collision), so archives never overwrite each
     * other and the source directory is never reused.
     */
    fun archive(runId: String, reason: String): Path {
        val source = runDirectory(runId)
        require(Files.isDirectory(source)) { "run $runId does not exist" }
        val archiveRoot = root.resolve(ARCHIVE)
        Files.createDirectories(archiveRoot)

        val timestamp = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(ZoneOffset.UTC)
            .format(clock.instant())
        var target: Path
        do {
            val token = UUID.randomUUID().toString().replace("-", "").take(8)
            target = archiveRoot.resolve("$runId-$timestamp-$token")
        } while (Files.exists(target))

        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Exception) {
            Files.move(source, target)
        }
        writeAtomically(
            target.resolve(ARCHIVE_RECORD),
            json.encodeToString(
                ArchiveRecord(
                    runId = runId,
                    archivedAt = clock.instant().toString(),
                    reason = reason,
                ),
            ).toByteArray(StandardCharsets.UTF_8),
        )
        return target
    }

    private fun listRunDirectories(): List<Path> {
        val runsDir = root.resolve(RUNS)
        if (!Files.isDirectory(runsDir)) return emptyList()
        return Files.list(runsDir).use { stream ->
            stream.filter { Files.isDirectory(it) }.toList()
        }
    }

    private fun readManifest(directory: Path): PendingRun? {
        val manifestPath = directory.resolve(MANIFEST)
        if (!Files.isRegularFile(manifestPath)) return null
        return runCatching {
            val manifest = json.decodeFromString<RunManifest>(
                Files.readString(manifestPath, StandardCharsets.UTF_8),
            )
            PendingRun(
                runId = manifest.runId,
                directory = directory,
                createdAt = Instant.parse(manifest.createdAt),
                candidatesSha256 = manifest.candidatesSha256,
                reportSha256 = manifest.reportSha256,
            )
        }.getOrNull()
    }

    /** A manifest we cannot parse is still a pending run: never hide it. */
    private fun unreadablePending(directory: Path) = PendingRun(
        runId = directory.fileName.toString(),
        directory = directory,
        createdAt = Instant.EPOCH,
        candidatesSha256 = "",
        reportSha256 = "",
    )

    private fun runDirectory(runId: String): Path {
        requireValidRunId(runId)
        return root.resolve(RUNS).resolve(runId)
    }

    private fun writeAtomically(target: Path, bytes: ByteArray) {
        Files.createDirectories(target.parent)
        val temp = Files.createTempFile(target.parent, "artifact-", ".tmp")
        Files.write(temp, bytes)
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Exception) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun createNew(target: Path, bytes: ByteArray) {
        Files.createDirectories(target.parent)
        Files.write(target, bytes, java.nio.file.StandardOpenOption.CREATE_NEW)
    }

    private fun requireValidRunId(runId: String) {
        require(RUN_ID.matches(runId)) {
            "run id must be 1-64 characters of letters, digits, '.', '_' or '-', and must not be '.' or '..': '$runId'"
        }
    }

    private fun requireSafeFileName(name: String) {
        require(name.isNotBlank() && '/' !in name && '\\' !in name && name != "." && name != "..") {
            "unsafe snapshot file name: '$name'"
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private companion object {
        const val RUNS = "runs"
        const val ARCHIVE = "archive"
        const val SNAPSHOTS = "snapshots"
        const val MANIFEST = "run.json"
        const val CANDIDATES = "candidates.json"
        const val REPORT = "report.md"
        const val LOADED = "loaded.json"
        const val ABANDONED = "abandoned.json"
        const val ARCHIVE_RECORD = "archive.json"
        val RUN_ID = Regex("""[A-Za-z0-9][A-Za-z0-9._-]{0,63}""")
    }
}

@Serializable
private data class RunManifest(
    val runId: String,
    val createdAt: String,
    val status: String,
    val candidatesSha256: String,
    val reportSha256: String,
)

@Serializable
private data class LoadMarker(
    val runId: String,
    val loadRunId: String,
    val loadedAt: String,
    val candidatesSha256: String,
)

@Serializable
private data class AbandonMarker(
    val runId: String,
    val abandonedAt: String,
    val reason: String,
)

@Serializable
private data class ArchiveRecord(
    val runId: String,
    val archivedAt: String,
    val reason: String,
)
