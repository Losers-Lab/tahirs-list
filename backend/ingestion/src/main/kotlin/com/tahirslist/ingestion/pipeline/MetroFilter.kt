package com.tahirslist.ingestion.pipeline

import com.tahirslist.domain.restaurant.LatLng
import com.tahirslist.ingestion.SourceListing
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A metro is a **circle**: a configured centre and radius. Membership is decided
 * by distance, so growing the catalogue never requires a code change — the
 * operator adds an entry to `metros.json` (or points `--metros-file` at another
 * catalogue).
 *
 * [aliases] are only a cheap **pre-geocode** prefilter: a directory row whose
 * city text matches an alias is allowed through to geocoding. The confirmed
 * location still decides membership, because a directory's own coordinates are
 * evidence, not the accepted location (BR-3). An alias never makes a row a
 * member on its own, and a row with neither a matching alias nor coordinates is
 * skipped without spending a geocoder call.
 */
data class MetroDefinition(
    val name: String,
    val center: LatLng,
    val radiusKm: Double,
    val aliases: Set<String> = emptySet(),
) {
    init {
        require(name.isNotBlank()) { "a metro must have a name" }
        require(radiusKm > 0.0) { "metro $name must have a positive radius" }
    }

    private val normalizedAliases: List<String> = aliases
        .map(::normalize)
        .filter { it.isNotEmpty() }
        .distinct()

    fun distanceKm(point: LatLng): Double = haversineKm(center, point)

    fun contains(point: LatLng): Boolean = distanceKm(point) <= radiusKm

    /** The pre-geocode prefilter: an exact alias, never a substring of another word. */
    fun matchesText(city: String?): Boolean {
        val text = normalize(city ?: return false)
        if (text.isEmpty()) return false
        return normalizedAliases.any { alias -> text == alias || text.startsWith("$alias ") }
    }

    companion object {
        fun normalize(value: String): String =
            value.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()
    }
}

/** Great-circle distance in kilometres (haversine, mean Earth radius). */
fun haversineKm(from: LatLng, to: LatLng): Double {
    val earthRadiusKm = 6371.0088
    val dLat = Math.toRadians(to.lat - from.lat)
    val dLng = Math.toRadians(to.lng - from.lng)
    val fromLat = Math.toRadians(from.lat)
    val toLat = Math.toRadians(to.lat)
    val h = sin(dLat / 2) * sin(dLat / 2) +
        cos(fromLat) * cos(toLat) * sin(dLng / 2) * sin(dLng / 2)
    return 2 * earthRadiusKm * asin(min(1.0, sqrt(h)))
}

/**
 * The configured metros, loaded from JSON. Adding a city, a metro, or a second
 * country is a data change; nothing here names a place.
 */
class MetroCatalog(private val metros: List<MetroDefinition>) {

    init {
        require(metros.isNotEmpty()) { "at least one metro must be configured" }
        val duplicates = metros.groupBy { it.name.lowercase() }.filterValues { it.size > 1 }.keys
        require(duplicates.isEmpty()) { "duplicate metro name(s): ${duplicates.joinToString()}" }
    }

    fun all(): List<MetroDefinition> = metros

    fun named(name: String): MetroDefinition? =
        metros.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }

    /**
     * The requested metros; empty means every configured metro. An unknown name
     * is an error rather than an empty selection: a typo must not produce a run
     * that silently fetched nothing.
     */
    fun selected(names: Set<String>): List<MetroDefinition> {
        if (names.isEmpty()) return metros
        val wanted = names.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
        val found = metros.filter { it.name.lowercase() in wanted }
        val missing = wanted - found.map { it.name.lowercase() }.toSet()
        require(missing.isEmpty()) {
            "unknown metro(s): ${missing.sorted().joinToString()}; " +
                "configured: ${metros.joinToString { it.name }}"
        }
        return found
    }

    /** The nearest metro whose circle contains [point]; null means outside them all. */
    fun forPoint(point: LatLng): MetroDefinition? =
        metros.filter { it.contains(point) }
            .minWithOrNull(compareBy({ it.distanceKm(point) }, { it.name }))

    companion object {
        const val RESOURCE = "/metros.json"

        private val json = Json { ignoreUnknownKeys = true }

        fun default(): MetroCatalog =
            MetroCatalog::class.java.getResourceAsStream(RESOURCE)?.use(::load)
                ?: error("missing $RESOURCE on the classpath")

        fun load(path: Path): MetroCatalog = Files.newInputStream(path).use(::load)

        fun load(stream: InputStream): MetroCatalog {
            val file = json.decodeFromString<MetroFile>(stream.readBytes().decodeToString())
            require(file.metros.isNotEmpty()) { "a metro catalogue must define at least one metro" }
            return MetroCatalog(
                file.metros.map {
                    MetroDefinition(
                        name = it.name,
                        center = LatLng(it.center.lat, it.center.lng),
                        radiusKm = it.radiusKm,
                        aliases = it.aliases.toSet(),
                    )
                },
            )
        }
    }
}

/**
 * Assigns a source row to a configured metro; null means "outside the
 * selection". The city alias is tried first (cheap, and city text is usually
 * right); the directory's own coordinates are only a fallback for rows whose
 * city is missing or unrecognised. The candidate factory then confirms the
 * geocoded point against the same circle.
 */
class MetroFilter(private val catalog: MetroCatalog = MetroCatalog.default()) {

    val metros: List<MetroDefinition> get() = catalog.all()

    fun metroFor(listing: SourceListing): MetroDefinition? {
        listing.city?.let { city -> metros.firstOrNull { it.matchesText(city) } }?.let { return it }
        val point = listing.point() ?: return null
        return catalog.forPoint(point)
    }

    fun forPoint(point: LatLng): MetroDefinition? = catalog.forPoint(point)

    fun named(name: String): MetroDefinition? = catalog.named(name)

    fun selected(names: Set<String>): List<MetroDefinition> = catalog.selected(names)

    private fun SourceListing.point(): LatLng? {
        val lat = latitude ?: return null
        val lng = longitude ?: return null
        return runCatching { LatLng(lat, lng) }.getOrNull()
    }
}

@Serializable
private data class MetroFile(val metros: List<MetroEntry> = emptyList())

@Serializable
private data class MetroEntry(
    val name: String,
    val center: CenterEntry,
    val radiusKm: Double,
    val aliases: List<String> = emptyList(),
)

@Serializable
private data class CenterEntry(val lat: Double, val lng: Double)
