package com.tahirslist.ingestion.enrich

import com.tahirslist.domain.restaurant.AdmissionDecision
import com.tahirslist.domain.restaurant.EnrichmentSource
import com.tahirslist.domain.restaurant.VenueClass
import com.tahirslist.domain.restaurant.VenueType

/**
 * The admission gate's rule (SC-231, UC-01): is this row a restaurant, and if so
 * what kind?
 *
 * The order is fixed and the reason it exists is a real row: HMS files
 * `Jamaica Live Poultry Meat Market [Butcher Dept. Only]` under **Restaurants**,
 * so a coarse category cannot be trusted on its own. A specific shop word with no
 * premises word excludes the row before the category is even consulted; a
 * premises word (`kitchen`, `grill`, `bakery`) means the row is a restaurant of
 * some kind and the category may then narrow it.
 *
 * Pure: no I/O, no clock, no randomness. The same row always gets the same
 * verdict, which is what makes the batch reviewable.
 */
class AdmissionPolicy(private val rules: EnrichmentRules = EnrichmentRules()) {

    fun decide(
        category: String,
        name: String,
        osmKey: String? = null,
        osmValue: String? = null,
    ): AdmissionDecision {
        val shopWords = rules.shopMarkersIn(name)
        val premisesWords = rules.premisesMarkersIn(name)

        // 1. A shop with nothing that says premises is out, whatever the source
        //    category claims.
        if (shopWords.isNotEmpty() && premisesWords.isEmpty()) {
            val venueClass = mostSpecificClass(shopWords)
            return AdmissionDecision.Excluded(venueClass, "name: ${shopWords.joinToString(" + ")}")
        }

        val byCategory = rules.classForCategory(category)
        if (byCategory != null && !byCategory.isPremises) {
            return AdmissionDecision.Excluded(byCategory, "category: $category")
        }

        // 2. A restaurant category admits the row; the name and the OSM feature
        //    only narrow *which kind*.
        if (byCategory != null) {
            return AdmissionDecision.Admitted(
                venueType = narrow(byCategory, name, osmKey, osmValue),
                source = EnrichmentSource.SOURCE_CATEGORY,
            )
        }

        // 3. Otherwise the matched feature decides.
        val byOsm = rules.classForOsm(osmKey, osmValue)
        if (byOsm != null && !byOsm.isPremises) {
            return AdmissionDecision.Excluded(byOsm, "osm: $osmKey=$osmValue")
        }
        if (byOsm != null) {
            return AdmissionDecision.Admitted(
                venueType = narrow(byOsm, name, osmKey, osmValue),
                source = EnrichmentSource.OSM_FEATURE,
            )
        }

        // 4. Otherwise a premises word in the name.
        val byName = rules.typeForName(name)
        if (byName != null) {
            return AdmissionDecision.Admitted(byName, EnrichmentSource.NAME_KEYWORD)
        }

        // 5. Nothing says it is a restaurant: hold it, do not guess.
        return AdmissionDecision.Undecided(
            "no restaurant category, no venue feature and no premises word",
        )
    }

    /**
     * When a name carries several shop words ("meat" and "grocery"), the most
     * specific class wins so the report says what the place actually is.
     */
    private fun mostSpecificClass(shopWords: List<String>): VenueClass {
        val classes = shopWords.map { rules.classForShopMarker(it) }.toSet()
        return listOf(VenueClass.BUTCHER, VenueClass.GROCERY, VenueClass.MARKET, VenueClass.OTHER)
            .firstOrNull { it in classes } ?: VenueClass.OTHER
    }

    /**
     * The fixed narrowing order the report relies on: a cafe word beats a
     * restaurant word, which beats a bakery word. "Mannan Bakery and Grill" is a
     * restaurant; a bare "Firin Bakery" is a bakery.
     */
    private fun narrow(
        byCategory: VenueClass,
        name: String,
        osmKey: String?,
        osmValue: String?,
    ): VenueType {
        val byName = rules.typeForName(name)
        val byOsm = rules.classForOsm(osmKey, osmValue)
        return when {
            byName == VenueType.CAFE || byCategory == VenueClass.CAFE -> VenueType.CAFE
            byName != null -> byName
            byCategory == VenueClass.BAKERY -> VenueType.BAKERY
            byOsm == VenueClass.FAST_FOOD -> VenueType.FAST_FOOD
            byOsm != null && byOsm.isPremises -> VenueType.valueOf(byOsm.name)
            else -> VenueType.RESTAURANT
        }
    }
}
