package com.tahirslist.web

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post

/**
 * Favourites surface (sc-50/51/52):
 *  - `POST /v1/favorites/{listingId}`   — favourite (idempotent, 204);
 *  - `DELETE /v1/favorites/{listingId}` — unfavourite (idempotent, 204);
 *  - `GET /v1/favorites`                — the user's favourites as the exact
 *    `/v1/listings` browse-card shape, so the frontend reuses its card/grid.
 *
 * Every request acts on the authenticated account (the JWT `sub`), never a
 * client-supplied id.
 */
fun Route.favoriteRoutes(favorites: FavoriteUseCases) {
    authenticate(JWT_AUTH_PROVIDER) {

        post("/v1/favorites/{listingId}") {
            val listingId = call.pathUuid("listingId")
            favorites.favorite.execute(userId = call.requireAccountId(), listingId = listingId)
            call.respond(HttpStatusCode.NoContent)
        }

        delete("/v1/favorites/{listingId}") {
            val listingId = call.pathUuid("listingId")
            favorites.unfavorite.execute(userId = call.requireAccountId(), listingId = listingId)
            call.respond(HttpStatusCode.NoContent)
        }

        get("/v1/favorites") {
            val cards = favorites.list.execute(call.requireAccountId())
                .map { call.toBrowseCard(it) }
            call.respond(HttpStatusCode.OK, cards)
        }
    }
}
