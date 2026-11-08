package com.tahirslist.certifiers.core

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** A cached response. [fetchedAt] is when the body was last confirmed fresh. */
data class StoredPage(
    val url: String,
    val status: Int,
    val contentType: String?,
    val etag: String?,
    val lastModified: String?,
    val headers: Map<String, String>,
    val fetchedAt: Instant,
    val body: String,
)

/** Persists snapshots between runs so a repeat run performs no network I/O. */
interface SnapshotStore {
    suspend fun read(key: String): StoredPage?
    suspend fun write(key: String, page: StoredPage)
}

/** For tests and short-lived probes. */
class InMemorySnapshotStore : SnapshotStore {
    private val pages = mutableMapOf<String, StoredPage>()

    override suspend fun read(key: String): StoredPage? = pages[key]

    override suspend fun write(key: String, page: StoredPage) {
        pages[key] = page
    }
}

/**
 * On-disk snapshot cache. Bodies and metadata are separate files so a 10 MB
 * directory page is never re-encoded into JSON.
 */
class FileSnapshotStore(private val root: Path) : SnapshotStore {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun read(key: String): StoredPage? {
        val metaFile = root.resolve("$key.meta.json")
        val bodyFile = root.resolve("$key.body")
        if (!Files.isRegularFile(metaFile) || !Files.isRegularFile(bodyFile)) return null

        return runCatching {
            val meta = json.decodeFromString<Meta>(Files.readString(metaFile, StandardCharsets.UTF_8))
            StoredPage(
                url = meta.url,
                status = meta.status,
                contentType = meta.contentType,
                etag = meta.etag,
                lastModified = meta.lastModified,
                headers = meta.headers,
                fetchedAt = Instant.parse(meta.fetchedAt),
                body = Files.readString(bodyFile, StandardCharsets.UTF_8),
            )
        }.getOrNull()
    }

    override suspend fun write(key: String, page: StoredPage) {
        Files.createDirectories(root)
        writeAtomically(root.resolve("$key.body"), page.body)
        writeAtomically(
            root.resolve("$key.meta.json"),
            json.encodeToString(Meta.from(page)),
        )
    }

    private fun writeAtomically(target: Path, content: String) {
        val temp = Files.createTempFile(root, "snapshot-", ".tmp")
        Files.writeString(temp, content, StandardCharsets.UTF_8)
        runCatching {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }.onFailure {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    @Serializable
    private data class Meta(
        val url: String,
        val status: Int,
        val contentType: String? = null,
        val etag: String? = null,
        val lastModified: String? = null,
        val headers: Map<String, String> = emptyMap(),
        val fetchedAt: String,
    ) {
        companion object {
            fun from(page: StoredPage) = Meta(
                url = page.url,
                status = page.status,
                contentType = page.contentType,
                etag = page.etag,
                lastModified = page.lastModified,
                headers = page.headers,
                fetchedAt = page.fetchedAt.toString(),
            )
        }
    }
}
