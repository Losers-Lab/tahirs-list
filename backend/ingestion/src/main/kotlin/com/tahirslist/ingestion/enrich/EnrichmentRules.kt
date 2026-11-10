package com.tahirslist.ingestion.enrich

import com.tahirslist.domain.restaurant.VenueClass
import com.tahirslist.domain.restaurant.VenueType

/**
 * The curated tables the admission gate and the cuisine assigner read (SC-231).
 *
 * These are *data*, not rules: which name words mean "shop", which source
 * categories map to which class, which OSM tag means what, which brand serves
 * which cuisine. The rules themselves live in [AdmissionPolicy] and
 * [CuisineAssigner]; keeping the tables separate is what lets a curator correct a
 * word without touching a decision.
 *
 * The constructor validates the tables once. A malformed table must fail at
 * startup — a run that half-classifies is worse than a run that does not start.
 */
class EnrichmentRules(
    shopMarkers: Set<String> = DEFAULT_SHOP_MARKERS,
    premisesMarkers: Set<String> = DEFAULT_PREMISES_MARKERS,
    private val categoryRules: List<Pair<String, VenueClass>> = DEFAULT_CATEGORY_RULES,
    private val osmClasses: Map<String, VenueClass> = DEFAULT_OSM_CLASSES,
    private val brandCuisines: Map<String, String> = DEFAULT_BRAND_CUISINES,
    private val keywordCuisines: Map<String, String> = DEFAULT_KEYWORD_CUISINES,
    private val shopClassByMarker: Map<String, VenueClass> = DEFAULT_SHOP_CLASSES,
) {

    private val shop: Set<String> = normalizeAll(shopMarkers, "shop marker")
    private val premises: Set<String> = normalizeAll(premisesMarkers, "premises marker")
    private val brands: Map<String, String> = brandCuisines.mapKeys { normalize(it.key) }
    private val keywords: Map<String, String> = keywordCuisines.mapKeys { normalize(it.key) }

    init {
        require(shop.isNotEmpty()) { "The shop-marker table must not be empty." }
        require(premises.isNotEmpty()) { "The premises-marker table must not be empty." }
        val both = shop.intersect(premises)
        require(both.isEmpty()) {
            "A name word cannot be both a shop and a premises marker: ${both.sorted()}."
        }
        require(categoryRules.isNotEmpty()) { "The category table must not be empty." }
        brandCuisines.values.forEach { require(it.isNotBlank()) { "A brand cuisine must not be blank." } }
        keywordCuisines.values.forEach { require(it.isNotBlank()) { "A keyword cuisine must not be blank." } }
        shopClassByMarker.forEach { (marker, cls) ->
            require(marker in shop) { "Shop class '$cls' is attached to '$marker', which is not a shop marker." }
            require(!cls.isPremises) { "A shop class ($cls) must not be a premises class." }
        }
    }

    // ---- admission ----------------------------------------------------------

    /** The shop words present in [name], longest first so "meat market" beats "market". */
    fun shopMarkersIn(name: String): List<String> = markersIn(shop, name)

    /** The premises words present in [name]. */
    fun premisesMarkersIn(name: String): List<String> = markersIn(premises, name)

    /** The class a shop marker implies, for the reject reason. */
    fun classForShopMarker(marker: String): VenueClass =
        shopClassByMarker[marker] ?: VenueClass.OTHER

    /** The class a source category names, or null when the category says nothing. */
    fun classForCategory(category: String): VenueClass? {
        // HFSAA publishes MEAT_MARKET, HMS publishes "Restaurants": normalise the
        // separators so both read as words. A substring test is deliberate here —
        // "restaurant" must also match "Restaurants".
        val c = normalize(category)
        if (c.isEmpty()) return null
        return categoryRules.firstOrNull { (needle, _) -> needle in c }?.second
    }

    /** The class an OSM feature names, or null when the tag is not decisive. */
    fun classForOsm(key: String?, value: String?): VenueClass? {
        val k = key?.trim()?.lowercase() ?: return null
        val v = value?.trim()?.lowercase() ?: return null
        return osmClasses["$k=$v"]
    }

    /**
     * The restaurant kind a name narrows to, in the fixed order the report
     * relies on: cafe, then restaurant words, then bakery.
     */
    fun typeForName(name: String): VenueType? {
        val normalized = " ${normalize(name)} "
        if (CAFE_WORDS.any { " $it " in normalized }) return VenueType.CAFE
        if (RESTAURANT_WORDS.any { " $it " in normalized }) return VenueType.RESTAURANT
        if (BAKERY_WORDS.any { " $it " in normalized }) return VenueType.BAKERY
        return null
    }

    // ---- cuisine ------------------------------------------------------------

    /** The curated brand mapping for this name, if any: cuisine to the brand key. */
    fun cuisineForBrand(name: String): Pair<String, String>? {
        val normalized = normalize(name)
        return brands.entries
            .filter { (brand, _) -> normalized == brand || normalized.startsWith("$brand ") }
            .maxByOrNull { it.key.length }
            ?.let { (brand, cuisine) -> cuisine to brand }
    }

    /** Every cuisine a name keyword suggests, with the word that suggested it. */
    fun cuisineForKeywords(name: String): List<Pair<String, String>> {
        val normalized = " ${normalize(name)} "
        return keywords.entries
            .filter { (word, _) -> word in normalized }
            .map { (word, cuisine) -> cuisine to word }
            .distinct()
    }

    // ---- helpers ------------------------------------------------------------

    private fun markersIn(markers: Set<String>, name: String): List<String> {
        val normalized = " ${normalize(name)} "
        return markers
            .filter { it.isNotEmpty() && " $it " in normalized }
            .sortedByDescending { it.length }
    }

    private fun normalize(value: String): String =
        value.lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()

    private fun normalizeAll(values: Set<String>, label: String): Set<String> {
        val normalized = values.map { normalize(it) }
        require(normalized.none { it.isBlank() }) { "A blank $label is not a word." }
        return normalized.toSet()
    }

    private companion object {

        /** Retail: you buy food here, you do not sit down here. */
        val DEFAULT_SHOP_MARKERS = setOf(
            "butcher", "butcher shop", "meat shop", "meat market", "meat processing",
            "poultry", "slaughter", "abattoir", "grocery", "supermarket", "food mart",
            "wholesale", "distributor", "sweets", "confectionery", "fish market",
        )

        /**
         * Somewhere with a counter or a kitchen. `bakery` is here because the
         * owner ruled a bakery a restaurant (L-3); `market` is deliberately NOT
         * here, so a market is a shop unless the name also says kitchen.
         */
        val DEFAULT_PREMISES_MARKERS = setOf(
            "restaurant", "grill", "kitchen", "cafe", "coffee", "coffeehouse",
            "diner", "pizzeria", "pizza", "bbq", "bakery", "eatery", "bistro",
            "takeout", "take out", "canteen", "dining", "dining hall", "food court",
        )

        val CAFE_WORDS = listOf("cafe", "coffee", "coffeehouse")
        val RESTAURANT_WORDS = listOf(
            "restaurant", "grill", "kitchen", "diner", "pizzeria", "pizza",
            "bbq", "eatery", "bistro", "takeout", "take out", "canteen", "dining",
        )
        val BAKERY_WORDS = listOf("bakery", "patisserie", "pastry")

        /**
         * Ordered: the first needle that appears in the category decides. A
         * "Dining Hall" and a "Restaurant" are both premises; a "Meat Market" is
         * a shop even when it also says "market".
         */
        val DEFAULT_CATEGORY_RULES = listOf(
            "restaurant" to VenueClass.RESTAURANT,
            "dining" to VenueClass.RESTAURANT,
            "cafe" to VenueClass.CAFE,
            "bakery" to VenueClass.BAKERY,
            "cater" to VenueClass.CATERER,
            "meat market" to VenueClass.BUTCHER,
            "slaughter" to VenueClass.BUTCHER,
            "butcher" to VenueClass.BUTCHER,
            "grocery" to VenueClass.GROCERY,
            "retail" to VenueClass.OTHER,
            "distributor" to VenueClass.OTHER,
            "processor" to VenueClass.OTHER,
            "misc" to VenueClass.OTHER,
            "wholesale" to VenueClass.OTHER,
        )

        /** Decisive OSM features. Anything else (a road, a boundary) is not a venue. */
        val DEFAULT_OSM_CLASSES = mapOf(
            "amenity=restaurant" to VenueClass.RESTAURANT,
            "amenity=fast_food" to VenueClass.FAST_FOOD,
            "amenity=cafe" to VenueClass.CAFE,
            "amenity=food_court" to VenueClass.RESTAURANT,
            "amenity=marketplace" to VenueClass.MARKET,
            "shop=bakery" to VenueClass.BAKERY,
            "shop=butcher" to VenueClass.BUTCHER,
            "shop=meat" to VenueClass.BUTCHER,
            "shop=supermarket" to VenueClass.GROCERY,
            "shop=grocery" to VenueClass.GROCERY,
            "shop=convenience" to VenueClass.GROCERY,
            "shop=greengrocer" to VenueClass.GROCERY,
            "shop=marketplace" to VenueClass.MARKET,
        )

        val DEFAULT_SHOP_CLASSES = mapOf(
            "butcher" to VenueClass.BUTCHER,
            "butcher shop" to VenueClass.BUTCHER,
            "meat shop" to VenueClass.BUTCHER,
            "meat market" to VenueClass.BUTCHER,
            "meat processing" to VenueClass.BUTCHER,
            "poultry" to VenueClass.BUTCHER,
            "slaughter" to VenueClass.BUTCHER,
            "abattoir" to VenueClass.BUTCHER,
            "fish market" to VenueClass.MARKET,
            "grocery" to VenueClass.GROCERY,
            "supermarket" to VenueClass.GROCERY,
            "food mart" to VenueClass.GROCERY,
            "wholesale" to VenueClass.OTHER,
            "distributor" to VenueClass.OTHER,
            "sweets" to VenueClass.OTHER,
            "confectionery" to VenueClass.OTHER,
        )

        /**
         * Curated, small on purpose: a brand whose cuisine is not in dispute.
         * Nothing is guessed from a chain's name alone.
         */
        val DEFAULT_BRAND_CUISINES = mapOf(
            "paramount fine foods" to "lebanese",
            "popeyes" to "cajun",
            "jollibee" to "filipino",
        )

        /**
         * Weak evidence: a distinctive dish word. Kept small and specific, so two
         * families in one name is a visible conflict rather than a coin flip.
         */
        val DEFAULT_KEYWORD_CUISINES = mapOf(
            "biryani" to "pakistani",
            "karahi" to "pakistani",
            "nihari" to "pakistani",
            "haleem" to "pakistani",
            "tandoori" to "indian",
            "chaat" to "indian",
            "samosa" to "indian",
            "dosa" to "south indian",
            "idli" to "south indian",
            "kebab" to "turkish",
            "kabab" to "turkish",
            "shawarma" to "middle eastern",
            "afghan" to "afghan",
            "pho" to "vietnamese",
            "sushi" to "japanese",
            "taco" to "mexican",
            "ethiopian" to "ethiopian",
            "pizza" to "pizza",
        )
    }
}
