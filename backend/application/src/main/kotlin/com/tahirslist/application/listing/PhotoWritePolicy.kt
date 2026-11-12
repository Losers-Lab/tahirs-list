package com.tahirslist.application.listing

import com.tahirslist.domain.account.Role
import com.tahirslist.domain.restaurant.RestaurantListing

/**
 * SC-205 D-2 / AC-6: the single rule for "may this actor change this listing's
 * photo?".
 *
 * ```
 * role == ADMIN || accountId == ownerId
 * ```
 *
 * The manage read (`GetListingManage`, which drives whether the UI offers the
 * control) and the write (`AddListingImage`) both call this function, so the
 * button and the server can never disagree. If the rule ever grows — a
 * curator role, a locked listing — it grows here, once.
 *
 * An admin does **not** need to own the listing, and an ownerless listing (an
 * ingested row) is manageable by an admin but by nobody else.
 */
object PhotoWritePolicy {

    fun mayManage(actor: Actor, listing: RestaurantListing): Boolean =
        actor.role == Role.ADMIN || actor.accountId == listing.ownerId
}
