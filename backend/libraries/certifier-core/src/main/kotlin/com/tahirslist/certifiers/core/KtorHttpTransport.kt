package com.tahirslist.certifiers.core

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readAvailable
import java.io.ByteArrayOutputStream
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Ktor CIO transport. Redirects are **not** followed here: [CertifierHttp]
 * follows them manually so every hop is checked against the host allow-list,
 * robots.txt, throttle, budget and response-size cap.
 *
 * The body is read through a capped buffer so a hostile or broken directory
 * cannot exhaust memory even before [CertifierHttp] sees it.
 */
class KtorHttpTransport(
    requestTimeout: Duration = 60.seconds,
    private val maxResponseBytes: Int = 16 * 1024 * 1024,
    private val client: HttpClient = HttpClient(CIO) {
        expectSuccess = false
        followRedirects = false
        install(HttpTimeout) {
            requestTimeoutMillis = requestTimeout.inWholeMilliseconds
            connectTimeoutMillis = requestTimeout.inWholeMilliseconds
        }
    },
) : HttpTransport {

    override suspend fun execute(request: HttpRequest): HttpResponse {
        val response = when (request.method.uppercase()) {
            "GET" -> client.get(request.url) {
                request.headers.forEach { (name, value) -> header(name, value) }
            }

            "POST" -> client.post(request.url) {
                request.headers.forEach { (name, value) -> header(name, value) }
                request.body?.let { setBody(it) }
            }

            else -> error("Unsupported method ${request.method} for ${request.url}")
        }
        return HttpResponse(
            status = response.status.value,
            headers = response.headers.entries().associate { it.key to it.value.joinToString(", ") },
            body = readCapped(request.url, response),
        )
    }

    private suspend fun readCapped(url: String, response: io.ktor.client.statement.HttpResponse): String {
        val declared = response.headers["Content-Length"]?.toLongOrNull()
        if (declared != null && declared > maxResponseBytes) {
            throw SourceTooLargeException(url, maxResponseBytes)
        }

        val channel = response.bodyAsChannel()
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val read = channel.readAvailable(buffer, 0, buffer.size)
            if (read <= 0) break
            if (out.size() + read > maxResponseBytes) {
                throw SourceTooLargeException(url, maxResponseBytes)
            }
            out.write(buffer, 0, read)
        }
        return out.toString(Charsets.UTF_8.name())
    }
}
