package com.tahirslist.ingestion.pipeline

import com.tahirslist.application.geo.GeocodedPlace
import com.tahirslist.application.geo.GeocoderPort
import com.tahirslist.application.geo.GeocodingException
import com.tahirslist.application.ingestion.IngestReject
import com.tahirslist.application.ingestion.IngestionCandidate
import com.tahirslist.application.ingestion.RejectReason
import com.tahirslist.domain.restaurant.CertificationClaim
import com.tahirslist.domain.restaurant.CertificationTier
import com.tahirslist.domain.restaurant.CrossContamination
import com.tahirslist.domain.restaurant.HandCutDeclaration
import com.tahirslist.ingestion.SourceListing
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
 * The directory's own coordinates are deliberately not used: they are evidence,
 * not the accepted location (BR-3). A row that cannot be confirmed is rejected
 * with the reason, never given an invented point.
 */
class CandidateFactory(
    private val geocoder: GeocoderPort,
    private val matches: MatchConfirmationPolicy = MatchConfirmationPolicy(),
    private val metros: MetroCatalog = MetroCatalog.default(),
) {

    /**
     * One geocoder result per address for the lifetime of this factory (one
     * run). Metro selection needs the point of a row that carries no
     * coordinates, and the build then needs the same point; geocoding it twice
     * would be a wasted call and could even disagree between calls.
     */
    private val located = mutableMapOf<String, GeocodedPlace?>()

    /**
     * Resolve a row to a place without deciding trust or membership. Translation
     * failures abort the run (a provider outage is not a row-level reject); a
     * no-match is returned as null and becomes the caller's reject.
     */
    suspend fun locate(listing: SourceListing): GeocodedPlace? {
        val query = queryText(listing)
        if (located.containsKey(query)) return located[query]
        val place = try {
            geocoder.geocode(query)
        } catch (e: CancellationException) {
            throw e
        } catch (e: GeocodingException) {
            throw GeocoderUnavailableException(
                "geocoder failed while resolving '${listing.name}': ${e.message}",
                e,
            )
        }
        located[query] = place
        return place
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

        val query = MatchQuery(
            name = listing.name,
            city = listing.city,
            region = listing.region,
            country = listing.country,
        )
        val place = locate(listing)
        if (place == null) {
            return CandidateBuild.Rejected(
                reject(RejectReason.NO_GEOCODER_MATCH, "no geocoder match for '${listing.name}'", listing),
            )
        }
        when (val decision = matches.confirm(query, place)) {
            is MatchDecision.Rejected -> return CandidateBuild.Rejected(
                reject(
                    RejectReason.valueOf(decision.reason),
                    decision.detail,
                    listing,
                ),
            )
            is MatchDecision.Confirmed -> Unit
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

    private fun queryText(listing: SourceListing): String =
        (listOf(listing.name) + listing.addressLines + listOfNotNull(
            listing.city,
            listing.region,
            listing.postalCode,
            listing.country,
        )).joinToString(", ")

    private fun reject(reason: RejectReason, detail: String, listing: SourceListing) = IngestReject(
        reason = reason,
        detail = detail,
        source = listing.source.certifierId,
        sourceRecordRef = listing.source.recordId,
        name = listing.name,
    )
}
