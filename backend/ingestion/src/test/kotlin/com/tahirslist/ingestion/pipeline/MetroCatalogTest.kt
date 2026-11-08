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
        catalog.all().map { it.name } shouldContain "Austin"
        catalog.all().map { it.name } shouldContain "NYC"
        catalog.all().map { it.name } shouldContain "Toronto"
        catalog.named("toronto")?.name shouldBe "Toronto"
    }

    test("Austin covers its metro area and not the next city over") {
        val austin = MetroCatalog.default().named("Austin") ?: error("missing")
        austin.contains(LatLng(30.2672, -97.7431)) shouldBe true // downtown
        austin.contains(LatLng(30.5083, -97.6789)) shouldBe true // Round Rock ~27 km
        austin.contains(LatLng(30.6333, -97.6779)) shouldBe true // Georgetown ~41 km
        austin.contains(LatLng(29.8833, -97.9414)) shouldBe true // San Marcos ~48 km
        austin.contains(LatLng(29.7604, -95.3698)) shouldBe false // Houston ~236 km
        austin.contains(LatLng(29.4241, -98.4936)) shouldBe false // San Antonio ~119 km
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
                  "radiusKm": 25.0
                }
              ]
            }
            """.trimIndent(),
        )

        val filter = MetroFilter(MetroCatalog.load(file))
        filter.forPoint(LatLng(39.80, -89.64))?.name shouldBe "Springfield"
        filter.forPoint(LatLng(41.88, -87.63)) shouldBe null // Chicago, ~300 km
    }

    test("a circle rejects a point just outside its radius and accepts one inside") {
        val metro = MetroDefinition("M", LatLng(0.0, 0.0), 10.0)
        metro.contains(LatLng(0.0, 0.08)) shouldBe true // ~8.9 km
        metro.contains(LatLng(0.0, 0.10)) shouldBe false // ~11.1 km
        metro.contains(LatLng(0.0, 0.0)) shouldBe true
    }

    test("a catalogue with an invalid circle is rejected at load time") {
        shouldThrow<IllegalArgumentException> {
            MetroDefinition("A", LatLng(0.0, 0.0), 0.0)
        }
        shouldThrow<IllegalArgumentException> {
            MetroCatalog(
                listOf(
                    MetroDefinition("A", LatLng(0.0, 0.0), 10.0),
                    MetroDefinition("a", LatLng(1.0, 1.0), 10.0),
                ),
            )
        }
    }
})
