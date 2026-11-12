package com.tahirslist.domain.restaurant

/**
 * What the evidence says a place is, before the admission gate decides whether
 * it may exist in the catalogue (SC-231).
 *
 * This is deliberately wider than [VenueType]: to *exclude* a butcher we have to
 * recognise one, and that recognition is a property of the source row, not of any
 * stored listing. The class is never persisted; it appears in the run report and
 * in the reject reason so a curator can see what was dropped and why.
 *
 * [isShop] and [isPremises] group the classes the way the report does: a row that
 * looks like a shop is excluded unless something also says premises.
 */
enum class VenueClass {
    RESTAURANT,
    CAFE,
    FAST_FOOD,
    BAKERY,
    GROCERY,
    MARKET,
    BUTCHER,
    CATERER,
    OTHER,
    ;

    /** Retail: somewhere you buy food to take away, not somewhere you eat. */
    val isShop: Boolean
        get() = this == GROCERY || this == MARKET || this == BUTCHER

    /** Somewhere with a counter, a table or a kitchen: a restaurant of some kind. */
    val isPremises: Boolean
        get() = this == RESTAURANT || this == CAFE || this == FAST_FOOD || this == BAKERY
}
