package com.tahirslist.certifiers.core

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class HandCutDeclarationTest : FunSpec({

    test("only a declared hand-cut is true") {
        HandCutDeclaration.HAND_CUT.isHandCut shouldBe true
    }

    test("a declared not-hand-cut is false") {
        HandCutDeclaration.NOT_HAND_CUT.isHandCut shouldBe false
    }

    test("silence is false, not unknown") {
        HandCutDeclaration.UNSPECIFIED.isHandCut shouldBe false
    }
})
