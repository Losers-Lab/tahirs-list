package com.tahirslist.domain.restaurant

import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * A restaurant listing.
 *
 * Per the PRD's listing-first model, the canonical way to create a listing is
 * [RestaurantListing.new], which always starts [VerificationStatus.UNVERIFIED]:
 * anyone can add a restaurant, and it only becomes verified via the separate
 * owner-claim + certification-verification vertical.
 *
 * [ownerId] and [cuisine] are nullable because the listing-first model admits
 * community/research seed rows with no owning account and no ratified cuisine
 * (see V6 — Omar adjudication). [RestaurantListing.new] still requires both for
 * the authenticated Add Listing flow; seed rows are reconstituted via
 * [fromStorage] with nulls. [brandId] links a location to its brand (brand /
 * location split) and [provenance] stamps the row's origin.
 *
 * [isHandCut] is the founder's sc-42 ruling: hand-cut is the only cut claim we
 * model. It is a plain boolean — a listing either claims it or not. null means
 * "unknown / not claimed" (community/research seed rows, which never claim a
 * method); true means hand-cut (Zabiha); false means not-hand-cut. Search treats
 * null the same as false: only a hand-cut-only filter excludes it. This replaces
 * the earlier either/or CuttingMethod enum wholesale (V17 migrates the column).
 *
 * Partial-halal modeling (sc-119, founder re-scope):
 *  - [halalScope] + [halalItems] model *which* items are halal per listing. This
 *    is **orthogonal** to [verificationStatus]: a PARTIALLY_HALAL place may still
 *    be VERIFIED (it can hold a certificate for the halal portion).
 *  - [crossContamination] is a HARD index gate: only
 *    [CrossContamination.NO_CROSS_CONTAMINATION] qualifies a listing for the
 *    search index (see CrossContamination.isIndexQualified).
 *  - [alcoholServed] is a display attribute (sc-118), part of the
 *    partial-halal/alcohol MVP additions; it is a display attribute (no search
 *    filter), defaulting to false.
 *
 * [isDelivery] is the sc-184 service-mode field, modelled on the same sc-42
 * tri-state boolean pattern: delivery is an EXTRA on/off flag a listing claims
 * or not, NOT an enum of service modes. null means "unknown / not claimed"
 * (community/research seed rows); true means offers delivery; false means no
 * delivery (pickup-only). Search treats null the same as false: only a
 * delivery-only filter excludes it. Sc-184 stays deliberately lean (pickup is
 * the implicit baseline default), so a listing claims delivery explicitly and
 * pickup is the absence of that claim — the frontend child story surfaces this
 * dimension on cards/detail and wires a deliveryOnly search filter.
 *
 * Structured address (sc-187 founder re-scope #2): [address] remains the street
 * line ("3885 Belt Line Rd") for backward compat; [city], [province], [postal]
 * and [country] model the rest of the address so the detail page can render the
 * full comma-form address. They are nullable — legacy/community rows may predate
 * the structured fields (V7 seeds exist without them) — and province is a
 * country-agnostic field (works for both CA/ON and US/TX, per Aisha's seed data).
 * When provided they are trimmed and length-capped; a blank value is rejected
 * (a caller signals "unknown" with null, never whitespace).
 *
 * NOTE: ODbL share-alike on OSM/Photon-derived listing fields is an open founder
 * decision (docs/reviews/sc-138-external-services.md §5). Flagged here, not
 * decided — do not resolve it in code.
 */
data class RestaurantListing(
    val id: Uuid,
    val name: String,
    val address: String,
    val city: String? = null,
    val province: String? = null,
    val postal: String? = null,
    val country: String? = null,
    val location: LatLng,
    val cuisine: Cuisine?,
    val isHandCut: Boolean?,
    val isDelivery: Boolean?,
    val price: Price?,
    val rating: Rating?,
    val ownerId: Uuid?,
    val brandId: Uuid?,
    val provenance: Provenance?,
    val venueKey: String? = null,
    val verificationStatus: VerificationStatus,
    val createdAt: Instant,
    val halalScope: HalalScope = HalalScope.DEFAULT,
    val halalItems: Set<HalalItem> = emptySet(),
    val crossContamination: CrossContamination = CrossContamination.DEFAULT,
    val alcoholServed: Boolean = false,
    /** SC-231: which kind of restaurant this is, and the evidence that decided it. */
    val venueType: VenueType? = null,
    val venueTypeSource: EnrichmentSource? = null,
    /** SC-231: where the cuisine came from; null when an owner set it or it is unknown. */
    val cuisineSource: EnrichmentSource? = null,
    /** SC-231: the one cover photo, when there is one. */
    val coverPhoto: CoverPhoto? = null,
) {

    init {
        require((venueType == null) == (venueTypeSource == null)) {
            "A venue type and the evidence that produced it travel together."
        }
        require(cuisineSource == null || cuisine != null) {
            "A cuisine source without a cuisine is not auditable."
        }
    }

    /**
     * Produce an updated copy of this listing's *editable content fields*
     * (sc-23/47/48 owner listing edit). Unlike [new], identity and governance
     * fields — [id], [ownerId], [brandId], [provenance], [venueKey],
     * [verificationStatus], [createdAt], [price] and [rating] — are PRESERVED
     * untouched: an owner
     * editing their listing can never change who owns it, its verification
     * status, or audit fields. Ownership/status changes are out of scope for a
     * listing edit (they run through the claim/verification vertical).
     *
     * Names/addresses are trimmed; blank values are rejected (same contract
     * as [new]). [isHandCut] / [isDelivery] keep their tri-state (null =
     * unknown / not claimed) semantics — an owner may explicitly clear them.
     *
     * The structured-address fields ([city]/[province]/[postal]/[country]) are
     * carried through unchanged by this edit — sc-187's owner-facing address
     * editing is out of scope for owner listing edit (sc-23); they are only
     * populated on create / from storage.
     *
     * @throws IllegalArgumentException if [name] or [address] is blank.
     */
    fun withUpdatedFields(
        name: String,
        address: String,
        location: LatLng,
        cuisine: Cuisine?,
        isHandCut: Boolean?,
        isDelivery: Boolean?,
        halalScope: HalalScope = HalalScope.DEFAULT,
        halalItems: Set<HalalItem> = emptySet(),
        crossContamination: CrossContamination = CrossContamination.DEFAULT,
        alcoholServed: Boolean = false,
    ): RestaurantListing {
        val trimmedName = name.trim()
        val trimmedAddress = address.trim()
        require(trimmedName.isNotBlank()) { "Listing name must not be blank." }
        require(trimmedAddress.isNotBlank()) { "Listing address must not be blank." }
        return copy(
            name = trimmedName,
            address = trimmedAddress,
            location = location,
            cuisine = cuisine,
            isHandCut = isHandCut,
            isDelivery = isDelivery,
            halalScope = halalScope,
            halalItems = halalItems,
            crossContamination = crossContamination,
            alcoholServed = alcoholServed,
        )
    }

    companion object {

        /** sc-187 structured-address max lengths (mirror the V20 VARCHAR widths). */
        const val CITY_MAX_LENGTH: Int = 128
        const val PROVINCE_MAX_LENGTH: Int = 64
        const val POSTAL_MAX_LENGTH: Int = 32
        const val COUNTRY_MAX_LENGTH: Int = 16

        /** A deterministic venue identity: lowercase SHA-256 hex. */
        private val VENUE_KEY = Regex("^[0-9a-f]{64}$")

        /**
         * Trim + validate an optional structured-address field. null (unknown)
         * is preserved; a provided value must be non-blank after trimming and
         * within [maxLength].
         *
         * @throws IllegalArgumentException if [value] is blank after trim or too long.
         */
        private fun trimAddressField(field: String, value: String?, maxLength: Int): String? {
            if (value == null) return null
            val trimmed = value.trim()
            require(trimmed.isNotBlank()) { "Listing $field must not be blank." }
            require(trimmed.length <= maxLength) { "Listing $field must be $maxLength characters or fewer." }
            return trimmed
        }

        /**
         * Create a brand-new listing. Names/addresses are trimmed; blank values
         * are rejected. Always unverified (listing-first model) and timestamped now.
         *
         * The authenticated Add Listing flow requires a cuisine and an owning
         * account; brand/provenance are null for user-added rows.
         *
         * @throws IllegalArgumentException if [name] or [address] is blank, or any
         *   provided structured-address field ([city]/[province]/[postal]/[country])
         *   is blank after trim or exceeds its length cap.
         */
        fun new(
            name: String,
            address: String,
            city: String? = null,
            province: String? = null,
            postal: String? = null,
            country: String? = null,
            location: LatLng,
            cuisine: Cuisine,
            isHandCut: Boolean? = null,
            isDelivery: Boolean? = null,
            ownerId: Uuid,
            price: Price? = null,
            rating: Rating? = null,
            halalScope: HalalScope = HalalScope.DEFAULT,
            halalItems: Set<HalalItem> = emptySet(),
            crossContamination: CrossContamination = CrossContamination.DEFAULT,
            alcoholServed: Boolean = false,
        ): RestaurantListing {
            val trimmedName = name.trim()
            val trimmedAddress = address.trim()
            require(trimmedName.isNotBlank()) { "Listing name must not be blank." }
            require(trimmedAddress.isNotBlank()) { "Listing address must not be blank." }
            return RestaurantListing(
                id = Uuid.random(),
                name = trimmedName,
                address = trimmedAddress,
                city = trimAddressField("city", city, CITY_MAX_LENGTH),
                province = trimAddressField("province", province, PROVINCE_MAX_LENGTH),
                postal = trimAddressField("postal", postal, POSTAL_MAX_LENGTH),
                country = trimAddressField("country", country, COUNTRY_MAX_LENGTH),
                location = location,
                cuisine = cuisine,
                isHandCut = isHandCut,
                isDelivery = isDelivery,
                price = price,
                rating = rating,
                ownerId = ownerId,
                brandId = null,
                provenance = null,
                venueKey = null,
                verificationStatus = VerificationStatus.DEFAULT,
                createdAt = Clock.System.now(),
                halalScope = halalScope,
                halalItems = halalItems,
                crossContamination = crossContamination,
                alcoholServed = alcoholServed,
            )
        }

        /**
         * Build an ingested listing (D-15). Unlike [new], the row may have no
         * owner and no cuisine, carries a brand/provenance and a required
         * deterministic [venueKey] (D-18), and may start `VERIFIED` only when it
         * is backed by a certifier-registry provenance and the
         * `CERTIFIER_REGISTRY` basis. The claim that justifies it is enforced by
         * the load use case, not here.
         *
         * [venueKey] is deliberately immutable on edit: [withUpdatedFields]
         * preserves it, because it is the venue's identity, not its content.
         *
         * @throws IllegalArgumentException if the name/address is blank, an
         *   address field is blank/too long, [provenance] is null, [venueKey] is
         *   not a 64-char lowercase hex, a `VERIFIED` row is not certifier-backed
         *   with the `CERTIFIER_REGISTRY` basis, or an `UNVERIFIED` row carries a
         *   basis.
         */
        fun imported(
            name: String,
            address: String,
            city: String? = null,
            province: String? = null,
            postal: String? = null,
            country: String? = null,
            location: LatLng,
            cuisine: Cuisine? = null,
            isHandCut: Boolean? = null,
            isDelivery: Boolean? = null,
            price: Price? = null,
            rating: Rating? = null,
            ownerId: Uuid? = null,
            brandId: Uuid? = null,
            provenance: Provenance?,
            venueKey: String,
            verificationStatus: VerificationStatus = VerificationStatus.UNVERIFIED,
            verificationBasis: VerificationBasis? = null,
            createdAt: Instant = Clock.System.now(),
            halalScope: HalalScope = HalalScope.DEFAULT,
            halalItems: Set<HalalItem> = emptySet(),
            crossContamination: CrossContamination = CrossContamination.DEFAULT,
            alcoholServed: Boolean = false,
            venueType: VenueType? = null,
            venueTypeSource: EnrichmentSource? = null,
            cuisineSource: EnrichmentSource? = null,
            coverPhoto: CoverPhoto? = null,
        ): RestaurantListing {
            val trimmedName = name.trim()
            val trimmedAddress = address.trim()
            require(trimmedName.isNotBlank()) { "Listing name must not be blank." }
            require(trimmedAddress.isNotBlank()) { "Listing address must not be blank." }
            require(provenance != null) { "An imported listing must carry a provenance stamp." }
            require(VENUE_KEY.matches(venueKey)) {
                "venueKey must be a 64-character lowercase hex string."
            }
            require((venueType == null) == (venueTypeSource == null)) {
                "An imported venue type must name the evidence that produced it."
            }
            require(cuisineSource == null || cuisine != null) {
                "A cuisine source without a cuisine is not auditable."
            }
            when (verificationStatus) {
                VerificationStatus.VERIFIED -> {
                    require(verificationBasis == VerificationBasis.CERTIFIER_REGISTRY) {
                        "A VERIFIED imported listing requires the CERTIFIER_REGISTRY basis."
                    }
                    require(provenance.isCertifierRegistry) {
                        "A VERIFIED imported listing must carry a certifier-registry provenance."
                    }
                }
                VerificationStatus.UNVERIFIED -> require(verificationBasis == null) {
                    "An UNVERIFIED imported listing must not carry a verification basis."
                }
            }

            return RestaurantListing(
                id = Uuid.random(),
                name = trimmedName,
                address = trimmedAddress,
                city = trimAddressField("city", city, CITY_MAX_LENGTH),
                province = trimAddressField("province", province, PROVINCE_MAX_LENGTH),
                postal = trimAddressField("postal", postal, POSTAL_MAX_LENGTH),
                country = trimAddressField("country", country, COUNTRY_MAX_LENGTH),
                location = location,
                cuisine = cuisine,
                isHandCut = isHandCut,
                isDelivery = isDelivery,
                price = price,
                rating = rating,
                ownerId = ownerId,
                brandId = brandId,
                provenance = provenance,
                venueKey = venueKey,
                verificationStatus = verificationStatus,
                createdAt = createdAt,
                halalScope = halalScope,
                halalItems = halalItems,
                crossContamination = crossContamination,
                alcoholServed = alcoholServed,
                venueType = venueType,
                venueTypeSource = venueTypeSource,
                cuisineSource = cuisineSource,
                coverPhoto = coverPhoto,
            )
        }

        /**
         * Reconstitute a listing that was previously persisted (any status).
         * [cuisine], [ownerId], [brandId] and [provenance] may be null — this is
         * how unclaimed, no-cuisine community seed rows are materialised.
         */
        fun fromStorage(
            id: Uuid,
            name: String,
            address: String,
            city: String? = null,
            province: String? = null,
            postal: String? = null,
            country: String? = null,
            location: LatLng,
            cuisine: Cuisine?,
            isHandCut: Boolean? = null,
            isDelivery: Boolean? = null,
            price: Price? = null,
            rating: Rating? = null,
            ownerId: Uuid?,
            brandId: Uuid?,
            provenance: Provenance?,
            venueKey: String? = null,
            verificationStatus: VerificationStatus,
            createdAt: Instant,
            halalScope: HalalScope = HalalScope.DEFAULT,
            halalItems: Set<HalalItem> = emptySet(),
            crossContamination: CrossContamination = CrossContamination.DEFAULT,
            alcoholServed: Boolean = false,
        ): RestaurantListing = RestaurantListing(
            id = id,
            name = name,
            address = address,
            city = trimAddressField("city", city, CITY_MAX_LENGTH),
            province = trimAddressField("province", province, PROVINCE_MAX_LENGTH),
            postal = trimAddressField("postal", postal, POSTAL_MAX_LENGTH),
            country = trimAddressField("country", country, COUNTRY_MAX_LENGTH),
            location = location,
            cuisine = cuisine,
            isHandCut = isHandCut,
            isDelivery = isDelivery,
            price = price,
            rating = rating,
            ownerId = ownerId,
            brandId = brandId,
            provenance = provenance,
            venueKey = venueKey,
            verificationStatus = verificationStatus,
            createdAt = createdAt,
            halalScope = halalScope,
            halalItems = halalItems,
            crossContamination = crossContamination,
            alcoholServed = alcoholServed,
        )
    }
}