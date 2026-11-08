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
import io.kotest.matchers.string.shouldContain
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
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

private const val SEARCH_MESSAGE_1 =
    """{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"search_restaurants","arguments":{"center":{"lat":1,"lng":2},"radiusMiles":5}}}"""

private const val SEARCH_MESSAGE_2 =
    """{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"search_restaurants","arguments":{"center":{"lat":1,"lng":2},"radiusMiles":5}}}"""

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
            val limited = client.mcpPost(SEARCH_CALL)
            limited.status shouldBe HttpStatusCode.TooManyRequests
            limited.headers[HttpHeaders.RetryAfter] shouldBe "60"
            client.mcpPost(SEARCH_CALL).status shouldBe HttpStatusCode.TooManyRequests
        }

        query.calls shouldBe 1
    }

    test("batchCannotAmplifyBeyondBudget") {
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

            val response = client.mcpPost("[$SEARCH_MESSAGE_1,$SEARCH_MESSAGE_2]")

            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldContain "Rate limit exceeded. Try again shortly."
        }

        // One POST token covers the first call; the second call in the batch is refused.
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
