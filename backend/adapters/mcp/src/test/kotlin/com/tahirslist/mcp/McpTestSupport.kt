package com.tahirslist.mcp

import com.tahirslist.application.agent.FakeListingRepository
import com.tahirslist.application.agent.FakeReviewRepository
import com.tahirslist.application.agent.GetListingForAgent
import com.tahirslist.application.agent.RecordingGeocoder
import com.tahirslist.application.agent.RecordingSearchQuery
import com.tahirslist.application.agent.SearchListingsForAgent
import com.tahirslist.application.agent.approvedReview
import com.tahirslist.application.agent.place
import com.tahirslist.application.agent.sampleListing
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.options
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Shared plumbing for the MCP HTTP tests (stateless endpoint, JSON responses). */

internal fun Application.installTestMcp(
    config: McpConfig = testMcpConfig(),
    useCases: McpUseCases = testUseCases(),
    limiter: McpRateLimiter = config.rateLimit.buildLimiter(),
    originGuard: McpOriginGuard = McpOriginGuard(config.allowedOrigins),
) {
    // Mirrors the production composition root: one ContentNegotiation, with the
    // MCP-aware converter selecting McpJson for JSON-RPC payloads and the REST
    // Json (explicit nulls) for everything else.
    install(ContentNegotiation) {
        register(
            ContentType.Application.Json,
            McpAwareJsonConverter(Json { encodeDefaults = true }),
        )
    }
    installMcp(config = config, useCases = useCases, limiter = limiter, originGuard = originGuard)
}

internal fun testMcpConfig(
    capacity: Long = 10_000,
    refillTokens: Long = 10_000,
    trustProxy: Boolean = false,
    allowedOrigins: Set<String> = emptySet(),
) = McpConfig(
    publicWebBaseUrl = "http://localhost:5173",
    publicApiBaseUrl = "http://localhost:8080",
    allowedOrigins = allowedOrigins,
    enableDnsRebindingProtection = false,
    rateLimit = McpRateLimitSettings(
        capacity = capacity,
        refillTokens = refillTokens,
        refillWindowSeconds = 60,
        maxKeys = 10_000,
        trustProxy = trustProxy,
    ),
)

internal fun testUseCases(query: RecordingSearchQuery = RecordingSearchQuery()) = McpUseCases(
    search = SearchListingsForAgent(query, RecordingGeocoder(place())),
    detail = GetListingForAgent(
        FakeListingRepository(sampleListing(verified = true)),
        FakeReviewRepository(approvedReview()),
    ),
)

internal const val INITIALIZE_REQUEST: String =
    """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"test","version":"1.0"}}}"""

internal fun toolsListRequest(id: Int = 2): String =
    """{"jsonrpc":"2.0","id":$id,"method":"tools/list","params":{}}"""

internal fun toolCallRequest(id: Int = 3, name: String, arguments: String): String =
    """{"jsonrpc":"2.0","id":$id,"method":"tools/call","params":{"name":"$name","arguments":$arguments}}"""

internal suspend fun HttpClient.mcpPost(
    body: String,
    origin: String? = null,
    forwardedFor: String? = null,
): HttpResponse = post(MCP_PATH) {
    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    header(HttpHeaders.Accept, "application/json, text/event-stream")
    if (origin != null) header(HttpHeaders.Origin, origin)
    if (forwardedFor != null) header(HttpHeaders.XForwardedFor, forwardedFor)
    setBody(body)
}

internal suspend fun HttpClient.mcpOptions(origin: String?): HttpResponse = options(MCP_PATH) {
    if (origin != null) header(HttpHeaders.Origin, origin)
    header(HttpHeaders.AccessControlRequestMethod, "POST")
    header(HttpHeaders.AccessControlRequestHeaders, "content-type, accept")
}

internal suspend fun HttpResponse.jsonBody(): JsonObject =
    Json.parseToJsonElement(bodyAsText()).jsonObject

internal fun JsonObject.result(): JsonObject = this["result"]!!.jsonObject

internal fun JsonObject.toolNames(): List<String> =
    result()["tools"]!!.jsonArray.map { it.jsonObject["name"]!!.jsonPrimitive.content }

internal fun JsonObject.structuredContent(): JsonObject =
    result()["structuredContent"]!!.jsonObject
