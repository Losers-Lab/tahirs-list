package com.tahirslist.application.verification

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runTest

/**
 * The dev/test [CertificationImageStorage]: saving returns a unique key per
 * upload, and `load(key)` addresses exactly the bytes that key was issued for —
 * the identity contract the S3 adapter also honours (sc-73).
 */
class InMemoryCertificationImageStorageTest : FunSpec({

    test("save returns a unique key per upload and load(key) round-trips that object") {
        runTest {
            val storage = InMemoryCertificationImageStorage()
            val listingId = Uuid.random()
            val reviewed = byteArrayOf(1, 2, 3)

            val reviewedKey = storage.save(listingId, "image/jpeg", reviewed)
            // a later re-claim must not move the identity of the reviewed evidence
            val replacementKey = storage.save(listingId, "image/png", byteArrayOf(4, 5, 6))

            reviewedKey.startsWith("certifications/$listingId/") shouldBe true
            (reviewedKey != replacementKey) shouldBe true

            val loaded = storage.load(reviewedKey)
            loaded shouldNotBe null
            loaded!!.contentType shouldBe "image/jpeg"
            loaded.bytes shouldBe reviewed

            storage.load("certifications/$listingId/${Uuid.random()}") shouldBe null
        }
    }

    test("loadLatest still returns the most recent upload (legacy/operational read)") {
        runTest {
            val storage = InMemoryCertificationImageStorage()
            val listingId = Uuid.random()

            storage.save(listingId, "image/jpeg", byteArrayOf(1))
            storage.save(listingId, "image/png", byteArrayOf(2))

            val latest = storage.loadLatest(listingId)
            latest!!.contentType shouldBe "image/png"
            latest.bytes shouldBe byteArrayOf(2)
            storage.loadLatest(Uuid.random()) shouldBe null
        }
    }
})
