package com.tahirslist.certifiers.core

/** A transport-neutral HTTP request, so tests can replay recordings. */
data class HttpRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
)

/** A transport-neutral HTTP response. Header lookups are case-insensitive. */
data class HttpResponse(
    val status: Int,
    val headers: Map<String, String> = emptyMap(),
    val body: String = "",
) {
    fun header(name: String): String? =
        headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
}

/**
 * The only place a certifier library touches the network. Production uses
 * [KtorHttpTransport]; tests inject a recording/canned implementation.
 */
fun interface HttpTransport {
    suspend fun execute(request: HttpRequest): HttpResponse
}
