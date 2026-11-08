package com.tahirslist.mcp

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.ApplicationSendPipeline
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.modelcontextprotocol.kotlin.sdk.server.DnsRebindingProtection
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.StreamableHttpServerTransport
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean

private val log = KotlinLogging.logger("com.tahirslist.mcp.McpServer")

/** The one path the public MCP endpoint is mounted at. */
const val MCP_PATH: String = "/mcp"

private const val FIXED_PARSE_ERROR: String =
    """{"jsonrpc":"2.0","id":null,"error":{"code":-32700,"message":"Parse error"}}"""

private const val MAX_LOGGED_HEADER_LENGTH = 128

/**
 * Anonymous surface. The largest legitimate body is well under 2 KiB (a place
 * string of 256 chars plus 20 cuisines of 64); 16 KiB bounds a batch of cheap
 * methods to ~1.6 MB of response instead of the ~430 MB the SDK default allowed.
 */
private const val MAX_REQUEST_BODY_BYTES: Long = 16L * 1024

/** Salted so the logged budget key cannot be reversed to an IP by guessing. */
private val clientKeySalt: Int = java.security.SecureRandom().nextInt()

/**
 * Per-request call budget. One HTTP request costs one token at the gate; the
 * first tool call is covered by it, and every additional call in a JSON-RPC
 * batch must consume another token. That closes the batch amplification hole
 * without charging normal single-call requests twice.
 */
fun interface McpCallGate {
    fun allowCall(): Boolean
}

private class McpRequestBudget(
    private val limiter: McpRateLimiter,
    private val clientKey: String,
) : McpCallGate {
    private val firstCallCovered = AtomicBoolean(false)

    override fun allowCall(): Boolean =
        if (firstCallCovered.compareAndSet(false, true)) true
        else limiter.tryAcquire(clientKey)
}

/**
 * Mounts the stateless Streamable HTTP MCP endpoint.
 *
 * Stateless means: no session id, one server session per POST, POST only.
 * Refusals happen before any tool work — a disallowed browser `Origin` (403) and
 * an exhausted per-client budget (429) are answered by the pipeline intercept;
 * the SDK never sees them. CORS preflight is answered for exactly the configured
 * origins, and a JSON-RPC batch cannot amplify work beyond the same budget.
 *
 * Serialization is application-wide; [McpAwareJsonConverter] sends MCP payloads
 * through the SDK's `McpJson` and everything else through the REST `Json`.
 */
fun Application.installMcp(
    config: McpConfig,
    useCases: McpUseCases,
    limiter: McpRateLimiter = config.rateLimit.buildLimiter(),
    originGuard: McpOriginGuard = McpOriginGuard(config.allowedOrigins),
    path: String = MCP_PATH,
) {
    intercept(ApplicationCallPipeline.Plugins) {
        if (call.request.path() != path) return@intercept

        val origin = call.request.headers[HttpHeaders.Origin]
        val originAllowed = origin != null && originGuard.allows(origin)

        when (call.request.httpMethod) {
            HttpMethod.Options -> {
                if (!originAllowed) {
                    call.respond(HttpStatusCode.Forbidden)
                } else {
                    call.response.header("Access-Control-Allow-Origin", origin)
                    call.response.header("Access-Control-Allow-Methods", "POST, OPTIONS")
                    call.response.header(
                        "Access-Control-Allow-Headers",
                        "Content-Type, Accept, Mcp-Protocol-Version, Mcp-Session-Id, Last-Event-ID",
                    )
                    call.response.header("Access-Control-Max-Age", "600")
                    call.response.header(HttpHeaders.Vary, "Origin")
                    call.respond(HttpStatusCode.NoContent)
                }
                finish()
            }

            HttpMethod.Post -> {
                if (!originGuard.allows(origin)) {
                    log.debug { "mcp refused origin=${sanitize(origin)} status=403" }
                    call.respond(HttpStatusCode.Forbidden)
                    finish()
                    return@intercept
                }
                if (originAllowed) {
                    call.response.header("Access-Control-Allow-Origin", origin)
                    call.response.header(HttpHeaders.Vary, "Origin")
                }
                val key = call.mcpClientKey(config)
                if (!limiter.tryAcquire(key)) {
                    log.debug { "mcp refused clientKey=${clientKeyHash(key)} status=429" }
                    call.response.header(HttpHeaders.RetryAfter, config.rateLimit.refillWindowSeconds)
                    call.respond(HttpStatusCode.TooManyRequests)
                    finish()
                    return@intercept
                }
            }

            else -> Unit
        }
    }

    // The SDK answers malformed bodies with a JSON-RPC error whose message is
    // "Parse error: <parser exception text>". Never echo parser/decoder detail
    // from an anonymous endpoint. Match the anchored envelope AND the 400 status
    // so a listing string that happens to contain the code cannot rewrite a
    // successful result. The status lives on the outgoing content here: the
    // engine only commits `call.response.status()` in the Render phase.
    sendPipeline.intercept(ApplicationSendPipeline.Transform) {
        val outgoing = subject
        if (call.request.path() == path &&
            outgoing is TextContent &&
            outgoing.status == HttpStatusCode.BadRequest &&
            outgoing.contentType.match(ContentType.Application.Json) &&
            outgoing.text.isParseErrorEnvelope()
        ) {
            proceedWith(TextContent(FIXED_PARSE_ERROR, ContentType.Application.Json, HttpStatusCode.BadRequest))
        } else {
            proceed()
        }
    }

    routing {
        route(path) {
            if (config.enableDnsRebindingProtection) {
                install(DnsRebindingProtection) {
                    allowedHosts = allowedHosts(config)
                    allowedOrigins = null
                }
            }
            post {
                val transport = StreamableHttpServerTransport(
                    StreamableHttpServerTransport.Configuration(
                        enableJsonResponse = true,
                        // Anonymous endpoint: cap the body so a JSON-RPC batch of
                        // cheap methods cannot materialise hundreds of MB.
                        maxRequestBodySize = MAX_REQUEST_BODY_BYTES,
                    ),
                ).also { it.setSessionIdGenerator(null) }
                val gate = McpRequestBudget(limiter, call.mcpClientKey(config))
                val server: Server = TahirsListMcpServer(useCases, config, gate).createServer()
                val session = server.createSession(transport)
                try {
                    transport.handleRequest(null, call)
                } finally {
                    session.close()
                }
            }
            get {
                call.response.header(HttpHeaders.Allow, "POST, OPTIONS")
                call.respond(HttpStatusCode.MethodNotAllowed)
            }
            delete {
                call.response.header(HttpHeaders.Allow, "POST, OPTIONS")
                call.respond(HttpStatusCode.MethodNotAllowed)
            }
        }
    }
}

