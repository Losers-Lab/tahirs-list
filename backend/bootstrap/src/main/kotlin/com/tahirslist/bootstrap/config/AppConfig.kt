package com.tahirslist.bootstrap.config

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
    )
}
