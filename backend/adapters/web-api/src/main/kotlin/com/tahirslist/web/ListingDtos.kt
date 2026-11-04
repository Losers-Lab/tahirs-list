package com.tahirslist.web

import com.tahirslist.domain.restaurant.CrossContamination
import com.tahirslist.domain.restaurant.Cuisine
import com.tahirslist.domain.restaurant.HalalItem
import com.tahirslist.domain.restaurant.HalalScope
import com.tahirslist.domain.restaurant.RestaurantListing
import kotlinx.serialization.Serializable

/**
 * One halal menu item as carried on the wire (`{name, isHalal}`), mapped to the
 * domain [HalalItem] value object at the boundary.
 */
@Serializable
data class HalalItemPayload(val name: String, val isHalal: Boolean)

/**
 * Add Listing request (sc-138) — the shape the add-listing form submits.
 * Coordinates are supplied directly: this path does not geocode.
 *
 * Nullable booleans default to null (unknown / not claimed), matching the
 * previous Jackson binding where an omitted field became null.
 */
@Serializable
data class CreateListingRequest(
    val name: String,
    val address: String,
    /** Structured address (sc-187): optional; null = unknown. */
    val city: String? = null,
    val province: String? = null,
    val postal: String? = null,
    val country: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val cuisine: String,
    val isHandCut: Boolean? = null,
    val halalScope: String = HalalScope.DEFAULT.name,
    val halalItems: Set<HalalItemPayload> = emptySet(),
    val crossContamination: String = CrossContamination.DEFAULT.name,
    val alcoholServed: Boolean = false,
    val isDelivery: Boolean? = null,
)

/**
 * Edit Listing request (sc-23/47/48): a FULL replace of the editable content
 * fields, same shape as create minus the structured-address fields the edit path
 * intentionally preserves.
 */
@Serializable
data class UpdateListingRequest(
    val name: String,
    val address: String,
    val lat: Double? = null,
    val lng: Double? = null,
    val cuisine: String,
    val isHandCut: Boolean? = null,
    val halalScope: String = HalalScope.DEFAULT.name,
    val halalItems: Set<HalalItemPayload> = emptySet(),
    val crossContamination: String = CrossContamination.DEFAULT.name,
    val alcoholServed: Boolean = false,
    val isDelivery: Boolean? = null,
)

/**
 * The create/patch response: the persisted listing identity + governance facts.
 */
@Serializable
data class ListingResponse(
    val id: String,
    val name: String,
    val address: String,
    val city: String?,
    val province: String?,
    val postal: String?,
    val country: String?,
    val lat: Double,
    val lng: Double,
    val cuisine: String,
    val isHandCut: Boolean?,
    val halalScope: String,
    val isDelivery: Boolean?,
    val ownerId: String,
    val verificationStatus: String,
    val createdAt: String,
) {
    companion object {
        fun from(listing: RestaurantListing): ListingResponse = ListingResponse(
            id = listing.id.toString(),
            name = listing.name,
            address = listing.address,
            city = listing.city,
            province = listing.province,
            postal = listing.postal,
            country = listing.country,
            lat = listing.location.lat,
            lng = listing.location.lng,
            // The Add Listing flow always requires a cuisine, so the response
            // echoes a non-null value; the nullable domain field is only NULL
            // for community seed rows, which this endpoint does not create.
            cuisine = listing.cuisine!!.value,
            isHandCut = listing.isHandCut,
            halalScope = listing.halalScope.name,
            isDelivery = listing.isDelivery,
            // Add Listing always attaches the authenticated owner, so the
            // response echoes a non-null id; only community seed rows have
            // ownerId NULL, and this endpoint does not create those.
            ownerId = listing.ownerId!!.toString(),
            verificationStatus = listing.verificationStatus.name,
            createdAt = listing.createdAt.toString(),
        )
    }
}

/** Field-level validation messages in the same order the bean validation ran. */
internal fun CreateListingRequest.validationMessages(): List<String> = buildList {
    if (name.isBlank()) add("name is required")
    if (address.isBlank()) add("address is required")
    if (lat == null) add("lat is required")
    if (lng == null) add("lng is required")
    if (cuisine.isBlank()) add("cuisine is required")
}

/** Field-level validation messages in the same order the bean validation ran. */
internal fun UpdateListingRequest.validationMessages(): List<String> = buildList {
    if (name.isBlank()) add("name is required")
    if (address.isBlank()) add("address is required")
    if (lat == null) add("lat is required")
    if (lng == null) add("lng is required")
    if (cuisine.isBlank()) add("cuisine is required")
}

internal fun CreateListingRequest.cuisineValue(): Cuisine = Cuisine(cuisine)

internal fun UpdateListingRequest.cuisineValue(): Cuisine = Cuisine(cuisine)

internal fun CreateListingRequest.halalScopeValue(): HalalScope = HalalScope.valueOf(halalScope)

internal fun UpdateListingRequest.halalScopeValue(): HalalScope = HalalScope.valueOf(halalScope)

internal fun CreateListingRequest.crossContaminationValue(): CrossContamination =
    CrossContamination.valueOf(crossContamination)

internal fun UpdateListingRequest.crossContaminationValue(): CrossContamination =
    CrossContamination.valueOf(crossContamination)

internal fun CreateListingRequest.halalItemValues(): Set<HalalItem> =
    halalItems.mapTo(linkedSetOf()) { HalalItem(it.name, it.isHalal) }

internal fun UpdateListingRequest.halalItemValues(): Set<HalalItem> =
    halalItems.mapTo(linkedSetOf()) { HalalItem(it.name, it.isHalal) }
