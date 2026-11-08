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
        tool().call(args("""{"center":{"lat":1,"lng":2},"radiusMiles":0}""")).isError shouldBe true
        tool().call(args("""{"center":{"lat":1,"lng":2},"radiusMiles":-2}""")).isError shouldBe true
        tool().call(args("""{"center":{"lat":1,"lng":2}}""")).isError shouldBe true
    }

    test("rejectsLimitOutsideOneToOneHundred") {
        tool().call(args("""{"center":{"lat":1,"lng":2},"radiusMiles":5,"limit":0}"""))
            .isError shouldBe true
        tool().call(args("""{"center":{"lat":1,"lng":2},"radiusMiles":5,"limit":101}"""))
            .isError shouldBe true
    }

    test("rejectsNegativeOffset") {
        tool().call(args("""{"center":{"lat":1,"lng":2},"radiusMiles":5,"offset":-1}"""))
            .isError shouldBe true
    }

    test("rejectsMinPriceAboveMaxPrice") {
        tool().call(
            args("""{"center":{"lat":1,"lng":2},"radiusMiles":5,"minPrice":20,"maxPrice":10}"""),
        ).isError shouldBe true
    }

    test("rejectsRatingOutsideZeroToFive") {
        tool().call(args("""{"center":{"lat":1,"lng":2},"radiusMiles":5,"minRating":5.1}"""))
            .isError shouldBe true
        tool().call(args("""{"center":{"lat":1,"lng":2},"radiusMiles":5,"minRating":-1}"""))
            .isError shouldBe true
    }

    test("rejectsBlankOrOverlongCuisine") {
        tool().call(args("""{"center":{"lat":1,"lng":2},"radiusMiles":5,"cuisines":["  "]}"""))
            .isError shouldBe true
        val overlong = "x".repeat(65)
        tool().call(
            args("""{"center":{"lat":1,"lng":2},"radiusMiles":5,"cuisines":["$overlong"]}"""),
        ).isError shouldBe true
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
