package com.tahirslist.certifiers.core

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Ktor CIO transport. Redirects are followed (directory hosts redirect to
 * canonical URLs); status handling stays explicit in [CertifierHttp].
 */
class KtorHttpTransport(
    requestTimeout: Duration = 60.seconds,
    private val client: HttpClient = HttpClient(CIO) {
        expectSuccess = false
        followRedirects = true
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
            body = response.bodyAsText(),
        )
    }
}
