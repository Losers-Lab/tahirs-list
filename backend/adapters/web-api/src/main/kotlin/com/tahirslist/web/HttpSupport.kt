package com.tahirslist.web

import io.ktor.http.BadContentTypeFormatException
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.PayloadTooLargeException
import io.ktor.server.plugins.origin
import io.ktor.server.request.contentCharset
import io.ktor.server.request.contentLength
import io.ktor.server.request.receiveChannel
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respondBytes
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.uuid.Uuid
import java.io.ByteArrayOutputStream
import java.io.IOException

/** The configured kotlinx.serialization JSON instance (wire contract). */
internal val WebJsonKey = io.ktor.util.AttributeKey<Json>("WebJson")

/** The fixed refresh-cookie name (sc-133). */
internal const val REFRESH_COOKIE_NAME = "refresh_token"

/**
 * The largest JSON / auth request body accepted, in bytes (64 KiB).
 *
 * The previous Spring stack capped request bodies at 10 MB; losing that cap
 * would let one unauthenticated client make the server buffer arbitrary input
 * before any validation runs. 64 KiB is two orders of magnitude above the
 * largest legitimate body on this surface (signup/login credentials, a listing
 * write, a committee decision), so no real request is refused — while an
 * attacker cannot turn the JSON path into a memory amplifier.
 *
 * Deliberately not environment-configurable in this change: the value is a
 * property of the current product's request shapes, not an operator knob.
 */
internal const val MAX_JSON_BODY_BYTES = 64L * 1024

/**
 * The largest single uploaded file accepted, in bytes (8 MiB).
 *
 * Phone cameras top out around 5-6 MiB per photo, so 8 MiB fits every
 * legitimate hero image / certification photo (Spring's 1 MB file cap was too
 * small for real uploads), while keeping one request's in-memory buffer
 * bounded. It is enforced twice: as Ktor's `formFieldLimit` (the parser's
 * per-part cap) and as the bound on our own read of the file part.
 * Also not environment-configurable in this change, for the same reason as
 * [MAX_JSON_BODY_BYTES].
 */
internal const val MAX_UPLOAD_BYTES = 8L * 1024 * 1024

/**
 * The largest whole multipart body accepted, in bytes (8 MiB + 256 KiB).
 *
 * The per-part cap alone is not a request budget: a body may carry up to
 * [MAX_MULTIPART_PARTS] parts, and every file part is retained until the
 * handler returns. Without an aggregate bound sixteen 8 MiB parts would keep
 * ~128 MiB of heap alive for one request on an endpoint reachable by anyone who
 * signs up — the same class of memory exhaustion Spring's `max-request-size`
 * used to prevent. One image plus its short text fields fits comfortably.
 */
internal const val MAX_MULTIPART_BODY_BYTES = MAX_UPLOAD_BYTES + 256L * 1024

/**
 * The largest multipart text field value accepted, in bytes (8 KiB).
 *
 * The fields on this surface are short strings (an ownership proof, a consent
 * flag), never documents, so this is checked directly on each form item's value.
 * Ktor's `formFieldLimit` is NOT used for this: it is applied as the parser's
 * per-part cap for every part, file parts included (ktor-http-cio
 * `parseMultipart(maxPartSize)`), so it carries [MAX_UPLOAD_BYTES] instead.
 */
internal const val MAX_FORM_FIELD_BYTES = 8L * 1024

/**
 * The most parts accepted in one multipart body.
 *
 * The richest form here is the owner claim (proof + aiConsent + certImage =
 * 3 parts), so 16 leaves generous headroom while refusing a body that tries to
 * make the parser walk thousands of tiny parts.
 */
internal const val MAX_MULTIPART_PARTS = 16

/** Multipart file parts served as images; anything else is an opaque download. */
private val SERVABLE_IMAGE_TYPES = setOf("image/jpeg", "image/png", "image/webp", "image/gif")

/**
 * Decodes an optional JSON body using the application's configured [Json].
 * An empty body yields null (e.g. approve with no decision details); a
 * malformed body surfaces as a 400 via the status-pages whitelist, and a body
 * over [MAX_JSON_BODY_BYTES] as a 413.
 */
internal suspend inline fun <reified T> ApplicationCall.receiveOptionalJson(): T? {
    val text = receiveBoundedText(MAX_JSON_BODY_BYTES)
    if (text.isBlank()) return null
    return application.attributes[WebJsonKey].decodeFromString<T>(text)
}

/** Decodes a required JSON body; an empty body is a 400 `invalid_input`. */
internal suspend inline fun <reified T> ApplicationCall.receiveJson(): T =
    receiveOptionalJson<T>() ?: throw InvalidInputException(ErrorMessages.REQUEST_BODY_NOT_READABLE)

/**
 * Reads the request body as text, refusing anything larger than [limit] bytes
 * before it reaches the heap.
 *
 * The declared `Content-Length` is rejected up front (cheap), but the actual
 * read is bounded too: a chunked body declares no length, so that is where the
 * memory-pressure guard has to live.
 *
 * @throws PayloadTooLargeException when the body exceeds [limit] (→ 413).
 */
