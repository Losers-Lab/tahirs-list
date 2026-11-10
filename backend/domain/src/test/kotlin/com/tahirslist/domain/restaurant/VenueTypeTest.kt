package com.tahirslist.domain.restaurant

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

/**
 * SC-231: two vocabularies with different jobs — [VenueClass] recognises what the
 * evidence says (including the things we refuse), [VenueType] is what a stored
 * restaurant may be. These tests pin the vocabulary so a rename cannot silently
 * widen what the catalogue accepts.
 */
class VenueTypeTest : FunSpec({

    test("the published vocabulary is exactly four restaurant kinds") {
        VenueType.entries.map { it.name } shouldContainExactly
            listOf("RESTAURANT", "CAFE", "FAST_FOOD", "BAKERY")
    }

    test("the recognised vocabulary keeps the non-restaurant classes") {
        VenueClass.entries.map { it.name } shouldContainExactly listOf(
            "RESTAURANT", "CAFE", "FAST_FOOD", "BAKERY",
            "GROCERY", "MARKET", "BUTCHER", "CATERER", "OTHER",
        )
    }

    test("a bakery is premises, not a shop") {
        // L-3: the owner ruled that a bakery is a restaurant. If this flips, the
        // three bakery rows leave the catalogue again.
        VenueClass.BAKERY.isPremises shouldBe true
        VenueClass.BAKERY.isShop shouldBe false
    }

    test("a shop is never premises and premises is never a shop") {
        VenueClass.entries.filter { it.isShop && it.isPremises }.shouldBeEmpty()
    }

    test("the shops are the three we refuse") {
        VenueClass.entries.filter { it.isShop } shouldContainExactly
            listOf(VenueClass.GROCERY, VenueClass.MARKET, VenueClass.BUTCHER)
    }

    test("every published type is a premises class of the same name") {
        // The report maps an admitted type back to its class; a drift here would
        // make a stored listing unrepresentable in the run report.
        VenueType.entries.forEach { type ->
            VenueClass.valueOf(type.name).isPremises shouldBe true
        }
    }
})
