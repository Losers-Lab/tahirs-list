package com.tahirslist.application.audit

import com.tahirslist.application.listing.Actor
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.uuid.Uuid

private val log = KotlinLogging.logger("com.tahirslist.application.audit.LoggingAuditRecorder")

/**
 * SC-205 D-11 (approved): an admin override is recorded as one structured log
 * line. It is not queryable by a committee the way a table would be; that was a
 * deliberate scope call, recorded as D-11, and the port is the seam a table
 * would replace.
 *
 * The line carries facts only — no image bytes, no email, no token: the acting
 * account id, its role, the listing id and both versions.
 *
 * [sink] is injectable so the contract is testable without a logging backend.
 */
class LoggingAuditRecorder(
    private val sink: (String) -> Unit = { line -> log.info { line } },
) : AuditRecorder {

    override fun imageSet(listingId: Uuid, actor: Actor, previousVersion: Long, newVersion: Long) {
        sink(
            "listing_image_set listingId=$listingId actorId=${actor.accountId} " +
                "actorRole=${actor.role.name} previousVersion=$previousVersion newVersion=$newVersion",
        )
    }
}