private fun io.ktor.server.application.ApplicationCall.mcpClientKey(config: McpConfig): String =
    mcpClientKey(
        remoteHost = request.local.remoteHost,
        forwardedFor = request.headers[HttpHeaders.XForwardedFor],
        cfConnectingIp = request.headers["CF-Connecting-IP"],
        trustProxy = config.rateLimit.trustProxy,
    )

/**
 * Per-client budget key. `X-Forwarded-For` is client-controlled and used only
 * when the deployment explicitly trusts its proxy; otherwise the socket peer
 * keys the bucket. Only the first XFF hop is considered, and a value that is not
 * a plausible IP is discarded (it is only a bucket key, never resolved).
 */
internal fun mcpClientKey(
    remoteHost: String,
    forwardedFor: String?,
    cfConnectingIp: String? = null,
    trustProxy: Boolean,
): String {
    val forwarded = forwardedFor?.substringBefore(",")?.trim()
    val trustedIp = when {
        !trustProxy -> null
        cfConnectingIp?.trim()?.isPlausibleIp() == true -> cfConnectingIp.trim()
        forwarded?.isPlausibleIp() == true -> forwarded
        else -> null
    }
    val ip = trustedIp ?: remoteHost.takeIf { it.isNotBlank() } ?: "unknown"
    return "mcp|$ip"
}

private fun String.isPlausibleIp(): Boolean =
    length in 1..64 && all { it.isLetterOrDigit() || it == '.' || it == ':' || it == '-' || it == '_' }

private fun sanitize(header: String?): String =
    header.orEmpty().filter { it.code in 0x20..0x7e }.take(MAX_LOGGED_HEADER_LENGTH)

/**
 * The SDK's parse-error envelopes are either
 * `{"error":{"code":-32700,...},"jsonrpc":"2.0"}` or a `jsonrpc`-first variant.
 * Anchoring to the start avoids matching a listing string that merely contains
 * the code.
 */
private fun String.isParseErrorEnvelope(): Boolean {
    val text = trimStart()
    return text.startsWith("{\"error\":{\"code\":-32700") ||
        text.startsWith("{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32700")
}

private fun clientKeyHash(key: String): String = (key + clientKeySalt).hashCode().toUInt().toString(16)

/**
 * Hosts accepted by the DNS-rebinding guard: loopback, explicit
 * `APP_MCP_ALLOWED_HOSTS`, and the hosts of the configured public base URLs.
 * Without the configured hosts a production deployment would be refused by the
 * SDK's localhost-only default.
 */
internal fun allowedHosts(config: McpConfig): List<String> {
    val hosts = linkedSetOf("localhost", "127.0.0.1", "[::1]")
    hosts += config.allowedHosts.map { it.trim() }.filter { it.isNotEmpty() }
    listOf(config.publicApiBaseUrl, config.publicWebBaseUrl).forEach { url ->
        runCatching { URI(url).host }.getOrNull()?.takeIf { it.isNotBlank() }?.let(hosts::add)
    }
    return hosts.toList()
}