internal suspend fun ApplicationCall.receiveBoundedText(limit: Long = MAX_JSON_BODY_BYTES): String {
    val declared = request.contentLength()
    if (declared != null && declared > limit) throw PayloadTooLargeException(limit)

    val charset = try {
        request.contentCharset() ?: Charsets.UTF_8
    } catch (cause: BadContentTypeFormatException) {
        // Same contract as Ktor's own receiveText(): a bogus Content-Type is a 400.
        throw BadRequestException("Illegal Content-Type header format", cause)
    }
    val bytes = request.receiveChannel().readAtMost(limit)
    // A declared length is a promise: if the body ends early, what we parsed is
    // not what the client sent, so refuse it instead of acting on a truncation.
    if (declared != null && bytes.size.toLong() != declared) {
        throw BadRequestException("Request body ended before its declared Content-Length")
    }
    return bytes.toString(charset)
}

/**
 * Reads at most [limit] bytes from this channel.
 *
 * The overflow is detected from the bytes themselves as they arrive, so a
 * streamed (chunked) body is bounded exactly like a sized one: at most `limit`
 * bytes ever accumulate, plus one fixed-size read buffer.
 *
 * An [IOException] mid-read is reported as the same size refusal: the multipart
 * parser closes a part that passes its per-part cap by throwing into the part
 * channel, which surfaces here as an I/O error rather than a clean EOF.
 */
