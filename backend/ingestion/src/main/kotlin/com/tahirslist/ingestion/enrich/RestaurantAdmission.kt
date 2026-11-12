package com.tahirslist.ingestion.enrich

import com.tahirslist.domain.restaurant.AdmissionDecision
import com.tahirslist.ingestion.SourceListing

/**
 * One row's enrichment: whether it may be a restaurant here (SC-231 UC-01) and,
 * if so, what cuisine it serves (UC-02).
 *
 * A thin seam over [AdmissionPolicy] and [CuisineAssigner] so the pipeline asks
 * one object per row and tests can replace the curated tables without rebuilding
 * the factory.
 *
 * **OSM evidence is not available on this build.** The tag lives on
 * `GeocodedPlace`, which gains `osmKey`/`osmValue` in SC-227 (PR #84). Until that
 * merges, [osmEvidenceAvailable] is false, the parameters below stay null, and
 * the gate rests on the source category and the name. The report says so rather
 * than implying the tag was consulted and said nothing.
 */
class RestaurantAdmission(
    private val policy: AdmissionPolicy = AdmissionPolicy(),
    private val cuisines: CuisineAssigner = CuisineAssigner(),
) {

    /** False until SC-227 adds the matched feature's tags to `GeocodedPlace`. */
    val osmEvidenceAvailable: Boolean = false

    fun decide(
        listing: SourceListing,
        osmKey: String? = null,
        osmValue: String? = null,
    ): AdmissionDecision = policy.decide(
        category = listing.category,
        name = listing.name,
        osmKey = osmKey,
        osmValue = osmValue,
    )

    fun cuisineFor(listing: SourceListing, osmCuisine: String? = null): CuisineAssigner.Decision =
        cuisines.assign(listing.name, osmCuisine)
}
