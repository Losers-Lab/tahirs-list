package com.tahirslist.web

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import kotlinx.serialization.Serializable

/**
 * The uniform error envelope. [message] is always serialized (explicit nulls and
 * encoded defaults are part of the configured JSON contract), so a client can
 * rely on the field existing.
 */
@Serializable
data class ErrorResponse(val code: String, val message: String? = null)

/** Error codes ratified for the public HTTP contract. */
internal object ErrorCodes {
    const val INVALID_INPUT = "invalid_input"
    const val NOT_FOUND = "not_found"
    const val UNAUTHORIZED = "unauthorized"
    const val RATE_LIMITED = "rate_limited"
    const val INTERNAL_ERROR = "internal_error"
    const val EMAIL_ALREADY_EXISTS = "email_already_exists"
    const val WEAK_PASSWORD = "weak_password"
    const val INVALID_CREDENTIALS = "invalid_credentials"
    const val NOT_LISTING_OWNER = "not_listing_owner"
    const val LISTING_NOT_FOUND = "listing_not_found"
    const val OWNER_NOT_FOUND = "owner_not_found"
    const val FORBIDDEN = "forbidden"
    const val REVIEW_NOT_FOUND = "review_not_found"
    const val REVIEW_NOT_PENDING = "review_not_pending"
    const val VERIFICATION_UNAVAILABLE = "verification_unavailable"
}

/** Fixed, client-safe messages (never built from a raw exception message). */
internal object ErrorMessages {
    const val AUTHENTICATION_REQUIRED = "Authentication required."
    const val RATE_LIMITED = "Too many requests. Please try again later."
    const val INTERNAL_ERROR = "An unexpected server error occurred."
    const val INVALID_CREDENTIALS = "Invalid email or password."
    const val REQUEST_BODY_NOT_READABLE = "Request body is not readable."
    const val INVALID_INPUT = "Invalid input."
    const val RESOURCE_NOT_FOUND = "Resource not found."
    const val IMAGE_PART_REQUIRED = "Image part is required."
    const val CERTIFICATION_PART_REQUIRED = "Certification image part is required."
    const val PROOF_PART_REQUIRED = "Proof part is required."
    const val CONSENT_PART_REQUIRED = "AI consent part is required."
}

/**
 * A client-facing validation failure whose message is safe to echo (field-level
 * detail such as "name is required"). Distinct from a bare
 * [IllegalArgumentException], whose raw message is never surfaced.
 */
internal class InvalidInputException(message: String) : RuntimeException(message)

/** Authenticated but not permitted for this operation (e.g. non-committee). */
internal class ForbiddenException : RuntimeException("Forbidden.")

/** Writes the error envelope with the given status. */
internal suspend fun ApplicationCall.respondError(
    status: HttpStatusCode,
    code: String,
    message: String? = null,
) {
    respond(status, ErrorResponse(code = code, message = message))
}
