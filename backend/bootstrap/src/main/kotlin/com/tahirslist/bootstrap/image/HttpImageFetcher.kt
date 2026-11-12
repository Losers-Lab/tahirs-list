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
import java.net.URI
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
 * Ktor-client image downloader: 2xx -> bytes, anything else -> [ImageFetchException].
 * Server-side only.
 *
 * Two guards the photo pass depends on:
 *
 *  - **https only, public hosts only.** The URL can originate in a third party's
 *    JSON (a Commons `thumburl`), so a plain-http or loopback/private target must
 *    be refused rather than fetched from inside our network. IP literals and
 *    resolved private ranges are rejected.
 *  - **A byte cap.** A hostile or broken response must not exhaust the process
 *    heap, so [MAX_BYTES] is enforced from `Content-Length` and again on the
 *    received body.
 *
 * Error messages never carry the query string: Places media URLs contain the API
 * key there, and the message is copied into the run report.
 */
class HttpImageFetcher(
    timeout: Duration = 20.seconds,
    private val userAgent: String = DEFAULT_IMAGE_USER_AGENT,
    private val maxBytes: Long = MAX_BYTES,
    /**
     * Checked before every request. Production uses [PublicHttpsOnly]; the
     * fixture-server tests pass a permissive policy because their loopback
     * `http://127.0.0.1` target is deliberately not a fetchable production URL.
     */
    private val urlPolicy: UrlPolicy = PublicHttpsOnly,
) : ImageFetcher {

    private val client = HttpClient(CIO) {
        followRedirects = true
        install(HttpTimeout) {
            requestTimeoutMillis = timeout.inWholeMilliseconds
            connectTimeoutMillis = timeout.inWholeMilliseconds
        }
    }

    override suspend fun fetch(url: String): ByteArray {
        val display = displayUrl(url)
        guardTarget(url, display)
        return try {
            val response = client.get(url) {
                header(HttpHeaders.Accept, "image/*")
                header(HttpHeaders.UserAgent, userAgent)
            }
            if (!response.status.isSuccess()) {
                throw ImageFetchException("non-2xx $display: HTTP ${response.status.value}")
            }
            val declared = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
            if (declared != null && declared > maxBytes) {
                throw ImageFetchException("$display is ${declared}B, over the ${maxBytes}B cap")
            }
            val bytes = response.bodyAsBytes()
            if (bytes.size > maxBytes) {
                throw ImageFetchException("$display is ${bytes.size}B, over the ${maxBytes}B cap")
            }
            bytes
        } catch (e: CancellationException) {
            throw e
        } catch (e: ImageFetchException) {
            throw e
        } catch (e: Exception) {
            throw ImageFetchException("fetch failed for $display: ${e.message}", e)
        }
    }

    /** The URL without its query — enough to identify the request, never the key. */
    private fun displayUrl(url: String): String = url.substringBefore('?')

    private fun guardTarget(url: String, display: String) {
        val uri = try {
            URI(url)
        } catch (e: Exception) {
            throw ImageFetchException("unparseable image url $display", e)
        }
        urlPolicy.check(uri)
    }

    companion object {
        /** 8 MiB — the same ceiling the owner-upload path accepts. */
        const val MAX_BYTES: Long = 8L * 1024 * 1024
    }
}
