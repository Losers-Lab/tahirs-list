package com.tahirslist.web

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.server.application.call
import io.ktor.server.plugins.PayloadTooLargeException
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeFully
import io.ktor.utils.io.writeStringUtf8
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import kotlin.time.Duration.Companion.minutes

/**
 * Request-size limits (security review finding #1) and the stored-media
 * allowlist (finding #2), pinned at the adapter boundary.
 *
 * The handlers registered here are deliberately thin: the subject is the shared
 * reader ([receiveJson] / [receiveForm] / [respondStoredBytes]), not a route.
 * They are mounted on public paths so the deny-by-default guard does not answer
 * 401 before the body is ever read.
 */
class RequestLimitsTest : FunSpec({

    test("a JSON body just over the cap is refused with 413 payload_too_large") {
        testApplication {
            application {
                configureWebPlugins(testJson(), testJwtConfig(), testRateLimitConfig())
                routing {
                    post("/v1/auth/signup") {
                        call.receiveJson<Echo>()
                        call.respondText("ok")
                    }
                }
            }

            val response = client.post("/v1/auth/signup") {
                headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(oversizedJson())
            }

            response.status shouldBe HttpStatusCode.PayloadTooLarge
            response.bodyAsText() shouldContain "\"code\":\"payload_too_large\""
        }
    }

    test("a chunked JSON body (no Content-Length) is bounded by the same streaming cap") {
        testApplication {
            application {
                configureWebPlugins(testJson(), testJwtConfig(), testRateLimitConfig())
                routing {
                    post("/v1/auth/signup") {
                        call.receiveJson<Echo>()
                        call.respondText("ok")
                    }
                }
            }

            val response = client.post("/v1/auth/signup") {
                // No contentLength: the client streams the body, so only the
                // read-side bound can stop it.
                setBody(object : OutgoingContent.WriteChannelContent() {
                    override val contentType: ContentType = ContentType.Application.Json

                    override suspend fun writeTo(channel: ByteWriteChannel) {
                        channel.writeStringUtf8(oversizedJson())
                    }
                })
            }

            response.status shouldBe HttpStatusCode.PayloadTooLarge
            response.bodyAsText() shouldContain "\"code\":\"payload_too_large\""
        }
    }

    test("a JSON body under the cap is read normally") {
        testApplication {
            application {
                configureWebPlugins(testJson(), testJwtConfig(), testRateLimitConfig())
                routing {
                    post("/v1/auth/signup") {
                        call.receiveJson<Echo>()
                        call.respondText("ok")
                    }
                }
            }

            val response = client.post("/v1/auth/signup") {
                headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody("{\"value\":\"" + "a".repeat(1_000) + "\"}")
            }

            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldBe "ok"
        }
    }

    test("an oversized multipart file part is refused with 413 payload_too_large") {
        testApplication {
            application {
                configureWebPlugins(testJson(), testJwtConfig(), testRateLimitConfig())
                routing {
                    post("/v1/auth/logout") {
                        val form = call.receiveForm()
                        call.respondText(form.files["certImage"]?.bytes?.size?.toString() ?: "none")
                    }
                }
            }

            val oversized = ByteArray((MAX_UPLOAD_BYTES + 1).toInt())
            val response = client.post("/v1/auth/logout") {
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            append(
                                "certImage",
                                oversized,
                                Headers.build {
                                    append(HttpHeaders.ContentType, "image/jpeg")
                                    append(HttpHeaders.ContentDisposition, "filename=\"cert.jpg\"")
                                },
                            )
                        },
                    ),
                )
            }

            response.status shouldBe HttpStatusCode.PayloadTooLarge
            response.bodyAsText() shouldContain "\"code\":\"payload_too_large\""
        }
    }

    test("a multipart file part under the cap is read whole") {
        testApplication {
            application {
                configureWebPlugins(testJson(), testJwtConfig(), testRateLimitConfig())
                routing {
                    post("/v1/auth/logout") {
                        val form = call.receiveForm()
                        call.respondText(form.files["certImage"]?.bytes?.size?.toString() ?: "none")
                    }
                }
            }

            val response = client.post("/v1/auth/logout") {
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            append(
                                "certImage",
                                ByteArray(4),
                                Headers.build {
                                    append(HttpHeaders.ContentType, "image/jpeg")
                                    append(HttpHeaders.ContentDisposition, "filename=\"cert.jpg\"")
                                },
                            )
                        },
                    ),
                )
            }

            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldBe "4"
        }
    }

    test("a multipart text field over the field cap is refused with 413, not a 500") {
        testApplication {
            application {
                configureWebPlugins(testJson(), testJwtConfig(), testRateLimitConfig())
                routing {
                    post("/v1/auth/logout") {
                        call.receiveForm()
                        call.respondText("ok")
                    }
                }
            }

            // The field cap is enforced by the reader itself (Ktor's
            // `formFieldLimit` is a per-part cap, not a field-only one), so this
            // must come back as the 413 envelope rather than a 500.
            val response = client.post("/v1/auth/logout") {
                setBody(
                    MultiPartFormDataContent(
                        formData { append("proof", "p".repeat((MAX_FORM_FIELD_BYTES + 1).toInt())) },
                    ),
                )
            }

            response.status shouldBe HttpStatusCode.PayloadTooLarge
            response.bodyAsText() shouldContain "\"code\":\"payload_too_large\""
        }
    }

    test("an allowlisted image type is served inline as that image type") {
        testApplication {
            application {
                configureWebPlugins(testJson(), testJwtConfig(), testRateLimitConfig())
                routing { storedMediaRoute() }
            }

            val response = client.get("/v1/listings/stored?type=image%2Fpng")

            response.status shouldBe HttpStatusCode.OK
            response.headers[HttpHeaders.ContentType] shouldBe "image/png"
            response.headers[HttpHeaders.ContentDisposition] shouldBe "inline"
        }
    }

    test("a client-chosen text/html type is neutralised to octet-stream + attachment") {
        testApplication {
            application {
                configureWebPlugins(testJson(), testJwtConfig(), testRateLimitConfig())
                routing { storedMediaRoute() }
            }

            val response = client.get("/v1/listings/stored?type=text%2Fhtml")

            response.status shouldBe HttpStatusCode.OK
            response.headers[HttpHeaders.ContentType] shouldBe "application/octet-stream"
            response.headers[HttpHeaders.ContentDisposition] shouldBe "attachment"
        }
    }

    test("an image type outside the allowlist (svg) is also served as an attachment") {
        testApplication {
            application {
                configureWebPlugins(testJson(), testJwtConfig(), testRateLimitConfig())
                routing { storedMediaRoute() }
            }

            val response = client.get("/v1/listings/stored?type=image%2Fsvg%2Bxml")

            response.headers[HttpHeaders.ContentType] shouldBe "application/octet-stream"
            response.headers[HttpHeaders.ContentDisposition] shouldBe "attachment"
        }
    }

    test("a multipart body whose declared Content-Length exceeds the budget is refused before parsing") {
        testApplication {
            application {
                configureWebPlugins(testJson(), testJwtConfig(), testRateLimitConfig())
                routing {
                    post("/v1/auth/logout") {
                        val form = call.receiveForm()
                        call.respondText(form.files.keys.sorted().joinToString(","))
                    }
                }
            }

            // Two individually-legal 5 MiB parts: the per-part cap passes, the
            // aggregate budget (8 MiB + 256 KiB) must not. This is the *declared
            // length* gate: the client sends Content-Length, so nothing is parsed.
            val fiveMiB = ByteArray(5 * 1024 * 1024)
            val response = client.post("/v1/auth/logout") {
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            listOf("image", "certImage").forEach { name ->
                                append(
                                    name,
                                    fiveMiB,
                                    Headers.build {
                                        append(HttpHeaders.ContentType, "image/jpeg")
                                        append(HttpHeaders.ContentDisposition, "filename=\"$name.jpg\"")
                                    },
                                )
                            }
                        },
                    ),
                )
            }

            response.status shouldBe HttpStatusCode.PayloadTooLarge
            response.bodyAsText() shouldContain "\"code\":\"payload_too_large\""
        }
    }

    test("a chunked multipart body (no Content-Length) is bounded by the running retained-bytes total") {
        testApplication {
            application {
                configureWebPlugins(testJson(), testJwtConfig(), testRateLimitConfig())
                routing {
                    post("/v1/auth/logout") {
                        val form = call.receiveForm()
                        call.respondText(form.files.keys.sorted().joinToString(","))
                    }
                }
            }

            // No Content-Length, so the declared-length precheck cannot fire: this
            // is the only shape that can multiply the per-part cap by the part
            // count, i.e. the attacker's path. It must still be refused.
            val fiveMiB = ByteArray(5 * 1024 * 1024)
            val response = client.post("/v1/auth/logout") {
                setBody(
                    chunkedMultipart(
                        parts = listOf("image" to fiveMiB, "certImage" to fiveMiB),
                    ),
                )
            }

            response.status shouldBe HttpStatusCode.PayloadTooLarge
            response.bodyAsText() shouldContain "\"code\":\"payload_too_large\""
        }
    }

    test("a chunked multipart part over the per-part cap is refused (scanner failure shape)") {
        testApplication {
            application {
                configureWebPlugins(testJson(), testJwtConfig(), testRateLimitConfig())
                routing {
                    post("/v1/auth/logout") {
                        call.receiveForm()
                        call.respondText("stored")
                    }
                }
            }

            // 8 MiB + 1 with no Content-Length: Ktor's multipart scanner throws the
            // `Limit of N bytes exceeded while searching for <boundary>` IOException,
            // which must map to 413 rather than falling through to a 500.
            val response = client.post("/v1/auth/logout") {
                setBody(
                    chunkedMultipart(
                        parts = listOf("certImage" to ByteArray(8 * 1024 * 1024 + 1)),
                    ),
                )
            }

            response.status shouldBe HttpStatusCode.PayloadTooLarge
            response.bodyAsText() shouldContain "\"code\":\"payload_too_large\""
        }
    }

    test("a multipart body with too many parts is refused") {
        testApplication {
            application {
                configureWebPlugins(testJson(), testJwtConfig(), testRateLimitConfig())
                routing {
                    post("/v1/auth/logout") {
                        val form = call.receiveForm()
                        call.respondText(form.fields.size.toString())
                    }
                }
            }

            val response = client.post("/v1/auth/logout") {
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            repeat(MAX_MULTIPART_PARTS + 1) { index -> append("field$index", "x") }
                        },
                    ),
                )
            }

            response.status shouldBe HttpStatusCode.PayloadTooLarge
        }
    }

    test("both shapes of Ktor's multipart limit failure are recognised, and plain IO errors are not") {
        // Declared-length shape.
        IOException("Multipart content length exceeds limit 5 > 4; limit is defined using 'formFieldLimit' argument")
            .isMultipartLimitExceeded() shouldBe true
        // Streaming scanner shape (file part): the one the runtime probe hit.
        IOException("Limit of 8388608 bytes exceeded while searching for \"\\r\\n------boundary\"")
            .isMultipartLimitExceeded() shouldBe true
        // Wrapped refusal with the typed exception suppressed.
        IOException("read failed").apply {
            addSuppressed(PayloadTooLargeException(MAX_UPLOAD_BYTES))
        }.isMultipartLimitExceeded() shouldBe true
        // A genuine transport failure must stay a 500, not become a 413.
        IOException("Connection reset by peer").isMultipartLimitExceeded() shouldBe false
    }
    test("a parameterised / mixed-case allowlisted type is still recognised") {
        testApplication {
            application {
                configureWebPlugins(testJson(), testJwtConfig(), testRateLimitConfig())
                routing { storedMediaRoute() }
            }

            val response = client.get("/v1/listings/stored?type=IMAGE%2FJPEG%3B%20charset%3Dbinary")

            response.headers[HttpHeaders.ContentType] shouldBe "image/jpeg"
            response.headers[HttpHeaders.ContentDisposition] shouldBe "inline"
        }
    }
})

