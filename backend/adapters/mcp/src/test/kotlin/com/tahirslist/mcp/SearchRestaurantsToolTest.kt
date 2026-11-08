package com.tahirslist.mcp

import com.tahirslist.application.agent.RecordingGeocoder
import com.tahirslist.application.agent.RecordingSearchQuery
import com.tahirslist.application.agent.SearchListingsForAgent
import com.tahirslist.application.agent.place
import com.tahirslist.application.agent.sampleResult
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private fun tool(
    query: RecordingSearchQuery = RecordingSearchQuery(),
    config: McpConfig = McpConfig(),
) = SearchRestaurantsTool(
    SearchListingsForAgent(query, RecordingGeocoder(place())),
    config,
)

private fun args(json: String): JsonObject = Json.parseToJsonElement(json).jsonObject

private fun CallToolResult.message(): String = (content.first() as TextContent).text

private fun CallToolResult.structured(): JsonObject = requireNotNull(structuredContent)

class SearchRestaurantsToolTest : FunSpec({

    test("rejectsWhenNeitherPlaceNorCenter") {
        val result = tool().call(args("""{"radiusMiles": 5}"""))

        result.isError shouldBe true
        result.message() shouldBe "Provide exactly one of place or center."
    }

    test("rejectsWhenBothPlaceAndCenter") {
        val result = tool().call(
            args("""{"place":"Toronto","center":{"lat":1,"lng":2},"radiusMiles":5}"""),
        )

        result.isError shouldBe true
        result.message() shouldBe "Provide exactly one of place or center."
    }

    test("rejectsNonPositiveRadius") {
        val zero = tool().call(args("""{"center":{"lat":1,"lng":2},"radiusMiles":0}"""))
        zero.isError shouldBe true
        zero.message() shouldBe "radiusMiles must be positive"

        tool().call(args("""{"center":{"lat":1,"lng":2},"radiusMiles":-2}""")).isError shouldBe true
        tool().call(args("""{"center":{"lat":1,"lng":2}}""")).isError shouldBe true
    }

    test("rejectsNonFiniteRadius") {
        val result = tool().call(args("""{"center":{"lat":1,"lng":2},"radiusMiles":1e400}"""))

        result.isError shouldBe true
        result.message() shouldBe "radiusMiles must be a finite number"
    }

    test("rejectsLimitOutsideOneToOneHundred") {
        val zero = tool().call(args("""{"center":{"lat":1,"lng":2},"radiusMiles":5,"limit":0}"""))
        zero.isError shouldBe true
        zero.message() shouldBe "limit must be between 1 and 100"

        tool().call(args("""{"center":{"lat":1,"lng":2},"radiusMiles":5,"limit":101}"""))
            .isError shouldBe true
    }

    test("rejectsNegativeOffset") {
        val result = tool().call(args("""{"center":{"lat":1,"lng":2},"radiusMiles":5,"offset":-1}"""))

        result.isError shouldBe true
        result.message() shouldBe "offset must not be negative"
    }

    test("rejectsHugeOffset") {
        val result = tool().call(args("""{"center":{"lat":1,"lng":2},"radiusMiles":5,"offset":10001}"""))

        result.isError shouldBe true
        result.message() shouldBe "offset must be 10000 or fewer"
    }

    test("rejectsTooManyCuisines") {
        val many = (1..21).joinToString(",") { "\"c$it\"" }
        val result = tool().call(
            args("""{"center":{"lat":1,"lng":2},"radiusMiles":5,"cuisines":[$many]}"""),
        )

        result.isError shouldBe true
        result.message() shouldBe "cuisines must contain at most 20 values"
    }

    test("rejectsMinPriceAboveMaxPrice") {
        val result = tool().call(
            args("""{"center":{"lat":1,"lng":2},"radiusMiles":5,"minPrice":20,"maxPrice":10}"""),
        )

        result.isError shouldBe true
        result.message() shouldBe "minPrice must not exceed maxPrice"
    }

    test("rejectsRatingOutsideZeroToFive") {
        val high = tool().call(args("""{"center":{"lat":1,"lng":2},"radiusMiles":5,"minRating":5.1}"""))
        high.isError shouldBe true
        high.message() shouldBe "minRating must be 5.0 or fewer"

        val low = tool().call(args("""{"center":{"lat":1,"lng":2},"radiusMiles":5,"minRating":-1}"""))
        low.isError shouldBe true
        low.message() shouldBe "minRating must not be negative"
    }

    test("rejectsBlankOrOverlongCuisine") {
        val blank = tool().call(args("""{"center":{"lat":1,"lng":2},"radiusMiles":5,"cuisines":["  "]}"""))
        blank.isError shouldBe true
        blank.message() shouldBe "cuisine must not be blank"

        val overlong = "x".repeat(65)
        val long = tool().call(
            args("""{"center":{"lat":1,"lng":2},"radiusMiles":5,"cuisines":["$overlong"]}"""),
        )
        long.isError shouldBe true
        long.message() shouldBe "cuisine must be 64 characters or fewer"
    }

    test("emptyResultIsSuccess") {
        val result = tool(RecordingSearchQuery(emptyList())).call(
            args("""{"center":{"lat":1,"lng":2},"radiusMiles":5}"""),
        )

        result.isError shouldBe false
        result.structured()["count"]!!.jsonPrimitive.int shouldBe 0
        result.structured()["results"]!!.jsonArray shouldHaveSize 0
        result.structured()["nextOffset"] shouldBe JsonNull
    }

    test("reportsNextOffsetWhenPageIsFull") {
        val query = RecordingSearchQuery(List(3) { sampleResult(name = "R$it") })
        val result = tool(query).call(
            args("""{"center":{"lat":1,"lng":2},"radiusMiles":5,"limit":3}"""),
        )

        result.structured()["nextOffset"]!!.jsonPrimitive.int shouldBe 3
    }

    test("preservesTriStateHandCutAndDelivery") {
        val query = RecordingSearchQuery(listOf(sampleResult().copy(isHandCut = null, isDelivery = null)))
        val result = tool(query).call(args("""{"center":{"lat":1,"lng":2},"radiusMiles":5}"""))

        val card = result.structured()["results"]!!.jsonArray.first().jsonObject
        card["isHandCut"] shouldBe JsonNull
        card["isDelivery"] shouldBe JsonNull
    }

    test("preservesNullRating") {
        val result = tool().call(args("""{"center":{"lat":1,"lng":2},"radiusMiles":5}"""))

        val card = result.structured()["results"]!!.jsonArray.first().jsonObject
        card["rating"] shouldBe JsonNull
    }

    test("buildsWebsiteUrlFromConfig") {
        val config = McpConfig(publicWebBaseUrl = "https://tahirslist.com/")
        val result = tool(config = config).call(
            args("""{"center":{"lat":1,"lng":2},"radiusMiles":5}"""),
        )

        val card = result.structured()["results"]!!.jsonArray.first().jsonObject
        card["websiteUrl"]!!.jsonPrimitive.content shouldBe
            "https://tahirslist.com/restaurants/11111111-2222-3333-4444-555555555555"
    }

    test("contentCarriesTheStructuredJson") {
        val result = tool().call(args("""{"center":{"lat":1,"lng":2},"radiusMiles":5}"""))

        result.content.first().shouldBeInstanceOf<TextContent>()
        result.message() shouldBe result.structured().toString()
    }
})
