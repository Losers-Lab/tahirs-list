package com.tahirslist.mcp

import com.tahirslist.application.agent.GetListingForAgent
import com.tahirslist.application.agent.SearchListingsForAgent
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
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
import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.URI

private val log = KotlinLogging.logger("com.tahirslist.mcp.McpServer")

/** The one path the public MCP endpoint is mounted at. */
const val MCP_PATH: String = "/mcp"

/**
 * Mounts the stateless Streamable HTTP MCP endpoint.
 *
 * Stateless means: no session id, one server session per POST, POST only.
 * Refusals happen before any tool work — a disallowed browser `Origin` (403) and
 * an exhausted per-client budget (429) are answered by the pipeline intercept;
 * the SDK never sees them.
 *
 * Content negotiation is installed on the MCP route with the SDK's [McpJson]
 * (not the REST `Json`), which keeps REST's explicit-null contract intact while
 * MCP gets the serializer the protocol requires.
 */
fun Application.installMcp(
    config: McpConfig,
    useCases: McpUseCases,
    limiter: McpRateLimiter = config.rateLimit.buildLimiter(),
    originGuard: McpOriginGuard = McpOriginGuard(config.allowedOrigins),
    path: String = MCP_PATH,
) {
    val serverFactory = {
        TahirsListMcpServer(useCases, config).createServer()
    }

    intercept(ApplicationCallPipeline.Plugins) {
        if (call.request.httpMethod == HttpMethod.Post && call.request.path() == path) {
            val origin = call.request.headers[HttpHeaders.Origin]
            if (!originGuard.allows(origin)) {
                log.info { "mcp refused origin=$origin status=403" }
                call.respond(HttpStatusCode.Forbidden)
                finish()
                return@intercept
            }
            val key = mcpClientKey(
                remoteHost = call.request.local.remoteHost,
                forwardedFor = call.request.headers[HttpHeaders.XForwardedFor],
                trustProxy = config.rateLimit.trustProxy,
            )
            if (!limiter.tryAcquire(key)) {
                log.info { "mcp refused client=$key status=429" }
                call.response.header(HttpHeaders.RetryAfter, config.rateLimit.refillWindowSeconds)
                call.respond(HttpStatusCode.TooManyRequests)
                finish()
                return@intercept
            }
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
                    StreamableHttpServerTransport.Configuration(enableJsonResponse = true),
                ).also { it.setSessionIdGenerator(null) }
                val server: Server = serverFactory()
                val session = server.createSession(transport)
                try {
                    transport.handleRequest(null, call)
                } finally {
                    session.close()
                }
            }
            get {
                call.response.header(HttpHeaders.Allow, HttpMethod.Post.value)
                call.respond(HttpStatusCode.MethodNotAllowed)
            }
            delete {
                call.response.header(HttpHeaders.Allow, HttpMethod.Post.value)
                call.respond(HttpStatusCode.MethodNotAllowed)
            }
        }
    }
}

/**
 * Per-client budget key. `X-Forwarded-For` is client-controlled and used only
 * when the deployment explicitly trusts its proxy; otherwise the socket peer
 * keys the bucket. Only the first XFF hop is considered.
 */
internal fun mcpClientKey(remoteHost: String, forwardedFor: String?, trustProxy: Boolean): String {
    val ip = if (trustProxy) {
        forwardedFor?.substringBefore(",")?.trim()?.takeIf { it.isNotBlank() }
            ?: remoteHost.takeIf { it.isNotBlank() }
            ?: "unknown"
    } else {
        remoteHost.takeIf { it.isNotBlank() } ?: "unknown"
    }
    return "mcp|$ip"
}

/**
 * Hosts accepted by the DNS-rebinding guard: loopback plus the configured public
 * hosts. Without the configured hosts a production deployment would be refused
 * by the SDK's localhost-only default.
 */
internal fun allowedHosts(config: McpConfig): List<String> {
    val hosts = linkedSetOf("localhost", "127.0.0.1", "[::1]")
    listOf(config.publicApiBaseUrl, config.publicWebBaseUrl).forEach { url ->
        runCatching { URI(url).host }.getOrNull()?.takeIf { it.isNotBlank() }?.let(hosts::add)
    }
    return hosts.toList()
}