/** A body big enough to cross [MAX_JSON_BODY_BYTES] by a comfortable margin. */
private fun oversizedJson(): String =
    "{\"value\":\"" + "a".repeat(MAX_JSON_BODY_BYTES.toInt()) + "\"}"

/**
 * A multipart body written as a streamed [OutgoingContent.WriteChannelContent],
 * so the client sends it **chunked** with no `Content-Length`. That is the only
 * shape that reaches [receiveForm]'s running retained-bytes total instead of the
 * declared-length pre-check.
 */
private fun chunkedMultipart(parts: List<Pair<String, ByteArray>>): OutgoingContent {
    val boundary = "----TahirsListChunked${System.nanoTime()}"
    return object : OutgoingContent.WriteChannelContent() {
        override val contentType: ContentType =
            ContentType.MultiPart.FormData.withParameter("boundary", boundary)

        override suspend fun writeTo(channel: ByteWriteChannel) {
            parts.forEach { (name, bytes) ->
                channel.writeFully("--$boundary\r\n".toByteArray())
                channel.writeFully(
                    "Content-Disposition: form-data; name=\"$name\"; filename=\"$name.jpg\"\r\n".toByteArray(),
                )
                channel.writeFully("Content-Type: image/jpeg\r\n\r\n".toByteArray())
                channel.writeFully(bytes)
                channel.writeFully("\r\n".toByteArray())
            }
            channel.writeFully("--$boundary--\r\n".toByteArray())
        }
    }
}

/** Echoes the stored type straight back through the serving helper under test. */
private fun io.ktor.server.routing.Route.storedMediaRoute() {
    get("/v1/listings/stored") {
        val type = call.request.queryParameters["type"] ?: "image/png"
        call.respondStoredBytes(byteArrayOf(1, 2, 3), type)
    }
}

@Serializable
private data class Echo(val value: String)

private fun testJson(): Json = Json {
    encodeDefaults = true
    explicitNulls = true
}

private val testPublicKey: RSAPublicKey by lazy {
    val generator = KeyPairGenerator.getInstance("RSA")
    generator.initialize(2048)
    generator.generateKeyPair().public as RSAPublicKey
}

private fun testJwtConfig(): JwtConfig = JwtConfig(issuer = "tahirs-list", publicKey = testPublicKey)

private fun testRateLimitConfig(): RateLimitConfig = RateLimitConfig(
    capacity = 20,
    refillTokens = 20,
    refillWindow = 1.minutes,
    trustProxy = false,
    maxKeys = 1_000,
)
