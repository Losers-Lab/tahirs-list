package com.tahirslist.bootstrap.config

/**
 * Boot-time guard against running the `prod` profile on the local-development
 * defaults baked into [loadConfig] (a throwaway DB password, MinIO's
 * `minioadmin` keys). Twelve-factor config is silent about defaults, so
 * without this check a production deploy that forgets one environment variable
 * starts happily and serves real traffic against a publicly known credential.
 *
 * Called from the Ktor module before any resource is created, so a
 * misconfiguration fails fast at boot instead of at the first login or upload.
 * Messages name the environment variable only — never the value, which may
 * itself be a secret that must not end up in logs.
 *
 * @throws IllegalStateException when [config] is production and still carries
 *   a default or blank credential.
 */
fun requireValidProductionConfig(config: AppConfig) {
    requireKnownProfile(config.profile)
    if (!config.isProduction) return

    if (config.database.user.isBlank()) {
        fail("APP_DB_USER")
    }
    if (config.database.password.isBlank() || config.database.password == "halal") {
        fail("APP_DB_PASSWORD")
    }
    config.storage?.let { storage ->
        if (storage.accessKey.isBlank() || storage.accessKey == "minioadmin") {
            fail("APP_S3_ACCESS_KEY")
        }
        if (storage.secretKey.isBlank() || storage.secretKey == "minioadmin") {
            fail("APP_S3_SECRET_KEY")
        }
    }
    if (config.storage == null) {
        throw IllegalStateException(
            "Refusing to start in the 'prod' profile: no object storage is configured " +
                "(APP_S3_ENDPOINT). Hero images and certification evidence would live only in " +
                "process memory, so an approved certificate would vanish on restart.",
        )
    }

    if (config.mcp.enabled) {
        requireHttps("APP_MCP_PUBLIC_WEB_BASE_URL", config.mcp.publicWebBaseUrl)
        requireHttps("APP_MCP_PUBLIC_API_BASE_URL", config.mcp.publicApiBaseUrl)
        requireHttps("APP_GEOCODER_BASE_URL", config.mcp.geocoderBaseUrl)
        if (config.mcp.capacity <= 0) fail("APP_MCP_RATELIMIT_CAPACITY")
        if (config.mcp.refillTokens <= 0) fail("APP_MCP_RATELIMIT_REFILL_TOKENS")
        if (config.mcp.refillWindowSeconds <= 0) fail("APP_MCP_RATELIMIT_WINDOW_SECONDS")
    }
}

/**
 * Only the ratified profile names are accepted. The production guard keys off the
 * literal `prod` token, so a typo such as `production` (or `Prod`) would otherwise
 * silently select the development branch — including the ephemeral JWT key and the
 * default database credentials. Comma-separated lists are supported.
 */
private fun requireKnownProfile(profile: String) {
    val unknown = profile.split(',').map { it.trim() }.filter { it !in KNOWN_PROFILES }
    if (unknown.isNotEmpty()) {
        throw IllegalStateException(
            "Unknown APP_PROFILE value(s): ${unknown.joinToString(", ")}. " +
                "Accepted profiles: ${KNOWN_PROFILES.joinToString(", ")}.",
        )
    }
}

private val KNOWN_PROFILES = setOf("dev", "test", "live", "prod")

/** A public URL must be absolute https and must not be a localhost default. */
private fun requireHttps(variable: String, value: String) {
    val uri = runCatching { java.net.URI(value) }.getOrNull()
    if (uri == null || uri.scheme != "https" || uri.host.isNullOrBlank()) {
        throw IllegalStateException(
            "Refusing to start in the 'prod' profile: $variable must be an absolute https URL.",
        )
    }
    val host = uri.host.lowercase()
    if (host == "localhost" || host == "127.0.0.1" || host == "::1") {
        throw IllegalStateException(
            "Refusing to start in the 'prod' profile: $variable still points at localhost.",
        )
    }
}

private fun fail(variable: String): Nothing =
    throw IllegalStateException(
        "Refusing to start in the 'prod' profile: $variable is blank or still set to its " +
            "local development default. Set it to a real value before deploying.",
    )
