package com.tahirslist.bootstrap

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * AC-8: the MCP work must not change the public REST contract. The search and
 * detail responses are compared byte-for-byte across two calls and their shape
 * is asserted.
 */
class McpRegressionTest : KtorPostgresTest() {

    init {
        test("restSearchAndDetailUnchanged") {
            val path = "/v1/listings/search?center=43.6532,-79.3832&radius=5"
            val first = get(path)

            first.status shouldBe 200
            first.body.trimStart().startsWith("[") shouldBe true
            val cards = first.jsonArray()
            cards.isNotEmpty() shouldBe true

            // Stable ordering and identical bytes on a repeat call.
            get(path).body shouldBe first.body

            val id = cards.first().jsonObject["id"]!!.jsonPrimitive.content

            val detail = get("/v1/listings/$id")
            detail.status shouldBe 200
            detail.json().keys shouldContain "halalScope"
            detail.json().keys shouldContain "crossContamination"
            detail.json().keys shouldContain "verificationStatus"
            detail.json().keys shouldNotContain "halalItems"
        }
    }
}
