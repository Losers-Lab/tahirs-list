package com.tahirslist.ingestion.pipeline

import com.tahirslist.application.geo.GeocodedPlace
import com.tahirslist.application.geo.GeocoderPort
import com.tahirslist.application.geo.GeocodingException
import com.tahirslist.application.ingestion.IngestReject
import com.tahirslist.application.ingestion.IngestionCandidate
import com.tahirslist.application.ingestion.RejectReason
import com.tahirslist.domain.restaurant.AdmissionDecision
import com.tahirslist.domain.restaurant.CertificationClaim
import com.tahirslist.domain.restaurant.CertificationTier
import com.tahirslist.domain.restaurant.CrossContamination
import com.tahirslist.domain.restaurant.HandCutDeclaration
import com.tahirslist.ingestion.SourceListing
import com.tahirslist.ingestion.enrich.RestaurantAdmission
import com.tahirslist.ingestion.identity.VenueIdentity
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.datetime.toKotlinLocalDate

/** The geocoder itself is down: a run cannot produce a single acceptable location. */
class GeocoderUnavailableException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)

/** One row's outcome after geocoding and match confirmation. */
sealed interface CandidateBuild {
    data class Built(val candidate: IngestionCandidate) : CandidateBuild
    data class Rejected(val reject: IngestReject) : CandidateBuild
}

/**
 * Turns one translated directory row into a loadable candidate: geocode,
 * confirm the match, derive the venue key and build the citation claim.
 *
 * ## Query strategy
 *
 * Photon matches every token it is given, and it has no idea that "suite c200"
 * is not part of a street. A single query assembled as "name, raw address, city"
 * therefore resolves almost nothing: in a real four-metro run 191 of 202 rows
 * came back with no hit purely because of unit noise and unexpanded suffixes.
 *
 * Each row is therefore asked for in two forms, cheapest first:
 *
 *  1. `name, cleaned street, city, region` — pins the POI when OSM has it;
 *  2. `cleaned street, city, region` — pins a house when the business itself is
 *     not in OSM but its address is.
 *
 * A variant is used only if [MatchConfirmationPolicy] confirms it, so a fallback
 * can never trade precision for coverage.
 *
 * The directory's own coordinates are deliberately not used: they are evidence,
 * not the accepted location (BR-3). A row that cannot be confirmed is rejected
 * with the reason, never given an invented point.
 */
