package com.tahirslist.bootstrap.config

import com.tahirslist.application.listing.SearchSettings
import com.tahirslist.domain.restaurant.CoordinatePrecision
import com.tahirslist.verification.ai.HostedVisionConfig

/**
 * Twelve-factor application configuration, read from environment variables with
 * local-dev defaults. This replaces Spring's `application.yml` + property binding:
 * no framework, no reflection, fully unit-testable via the `env` lookup.
 */
data class AppConfig(
    val host: String = "0.0.0.0",
    val port: Int = 8080,
    val profile: String = "dev",
    val database: DatabaseSettings,
    val jwt: JwtSettings,
    val rateLimit: RateLimitSettings,
    val cookie: CookieSettings,
    val storage: StorageSettings?,
    val verification: HostedVisionConfig?,
    val seedIngest: SeedIngestSettings,
    val mcp: McpSettings = McpSettings(),
    val geocodeCache: GeocodeCacheSettings = GeocodeCacheSettings(),
    val search: SearchSettings = SearchSettings(),
) {
    val isProduction: Boolean get() = profile.split(",").map { it.trim() }.contains("prod")
}

data class DatabaseSettings(
    val jdbcUrl: String,
    val user: String,
    val password: String,
    val maxPoolSize: Int = 8,
)

data class JwtSettings(
    val issuer: String = "tahirs-list",
    val accessTtlSeconds: Long = 900,
    val privateKeyBase64: String = "",
)

data class RateLimitSettings(
    val capacity: Long = 20,
    val refillTokens: Long = 20,
    val refillWindowSeconds: Long = 60,
    val trustProxy: Boolean = false,
    val maxKeys: Int = 100_000,
)

data class CookieSettings(val secure: Boolean = true)

data class StorageSettings(
    val endpoint: String,
    val region: String,
    val accessKey: String,
    val secretKey: String,
    val bucket: String,
)

data class SeedIngestSettings(
    val enabled: Boolean = false,
    val manifest: String = "classpath:seed-photos.json",
)

/** Public MCP endpoint settings (SC-209). */
data class McpSettings(
    val enabled: Boolean = true,
    val publicWebBaseUrl: String = "http://localhost:5173",
    val publicApiBaseUrl: String = "http://localhost:8080",
    val allowedOrigins: Set<String> = emptySet(),
    val allowedHosts: Set<String> = emptySet(),
    val geocoderBaseUrl: String = "https://photon.komoot.io/api/",
    val capacity: Long = 60,
    val maxKeys: Int = 100_000,
    val refillTokens: Long = 60,
    val refillWindowSeconds: Long = 60,
)

/** Geocode cache lifetime (SC-198). `0` means a row never goes stale. */
data class GeocodeCacheSettings(val ttlDays: Int = 90)