private suspend fun ByteReadChannel.readAtMost(limit: Long): ByteArray {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(READ_CHUNK_BYTES)
    while (true) {
        val read = try {
            readAvailable(buffer, 0, buffer.size)
        } catch (ignored: IOException) {
            throw PayloadTooLargeException(limit)
        }
        if (read == -1) break // channel closed: end of body
        if (out.size().toLong() + read > limit) throw PayloadTooLargeException(limit)
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}

/** The fixed read buffer for bounded body reads (never sized from the request). */
private const val READ_CHUNK_BYTES = 8 * 1024

/**
 * The absolute request origin, mirroring the previous
 * `ServletUriComponentsBuilder.fromCurrentContextPath()` behaviour: scheme +
 * host, with the port only when it is not the scheme default.
 */
internal fun ApplicationCall.baseUrl(): String {
    val origin = request.origin
    val isDefaultPort =
        (origin.scheme == "http" && origin.serverPort == 80) ||
            (origin.scheme == "https" && origin.serverPort == 443)
    return if (isDefaultPort) {
        "${origin.scheme}://${origin.serverHost}"
    } else {
        "${origin.scheme}://${origin.serverHost}:${origin.serverPort}"
    }
}

/** Parses a path parameter as a [Uuid]; malformed values are client errors (400). */
internal fun ApplicationCall.pathUuid(name: String): Uuid {
    val raw = parameters[name] ?: throw InvalidInputException("$name is required")
    return try {
        Uuid.parse(raw)
    } catch (ignored: IllegalArgumentException) {
        throw InvalidInputException("$name must be a valid UUID")
    }
}

/** Throws one 400 carrying every field-level validation message (Spring order). */
internal fun requireValid(messages: List<String>) {
    if (messages.isNotEmpty()) throw InvalidInputException(messages.joinToString("; "))
}

/** Appends an explicit cache policy (overrides the default `no-store`). */
internal fun ApplicationCall.setCacheControl(value: String) {
    response.headers.append(HttpHeaders.CacheControl, value)
}

/**
 * Serves stored bytes without ever letting the stored type decide how the
 * browser renders them.
 *
 * Only the four image types the ingest pipeline can derive are served as
 * images (and rendered `inline`); every other stored type — a legacy row, or a
 * certificate archived before the type was derived from its bytes — is forced
 * to `application/octet-stream` and marked `attachment`, so a stored
 * `text/html` can never execute in a page under our origin.
 */
internal suspend fun ApplicationCall.respondStoredBytes(bytes: ByteArray, storedContentType: String) {
    val bare = storedContentType.substringBefore(';').trim().lowercase()
    val isImage = bare in SERVABLE_IMAGE_TYPES
    response.headers.append(
        HttpHeaders.ContentDisposition,
        if (isImage) "inline" else "attachment",
    )
    respondBytes(bytes, if (isImage) ContentType.parse(bare) else ContentType.Application.OctetStream)
}

/**
 * Issues (or re-issues) the HttpOnly refresh cookie. Attribute order matches the
 * ratified contract: `Path`, `Max-Age`, `HttpOnly`, `Secure` (per config),
 * `SameSite=Lax`.
 */
internal fun ApplicationCall.setRefreshCookie(config: RefreshCookieConfig, refreshToken: String) {
    response.headers.append(
        HttpHeaders.SetCookie,
        refreshCookie(config, refreshToken, config.maxAgeSeconds),
    )
}

/** Clears the refresh cookie (zero Max-Age). */
internal fun ApplicationCall.clearRefreshCookie(config: RefreshCookieConfig) {
    response.headers.append(HttpHeaders.SetCookie, refreshCookie(config, "", 0))
}

private fun refreshCookie(config: RefreshCookieConfig, value: String, maxAgeSeconds: Long): String =
    buildList {
        add("$REFRESH_COOKIE_NAME=$value")
        add("Path=${config.path}")
        add("Max-Age=$maxAgeSeconds")
        add("HttpOnly")
        if (config.secure) add("Secure")
        add("SameSite=Lax")
    }.joinToString("; ")

/**
 * A multipart file part held in memory (the payloads here are small images).
 *
 * [contentType] is the client-declared part header. It is untrusted metadata:
 * the ingest pipeline derives the stored media type from the bytes and this
 * value is never persisted or re-served.
 */
internal class UploadedFile(val bytes: ByteArray, val contentType: String)

/** Multipart form fields + file parts, keyed by part name. */
internal class MultipartForm(
    val fields: Map<String, String>,
    val files: Map<String, UploadedFile>,
) {
    fun requiredField(name: String, missingMessage: String): String =
        fields[name]?.takeIf { it.isNotBlank() } ?: throw InvalidInputException(missingMessage)

    fun requiredFile(name: String, missingMessage: String): UploadedFile =
        files[name] ?: throw InvalidInputException(missingMessage)
}

/**
 * Receives a multipart body once, disposing every part.
 *
 * Three limits replace the Spring multipart caps that were lost in the
 * migration:
 *  - at most [MAX_MULTIPART_PARTS] parts;
 *  - every file part read through a bounded stream of at most
 *    [MAX_UPLOAD_BYTES] (the part provider is never drained with an unbounded
 *    `toByteArray()`), with the same value also handed to Ktor as
 *    `formFieldLimit` — that parameter is the parser's per-part cap (file parts
 *    included), so it bounds how much the parser itself can buffer for one part;
 *  - every text field value checked against [MAX_FORM_FIELD_BYTES];
 *  - the bytes retained across all parts are counted against
 *    [MAX_MULTIPART_BODY_BYTES], so the per-part cap cannot be multiplied by the
 *    part count into a request-sized heap allocation.
 *
 * @throws PayloadTooLargeException when any limit is exceeded (→ 413).
 */
internal suspend fun ApplicationCall.receiveForm(): MultipartForm {
    val fields = mutableMapOf<String, String>()
    val files = mutableMapOf<String, UploadedFile>()

    val declared = request.contentLength()
    if (declared != null && declared > MAX_MULTIPART_BODY_BYTES) {
        throw PayloadTooLargeException(MAX_MULTIPART_BODY_BYTES)
    }

    var parts = 0
    var retainedBytes = 0L
    try {
        // Ktor starts parsing eagerly, so the creation and the iteration must sit
        // in the same guard: an oversized part surfaces as a bare IOException from
        // either call, and would otherwise reach the generic 500 handler.
        val multipart = receiveMultipart(formFieldLimit = MAX_UPLOAD_BYTES)
        // Disposal is the parser's job: CIOMultipartDataBase.readPart() releases
        // the previous part on the next read, after it has been drained. An
        // explicit release here would instead run the blocking discardBlocking()
        // on the part we just refused to drain, stalling a CIO worker.
        multipart.forEachPart { part ->
            parts += 1
            if (parts > MAX_MULTIPART_PARTS) throw PayloadTooLargeException(MAX_MULTIPART_PARTS.toLong())
            val name = part.name
            if (name != null) {
                when (part) {
                    is PartData.FormItem -> {
                        val fieldBytes = part.value.toByteArray(Charsets.UTF_8)
                        if (fieldBytes.size > MAX_FORM_FIELD_BYTES) {
                            throw PayloadTooLargeException(MAX_FORM_FIELD_BYTES)
                        }
                        retainedBytes = retainWithinBudget(retainedBytes, fieldBytes.size.toLong())
                        fields[name] = part.value
                    }
                    is PartData.FileItem -> {
                        val bytes = part.provider().readAtMost(MAX_UPLOAD_BYTES)
                        retainedBytes = retainWithinBudget(retainedBytes, bytes.size.toLong())
                        files[name] = UploadedFile(
                            bytes = bytes,
                            contentType = part.contentType?.toString()
                                ?: ContentType.Application.OctetStream.toString(),
                        )
                    }
                    else -> Unit
                }
            }
        }
    } catch (cause: PayloadTooLargeException) {
        // Our own refusals are already the right failure; rethrow before the
        // IOException catch below, which would otherwise swallow them (a
        // PayloadTooLargeException IS an IOException via ContentTransformation).
        throw cause
    } catch (ignored: IOException) {
        // Reading the body is the only I/O here, so an I/O failure means the
        // payload could not be accepted as sent — surface it as the size refusal
        // it is instead of letting it fall through to a 500.
        throw PayloadTooLargeException(MAX_UPLOAD_BYTES)
    }
    return MultipartForm(fields = fields, files = files)
}

/**
 * Adds [bytes] to the running multipart budget, refusing the request once the
 * retained total would exceed [MAX_MULTIPART_BODY_BYTES].
 */
private fun retainWithinBudget(total: Long, bytes: Long): Long {
    val next = total + bytes
    if (next > MAX_MULTIPART_BODY_BYTES) throw PayloadTooLargeException(MAX_MULTIPART_BODY_BYTES)
    return next
}
