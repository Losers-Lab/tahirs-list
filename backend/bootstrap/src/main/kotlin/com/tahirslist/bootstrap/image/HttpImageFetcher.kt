package com.tahirslist.bootstrap.image

import com.tahirslist.application.image.ImageFetchException
import com.tahirslist.application.image.ImageFetcher
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Descriptive User-Agent for outbound image fetches. Wikimedia (and some CDNs)
 * reject clients whose UA is a bare default, returning HTTP 403 for otherwise
 * valid freely-licensed Commons images.
 */
const val DEFAULT_IMAGE_USER_AGENT =
    "TahirsList/0.1 (Tahirs List seed-photo ingest; https://github.com/Losers-Lab/tahirs-list)"

/**
 * Ktor-client image downloader (replaces the JDK HttpClient): 2xx -> bytes,
 * anything else -> [ImageFetchException]. Server-side only.
 */
class HttpImageFetcher(
    timeout: Duration = 20.seconds,
    private val userAgent: String = DEFAULT_IMAGE_USER_AGENT,
) : ImageFetcher {

    private val client = HttpClient(CIO) {
        followRedirects = true
        install(HttpTimeout) {
            requestTimeoutMillis = timeout.inWholeMilliseconds
            connectTimeoutMillis = timeout.inWholeMilliseconds
        }
    }

    override suspend fun fetch(url: String): ByteArray {
        return try {
            val response = client.get(url) {
                header(HttpHeaders.Accept, "image/*")
                header(HttpHeaders.UserAgent, userAgent)
            }
            if (!response.status.isSuccess()) {
                throw ImageFetchException("non-2xx $url: HTTP ${response.status.value}")
            }
            response.bodyAsBytes()
        } catch (e: CancellationException) {
            throw e
        } catch (e: ImageFetchException) {
            throw e
        } catch (e: Exception) {
            throw ImageFetchException("fetch failed for $url: ${e.message}", e)
        }
    }
}
