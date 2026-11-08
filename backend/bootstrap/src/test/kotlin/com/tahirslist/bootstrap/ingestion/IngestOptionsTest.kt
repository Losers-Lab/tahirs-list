package com.tahirslist.bootstrap.ingestion

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class IngestOptionsTest : FunSpec({

    test("reads --key=value, --key value and bare flags") {
        val options = IngestOptions.parse(
            arrayOf("--metros=Toronto,Dallas", "--caps", "20", "--execute", "--run-id=run-1"),
        )

        options.csv("metros") shouldBe setOf("Toronto", "Dallas")
        options["caps"] shouldBe "20"
        options["run-id"] shouldBe "run-1"
        options.flag("execute") shouldBe true
        options.flag("refresh") shouldBe false
    }

    test("a blank csv means the default set") {
        IngestOptions.parse(arrayOf("--metros=")).csv("metros") shouldBe emptySet()
        IngestOptions.parse(emptyArray()).csv("sources") shouldBe emptySet()
    }
})
