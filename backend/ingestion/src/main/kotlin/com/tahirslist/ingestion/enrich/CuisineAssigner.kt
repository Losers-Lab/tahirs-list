package com.tahirslist.ingestion.enrich

import com.tahirslist.domain.restaurant.EnrichmentSource

/**
 * Chooses a cuisine for an admitted row (SC-231, UC-02).
 *
 * Order: what the source publishes, then a curated brand mapping, then the OSM
 * `cuisine` tag, then a distinctive name word. The first decisive source wins and
 * the evidence class is recorded with it.
 *
 * Two families in one name ("Pizza & Biryani") is a **conflict**, not a choice:
 * the cuisine stays null and the conflict goes to the review queue. A coin flip
 * here would look like knowledge.
 */
class CuisineAssigner(private val rules: EnrichmentRules = EnrichmentRules()) {

    /** @param cuisine lower-cased cuisine or null; [source] null when undecided. */
    data class Decision(
        val cuisine: String?,
        val source: EnrichmentSource? = null,
        val conflict: String? = null,
    ) {
        init {
            require((cuisine == null) || (source != null)) {
                "A cuisine must carry the evidence that produced it."
            }
            require(cuisine == null || conflict == null) {
                "A decided cuisine cannot also be a conflict."
            }
        }
    }

    fun assign(
        name: String,
        osmCuisine: String? = null,
        publishedCuisine: String? = null,
    ): Decision {
        publishedCuisine?.trim()?.takeIf { it.isNotEmpty() }?.let {
            return Decision(it.lowercase(), EnrichmentSource.SOURCE_CATEGORY)
        }

        rules.cuisineForBrand(name)?.let { (cuisine, _) ->
            return Decision(cuisine.lowercase(), EnrichmentSource.BRAND_MAP)
        }

        osmCuisine?.trim()?.takeIf { it.isNotEmpty() }?.let {
            return Decision(it.lowercase(), EnrichmentSource.OSM_FEATURE)
        }

        val hints = rules.cuisineForKeywords(name)
        val families = hints.map { it.first }.distinct()
        return when (families.size) {
            0 -> Decision(cuisine = null)
            1 -> Decision(families.single().lowercase(), EnrichmentSource.NAME_KEYWORD)
            else -> Decision(
                cuisine = null,
                conflict = "name suggests " + families.sorted().joinToString(" / "),
            )
        }
    }
}
