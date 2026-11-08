package com.tahirslist.certifiers.core

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.test.runTest

class FileSnapshotStoreTest : FunSpec({

    fun page(body: String) = StoredPage(
        url = "https://example.test/final",
        status = 200,
        contentType = "text/html",
        etag = "\"abc\"",
        lastModified = null,
        headers = emptyMap(),
        fetchedAt = Instant.parse("2026-11-08T00:00:00Z"),
        body = body,
    )

    test("round-trips a snapshot") {
        runTest {
            val dir = createTempDirectory("snapshot-test")
            val store = FileSnapshotStore(dir)

            store.write("key", page("hello"))

            val read = store.read("key")
            read shouldNotBe null
            read!!.body shouldBe "hello"
            read.url shouldBe "https://example.test/final"
        }
    }

    test("treats a tampered body as a cache miss") {
        runTest {
            val dir = createTempDirectory("snapshot-test")
            val store = FileSnapshotStore(dir)
            store.write("key", page("trusted"))

            Files.writeString(dir.resolve("key.body"), "tampered")

            store.read("key").shouldBeNull()
        }
    }

    test("treats a missing body as a cache miss") {
        runTest {
            val dir = createTempDirectory("snapshot-test")
            val store = FileSnapshotStore(dir)
            store.write("key", page("trusted"))

            Files.delete(dir.resolve("key.body"))

            store.read("key").shouldBeNull()
        }
    }

    test("default cache lives under the user home, not the shared temp dir") {
        val dir = CertifierHttp.defaultCacheDir().toString()
        dir.contains(".cache") shouldBe true
        dir.contains(Path.of(System.getProperty("java.io.tmpdir")).toString()) shouldBe false
    }
})
