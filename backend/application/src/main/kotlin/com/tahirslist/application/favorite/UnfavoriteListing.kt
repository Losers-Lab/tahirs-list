package com.tahirslist.application.favorite

import kotlin.uuid.Uuid

/**
 * Unfavorite a listing (sc-51): removes the user↔listing relation. Idempotent —
 * unfavouriting a listing that is not favourited is a no-op.
 */
class UnfavoriteListing(private val favorites: FavoritesRepository) {

    suspend fun execute(userId: Uuid, listingId: Uuid) {
        favorites.remove(userId, listingId)
    }
}
