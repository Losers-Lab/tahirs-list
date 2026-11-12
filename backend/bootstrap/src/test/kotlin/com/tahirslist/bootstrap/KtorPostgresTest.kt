package com.tahirslist.bootstrap

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.tahirslist.bootstrap.config.AppConfig
import com.tahirslist.bootstrap.config.CookieSettings
import com.tahirslist.bootstrap.config.DatabaseSettings
import com.tahirslist.bootstrap.config.JwtSettings
import com.tahirslist.bootstrap.config.McpSettings
import com.tahirslist.bootstrap.config.RateLimitSettings
import com.tahirslist.bootstrap.config.SeedIngestSettings
import com.tahirslist.application.listing.SearchSettings
import com.tahirslist.persistence.db.DatabaseFactory
import com.tahirslist.persistence.db.Migrations
import com.zaxxer.hikari.HikariDataSource
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.v1.jdbc.Database
import org.koin.core.module.Module
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.security.KeyFactory
import java.security.interfaces.RSAPrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Instant
import java.util.Base64
import java.util.Date

/**
 * One shared PostGIS container for the whole test JVM (the Ktor successor of the
 * old Spring `PostgresBootTest` companion object): started lazily on the first
 * spec, migrated by Flyway exactly once, and reused by every boot test so the
 * 30-listing seed dataset and cross-spec fixtures stay stable.
 *
 * The container's credentials are handed to each spec's [AppConfig], so the
 * application under test and the test's own assertions see the same database.
 */
object SharedPostgres {

    val container: PostgreSQLContainer<*> by lazy {
        PostgreSQLContainer(
            // postgis/postgis extends the official postgres image; declare it
            // compatible so Testcontainers accepts it for PostgreSQLContainer.
            DockerImageName.parse("postgis/postgis:17-3.4").asCompatibleSubstituteFor("postgres"),
        )
            .withDatabaseName("test")
            .withUsername("test")
            .withPassword("test")
            .apply { start() }
    }

    private var migrated = false

    /**
     * Apply the Flyway migrations once per JVM. Every booted application also
     * runs them, but Flyway's history table makes the repeat a no-op; doing it
     * here first means the very first spec never races its own assertions.
     */
    @Synchronized
    fun migrateOnce() {
        if (migrated) return
        Migrations.migrate(container.jdbcUrl, container.username, container.password)
        migrated = true
    }

    /** Pool used by the test's own SQL helpers (separate from each app's pool). */
    val dataSource: HikariDataSource by lazy {
        migrateOnce()
        DatabaseFactory.dataSource(
            jdbcUrl = container.jdbcUrl,
            user = container.username,
            password = container.password,
            maxPoolSize = 4,
        )
    }

    /** Exposed handle for the test-side fixtures and assertions. */
    val database: Database by lazy { DatabaseFactory.connect(dataSource) }
}

/**
 * The fixed test RSA key pair (`test-jwt-rsa-private.pem`, base64 PKCS#8 DER,
 * read as a plain classloader resource — `ClassPathResource` was a Spring type).
 *
 * The application boots with this key so the tests can mint adversarial / valid
 * RS256 tokens with the matching private half.
 */
object TestJwt {

    val privateKeyBase64: String by lazy {
        TestJwt::class.java.classLoader.getResourceAsStream("test-jwt-rsa-private.pem")
            ?.bufferedReader()
            ?.use { it.readText() }
            ?.trim()
            ?: error("test-jwt-rsa-private.pem is missing from the test resources")
    }

    fun privateKey(): RSAPrivateKey {
        val der = Base64.getDecoder().decode(privateKeyBase64)
        return KeyFactory.getInstance("RSA")
            .generatePrivate(PKCS8EncodedKeySpec(der)) as RSAPrivateKey
    }

