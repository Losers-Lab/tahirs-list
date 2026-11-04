package com.tahirslist.web

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jwt.SignedJWT
import com.tahirslist.domain.account.Role
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.AuthenticationContext
import io.ktor.server.auth.AuthenticationFailedCause
import io.ktor.server.auth.AuthenticationProvider
import io.ktor.server.auth.principal
import java.util.Date
import kotlin.uuid.Uuid

/** The authenticated identity carried by a verified access JWT. */
data class JwtPrincipal(
    /** The JWT `sub`: the account id, echoed by `/v1/me` and parsed for writes. */
    val subject: String,
    /** The RBAC `role` claim, guaranteed to be one of the six MVP role names. */
    val role: String,
)

/** The name the custom provider registers under; routes authenticate with it. */
internal const val JWT_AUTH_PROVIDER = "jwt-rs256"

private const val BEARER_PREFIX = "Bearer "

/** Returns the raw Bearer token from the Authorization header, if any. */
internal fun bearerToken(call: ApplicationCall): String? =
    call.request.headers[HttpHeaders.Authorization]
        ?.takeIf { it.startsWith(BEARER_PREFIX, ignoreCase = true) }
        ?.substring(BEARER_PREFIX.length)
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

/**
 * RS256 access-token verification with Nimbus (sc-131).
 *
 * A token is accepted only when ALL of the following hold:
 *  - the compact serialization parses and is signed with RS256;
 *  - the signature verifies against the configured RSA public key;
 *  - `iss` equals the configured issuer;
 *  - `exp` is present and not older than now minus the configured clock skew;
 *  - `role` is present and is one of the six [Role] names;
 *  - `sub` is present.
 *
 * Any failure returns `null`; callers translate that into the generic 401 —
 * the reason is never revealed to the client.
 */
internal class JwtVerifier(private val config: JwtConfig) {

    private val verifier = RSASSAVerifier(config.publicKey)

    fun verify(token: String): JwtPrincipal? = try {
        val signed = SignedJWT.parse(token)
        val claims = signed.jwtClaimsSet
        if (signed.header.algorithm != JWSAlgorithm.RS256) return null
        if (!signed.verify(verifier)) return null
        if (claims.issuer != config.issuer) return null
        val expiry: Date = claims.expirationTime ?: return null
        if (expiry.time + config.clockSkewSeconds * 1_000L <= System.currentTimeMillis()) return null
        val subject = claims.subject
        if (subject.isNullOrBlank()) return null
        val role = claims.getStringClaim("role")
        if (role == null || Role.entries.none { it.name == role }) return null
        JwtPrincipal(subject = subject, role = role)
    } catch (ignored: Exception) {
        // Malformed / unparsable / tampered: indistinguishable generic failure.
        null
    }
}

/**
 * Ktor [AuthenticationProvider] that verifies RS256 access JWTs with Nimbus.
 *
 * The challenge always writes the same generic 401 envelope, regardless of the
 * concrete failure (missing header, malformed token, bad signature, expired,
 * wrong issuer, missing/unknown role) — no auth detail leaks.
 */
internal class JwtAuthenticationProvider(
    config: Config,
) : AuthenticationProvider(config) {

    private val verifier: JwtVerifier = config.verifier

    override suspend fun onAuthenticate(context: AuthenticationContext) {
        val principal = bearerToken(context.call)?.let(verifier::verify)
        if (principal == null) {
            context.challenge(CHALLENGE_KEY, AuthenticationFailedCause.InvalidCredentials) { challenge, call ->
                call.respondError(
                    HttpStatusCode.Unauthorized,
                    ErrorCodes.UNAUTHORIZED,
                    ErrorMessages.AUTHENTICATION_REQUIRED,
                )
                challenge.complete()
            }
            return
        }
        context.principal(name, principal)
    }

    internal class Config(val verifier: JwtVerifier) :
        AuthenticationProvider.Config(JWT_AUTH_PROVIDER)

    private companion object {
        const val CHALLENGE_KEY = "jwt-rs256-challenge"
    }
}

/** The principal of the current call, or null when the route is not protected. */
internal fun ApplicationCall.jwtPrincipal(): JwtPrincipal? = principal<JwtPrincipal>()

/** The authenticated account id of the call. */
internal fun ApplicationCall.requireAccountId(): Uuid =
    Uuid.parse(
        jwtPrincipal()?.subject
            ?: throw InvalidInputException(ErrorMessages.AUTHENTICATION_REQUIRED),
    )
