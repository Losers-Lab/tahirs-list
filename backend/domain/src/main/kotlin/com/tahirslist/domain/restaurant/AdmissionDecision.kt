package com.tahirslist.domain.restaurant

/**
 * The admission gate's verdict on one source row (SC-231).
 *
 * The catalogue holds restaurants only (L-1), so a row must earn its place
 * before it can become a candidate. There are exactly three answers and no
 * fourth: admitted with a published type, excluded with the class that was
 * recognised, or undecided — held for a curator, never guessed into the
 * catalogue.
 *
 * [Excluded] refuses a premises class by construction. If a row is recognised as
 * a restaurant it cannot also be excluded as a non-restaurant; that combination
 * would mean the policy contradicted itself, and the report would have no honest
 * way to describe it.
 */
sealed interface AdmissionDecision {

    /** The row is a restaurant; [venueType] is what the listing will say it is. */
    data class Admitted(
        val venueType: VenueType,
        val source: EnrichmentSource,
    ) : AdmissionDecision

    /** The row is not a restaurant. [reason] names the evidence that decided it. */
    data class Excluded(
        val venueClass: VenueClass,
        val reason: String,
    ) : AdmissionDecision {
        init {
            require(reason.isNotBlank()) { "An exclusion must say why." }
            require(!venueClass.isPremises) {
                "A premises class ($venueClass) cannot be excluded as a non-restaurant."
            }
        }
    }

    /** No evidence decided it; the row is held and reported. */
    data class Undecided(
        val reason: String,
    ) : AdmissionDecision {
        init {
            require(reason.isNotBlank()) { "An undecided row must say what was missing." }
        }
    }
}
