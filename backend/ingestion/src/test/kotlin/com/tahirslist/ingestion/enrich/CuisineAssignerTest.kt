package com.tahirslist.ingestion.enrich

import com.tahirslist.domain.restaurant.EnrichmentSource
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/**
 * SC-231 UC-02: cuisine only with evidence, and a conflict is never resolved by
 * guessing. The brand and keyword tables are small and curated on purpose — this
 * spec pins the order and the weak-evidence label.
 */
class CuisineAssignerTest : FunSpec({

    val assigner = CuisineAssigner()

    test("a source-published cuisine wins over everything") {
        val decision = assigner.assign("Biryani House", osmCuisine = "indian", publishedCuisine = "Pakistani")

        decision.cuisine shouldBe "pakistani"
        decision.source shouldBe EnrichmentSource.SOURCE_CATEGORY
    }

    test("a curated brand beats the OSM tag and the name") {
        val decision = assigner.assign("Paramount Fine Foods", osmCuisine = "mediterranean")

        decision.cuisine shouldBe "lebanese"
        decision.source shouldBe EnrichmentSource.BRAND_MAP
    }

    test("the OSM cuisine tag is used when the brand had nothing") {
        val decision = assigner.assign("Noor", osmCuisine = "Afghan")

        decision.cuisine shouldBe "afghan"
        decision.source shouldBe EnrichmentSource.OSM_FEATURE
    }

    test("a distinctive name word is weak evidence and says so") {
        val decision = assigner.assign("Biryani House")

        decision.cuisine shouldBe "pakistani"
        decision.source?.isWeak shouldBe true
    }

    test("two families in one name is a conflict, not a coin flip") {
        val decision = assigner.assign("Pizza & Biryani")

        decision.cuisine shouldBe null
        decision.conflict.orEmpty() shouldContain "pakistani"
        decision.conflict.orEmpty() shouldContain "pizza"
    }

    test("nothing found leaves it null") {
        val decision = assigner.assign("Noor")

        decision.cuisine shouldBe null
        decision.source shouldBe null
        decision.conflict shouldBe null
    }

    test("a blank OSM cuisine is treated as absent") {
        assigner.assign("Noor", osmCuisine = "   ").cuisine shouldBe null
    }

    test("a cuisine cannot be decided without its evidence") {
        shouldThrow<IllegalArgumentException> {
            CuisineAssigner.Decision(cuisine = "afghan")
        }
    }

    test("a decided cuisine cannot also be a conflict") {
        shouldThrow<IllegalArgumentException> {
            CuisineAssigner.Decision("afghan", EnrichmentSource.NAME_KEYWORD, conflict = "x")
        }
    }
})

/**
 * The curated tables are data, and bad data must stop the run at startup rather
 * than half-classify a batch.
 */
class EnrichmentRulesTest : FunSpec({

    test("a word cannot be both a shop and a premises marker") {
        val thrown = shouldThrow<IllegalArgumentException> {
            EnrichmentRules(
                shopMarkers = setOf("bakery"),
                premisesMarkers = setOf("bakery"),
            )
        }
        thrown.message.orEmpty() shouldContain "both a shop and a premises marker"
    }

    test("a blank marker is refused") {
        shouldThrow<IllegalArgumentException> {
            EnrichmentRules(shopMarkers = setOf("butcher", "  "))
        }
    }

    test("a shop class cannot be attached to a premises word") {
        shouldThrow<IllegalArgumentException> {
            EnrichmentRules(
                shopMarkers = setOf("butcher"),
                shopClassByMarker = mapOf("butcher" to com.tahirslist.domain.restaurant.VenueClass.BAKERY),
            )
        }
    }

    test("multi-word markers match the whole phrase, not a fragment") {
        val rules = EnrichmentRules()

        rules.shopMarkersIn("Hamza Meat Shop") shouldBe listOf("meat shop")
        rules.shopMarkersIn("Hamza Meatballs") shouldBe emptyList()
    }
})
