package com.tahirslist.web

import com.tahirslist.application.account.EmailAlreadyExistsException
import com.tahirslist.application.account.InvalidCredentialsException
import com.tahirslist.application.account.WeakPasswordException
import com.tahirslist.application.favorite.ListingNotFoundException
import com.tahirslist.application.listing.ListingOwnerNotFoundException
import com.tahirslist.application.verification.NotListingOwnerException
import com.tahirslist.application.verification.ReviewNotFoundException
import com.tahirslist.application.verification.ReviewNotPendingException
import com.tahirslist.application.verification.VerificationUnavailableException
import com.tahirslist.web.ratelimit.AuthRateLimitGateKey
import com.tahirslist.web.ratelimit.Bucket4jAuthRateLimiter
import com.tahirslist.web.ratelimit.RateLimitGate
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.hooks.ResponseBodyReadyForSend
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.plugins.PayloadTooLargeException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException

/**
 * Installs the framework-level web plugins shared by every route module:
 *
 *  - kotlinx.serialization content negotiation using the caller's [Json]
 *    (defaults + explicit nulls are part of the wire contract);
 *  - the custom Nimbus RS256 [JwtAuthenticationProvider] (deny-by-default);
 *  - the ratified status-pages whitelist (generic envelopes only, never a raw
 *    exception message);
 *  - baseline security headers (including HSTS) and a `no-store` cache policy;
 *  - the in-process sc-136 auth rate-limit gate (signup, login + refresh).
 *
 * Route modules are plain `Route` extensions so bootstrap owns routing while
 * this module owns the HTTP contract.
 */
