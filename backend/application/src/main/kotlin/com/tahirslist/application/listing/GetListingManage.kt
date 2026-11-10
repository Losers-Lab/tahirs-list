package com.tahirslist.application.listing

import kotlin.uuid.Uuid

/**
 * SC-205: may the current actor change this listing's photo?
 *
 * This is the read behind the Add/Replace control. It answers the *same*
 * question as the write ([PhotoWritePolicy]), so the browser never offers an
 * action the server would refuse. It deliberately returns only a boolean —
 * never the owner's id — so the public detail payload stays viewer-neutral.
 */
class GetListingManage(
    private val listings: RestaurantListingRepository,
) {

    /** @return the outcome, or `null` when no listing has [listingId] (→ 404). */
    suspend fun execute(listingId: Uuid, actor: Actor): ManageOutcome? {
        val listing = listings.findById(listingId) ?: return null
        return ManageOutcome(canManage = PhotoWritePolicy.mayManage(actor, listing))
    }
}

data class ManageOutcome(val canManage: Boolean)
