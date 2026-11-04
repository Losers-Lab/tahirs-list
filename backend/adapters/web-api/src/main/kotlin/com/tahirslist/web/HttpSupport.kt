package com.tahirslist.web

import io.ktor.http.HttpHeaders
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.origin
import io.ktor.server.request.receiveMultipart
import io.ktor.server.request.receiveText
import io.ktor.utils.io.toByteArray
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.uuid.Uuid

/** The configured kotlinx.serialization JSON instance (wire contract). */
internal val WebJsonKey = io.ktor.util.AttributeKey<Json>("WebJson")

/** The fixed refresh-cookie name (sc-133). */
internal const val REFRESH_COOKIE_NAME = "refresh_token"

/**
 * Decodes an optional JSON body using the application's configured [Json].
 * An empty body yields null (e.g. approve with no decision details); a
 * malformed body surfaces as a 400 via the status-pages whitelist.
 */
internal suspend inline fun <reified T> ApplicationCall.receiveOptionalJson(): T? {
    val text = receiveText()
    if (text.isBlank()) return null
    return application.attributes[WebJsonKey].decodeFromString<T>(text)
}

/** Decodes a required JSON body; an empty body is a 400 `invalid_input`. */
internal suspend inline fun <reified T> ApplicationCall.receiveJson(): T =
    receiveOptionalJson<T>() ?: throw InvalidInputException(ErrorMessages.REQUEST_BODY_NOT_READABLE)

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

/** A multipart file part held in memory (the payloads here are small images). */
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

/** Receives a multipart body once, disposing every part. */
internal suspend fun ApplicationCall.receiveForm(): MultipartForm {
    val fields = mutableMapOf<String, String>()
    val files = mutableMapOf<String, UploadedFile>()
    receiveMultipart().forEachPart { part ->
        val name = part.name
        if (name != null) {
            when (part) {
                is PartData.FormItem -> fields[name] = part.value
                is PartData.FileItem -> files[name] = UploadedFile(
                    bytes = part.provider().toByteArray(),
                    contentType = part.contentType?.toString() ?: "application/octet-stream",
                )
                else -> Unit
            }
        }
        part.release()
    }
    return MultipartForm(fields = fields, files = files)
}
