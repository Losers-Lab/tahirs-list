package com.tahirslist.ingestion.pipeline

import com.tahirslist.domain.restaurant.LatLng
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import kotlin.io.path.createTempDirectory

class MetroCatalogTest : FunSpec({

    test("the shipped catalogue loads from the classpath and names its metros") {
        val catalog = MetroCatalog.default()
        catalog.all().map { it.name } shouldContain "Dallas"
        catalog.all().map { it.name } shouldContain "NYC"
        catalog.all().map { it.name } shouldContain "Toronto"
        catalog.named("toronto")?.name shouldBe "Toronto"
    }

    test("membership is a radius, not a city list") {
        val toronto = MetroCatalog.default().named("Toronto") ?: error("missing")
        toronto.contains(LatLng(43.6532, -79.3832)) shouldBe true
        toronto.contains(LatLng(43.85, -79.45)) shouldBe true // ~24 km
        toronto.contains(LatLng(45.5019, -73.5674)) shouldBe false // Montreal ~505 km
        toronto.distanceKm(LatLng(45.5019, -73.5674)) shouldBe (505.0 plusOrMinus 20.0)
    }

    test("a new metro is a data change: the same code uses a metro it has never seen") {
        val directory = createTempDirectory("metros")
        val file = directory.resolve("metros.json")
        Files.writeString(
            file,
            """
            {
              "metros": [
                {
                  "name": "Springfield",
                  "center": { "lat": 39.7817, "lng": -89.6501 },
                  "radiusKm": 25.0,
                  "aliases": ["springfield", "sherman"]
                }
              ]
            }
            """.trimIndent(),
        )

        val filter = MetroFilter(MetroCatalog.load(file))
        filter.metroFor(sourceListing(city = "Springfield"))?.name shouldBe "Springfield"
        filter.metroFor(sourceListing(city = "  SHERMAN "))?.name shouldBe "Springfield"
        filter.metroFor(sourceListing(city = "Chicago")) shouldBe null
        filter.named("Springfield")?.contains(LatLng(39.80, -89.64)) shouldBe true
    }

    test("an alias matches whole words, never a fragment of another city") {
        val toronto = MetroCatalog.default().named("Toronto") ?: error("missing")
        toronto.matchesText("Toronto") shouldBe true
        toronto.matchesText("Toronto, Ontario") shouldBe true
        toronto.matchesText("  north   york ") shouldBe true
        toronto.matchesText("Torontonian") shouldBe false
        toronto.matchesText("East York") shouldBe false
    }

    test("a row without a known city falls back to its own coordinates, and only then") {
        val filter = MetroFilter()

        // Alias first: the placeholder coordinates are far outside and ignored.
        filter.metroFor(sourceListing(city = "Toronto"))?.name shouldBe "Toronto"
        // No alias: the coordinates decide.
        filter.metroFor(sourceListing(city = "Nowhere", lat = 43.65, lng = -79.38))?.name shouldBe "Toronto"
        filter.metroFor(sourceListing(city = "Nowhere", lat = 51.5, lng = -0.12)) shouldBe null
        // Neither signal: never guessed.
        filter.metroFor(sourceListing(city = null, lat = 51.5, lng = -0.12)) shouldBe null
    }

    test("overlapping circles resolve to the nearest centre, deterministically") {
        val catalog = MetroCatalog(
            listOf(
                MetroDefinition("A", LatLng(0.0, 0.0), 200.0),
                MetroDefinition("B", LatLng(0.0, 0.5), 200.0),
            ),
        )
        catalog.forPoint(LatLng(0.0, 0.1))?.name shouldBe "A"
        catalog.forPoint(LatLng(0.0, 0.4))?.name shouldBe "B"
        catalog.forPoint(LatLng(10.0, 10.0)) shouldBe null
    }

    test("a catalogue with an unknown metro name is rejected at load time") {
        shouldThrow<IllegalArgumentException> {
            MetroCatalog(listOf(MetroDefinition("A", LatLng(0.0, 0.0), 10.0), MetroDefinition("a", LatLng(1.0, 1.0), 10.0)))
        }
    }
})
