package com.tahirslist.domain.restaurant

/**
 * Stamps the origin of a listing row. Closed vocabulary, mirrored by the
 * `ck_restaurant_listings_provenance` CHECK constraint (V6, widened by V22):
 *
 *  - [RESEARCH_SEED]          — the row came from the verified research seed set.
 *  - [PHOTON_GEOCODE]         — coordinates resolved via the Photon/OSM geocoder.
 *  - [RESEARCH_SEED_PHOTON_GEOCODE] — the combined stamp on every seed row
 *                            (`research-seed / photon-geocode`).
 *  - [CERTIFIER_HMS] / [CERTIFIER_HFSAA] / [CERTIFIER_HMA] — the row came from
 *                            that certifier's published directory (SC-211).
 *  - [MANUAL_ZABIHAH]         — a curator transcribed a licensed Zabihah row
 *                            and cited it by hand (D-13).
 *
 * Ordinary user-added listings (Add Listing use case) carry `null` provenance.
 * The vocabulary is deliberately kept separate from the `is_hand_cut` boolean
 * (Omar adjudication): no cross-breeding between the two vocabularies.
 */
data class Provenance(val raw: String) {

    val value: String = raw.trim().lowercase()

    init {
        require(value in ClosedVocabulary) {
            "Provenance must be one of [${ClosedVocabulary.joinToString(", ")}], got: '$raw'"
        }
    }

    companion object {
        // Declared before the constants so Companion initialises the vocabulary
        // first (Kotlin initialises companion properties in declaration order);
        // the Provenance constructor validates against this set.
        val ClosedVocabulary: Set<String> = setOf(
            "research-seed",
            "photon-geocode",
            "research-seed / photon-geocode",
            "certifier-hms",
            "certifier-hfsaa",
            "certifier-hma",
            "manual-zabihah",
        )

        /** Provenance values stamped by an audited Tier-1 certifier registry. */
        val CertifierRegistry: Set<String> = setOf(
            "certifier-hms",
            "certifier-hfsaa",
            "certifier-hma",
        )

        val RESEARCH_SEED = Provenance("research-seed")
        val PHOTON_GEOCODE = Provenance("photon-geocode")
        val RESEARCH_SEED_PHOTON_GEOCODE = Provenance("research-seed / photon-geocode")
        val CERTIFIER_HMS = Provenance("certifier-hms")
        val CERTIFIER_HFSAA = Provenance("certifier-hfsaa")
        val CERTIFIER_HMA = Provenance("certifier-hma")
        val MANUAL_ZABIHAH = Provenance("manual-zabihah")
    }

    /** True only for a Tier-1 certifier registry stamp (never manual or seed). */
    val isCertifierRegistry: Boolean get() = value in CertifierRegistry
}
