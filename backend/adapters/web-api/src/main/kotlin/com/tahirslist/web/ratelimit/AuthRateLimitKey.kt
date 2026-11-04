package com.tahirslist.web.ratelimit

import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.path
import io.ktor.util.AttributeKey

/** Application attribute holding the assembled auth rate-limit gate. */
internal val AuthRateLimitGateKey: AttributeKey<RateLimitGate> =
    AttributeKey("AuthRateLimitGate")

/**
 * The auth rate-limit gate assembled at install time: the shared bucket map plus
 * the proxy-trust decision. Stored as an application attribute so the auth route
 * module can enforce it without widening its public signature.
 */
class RateLimitGate(
    val limiter: AuthRateLimiter,
    val trustProxy: Boolean,
)

/**
 * The rate-limit budget key: route + client IP, so signup, login and refresh
 * never starve each other. `X-Forwarded-For` is client-controlled and trusted
 * only when [trustProxy] is explicitly enabled; otherwise the socket peer keys
 * the bucket, so clients cannot rotate their budget by forging headers. Only the
 * first XFF hop is used.
 */
fun authRateLimitKey(
    requestUri: String,
    remoteHost: String,
    forwardedFor: String?,
    trustProxy: Boolean,
): String {
    val ip = if (trustProxy) {
        forwardedFor?.substringBefore(",")?.trim()?.takeIf { it.isNotBlank() }
            ?: remoteHost.takeIf { it.isNotBlank() }
            ?: "unknown"
    } else {
        remoteHost.takeIf { it.isNotBlank() } ?: "unknown"
    }
    return "$requestUri|$ip"
}

/** Derives the budget key for this request from its path and connection peer. */
fun ApplicationCall.authRateLimitKey(trustProxy: Boolean): String = authRateLimitKey(
    requestUri = request.path(),
    remoteHost = request.local.remoteHost,
    forwardedFor = request.headers[HttpHeaders.XForwardedFor],
    trustProxy = trustProxy,
)
