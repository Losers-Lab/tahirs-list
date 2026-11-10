package com.tahirslist.web

import com.tahirslist.application.image.StoredImage
import com.tahirslist.application.listing.ListingSearchFilters
import com.tahirslist.application.listing.ListingSearchRequestParser
import com.tahirslist.application.listing.SearchSettings
import com.tahirslist.application.verification.NotListingOwnerException
import com.tahirslist.domain.restaurant.LatLng
import com.tahirslist.domain.restaurant.VerificationStatus
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.put

/**
 * The listing surface:
 *
 *  - public reads (sc-157): browse, location search, detail, and the same-origin
 *    image / certification proxies;
 *  - authenticated writes (sc-138/sc-23/47/48/sc-53/54): create, full-replace
 *    edit, and owner image upload/removal.
 *
 * The listing's owner is always the authenticated account (the JWT `sub`), never
 * a client-supplied owner. These endpoints deliberately do NOT geocode: the
 * client supplies coordinates, so a listing is always saveable independently of
 * any external geocoding provider.
 */
fun Route.listingRoutes(listing: ListingUseCases, search: SearchSettings = SearchSettings()) {

    // ---- public read surface -------------------------------------------------

    get("/v1/listings/search") {
        // Missing / malformed centre or radius is a client error (400), never a
        // 500: the public search vocabulary is closed.
        val parsedCenter = ListingSearchRequestParser.centre(
            parseCenter(call.request.queryParameters["center"]),
            search.centrePrecision,
        )
        val radius = ListingSearchRequestParser.radius(
            call.request.queryParameters["radius"]?.toDoubleOrNull()
                ?: throw IllegalArgumentException("radius is required"),
        )

        val filters = ListingSearchFilters(
            handCutOnly = parseHandCutOnly(call.request.queryParameters["handCutOnly"]),
            deliveryOnly = parseDeliveryOnly(call.request.queryParameters["deliveryOnly"]),
            cuisines = ListingSearchRequestParser.cuisines(call.request.queryParameters.getAll("cuisine")),
            cuisineLogic = ListingSearchRequestParser.cuisineLogic(call.request.queryParameters["cuisineLogic"]),
            minPrice = ListingSearchRequestParser.price(call.doubleQueryParameter("minPrice"), "minPrice"),
            maxPrice = ListingSearchRequestParser.price(call.doubleQueryParameter("maxPrice"), "maxPrice"),
            minRating = ListingSearchRequestParser.rating(call.doubleQueryParameter("minRating")),
        )
        ListingSearchRequestParser.requirePriceRange(filters.minPrice, filters.maxPrice)

        val results = listing.search.searchNearby(
            center = parsedCenter,
            radiusMiles = radius,
            filters = filters,
            offset = call.intQueryParameter("offset", default = 0).coerceAtLeast(0),
            limit = call.intQueryParameter("limit", default = 50).coerceIn(1, 100),
        )
        // Build the body before opting into caching, so a mapping failure cannot
        // leave a public policy on an error response.
        val cards = results.map { call.toSearchCard(it) }
        SearchCachePolicy.applyTo(call, search)
        call.respond(HttpStatusCode.OK, cards)
    }

    get("/v1/listings") {
        val cards = listing.listings.findAll().map { call.toBrowseCard(it) }
        call.respond(HttpStatusCode.OK, cards)
    }

    get("/v1/listings/{id}") {
        val id = call.pathUuid("id")
        val found = listing.listings.findById(id)
            ?: return@get call.respondError(
                HttpStatusCode.NotFound,
                ErrorCodes.LISTING_NOT_FOUND,
                ErrorMessages.RESOURCE_NOT_FOUND,
            )
        call.respond(HttpStatusCode.OK, call.toDetail(found, listing.reviews))
    }

    /**
     * The public "View certificate" proxy.
     *
     * It serves **only** the artifact the Verification Committee approved:
     *  - the listing must be `VERIFIED` (pending/denied evidence is private);
     *  - the latest APPROVED review must carry an archived key (V21);
     *  - that exact object must still exist.
     * Anything else is a 404. Serving "the newest upload" instead let an owner
     * swap the displayed certificate after approval without a re-review, and
     * exposed in-review evidence to anyone with the listing id.
     */
    get("/v1/listings/{id}/certificate") {
        val id = call.pathUuid("id")
        val listingRow = listing.listings.findById(id)
        val approved = listingRow
            ?.takeIf { it.verificationStatus == VerificationStatus.VERIFIED }
            ?.let { listing.reviews.findLatestApprovedByListing(id) }
        val stored = approved?.certificateKey?.let { listing.certificates.load(it) }
            ?: return@get call.respondError(
                HttpStatusCode.NotFound,
                ErrorCodes.NOT_FOUND,
                ErrorMessages.RESOURCE_NOT_FOUND,
            )
        call.setCacheControl(PUBLIC_CACHE_CONTROL)
        call.respondStoredBytes(stored.bytes, stored.contentType)
    }

    get("/v1/listings/{id}/image") {
        val id = call.pathUuid("id")
        val rawVariant = call.request.queryParameters["variant"]
            ?: throw InvalidInputException("variant query parameter is required")
        val variant = parseVariant(rawVariant)
            ?: throw InvalidInputException("variant must be one of the supported image variants")
        val stored = listing.images.load(id, variant)
            ?: return@get call.respondError(
                HttpStatusCode.NotFound,
                ErrorCodes.NOT_FOUND,
                ErrorMessages.RESOURCE_NOT_FOUND,
            )
        call.setCacheControl(PUBLIC_CACHE_CONTROL)
        call.respondStoredBytes(stored.bytes, stored.contentType)
    }

    // ---- authenticated writes ------------------------------------------------

    authenticate(JWT_AUTH_PROVIDER) {

        /**
         * SC-205: may the caller change this listing's photo? Same rule as the
         * write (`PhotoWritePolicy`), and only a boolean — never the owner's id.
         */
        get("/v1/listings/{id}/manage") {
            val id = call.pathUuid("id")
            val actor = call.requireActor()
            val outcome = listing.manage.execute(id, actor)
                ?: return@get call.respondError(
                    HttpStatusCode.NotFound,
                    ErrorCodes.LISTING_NOT_FOUND,
                    ErrorMessages.RESOURCE_NOT_FOUND,
                )
            call.respond(HttpStatusCode.OK, ManageResponse(canManage = outcome.canManage))
        }

        post("/v1/listings") {
            val request = call.receiveJson<CreateListingRequest>()
            requireValid(request.validationMessages())
            val ownerId = call.requireAccountId()
            val created = listing.create.execute(
                name = request.name,
                address = request.address,
                city = request.city,
                province = request.province,
                postal = request.postal,
                country = request.country,
                location = LatLng(lat = request.lat!!, lng = request.lng!!),
                cuisine = request.cuisineValue(),
                isHandCut = request.isHandCut,
                isDelivery = request.isDelivery,
                ownerId = ownerId,
                halalScope = request.halalScopeValue(),
                halalItems = request.halalItemValues(),
                crossContamination = request.crossContaminationValue(),
                alcoholServed = request.alcoholServed,
            )
            call.respond(HttpStatusCode.Created, ListingResponse.from(created))
        }

        patch("/v1/listings/{id}") {
            val id = call.pathUuid("id")
            val request = call.receiveJson<UpdateListingRequest>()
            requireValid(request.validationMessages())
            val ownerId = call.requireAccountId()
            val updated = listing.update.execute(
                listingId = id,
                ownerId = ownerId,
                name = request.name,
                address = request.address,
                location = LatLng(lat = request.lat!!, lng = request.lng!!),
                cuisine = request.cuisineValue(),
                isHandCut = request.isHandCut,
                isDelivery = request.isDelivery,
                halalScope = request.halalScopeValue(),
                halalItems = request.halalItemValues(),
                crossContamination = request.crossContaminationValue(),
                alcoholServed = request.alcoholServed,
            )
            call.respond(HttpStatusCode.OK, ListingResponse.from(updated))
        }

        put("/v1/listings/{id}/image") {
            val id = call.pathUuid("id")
            val actor = call.requireActor()
            // Authorize BEFORE reading the multipart body: otherwise any
            // authenticated account could make the server parse and discard 8 MiB
            // for a listing it may not write. The use case re-checks the policy
            // (one source of truth, two gates).
            val manage = listing.manage.execute(id, actor)
                ?: return@put call.respondError(
                    HttpStatusCode.NotFound,
                    ErrorCodes.LISTING_NOT_FOUND,
                    ErrorMessages.RESOURCE_NOT_FOUND,
                )
            if (!manage.canManage) throw NotListingOwnerException(id, actor.accountId)

            val form = call.receiveForm()
            val image = form.requiredFile("image", ErrorMessages.IMAGE_PART_REQUIRED)
            listing.addImage.execute(
                listingId = id,
                actor = actor,
                original = StoredImage(bytes = image.bytes, contentType = image.contentType),
            )
            call.respond(HttpStatusCode.NoContent)
        }

        delete("/v1/listings/{id}/image") {
            val id = call.pathUuid("id")
            val ownerId = call.requireAccountId()
            listing.removeImage.execute(listingId = id, ownerId = ownerId)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

private const val PUBLIC_CACHE_CONTROL = "public, max-age=86400"

private fun ApplicationCall.intQueryParameter(name: String, default: Int): Int {
    val raw = request.queryParameters[name] ?: return default
    return raw.toIntOrNull() ?: throw IllegalArgumentException("$name must be an integer")
}

/** A present but non-numeric double query value is a client error (400). */
private fun ApplicationCall.doubleQueryParameter(name: String): Double? {
    val raw = request.queryParameters[name] ?: return null
    if (raw.isBlank()) return null
    return raw.toDoubleOrNull() ?: throw IllegalArgumentException("$name must be a number")
}

/** Parses `center=<lat>,<lng>`. Missing / malformed → IllegalArgumentException (→ 400). */
private fun parseCenter(raw: String?): LatLng {
    requireNotNull(raw) { "center is required" }
    val parts = raw.split(",")
    require(parts.size == 2) { "center must be '<lat>,<lng>'" }
    val lat = parts[0].trim().toDoubleOrNull() ?: throw IllegalArgumentException("center lat invalid")
    val lng = parts[1].trim().toDoubleOrNull() ?: throw IllegalArgumentException("center lng invalid")
    return LatLng(lat = lat, lng = lng) // LatLng validates the [-90,90]/[-180,180] ranges
}

/**
 * Parses the `handCutOnly` filter (sc-42): an EXTRA on/off boolean. Missing /
 * blank / `false` mean "any cutting status"; `true` narrows to hand-cut only.
 */
private fun parseHandCutOnly(raw: String?): Boolean = when (raw?.trim()?.lowercase()) {
    null, "", "false" -> false
    "true" -> true
    else -> throw IllegalArgumentException("handCutOnly must be true or false")
}

/**
 * Parses the `deliveryOnly` filter (sc-184): an EXTRA on/off boolean, on the same
 * pattern as `handCutOnly`.
 */
private fun parseDeliveryOnly(raw: String?): Boolean = when (raw?.trim()?.lowercase()) {
    null, "", "false" -> false
    "true" -> true
    else -> throw IllegalArgumentException("deliveryOnly must be true or false")
}
