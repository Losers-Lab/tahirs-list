package com.tahirslist.mcp

import com.tahirslist.application.agent.FakeListingRepository
import com.tahirslist.application.agent.FakeReviewRepository
import com.tahirslist.application.agent.GetListingForAgent
import com.tahirslist.application.agent.OTHER_ID
import com.tahirslist.application.agent.SAMPLE_ID
import com.tahirslist.application.agent.approvedReview
import com.tahirslist.application.agent.sampleListing
import com.tahirslist.domain.restaurant.HalalItem
import com.tahirslist.domain.restaurant.HalalScope
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private fun tool(
    listing: com.tahirslist.domain.restaurant.RestaurantListing? = sampleListing(verified = true),
    review: com.tahirslist.domain.verification.HalalCertificationReview? = approvedReview(),
    config: McpConfig = McpConfig(),
) = GetRestaurantTool(
    GetListingForAgent(FakeListingRepository(listing), FakeReviewRepository(review)),
    config,
)

private fun args(json: String): JsonObject = Json.parseToJsonElement(json).jsonObject

private fun CallToolResult.message(): String = (content.first() as TextContent).text

private fun CallToolResult.structured(): JsonObject = requireNotNull(structuredContent)

class GetRestaurantToolTest : FunSpec({

    test("unknownIdReturnsNotFoundToolError") {
        val result = tool().call(args("""{"id":"$OTHER_ID"}"""))

        result.isError shouldBe true
        result.message() shouldBe "Restaurant not found. Use an id returned by search_restaurants."
    }

    test("malformedIdReturnsToolError") {
        val result = tool().call(args("""{"id":"not-a-uuid"}"""))

        result.isError shouldBe true
        result.message() shouldBe "id must be a valid listing id"
    }

    test("verifiedWithApprovedCertificateIncludesFacts") {
        val result = tool().call(args("""{"id":"$SAMPLE_ID"}"""))

        result.isError shouldBe false
        val certificate = result.structured()["certificate"]!!.jsonObject
        certificate["certifier"]!!.jsonPrimitive.content shouldBe "Halal Certification Board"
        certificate["reviewedOn"]!!.jsonPrimitive.content shouldBe
            com.tahirslist.application.agent.now().toString()
        certificate["expiresOn"]!!.jsonPrimitive.content shouldBe "2027-01-01"
        certificate["certificateUrl"]!!.jsonPrimitive.content shouldBe
            "http://localhost:8080/v1/listings/$SAMPLE_ID/certificate"
    }

    test("unverifiedOrUnapprovedOmitsCertificate") {
        tool(listing = sampleListing(verified = false), review = approvedReview())
            .call(args("""{"id":"$SAMPLE_ID"}"""))
            .structured()["certificate"] shouldBe JsonNull

        tool(listing = sampleListing(verified = true), review = null)
            .call(args("""{"id":"$SAMPLE_ID"}"""))
            .structured()["certificate"] shouldBe JsonNull
    }

    test("partialHalalReportsScopeAndItems") {
        val listing = sampleListing(
            verified = true,
            halalScope = HalalScope.PARTIALLY_HALAL,
            halalItems = setOf(
                HalalItem("chicken", true),
                HalalItem("bacon", false),
            ),
        )
        val result = tool(listing = listing).call(args("""{"id":"$SAMPLE_ID"}"""))

        val payload = result.structured()
        payload["halalScope"]!!.jsonPrimitive.content shouldBe "PARTIALLY_HALAL"
        val items = payload["halalItems"]!!.jsonArray
        items shouldHaveSize 2
        items[0].jsonObject["name"]!!.jsonPrimitive.content shouldBe "bacon"
        items[0].jsonObject["isHalal"]!!.jsonPrimitive.content shouldBe "false"
        items[1].jsonObject["name"]!!.jsonPrimitive.content shouldBe "chicken"
        items[1].jsonObject["isHalal"]!!.jsonPrimitive.content shouldBe "true"
    }

    test("certificateUrlAndImageUrlUseConfiguredApiBase") {
        val config = McpConfig(publicApiBaseUrl = "https://api.tahirslist.com/")
        val result = tool(config = config).call(args("""{"id":"$SAMPLE_ID"}"""))

        val payload = result.structured()
        payload["imageUrl"]!!.jsonPrimitive.content shouldBe
            "https://api.tahirslist.com/v1/listings/$SAMPLE_ID/image?variant=full"
        payload["certificate"]!!.jsonObject["certificateUrl"]!!.jsonPrimitive.content shouldBe
            "https://api.tahirslist.com/v1/listings/$SAMPLE_ID/certificate"
    }
})
