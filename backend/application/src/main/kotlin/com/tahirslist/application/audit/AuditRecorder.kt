package com.tahirslist.application.audit

import com.tahirslist.application.listing.Actor
import kotlin.uuid.Uuid

/**
 * SC-205 D-11 / INV-12: attribution for writes made by someone other than the
 * listing's owner.
 *
 * Today the only producer is an admin replacing another account's photo. The
 * port exists so the depth of the trail is a wiring decision (a log sink now, a
 * table later) rather than a change scattered through the use cases.
 *
 * Implementations must never throw on the write path: an audit failure is a
 * defect in the recorder, not a reason to reject an authorized upload.
 */
interface AuditRecorder {

    /**
     * Record that [actor] set the hero photo of [listingId], moving the published
     * version from [previousVersion] to [newVersion].
     */
    fun imageSet(listingId: Uuid, actor: Actor, previousVersion: Long, newVersion: Long)
}