    /** Mint and RS256-sign a token with the same key the application boots with. */
    fun mint(
        role: String? = "USER",
        sub: String,
        issuer: String = "tahirs-list",
        expSecondsAhead: Long = 3600,
    ): String {
        val builder = JWTClaimsSet.Builder()
            .subject(sub)
            .issuer(issuer)
            .issueTime(Date.from(Instant.now()))
            .expirationTime(Date.from(Instant.now().plusSeconds(expSecondsAhead)))
        if (role != null) builder.claim("role", role)

        val signed = SignedJWT(
            JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT).build(),
            builder.build(),
        )
        signed.sign(RSASSASigner(privateKey()))
        return signed.serialize()
    }

    /** Corrupts a well-formed JWT's signature so signature verification must fail. */
    fun tamper(jwt: String): String {
        val parts = jwt.split(".")
        require(parts.size == 3) { "expected a compact JWT" }
        val signature = parts[2].toCharArray()
        signature[0] = if (signature[0] == 'A') 'B' else 'A'
        return "${parts[0]}.${parts[1]}.${String(signature)}"
    }
}

/** The rate-limit budget every spec boots with unless it opts into a tiny one. */
val DEFAULT_RATE_LIMIT = RateLimitSettings(
    capacity = 10_000,
    refillTokens = 10_000,
    refillWindowSeconds = 60,
    trustProxy = false,
    maxKeys = 100_000,
)

/**
 * MCP settings every spec boots with: enabled, generous budget (so a spec's own
 * calls are never throttled), predictable public base URLs for link assertions.
 */
val DEFAULT_MCP = McpSettings(
    enabled = true,
    publicWebBaseUrl = "http://localhost:5173",
    publicApiBaseUrl = "http://localhost:8080",
    allowedOrigins = emptySet(),
    // Unroutable on purpose: no bootstrap spec may reach the public geocoder.
    geocoderBaseUrl = "http://127.0.0.1:9/",
    capacity = 10_000,
    maxKeys = 100_000,
    refillTokens = 10_000,
    refillWindowSeconds = 60,
)

/** The password every end-to-end spec signs accounts up with. */
const val TEST_PASSWORD = "s3cr3t-password"

/**
 * The Ktor end-to-end harness: one real embedded CIO server per spec, booted
 * through the production [com.tahirslist.bootstrap.module] composition root, on
 * a random port, talking to the shared PostGIS container.
 *
 * Lifecycle mirrors the old Spring harness: the shared container is started
 * lazily on the first spec; each spec starts its own server + HTTP client in
 * `beforeSpec` and stops both in `afterSpec`. Database fixtures/assertions go
 * through [dbRows]/[dbUpdate] against the same container.
 *
 * [rateLimit] is configurable because the rate-limit spec needs a deliberately
 * tiny budget; everything else boots with an effectively unlimited one so that
 * a spec's own login/signup volume is never throttled (the limiter's real
 * defaults are pinned by the web-api unit tests and by the dedicated spec).
 */
