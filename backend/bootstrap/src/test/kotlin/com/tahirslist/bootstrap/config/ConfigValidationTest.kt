package com.tahirslist.bootstrap.config

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

/**
 * Production boot guard: a `prod` profile must never run on the local-dev
 * defaults that [loadConfig] falls back to (DB password "halal", MinIO
 * "minioadmin"). Error messages name the environment variable, never its value.
 */
class ConfigValidationTest : FunSpec({

    test("rejects the dev database password in prod") {
        val ex = shouldThrow<IllegalStateException> {
            requireValidProductionConfig(config(password = "halal"))
        }

        val message = ex.message.orEmpty()
        message shouldContain "APP_DB_PASSWORD"
        // The offending value must not be echoed back into the message/logs.
        message shouldNotContain "halal"
    }

    test("rejects a blank database password in prod") {
        val ex = shouldThrow<IllegalStateException> {
            requireValidProductionConfig(config(password = "  "))
        }
        ex.message.orEmpty() shouldContain "APP_DB_PASSWORD"
    }

    test("rejects a blank database user in prod") {
        val ex = shouldThrow<IllegalStateException> {
            requireValidProductionConfig(config(user = ""))
        }
        ex.message.orEmpty() shouldContain "APP_DB_USER"
    }

    test("rejects MinIO's default access key in prod") {
        val ex = shouldThrow<IllegalStateException> {
            requireValidProductionConfig(
                config(
                    password = "real-db-password",
                    storage = storage(accessKey = "minioadmin", secretKey = "real-secret"),
                ),
            )
        }
        ex.message.orEmpty() shouldContain "APP_S3_ACCESS_KEY"
    }

    test("rejects MinIO's default secret key in prod") {
        val ex = shouldThrow<IllegalStateException> {
            requireValidProductionConfig(
                config(
                    password = "real-db-password",
                    storage = storage(accessKey = "real-access-key", secretKey = "minioadmin"),
                ),
            )
        }
        ex.message.orEmpty() shouldContain "APP_S3_SECRET_KEY"
    }

    test("rejects blank storage credentials in prod") {
        shouldThrow<IllegalStateException> {
            requireValidProductionConfig(
                config(
                    password = "real-db-password",
                    storage = storage(accessKey = "", secretKey = "real-secret"),
                ),
            )
        }
    }

    test("accepts real credentials in prod") {
        shouldNotThrowAny {
            requireValidProductionConfig(
                config(
                    user = "halal_app",
                    password = "correct-horse-battery-staple",
                    storage = storage(accessKey = "real-access-key", secretKey = "real-secret"),
                ),
            )
        }
    }

    test("refuses prod without object storage (evidence would live only in memory)") {
        val ex = shouldThrow<IllegalStateException> {
            requireValidProductionConfig(
                config(user = "halal_app", password = "correct-horse-battery-staple", storage = null),
            )
        }
        ex.message.orEmpty() shouldContain "APP_S3_ENDPOINT"
    }

    test("refuses an unrecognised profile instead of silently treating it as dev") {
        listOf("production", "Prod", "prod-us", "staging").forEach { profile ->
            val ex = shouldThrow<IllegalStateException> {
                requireValidProductionConfig(config(profile = profile, storage = storage()))
            }
            ex.message.orEmpty() shouldContain "APP_PROFILE"
        }
    }

    test("accepts dev defaults outside prod") {
        shouldNotThrowAny {
            requireValidProductionConfig(
                config(
                    profile = "dev",
                    password = "halal",
                    storage = storage(accessKey = "minioadmin", secretKey = "minioadmin"),
                ),
            )
        }
    }

    test("treats a comma-separated profile list containing prod as production") {
        shouldThrow<IllegalStateException> {
            requireValidProductionConfig(config(profile = "dev, prod"))
        }
    }
})

private fun config(
    profile: String = "prod",
    user: String = "halal",
    password: String = "halal",
    storage: StorageSettings? = null,
): AppConfig = AppConfig(
    profile = profile,
    database = DatabaseSettings(
        jdbcUrl = "jdbc:postgresql://db:5432/halal",
        user = user,
        password = password,
    ),
    jwt = JwtSettings(privateKeyBase64 = "not-exercised-by-this-guard"),
    rateLimit = RateLimitSettings(),
    cookie = CookieSettings(),
    storage = storage,
    verification = null,
    seedIngest = SeedIngestSettings(),
)

private fun storage(
    accessKey: String = "real-access-key",
    secretKey: String = "real-secret",
): StorageSettings = StorageSettings(
    endpoint = "https://s3.example.com",
    region = "us-east-1",
    accessKey = accessKey,
    secretKey = secretKey,
    bucket = "halal",
)
