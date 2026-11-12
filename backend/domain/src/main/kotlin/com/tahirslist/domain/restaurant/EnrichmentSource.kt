package com.tahirslist.domain.restaurant

/**
 * Which evidence decided a derived value (SC-231).
 *
 * The order is a rule, not a preference: the source's own category, then the
 * matched OSM feature, then a curated brand map, then a name keyword. A weaker
 * source never overrides a stronger one, so a name can never turn a certified
 * retail store into a restaurant.
 *
 * [NAME_KEYWORD] is the only weak one: it is deterministic but a heuristic, and
 * the run report puts it in the review queue rather than treating it as fact.
 */
enum class EnrichmentSource {
    SOURCE_CATEGORY,
    OSM_FEATURE,
    BRAND_MAP,
    NAME_KEYWORD,
    ;

    val isWeak: Boolean
        get() = this == NAME_KEYWORD
}
