package com.tahirslist.application.audit

import com.tahirslist.application.listing.Actor
import com.tahirslist.domain.account.Role
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.string.shouldContain
import kotlin.uuid.Uuid

/**
 * SC-205 D-11: an admin write on a listing they do not own is attributable.
 * The recorder's sink is injectable, so the contract (who, what, which
 * versions) is pinned without depending on a logging backend.
 */
class LoggingAuditRecorderTest : FunSpec({

    test("records the actor, the listing and both versions") {
        val lines = mutableListOf<String>()
        val recorder = LoggingAuditRecorder(sink = { lines += it })

        val listingId = Uuid.random()
        val adminId = Uuid.random()
        recorder.imageSet(
            listingId = listingId,
            actor = Actor(adminId, Role.ADMIN),
            previousVersion = 1L,
            newVersion = 1_760_000_000_999L,
        )

        val line = lines.single()
        line shouldContain "listing_image_set"
        line shouldContain listingId.toString()
        line shouldContain adminId.toString()
        line shouldContain "ADMIN"
        line shouldContain "previousVersion=1"
        line shouldContain "newVersion=1760000000999"
    }
})