fun Application.configureWebPlugins(
    json: Json,
    jwt: JwtConfig,
    rateLimit: RateLimitConfig,
) {
    val verifier = JwtVerifier(jwt)

    // Request decoding is lenient about unknown fields (parity with the previous
    // Jackson binding), while responses keep the caller's exact Json contract.
    attributes.put(
        WebJsonKey,
        if (json.configuration.ignoreUnknownKeys) json else Json(json) { ignoreUnknownKeys = true },
    )

    attributes.put(
        AuthRateLimitGateKey,
        RateLimitGate(
            limiter = Bucket4jAuthRateLimiter(
                capacity = rateLimit.capacity,
                refillTokensPerWindow = rateLimit.refillTokens,
                refillWindow = rateLimit.refillWindow,
                maxKeys = rateLimit.maxKeys.toLong(),
            ),
            trustProxy = rateLimit.trustProxy,
        ),
    )

    install(ContentNegotiation) {
        json(json)
    }

    install(Authentication) {
        register(JwtAuthenticationProvider(JwtAuthenticationProvider.Config(verifier)))
    }

    install(StatusPages) {
        exception<InvalidInputException> { call, cause ->
            call.respondError(HttpStatusCode.BadRequest, ErrorCodes.INVALID_INPUT, cause.message)
        }
        // Declared BEFORE the generic transformation mapping below: the two
        // overlap, and `PayloadTooLargeException` travels as a
        // ContentTransformationException. StatusPages resolves the handler whose
        // class is nearest to the thrown type (not the registration order), yet
        // keeping the specific case first makes the precedence explicit instead
        // of relying on that resolution rule.
        exception<PayloadTooLargeException> { call, _ ->
            call.respondError(
                HttpStatusCode.PayloadTooLarge,
                ErrorCodes.PAYLOAD_TOO_LARGE,
                ErrorMessages.PAYLOAD_TOO_LARGE,
            )
        }
        // Ktor's multipart parser enforces `formFieldLimit` on a producer
        // coroutine: an oversized part throws a bare IOException on the call
        // rather than at the route's suspend point, so `receiveForm` cannot
        // translate it. Map the limit case here; any other I/O failure keeps the
        // generic 500 shape (a client disconnect has no response to deliver).
        exception<IOException> { call, cause ->
            if (cause.isMultipartLimitExceeded()) {
                call.respondError(
                    HttpStatusCode.PayloadTooLarge,
                    ErrorCodes.PAYLOAD_TOO_LARGE,
                    ErrorMessages.PAYLOAD_TOO_LARGE,
                )
            } else {
                log.error(cause) { "I/O failure while handling the request" }
                call.respondError(
                    HttpStatusCode.InternalServerError,
                    ErrorCodes.INTERNAL_ERROR,
                    ErrorMessages.INTERNAL_ERROR,
                )
            }
        }
        exception<ContentTransformationException> { call, _ ->
            call.respondError(
                HttpStatusCode.BadRequest,
                ErrorCodes.INVALID_INPUT,
                ErrorMessages.REQUEST_BODY_NOT_READABLE,
            )
        }
        // Ktor raises this for a structurally malformed request body (a broken
        // multipart boundary, for instance). Without an explicit mapping it is
        // not a ContentTransformationException and would fall through to 500.
        exception<BadRequestException> { call, _ ->
            call.respondError(
                HttpStatusCode.BadRequest,
                ErrorCodes.INVALID_INPUT,
                ErrorMessages.REQUEST_BODY_NOT_READABLE,
            )
        }
        exception<SerializationException> { call, _ ->
            call.respondError(
                HttpStatusCode.BadRequest,
                ErrorCodes.INVALID_INPUT,
                ErrorMessages.REQUEST_BODY_NOT_READABLE,
            )
        }
        exception<EmailAlreadyExistsException> { call, _ ->
            call.respondError(HttpStatusCode.Conflict, ErrorCodes.EMAIL_ALREADY_EXISTS)
        }
        exception<WeakPasswordException> { call, cause ->
            call.respondError(HttpStatusCode.UnprocessableEntity, ErrorCodes.WEAK_PASSWORD, cause.message)
        }
        exception<InvalidCredentialsException> { call, _ ->
            call.respondError(
                HttpStatusCode.Unauthorized,
                ErrorCodes.INVALID_CREDENTIALS,
                ErrorMessages.INVALID_CREDENTIALS,
            )
        }
        exception<ListingOwnerNotFoundException> { call, _ ->
            call.respondError(HttpStatusCode.NotFound, ErrorCodes.OWNER_NOT_FOUND)
        }
        exception<ListingNotFoundException> { call, _ ->
            call.respondError(HttpStatusCode.NotFound, ErrorCodes.LISTING_NOT_FOUND)
        }
        exception<NotListingOwnerException> { call, _ ->
            call.respondError(HttpStatusCode.Forbidden, ErrorCodes.NOT_LISTING_OWNER)
        }
        exception<ForbiddenException> { call, _ ->
            call.respondError(HttpStatusCode.Forbidden, ErrorCodes.FORBIDDEN)
        }
        exception<ReviewNotFoundException> { call, _ ->
            call.respondError(HttpStatusCode.NotFound, ErrorCodes.REVIEW_NOT_FOUND)
        }
        exception<ReviewNotPendingException> { call, _ ->
            call.respondError(HttpStatusCode.Conflict, ErrorCodes.REVIEW_NOT_PENDING)
        }
        exception<VerificationUnavailableException> { call, _ ->
            call.respondError(HttpStatusCode.ServiceUnavailable, ErrorCodes.VERIFICATION_UNAVAILABLE)
        }
        // Other invalid client input: generic, never echo the raw reason.
        exception<IllegalArgumentException> { call, _ ->
            call.respondError(HttpStatusCode.BadRequest, ErrorCodes.INVALID_INPUT, ErrorMessages.INVALID_INPUT)
        }
        exception<Throwable> { call, cause ->
            log.error(cause) { "Unhandled exception; returning generic internal_error" }
            call.respondError(
                HttpStatusCode.InternalServerError,
                ErrorCodes.INTERNAL_ERROR,
                ErrorMessages.INTERNAL_ERROR,
            )
        }
        // Unmatched route: a plain 404 envelope (never a 500). Deliberate 404s
        // from the read endpoints are already handled, so their bodies stay empty.
        unhandled { call ->
            call.respondError(HttpStatusCode.NotFound, ErrorCodes.NOT_FOUND, ErrorMessages.RESOURCE_NOT_FOUND)
        }
    }

    install(SecurityHeadersPlugin)

    // Baseline headers are set for every call, including ones the deny guard
    // rejects before routing.
    intercept(ApplicationCallPipeline.Setup) {
        call.response.headers.append("X-Content-Type-Options", "nosniff")
        call.response.headers.append("X-Frame-Options", "DENY")
        call.response.headers.append("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'")
        // The deployment is TLS-only (Cloudflare tunnel terminates TLS), so HSTS
        // is safe to assert unconditionally: it pins browsers to HTTPS for this
        // host and its subdomains for a year.
        call.response.headers.append("Strict-Transport-Security", "max-age=31536000; includeSubDomains")
    }

    // Deny-by-default: everything except the explicitly public surface requires
    // a verified access token. The route modules re-authenticate via the Ktor
    // Authentication provider (which sets the principal); this guard makes the
    // posture structural for unknown/unregistered paths too, matching the
    // previous `anyRequest().authenticated()` chain.
    intercept(ApplicationCallPipeline.Setup) {
        if (!isPublicRequest(call.request.httpMethod, call.request.path())) {
            val principal = bearerToken(call)?.let(verifier::verify)
            if (principal == null) {
                call.respondError(
                    HttpStatusCode.Unauthorized,
                    ErrorCodes.UNAUTHORIZED,
                    ErrorMessages.AUTHENTICATION_REQUIRED,
                )
                finish()
            }
        }
    }
}

