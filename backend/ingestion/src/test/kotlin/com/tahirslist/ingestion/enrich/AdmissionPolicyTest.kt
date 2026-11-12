package com.tahirslist.ingestion.enrich

import com.tahirslist.domain.restaurant.AdmissionDecision
import com.tahirslist.domain.restaurant.EnrichmentSource
import com.tahirslist.domain.restaurant.VenueClass
import com.tahirslist.domain.restaurant.VenueType
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * SC-231 UC-01: the gate that keeps non-restaurants out of the catalogue.
 *
 * The first three cases are the rows that are in the catalogue today: HMS files
 * all three under **Restaurants**, so a rule that trusted the category would keep
 * them. That is the whole reason a shop word outranks a coarse category.
 */
class AdmissionPolicyTest : FunSpec({

    val policy = AdmissionPolicy()

    fun admitted(category: String, name: String, osmKey: String? = null, osmValue: String? = null) =
        policy.decide(category, name, osmKey, osmValue).shouldBeInstanceOf<AdmissionDecision.Admitted>()

    fun excluded(category: String, name: String, osmKey: String? = null, osmValue: String? = null) =
        policy.decide(category, name, osmKey, osmValue).shouldBeInstanceOf<AdmissionDecision.Excluded>()

    // ---- the rows that exist today -----------------------------------------

    test("a poultry meat market filed under Restaurants is excluded") {
        val decision = excluded("Restaurants", "Jamaica Live Poultry Meat Market [Butcher Dept. Only]")

        decision.venueClass shouldBe VenueClass.BUTCHER
        decision.reason shouldContain "poultry"
    }

    test("a meat shop filed under Restaurants is excluded") {
        excluded("Restaurants", "Hamza Meat Shop [Butcher Dept. ONLY]").venueClass shouldBe VenueClass.BUTCHER
    }

    test("a meat-and-grocery shop is classed by its most specific word") {
        // Both 'butcher' and 'grocery' appear; a butcher is the more specific answer.
        excluded("Restaurants", "Alladin Halal Meat & Grocery [Butcher Dept. ONLY]")
            .venueClass shouldBe VenueClass.BUTCHER
    }

    // ---- the rows that must stay -------------------------------------------

    test("a market with a kitchen in it is a restaurant") {
        admitted("Restaurants", "Madina Market (Kitchen)").let {
            it.venueType shouldBe VenueType.RESTAURANT
            it.source shouldBe EnrichmentSource.SOURCE_CATEGORY
        }
    }

    test("a bakery that also grills is a restaurant, not a bakery") {
        admitted("Restaurants", "Mannan Bakery and Grill").venueType shouldBe VenueType.RESTAURANT
    }

    test("a pure bakery is admitted as a bakery") {
        // L-3: the owner ruled a bakery a restaurant.
        admitted("Restaurants", "Firin Bakery").venueType shouldBe VenueType.BAKERY
    }

    test("a coffee shop narrows to a cafe") {
        admitted("Restaurants", "Corner Coffee").venueType shouldBe VenueType.CAFE
    }

    // ---- the category decides when nothing else does ------------------------

    test("a retail store with no decisive name is excluded as other") {
        excluded("Retail Store", "Iqbal Foods").venueClass shouldBe VenueClass.OTHER
    }

    test("a slaughter house, distributor or processor is excluded") {
        excluded("Slaughter House", "City Halal").venueClass shouldBe VenueClass.BUTCHER
        excluded("Distributor", "Halal Meats Ltd").venueClass shouldBe VenueClass.OTHER
        excluded("Processor", "Halal Meats Ltd").venueClass shouldBe VenueClass.OTHER
    }

    test("a caterer is not a restaurant you can visit") {
        excluded("Caterer", "Wedding Feasts").venueClass shouldBe VenueClass.CATERER
    }

    test("an HFSAA meat market type is excluded") {
        excluded("MEAT_MARKET", "Al Noor").venueClass shouldBe VenueClass.BUTCHER
    }

    // ---- OSM, then the name, then undecided --------------------------------

    test("an unknown category uses the OSM feature") {
        policy.decide("Unknown", "Sweet Corner", osmKey = "shop", osmValue = "bakery")
            .shouldBeInstanceOf<AdmissionDecision.Admitted>()
            .let {
                it.venueType shouldBe VenueType.BAKERY
                it.source shouldBe EnrichmentSource.OSM_FEATURE
            }

        excluded("Unknown", "Ali Meats", osmKey = "shop", osmValue = "butcher")
            .venueClass shouldBe VenueClass.BUTCHER
    }

    test("a name with only a premises word still admits, as weak evidence") {
        val decision = policy.decide("Unknown", "Noor Grill")
            .shouldBeInstanceOf<AdmissionDecision.Admitted>()

        decision.venueType shouldBe VenueType.RESTAURANT
        decision.source.isWeak shouldBe true
    }

    test("nothing decides means held, never guessed") {
        val decision = policy.decide("", "Noor")
            .shouldBeInstanceOf<AdmissionDecision.Undecided>()
        decision.reason shouldContain "no restaurant category"
    }

    test("a road or boundary feature is not a venue") {
        policy.decide("Unknown", "Noor", osmKey = "highway", osmValue = "residential")
            .shouldBeInstanceOf<AdmissionDecision.Undecided>()
    }

    test("the decision is deterministic") {
        repeat(5) {
            policy.decide("Restaurants", "Mannan Bakery and Grill") shouldBe
                policy.decide("Restaurants", "Mannan Bakery and Grill")
        }
    }
})
