package com.tahirslist.verification.ai

import com.tahirslist.application.verification.VerificationProviderException
import com.tahirslist.domain.verification.CertificationImage
import com.tahirslist.domain.verification.ModelJudgment
import com.tahirslist.domain.verification.ModelVerdict
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import java.io.IOException
import java.util.Base64
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put

/**
 * The default [VisionModelClient]: JSON-over-HTTP to a hosted multimodal model,
 * using a **Ktor client (CIO)** with kotlinx.serialization for the JSON body /
 * response. [config] supplies the endpoint, model name and optional API key
 * (resolved from a secret source at bootstrap, never baked here).
 *
 * The request shape is unchanged from the previous transport: a JSON object with
 * `model`, `prompt` and `image_base64`; the response is read for
 * `verdict` / `confidence` / `summary`.
 *
 * Conservative failure handling:
 *  - non-2xx status -> [VerificationProviderException] (no usable response).
 *  - IO / timeout -> [VerificationProviderException] (provider outage).
 *  - cancellation is rethrown (not a provider fault).
 *  - a 2xx body with no determinable verdict (not JSON, missing/unknown verdict)
 *    -> [ModelVerdict.INCONCLUSIVE] — never invent a verdict; the caller's
 *    conservative policy defers that to a human.
 */
class RestVisionModelClient(
    private val config: HostedVisionConfig,
    httpClient: HttpClient? = null,
) : VisionModelClient {

    /**
     * Owned client (when not injected). Connect + request timeouts are bounded by
     * [HostedVisionConfig.timeoutMillis], as before; status handling is explicit
     * so a non-2xx response is mapped to [VerificationProviderException].
     */
    private val client: HttpClient = httpClient ?: HttpClient(CIO) {
        expectSuccess = false
        // Match the previous JDK client's default: redirects are not followed
        // transparently (a 3xx is surfaced as a provider response, not success).
        followRedirects = false
        install(HttpTimeout) {
            requestTimeoutMillis = config.timeoutMillis
            connectTimeoutMillis = config.timeoutMillis
        }
    }

    override suspend fun analyze(prompt: String, image: CertificationImage): ModelJudgment {
        val body = requestBody(prompt, image)
        val apiKey = config.apiKey

        val (status, responseBody) = try {
            val response = client.post(config.endpoint) {
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
                if (apiKey != null) header(HttpHeaders.Authorization, "Bearer $apiKey")
                setBody(body)
            }
            response.status to response.bodyAsText()
        } catch (e: IOException) {
            throw VerificationProviderException("Vision provider unreachable or timed out: ${e.message}", e)
        } catch (e: CancellationException) {
            // Caller cancelled: preserve structured concurrency, do not mask it
            // as a provider outage.
            throw e
        }

        if (status.value !in 200..299) {
            throw VerificationProviderException("Vision provider returned HTTP ${status.value}")
        }
        return parseJudgment(responseBody)
    }

    private fun requestBody(prompt: String, image: CertificationImage): String = buildJsonObject {
        put("model", config.modelName)
        put("prompt", prompt)
        put("image_base64", Base64.getEncoder().encodeToString(image.bytes))
    }.toString()

    private fun parseJudgment(body: String): ModelJudgment {
        val root = try {
            Json.parseToJsonElement(body)
        } catch (e: SerializationException) {
            return ModelJudgment(ModelVerdict.INCONCLUSIVE, 0.0, "unparseable provider response")
        }
        // A valid JSON value that is not an object has no verdict members; it is
        // handled as an empty response (conservative INCONCLUSIVE), not an error.
        val node = root as? JsonObject

        val verdictName = text(node, "verdict").orEmpty().trim()
        val verdict = runCatching { ModelVerdict.valueOf(verdictName) }
            .getOrElse { ModelVerdict.INCONCLUSIVE }
        val confidence = (node?.get("confidence") as? JsonPrimitive)?.doubleOrNull ?: 0.0
        val summary = text(node, "summary")?.takeIf { it.isNotBlank() }
        return ModelJudgment(verdict, confidence, summary)
    }

    private fun text(node: JsonObject?, field: String): String? =
        (node?.get(field) as? JsonPrimitive)?.contentOrNull
}