abstract class KtorPostgresTest(
    private val rateLimit: RateLimitSettings = DEFAULT_RATE_LIMIT,
    private val mcpSettings: McpSettings = DEFAULT_MCP,
    /** Search tuning (SC-199): lets a spec boot with a non-default precision/window. */
    private val searchSettings: SearchSettings = SearchSettings(),
    /** Extra Koin definitions appended after the production graph. */
    private val extraModules: List<Module> = emptyList(),
) : FunSpec() {

    /** The exact configuration the application under test boots with. */
    lateinit var appConfig: AppConfig
        private set

    /** Base URL of the running application, e.g. `http://127.0.0.1:53211`. */
    lateinit var baseUrl: String
        private set

    private lateinit var client: HttpClient
    private lateinit var server: EmbeddedServer<*, *>

    init {
        beforeSpec {
            // Start + migrate the shared container before the app boots.
            SharedPostgres.migrateOnce()
            appConfig = testConfig(rateLimit, searchSettings)
            server = embeddedServer(ServerCIO, host = "127.0.0.1", port = 0) {
                module(appConfig, extraModules)
            }
            server.start(wait = false)
            val port = server.engine.resolvedConnectors().first().port
            baseUrl = "http://127.0.0.1:$port"
            client = HttpClient(ClientCIO) { expectSuccess = false }
        }

        afterSpec {
            if (::client.isInitialized) client.close()
            if (::server.isInitialized) server.stop(0, 2_000)
        }
    }

    /* ------------------------------------------------------------------ HTTP */

    /**
     * Sends a request and snapshots the response (status, headers, body bytes),
     * so a test can read both the JSON and the raw/byte payloads.
     */
    suspend fun call(
        method: HttpMethod,
        path: String,
        token: String? = null,
        json: Map<String, Any?>? = null,
        jsonText: String? = null,
        multipart: MultipartBody? = null,
        cookies: Map<String, String> = emptyMap(),
        extraHeaders: Map<String, String> = emptyMap(),
    ): ApiResponse {
        val response = client.request(baseUrl + path) {
            this.method = method
            if (token != null) headers.append(HttpHeaders.Authorization, "Bearer $token")
            if (cookies.isNotEmpty()) {
                headers.append(
                    HttpHeaders.Cookie,
                    cookies.entries.joinToString("; ") { "${it.key}=${it.value}" },
                )
            }
            extraHeaders.forEach { (name, value) -> headers.append(name, value) }
            when {
                jsonText != null -> {
                    headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    setBody(jsonText)
                }
                json != null -> {
                    headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    setBody(jsonString(json))
                }
                multipart != null -> setBody(multipart.toContent())
            }
        }
        return ApiResponse(
            status = response.status.value,
            headers = response.headers,
            bytes = response.bodyAsBytes(),
        )
    }

    suspend fun get(
        path: String,
        token: String? = null,
        cookies: Map<String, String> = emptyMap(),
        extraHeaders: Map<String, String> = emptyMap(),
    ): ApiResponse = call(HttpMethod.Get, path, token = token, cookies = cookies, extraHeaders = extraHeaders)

    suspend fun post(
        path: String,
        token: String? = null,
        json: Map<String, Any?>? = null,
        jsonText: String? = null,
        multipart: MultipartBody? = null,
        cookies: Map<String, String> = emptyMap(),
        extraHeaders: Map<String, String> = emptyMap(),
    ): ApiResponse = call(
        HttpMethod.Post, path, token = token, json = json, jsonText = jsonText,
        multipart = multipart, cookies = cookies, extraHeaders = extraHeaders,
    )

    suspend fun patch(
        path: String,
        token: String? = null,
        json: Map<String, Any?>? = null,
        jsonText: String? = null,
    ): ApiResponse = call(HttpMethod.Patch, path, token = token, json = json, jsonText = jsonText)

    suspend fun put(
        path: String,
        token: String? = null,
        json: Map<String, Any?>? = null,
        multipart: MultipartBody? = null,
    ): ApiResponse = call(HttpMethod.Put, path, token = token, json = json, multipart = multipart)

    suspend fun delete(path: String, token: String? = null): ApiResponse =
        call(HttpMethod.Delete, path, token = token)

    /** Builds a multipart body with text fields and/or file parts. */
    fun multipart(block: MultipartBody.() -> Unit): MultipartBody = MultipartBody().apply(block)

    /* ------------------------------------------------------- auth shortcuts */

    /** `POST /v1/auth/signup` with the standard test credentials. */
    suspend fun signup(email: String, password: String = TEST_PASSWORD): ApiResponse =
        post("/v1/auth/signup", json = linkedMapOf("email" to email, "password" to password))

    /** `POST /v1/auth/login` with the standard test credentials. */
    suspend fun login(email: String, password: String = TEST_PASSWORD): ApiResponse =
        post("/v1/auth/login", json = linkedMapOf("email" to email, "password" to password))

    /** Signs a fresh account up, logs it in, and returns its identity + refresh cookie. */
    suspend fun signupAndLogin(email: String, password: String = TEST_PASSWORD): TestSession {
        val signupResponse = signup(email, password)
        signupResponse.status shouldBe 201
        val loginResponse = login(email, password)
        loginResponse.status shouldBe 200
        return TestSession(
            accountId = loginResponse.json()["accountId"].asString(),
            accessToken = loginResponse.json()["accessToken"].asString(),
            refreshCookie = loginResponse.refreshCookie(),
        )
    }

    /** The Add Listing body the write-surface specs share. */
    fun listingBody(
        name: String = "Halal Grill",
        address: String = "123 Main St",
        lat: Double = 40.7128,
        lng: Double = -74.0060,
        cuisine: String = "mediterranean",
        isHandCut: Boolean? = true,
        isDelivery: Boolean? = true,
        overrides: Map<String, Any?> = emptyMap(),
    ): Map<String, Any?> = linkedMapOf<String, Any?>(
        "name" to name,
        "address" to address,
        "lat" to lat,
        "lng" to lng,
        "cuisine" to cuisine,
        "isHandCut" to isHandCut,
        "isDelivery" to isDelivery,
    ) + overrides

    /** `POST /v1/listings` and returns the created listing id. */
    suspend fun createListing(
        bearer: String,
        ownerId: String,
        body: Map<String, Any?> = listingBody(),
    ): String {
        val response = post("/v1/listings", token = bearer, json = body)
        response.status shouldBe 201
        response.json()["ownerId"].asString() shouldBe ownerId
        return response.json()["id"].asString()
    }

    private fun testConfig(
        rateLimit: RateLimitSettings,
        searchSettings: SearchSettings = SearchSettings(),
    ): AppConfig = AppConfig(
        host = "127.0.0.1",
        port = 0,
        profile = "test",
        database = DatabaseSettings(
            jdbcUrl = SharedPostgres.container.jdbcUrl,
            user = SharedPostgres.container.username,
            password = SharedPostgres.container.password,
            // Each spec owns one pool; kept small so the whole suite stays
            // comfortably below the container's connection limit.
            maxPoolSize = 4,
        ),
        jwt = JwtSettings(
            issuer = "tahirs-list",
            accessTtlSeconds = 900,
            privateKeyBase64 = TestJwt.privateKeyBase64,
        ),
        rateLimit = rateLimit,
        cookie = CookieSettings(secure = true),
        // No object store and no hosted verifier: the in-memory image ports and
        // the safe DeferToHumanProvider are wired (test boot never hits a network).
        storage = null,
        verification = null,
        seedIngest = SeedIngestSettings(enabled = false),
        mcp = mcpSettings,
        search = searchSettings,
    )
}