/**
 * Adds the `no-store` cache policy to responses that did not opt into an
 * explicit one (the public image/certificate proxies set `public, max-age`).
 */
private val SecurityHeadersPlugin = createApplicationPlugin("SecurityHeaders") {
    on(ResponseBodyReadyForSend) { call, _ ->
        if (call.response.headers[HttpHeaders.CacheControl] == null) {
            call.response.headers.append(HttpHeaders.CacheControl, "no-store")
        }
    }
}

private val log = KotlinLogging.logger("com.tahirslist.web.WebApi")

/**
 * True when an [IOException] is Ktor's multipart size refusal.
 *
 * ktor-http-cio signals this in two shapes, neither of which is a dedicated
 * exception type: `Multipart content length exceeds limit …` when a part's
 * declared length is known, and `Limit of N bytes exceeded while searching for
 * <boundary>` when the parser hits the cap while scanning a streamed part. Both
 * must map to 413 rather than the generic 500. A wrapped typed refusal
 * ([PayloadTooLargeException] as the cause or suppressed) is also accepted as
 * defence in depth; Ktor 3.6.1 was not observed to attach one, so that branch is
 * not part of the pinned contract.
 */
internal fun IOException.isMultipartLimitExceeded(): Boolean {
    if (this is PayloadTooLargeException || cause is PayloadTooLargeException) return true
    if (suppressed.any { it is PayloadTooLargeException }) return true
    var current: Throwable? = this
    while (current != null) {
        val text = current.message
        if (text != null &&
            (
                text.contains("exceeds limit", ignoreCase = true) ||
                    (text.startsWith("Limit of ") && text.contains("bytes exceeded"))
                )
        ) {
            return true
        }
        current = current.cause
    }
    return false
}

private val PUBLIC_POST_PATHS = setOf(
    "/v1/auth/signup",
    "/v1/auth/login",
    "/v1/auth/refresh",
    "/v1/auth/logout",
)

/**
 * The public HTTP surface: the auth mutations, the health probe, and the public
 * GET read surface under `/v1/listings` (search/browse, detail, image +
 * certificate proxies). Everything else is authenticated.
 */
internal fun isPublicRequest(method: HttpMethod, path: String): Boolean = when {
    method == HttpMethod.Post && path in PUBLIC_POST_PATHS -> true
    method == HttpMethod.Get && path == "/v1/health" -> true
    method == HttpMethod.Get && (path == "/v1/listings" || path.startsWith("/v1/listings/")) -> true
    else -> false
}
