package com.tahirslist.mcp

import com.tahirslist.application.agent.RecordingGeocoder
import com.tahirslist.application.agent.SearchListingsForAgent
import com.tahirslist.application.agent.place
import com.tahirslist.application.agent.sampleResult
import com.tahirslist.application.listing.ListingSearchQuery
import com.tahirslist.application.listing.ListingSearchResult
import com.tahirslist.domain.restaurant.LatLng
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication

private class CountingQuery : ListingSearchQuery {
    var calls: Int = 0

    override suspend fun searchNearby(
        center: LatLng,
        radiusMiles: Double,
        filters: com.tahirslist.application.listing.ListingSearchFilters,
        offset: Int,
        limit: Int,
    ): List<ListingSearchResult> {
        calls++
        return listOf(sampleResult())
    }
}

private val SEARCH_CALL = toolCallRequest(
    name = "search_restaurants",
    arguments = """{"center":{"lat":1,"lng":2},"radiusMiles":5}""",
)

class McpRateLimitTest : FunSpec({

    test("rejectsBeyondBudgetBeforeToolExecution") {
        val query = CountingQuery()
        val useCases = McpUseCases(
            search = SearchListingsForAgent(query, RecordingGeocoder(place())),
            detail = testUseCases().detail,
        )

        testApplication {
            application {
                installTestMcp(
                    config = testMcpConfig(capacity = 1, refillTokens = 1),
                    useCases = useCases,
                )
            }

            client.mcpPost(SEARCH_CALL).status shouldBe HttpStatusCode.OK
            client.mcpPost(SEARCH_CALL).status shouldBe HttpStatusCode.TooManyRequests
            client.mcpPost(SEARCH_CALL).status shouldBe HttpStatusCode.TooManyRequests
        }

        query.calls shouldBe 1
    }

    test("budgetIsPerClient") {
        val config = testMcpConfig(capacity = 1, refillTokens = 1, trustProxy = true)
        testApplication {
            application { installTestMcp(config = config) }

            client.mcpPost(INITIALIZE_REQUEST, forwardedFor = "203.0.113.1").status shouldBe HttpStatusCode.OK
            client.mcpPost(INITIALIZE_REQUEST, forwardedFor = "203.0.113.1").status shouldBe
                HttpStatusCode.TooManyRequests
            client.mcpPost(INITIALIZE_REQUEST, forwardedFor = "203.0.113.2").status shouldBe HttpStatusCode.OK
        }
    }

    test("trustedProxyOffIgnoresForwardedFor") {
        val config = testMcpConfig(capacity = 1, refillTokens = 1, trustProxy = false)
        testApplication {
            application { installTestMcp(config = config) }

            client.mcpPost(INITIALIZE_REQUEST, forwardedFor = "203.0.113.1").status shouldBe HttpStatusCode.OK
            client.mcpPost(INITIALIZE_REQUEST, forwardedFor = "203.0.113.2").status shouldBe
                HttpStatusCode.TooManyRequests
        }
    }
})
