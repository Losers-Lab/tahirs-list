package com.tahirslist.domain.restaurant

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/**
 * SC-231: the gate has exactly three answers, and two of them must explain
 * themselves. A row recognised as a restaurant cannot be excluded as a
 * non-restaurant — that would mean the policy contradicted itself.
 */
class AdmissionDecisionTest : FunSpec({

    test("an admitted row names its type and the evidence that decided it") {
        val decision = AdmissionDecision.Admitted(VenueType.BAKERY, EnrichmentSource.NAME_KEYWORD)

        decision.venueType shouldBe VenueType.BAKERY
        decision.source.isWeak shouldBe true
    }

    test("an exclusion refuses a premises class") {
        // BAKERY is premises (L-3); excluding it would be the policy arguing with itself.
        shouldThrow<IllegalArgumentException> {
            AdmissionDecision.Excluded(VenueClass.BAKERY, "name: bakery")
        }
        val thrown = shouldThrow<IllegalArgumentException> {
            AdmissionDecision.Excluded(VenueClass.RESTAURANT, "category: Restaurants")
        }
        thrown.message.orEmpty() shouldContain "premises"
    }

    test("an exclusion of a shop is allowed and must say why") {
        val decision = AdmissionDecision.Excluded(VenueClass.BUTCHER, "name: poultry/meat market")

        decision.venueClass.isShop shouldBe true
        decision.reason shouldContain "poultry"
        shouldThrow<IllegalArgumentException> {
            AdmissionDecision.Excluded(VenueClass.BUTCHER, "  ")
        }
    }

    test("an undecided row must say what was missing") {
        AdmissionDecision.Undecided("no category, no OSM tag, no marker").reason shouldBe
            "no category, no OSM tag, no marker"
        shouldThrow<IllegalArgumentException> { AdmissionDecision.Undecided("") }
    }
})