/** A signed-up + logged-in account as the HTTP surface sees it. */
data class TestSession(
    val accountId: String,
    val accessToken: String,
    val refreshCookie: RefreshCookie,
)

/** A snapshot of one HTTP response. */
class ApiResponse(
    val status: Int,
    val headers: Headers,
    val bytes: ByteArray,
) {
    val body: String get() = bytes.toString(Charsets.UTF_8)

    fun json(): JsonObject = Json.parseToJsonElement(body).jsonObject

    fun jsonArray(): JsonArray = Json.parseToJsonElement(body).jsonArray

    /** The parsed body, or null when the body is empty / not JSON. */
    fun jsonOrNull(): JsonElement? =
        body.takeIf { it.isNotBlank() }?.let { runCatching { Json.parseToJsonElement(it) }.getOrNull() }

    /** The `code` of a JSON error envelope, or null when the body carries none. */
    fun code(): String? =
        (jsonOrNull() as? JsonObject)?.get("code")?.jsonPrimitive?.content

    fun message(): String? = (jsonOrNull() as? JsonObject)?.get("message").stringOrNull()

    fun header(name: String): String? = headers[name]

    /** The `refresh_token` Set-Cookie header, if this response set one. */
    fun refreshCookieHeader(): String? =
        headers.getAll(HttpHeaders.SetCookie)?.firstOrNull { it.startsWith("refresh_token=") }

    fun refreshCookie(): RefreshCookie = parseRefreshCookie(refreshCookieHeader())
}

/** The refresh-token cookie as the wire contract describes it (sc-133). */
data class RefreshCookie(
    val value: String,
    val httpOnly: Boolean,
    val secure: Boolean,
    val sameSite: String,
    val path: String,
    val maxAgeSeconds: Long,
)

