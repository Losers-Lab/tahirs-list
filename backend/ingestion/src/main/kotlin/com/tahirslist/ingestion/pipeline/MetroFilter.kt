package com.tahirslist.ingestion.pipeline

import com.tahirslist.ingestion.SourceListing

/**
 * One of the sample metros. Membership is by city: a directory row is in the
 * metro when its city (after normalisation) is one of [cities]. Rows without a
 * city are not guessed into a metro; they are reported as out-of-selection.
 */
data class Metro(
    val name: String,
    val cities: Set<String>,
) {
    init {
        require(name.isNotBlank()) { "a metro must have a name" }
        require(cities.isNotEmpty()) { "a metro must list at least one city" }
    }

    fun accepts(listing: SourceListing): Boolean {
        val city = listing.city?.let(::normalize) ?: return false
        return city in cities
    }

    private fun normalize(value: String): String =
        value.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()

    companion object {
        val DALLAS = Metro(
            name = "Dallas",
            cities = setOf(
                "dallas", "richardson", "plano", "irving", "garland", "addison",
                "carrollton", "frisco", "arlington", "grand prairie", "mesquite",
                "lewisville", "denton", "mckinney", "allen",
            ),
        )
        val NYC = Metro(
            name = "NYC",
            cities = setOf(
                "new york", "manhattan", "brooklyn", "queens", "bronx",
                "staten island", "long island city", "jersey city", "astoria",
                "flushing", "jackson heights",
            ),
        )
        val TORONTO = Metro(
            name = "Toronto",
            cities = setOf(
                "toronto", "mississauga", "brampton", "markham", "richmond hill",
                "vaughan", "scarborough", "etobicoke", "north york", "thornhill",
                "oakville", "ajax", "pickering",
            ),
        )

        val DEFAULTS: List<Metro> = listOf(DALLAS, NYC, TORONTO)
    }
}

/** Assigns a source row to a configured metro; null means "outside the selection". */
class MetroFilter(private val metros: List<Metro> = Metro.DEFAULTS) {

    fun metroFor(listing: SourceListing): Metro? = metros.firstOrNull { it.accepts(listing) }

    fun named(name: String): Metro? = metros.firstOrNull { it.name.equals(name, ignoreCase = true) }

    fun selected(names: Set<String>): List<Metro> {
        if (names.isEmpty()) return metros
        val normalised = names.map { it.trim().lowercase() }.toSet()
        return metros.filter { it.name.lowercase() in normalised }
    }
}