class CandidateFactory(
    private val geocoder: GeocoderPort,
    private val matches: MatchConfirmationPolicy = MatchConfirmationPolicy(),
    private val metros: MetroCatalog = MetroCatalog.default(),
    private val admission: RestaurantAdmission = RestaurantAdmission(),
) {

    /**
     * One resolution per row for the lifetime of this factory (one run). Metro
     * selection needs the point of a row that carries no coordinates, and the
     * build then needs the same decision; asking twice would be a wasted call
     * and could disagree between calls.
     */
    private val resolved = mutableMapOf<String, Resolution>()

    /**
     * Resolve a row to a confirmed place without deciding trust or membership.
     * Translation failures abort the run (a provider outage is not a row-level
     * reject); an unconfirmed row is returned as null and becomes the caller's
     * reject.
     */
    suspend fun locate(listing: SourceListing): GeocodedPlace? =
        (resolve(listing) as? Resolution.Found)?.place

    /**
     * Why [locate] could not return a place for [listing], if it has been asked
     * already. Callers that record the run's rejects use this so an imprecise
     * answer is not reported as "no geocoder match".
     */
    fun rejectionFor(listing: SourceListing): IngestReject? =
        when (val resolution = resolved[key(listing)]) {
            is Resolution.Ambiguous -> reject(
                RejectReason.valueOf(resolution.reason),
                resolution.detail,
                listing,
            )
            else -> null
        }

    /** Convenience for callers that hold only the metro's name. */
    suspend fun build(
        listing: SourceListing,
        metro: String,
        crossContamination: CrossContamination = CrossContamination.DEFAULT,
    ): CandidateBuild {
        val definition = metros.named(metro) ?: return CandidateBuild.Rejected(
            reject(RejectReason.MISSING_METRO, "metro '$metro' is not configured", listing),
        )
        return build(listing, definition, crossContamination)
    }

    suspend fun build(
        listing: SourceListing,
        metro: MetroDefinition,
        crossContamination: CrossContamination = CrossContamination.DEFAULT,
    ): CandidateBuild {
        if (listing.name.isBlank() || listing.addressLines.isEmpty()) {
            return CandidateBuild.Rejected(
                reject(RejectReason.INVALID_CANDIDATE, "row has no name or street address", listing),
            )
        }

        // The gate runs before any geocoder call: a butcher is not worth a query,
        // and the catalogue holds restaurants only (L-1).
        val admitted = when (val verdict = admission.decide(listing)) {
            is AdmissionDecision.Excluded -> return CandidateBuild.Rejected(
                reject(
                    RejectReason.NOT_A_RESTAURANT,
                    "${verdict.venueClass.name.lowercase()}: ${verdict.reason}",
                    listing,
                    venueClass = verdict.venueClass.name,
                ),
            )
            is AdmissionDecision.Undecided -> return CandidateBuild.Rejected(
                reject(RejectReason.UNDECIDED_VENUE, verdict.reason, listing),
            )
            is AdmissionDecision.Admitted -> verdict
        }

        val place = when (val resolution = resolve(listing)) {
            is Resolution.Found -> resolution.place
            is Resolution.Unmatched -> return CandidateBuild.Rejected(
                reject(RejectReason.NO_GEOCODER_MATCH, "no geocoder match for '${listing.name}'", listing),
            )
            is Resolution.Ambiguous -> return CandidateBuild.Rejected(
                reject(RejectReason.valueOf(resolution.reason), resolution.detail, listing),
            )
        }

        // The radius decides membership, not the directory's city text or its
        // own coordinates (BR-3). A confirmed point outside the circle is a
        // reject with the distance, never a candidate in the wrong metro.
        if (!metro.contains(place.location)) {
            return CandidateBuild.Rejected(
                reject(
                    RejectReason.OUTSIDE_METRO,
                    "geocoded to ${place.displayName} (${place.location.lat}, ${place.location.lng}), " +
                        "${"%.1f".format(metro.distanceKm(place.location))} km from ${metro.name}'s centre " +
                        "(limit ${metro.radiusKm} km)",
                    listing,
                ),
            )
        }

        val venueKey = VenueIdentity.from(
            name = listing.name,
            addressLines = listing.addressLines,
            city = listing.city,
            region = listing.region,
            country = listing.country,
        ) ?: return CandidateBuild.Rejected(
            reject(RejectReason.INVALID_CANDIDATE, "cannot derive a venue key from the row", listing),
        )

        // Cuisine is only asked for once the row is admitted, and it never blocks
        // the load: a conflict is reported, not resolved (UC-02).
        val cuisine = admission.cuisineFor(listing)

        return CandidateBuild.Built(
            IngestionCandidate(
                name = listing.name,
                address = listing.addressLines.joinToString(", "),
                city = listing.city,
                region = listing.region,
                postal = listing.postalCode,
                country = listing.country,
                location = place.location,
                venueKey = venueKey.key,
                claims = listOf(claimFor(listing)),
                metro = metro.name,
                manualCrossContamination = crossContamination,
                admission = admitted,
                cuisineName = cuisine.cuisine,
                cuisineSource = cuisine.source,
                cuisineConflict = cuisine.conflict,
            ),
        )
    }

    fun claimFor(listing: SourceListing): CertificationClaim = CertificationClaim(
        source = listing.source.certifierId,
        sourceRecordRef = listing.source.recordId,
        sourceUrl = listing.source.url,
        certifier = listing.standard.certifier,
        tier = CertificationTier.valueOf(listing.standard.tier.name),
        declaredHandCut = HandCutDeclaration.valueOf(listing.standard.handCut.name),
        expiresOn = listing.expiresOn?.toKotlinLocalDate(),
    )

    /**
     * Runs the query ladder and keeps the first confirmed answer. An unconfirmed
     * answer is remembered so the report can say *why* the row was rejected
     * (e.g. "matched a street but not house 13340").
     */
    private suspend fun resolve(listing: SourceListing): Resolution {
        val key = key(listing)
        resolved[key]?.let { return it }

        val address = AddressText.parse(
            lines = listing.addressLines,
            city = listing.city,
            region = listing.region,
            postalCode = listing.postalCode,
        )
        val query = MatchQuery(
            name = listing.name,
            city = address.city,
            region = address.region,
            country = listing.country,
            street = address.street,
            houseNumber = address.houseNumber,
            streetTokens = address.streetTokens,
            postalCode = address.postalCode,
        )

        var lastReject: MatchDecision.Rejected? = null
        for (text in queries(listing.name, address)) {
            val place = try {
                geocoder.geocode(text)
            } catch (e: CancellationException) {
                throw e
            } catch (e: GeocodingException) {
                throw GeocoderUnavailableException(
                    "geocoder failed while resolving '${listing.name}': ${e.message}",
                    e,
                )
            } ?: continue

            when (val decision = matches.confirm(query, place)) {
                is MatchDecision.Confirmed -> {
                    val found = Resolution.Found(decision.place)
                    resolved[key] = found
                    return found
                }
                is MatchDecision.Rejected -> lastReject = decision
            }
        }

        val result = lastReject?.let { Resolution.Ambiguous(it.reason, it.detail) }
            ?: Resolution.Unmatched
        resolved[key] = result
        return result
    }

    /** The two query forms, most precise first; a blank form is skipped. */
    private fun queries(name: String, address: GeocodingAddress): List<String> {
        val place = address.placeQuery()
        return listOfNotNull(
            place?.let { "$name, $it" },
            place,
        )
            // A pathological address is rejected, not sent as a URL the server
            // answers with 414 (which would abort the whole run).
            .filter { it.length <= MAX_QUERY_LENGTH }
            .distinct()
    }

    private fun key(listing: SourceListing): String =
        "${listing.source.certifierId}\u0000${listing.source.recordId}"

    private fun reject(
        reason: RejectReason,
        detail: String,
        listing: SourceListing,
        venueClass: String? = null,
    ) = IngestReject(
        reason = reason,
        detail = detail,
        source = listing.source.certifierId,
        sourceRecordRef = listing.source.recordId,
        name = listing.name,
        venueClass = venueClass,
    )

    private sealed interface Resolution {
        data class Found(val place: GeocodedPlace) : Resolution
        data class Ambiguous(val reason: String, val detail: String) : Resolution
        data object Unmatched : Resolution
    }

    private companion object {
        /** Photon answers a URL over a few hundred bytes with 414. */
        const val MAX_QUERY_LENGTH = 400
    }
}
