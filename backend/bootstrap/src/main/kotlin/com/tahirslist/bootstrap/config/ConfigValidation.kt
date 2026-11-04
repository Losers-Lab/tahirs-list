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
}

private fun fail(variable: String): Nothing =
    throw IllegalStateException(
        "Refusing to start in the 'prod' profile: $variable is blank or still set to its " +
            "local development default. Set it to a real value before deploying.",
    )