/** Reads all settings once at startup; no dynamic re-read. */
fun loadConfig(env: (String) -> String? = System::getenv): AppConfig {
    val storageEndpoint = env("APP_S3_ENDPOINT")?.takeIf { it.isNotBlank() }
    val verificationEndpoint = env("APP_VERIFICATION_ENDPOINT")?.takeIf { it.isNotBlank() }
    return AppConfig(
        host = env("APP_HOST") ?: "0.0.0.0",
        port = env("APP_PORT")?.toIntOrNull() ?: 8080,
        profile = env("APP_PROFILE") ?: "dev",
        database = DatabaseSettings(
            jdbcUrl = env("APP_DB_URL") ?: "jdbc:postgresql://localhost:5432/halal",
            user = env("APP_DB_USER") ?: "halal",
            password = env("APP_DB_PASSWORD") ?: "halal",
            maxPoolSize = env("APP_DB_MAX_POOL_SIZE")?.toIntOrNull() ?: 8,
        ),
        jwt = JwtSettings(
            issuer = env("APP_JWT_ISSUER") ?: "tahirs-list",
            accessTtlSeconds = env("APP_JWT_ACCESS_TTL_SECONDS")?.toLongOrNull() ?: 900,
            privateKeyBase64 = env("APP_JWT_RSA_PRIVATE_KEY_BASE64") ?: "",
        ),
        rateLimit = RateLimitSettings(
            capacity = env("APP_RATELIMIT_CAPACITY")?.toLongOrNull() ?: 20,
            refillTokens = env("APP_RATELIMIT_REFILL_TOKENS")?.toLongOrNull() ?: 20,
            refillWindowSeconds = env("APP_RATELIMIT_REFILL_WINDOW_SECONDS")?.toLongOrNull() ?: 60,
            trustProxy = env("APP_RATELIMIT_TRUST_PROXY")?.toBooleanStrictOrNull() ?: false,
            maxKeys = env("APP_RATELIMIT_MAX_KEYS")?.toIntOrNull() ?: 100_000,
        ),
        cookie = CookieSettings(
            secure = env("APP_COOKIE_SECURE")?.toBooleanStrictOrNull() ?: true,
        ),
        storage = storageEndpoint?.let {
            StorageSettings(
                endpoint = it,
                region = env("APP_S3_REGION") ?: "us-east-1",
                accessKey = env("APP_S3_ACCESS_KEY") ?: "minioadmin",
                secretKey = env("APP_S3_SECRET_KEY") ?: "minioadmin",
                bucket = env("APP_S3_BUCKET") ?: "halal",
            )
        },
        verification = verificationEndpoint?.let {
            HostedVisionConfig(
                endpoint = it,
                modelName = env("APP_VERIFICATION_MODEL") ?: "gemini-2.5-flash",
                apiKey = env("APP_VERIFICATION_API_KEY"),
                timeoutMillis = env("APP_VERIFICATION_TIMEOUT_MILLIS")?.toLongOrNull() ?: 5_000,
            )
        },
        seedIngest = SeedIngestSettings(
            enabled = env("APP_SEED_INGEST_ENABLED")?.toBooleanStrictOrNull() ?: false,
            manifest = env("APP_SEED_INGEST_MANIFEST") ?: "classpath:seed-photos.json",
        ),
        mcp = McpSettings(
            enabled = env("APP_MCP_ENABLED")?.toBooleanStrictOrNull() ?: true,
            publicWebBaseUrl = env("APP_MCP_PUBLIC_WEB_BASE_URL") ?: "http://localhost:5173",
            publicApiBaseUrl = env("APP_MCP_PUBLIC_API_BASE_URL") ?: "http://localhost:8080",
            allowedOrigins = env("APP_MCP_ALLOWED_ORIGINS")
                ?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.toSet()
                ?: emptySet(),
            allowedHosts = env("APP_MCP_ALLOWED_HOSTS")
                ?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.toSet()
                ?: emptySet(),
            geocoderBaseUrl = env("APP_GEOCODER_BASE_URL") ?: "https://photon.komoot.io/api/",
            capacity = env("APP_MCP_RATELIMIT_CAPACITY")?.toLongOrNull() ?: 60,
            maxKeys = env("APP_MCP_RATELIMIT_MAX_KEYS")?.toIntOrNull() ?: 100_000,
            refillTokens = env("APP_MCP_RATELIMIT_REFILL_TOKENS")?.toLongOrNull() ?: 60,
            refillWindowSeconds = env("APP_MCP_RATELIMIT_WINDOW_SECONDS")?.toLongOrNull() ?: 60,
        ),
        geocodeCache = GeocodeCacheSettings(
            ttlDays = env("APP_GEOCODE_CACHE_TTL_DAYS")?.toIntOrNull() ?: 90,
        ),
        search = SearchSettings(
            centrePrecision = searchPrecision(env("APP_SEARCH_CENTRE_DECIMALS")),
            cacheMaxAgeSeconds = seconds(env("APP_SEARCH_CACHE_MAX_AGE_SECONDS"), 60, "APP_SEARCH_CACHE_MAX_AGE_SECONDS"),
            sMaxAgeSeconds = seconds(env("APP_SEARCH_CACHE_S_MAXAGE_SECONDS"), 300, "APP_SEARCH_CACHE_S_MAXAGE_SECONDS"),
            staleWhileRevalidateSeconds = seconds(env("APP_SEARCH_CACHE_SWR_SECONDS"), 60, "APP_SEARCH_CACHE_SWR_SECONDS"),
        ),
    )
}

/**
 * A trust-relevant duration: absent means the ratified default, but a malformed
 * value fails startup instead of silently becoming the default (SC-199 review,
 * finding 7).
 */
private fun seconds(raw: String?, default: Long, name: String): Long {
    val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return default
    return value.toLongOrNull()
        ?: throw IllegalArgumentException("$name must be a whole number of seconds, got: $value")
}

/**
 * SC-199 D-1: the centre precision is exactly 3 or 4 decimals. A malformed value
 * fails startup rather than silently choosing a precision nobody ratified.
 */
private fun searchPrecision(raw: String?): CoordinatePrecision =
    when (raw?.trim()?.takeIf { it.isNotEmpty() }) {
        null -> CoordinatePrecision.FOUR_DECIMALS
        "3" -> CoordinatePrecision.THREE_DECIMALS
        "4" -> CoordinatePrecision.FOUR_DECIMALS
        else -> throw IllegalArgumentException(
            "APP_SEARCH_CENTRE_DECIMALS must be 3 or 4, got: $raw",
        )
    }