/** Parses one `Set-Cookie: refresh_token=...` header into its attributes. */
fun parseRefreshCookie(header: String?): RefreshCookie {
    val raw = requireNotNull(header) { "expected a refresh_token Set-Cookie header, but none was sent" }
    val parts = raw.split(";").map { it.trim() }
    val nameValue = parts.first()
    require(nameValue.startsWith("refresh_token=")) { "unexpected cookie: $raw" }
    return RefreshCookie(
        value = nameValue.substringAfter("refresh_token="),
        httpOnly = parts.any { it.equals("HttpOnly", ignoreCase = true) },
        secure = parts.any { it.equals("Secure", ignoreCase = true) },
        sameSite = parts.firstOrNull { it.startsWith("SameSite=") }?.substringAfter("SameSite=") ?: "",
        path = parts.firstOrNull { it.startsWith("Path=") }?.substringAfter("Path=") ?: "",
        maxAgeSeconds = parts.firstOrNull { it.startsWith("Max-Age=") }?.substringAfter("Max-Age=")?.toLong() ?: 0L,
    )
}

/** SHA-256 hex digest — the only form of a refresh token the server persists. */
fun sha256Hex(input: String): String =
    java.security.MessageDigest.getInstance("SHA-256")
        .digest(input.toByteArray())
        .joinToString("") { "%02x".format(it) }

/* ------------------------------------------------------------------ multipart */

/** One file part of a multipart request. */
class MultipartFile(
    val name: String,
    val bytes: ByteArray,
    val contentType: String,
    val filename: String,
)

/** Text fields + file parts for a `multipart/form-data` request. */
class MultipartBody {
    val fields: MutableList<Pair<String, String>> = mutableListOf()
    val files: MutableList<MultipartFile> = mutableListOf()

    fun field(name: String, value: String) {
        fields += name to value
    }

    fun file(name: String, bytes: ByteArray, contentType: String, filename: String = "$name.bin") {
        files += MultipartFile(name, bytes, contentType, filename)
    }

    internal fun toContent(): MultiPartFormDataContent = MultiPartFormDataContent(
        formData {
            fields.forEach { (name, value) -> append(name, value) }
            files.forEach { file ->
                append(
                    file.name,
                    file.bytes,
                    Headers.build {
                        append(HttpHeaders.ContentType, file.contentType)
                        append(HttpHeaders.ContentDisposition, "filename=\"${file.filename}\"")
                    },
                )
            }
        },
        boundary = "----TahirsListTestBoundary${System.nanoTime()}",
    )
}

/* ------------------------------------------------------------------ JSON */

/** Renders a Kotlin map as a JSON body (the wire contract is kotlinx.serialization). */
fun jsonString(values: Map<String, Any?>): String = jsonElementOf(values).toString()

/** Renders arbitrary test data (maps/lists/scalars) as a [JsonElement]. */
fun jsonElementOf(value: Any?): JsonElement = when (value) {
    null -> JsonNull
    is JsonElement -> value
    is String -> JsonPrimitive(value)
    is Boolean -> JsonPrimitive(value)
    is Int -> JsonPrimitive(value)
    is Long -> JsonPrimitive(value)
    is Double -> JsonPrimitive(value)
    is Float -> JsonPrimitive(value)
    is Number -> JsonPrimitive(value)
    is Map<*, *> -> JsonObject(value.entries.associate { it.key.toString() to jsonElementOf(it.value) })
    is Iterable<*> -> JsonArray(value.map { jsonElementOf(it) })
    else -> error("cannot encode ${value::class.qualifiedName} as JSON")
}

/** Body text of a JSON string field, failing loudly when absent. */
fun JsonObject.asString(key: String): String =
    (this[key] as? JsonPrimitive)?.content ?: error("expected a '$key' string in $this")

/** A JSON element as a string, failing loudly when it is not a primitive. */
fun JsonElement?.asString(): String =
    (this as? JsonPrimitive)?.content ?: error("expected a JSON string, got $this")

/** The content of a JSON primitive, or null for JSON null / a missing value. */
fun JsonElement?.stringOrNull(): String? = (this as? JsonPrimitive)?.contentOrNull
