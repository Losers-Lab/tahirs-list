package com.tahirslist.bootstrap

import com.tahirslist.bootstrap.config.AppConfig
import com.tahirslist.bootstrap.config.loadConfig
import com.tahirslist.bootstrap.config.requireValidProductionConfig
import com.tahirslist.bootstrap.image.SeedImageIngestRunner
import com.tahirslist.bootstrap.modules.appModules
import com.tahirslist.mcp.McpAwareJsonConverter
import com.tahirslist.mcp.installMcp
import com.tahirslist.persistence.db.Migrations
import com.tahirslist.web.AuthUseCases
import com.tahirslist.web.FavoriteUseCases
import com.tahirslist.web.ListingUseCases
import com.tahirslist.web.VerificationUseCases
import com.tahirslist.web.authRoutes
import com.tahirslist.web.configureWebPlugins
import com.tahirslist.web.favoriteRoutes
import com.tahirslist.web.healthRoutes
import com.tahirslist.web.listingRoutes
import com.tahirslist.web.meRoutes
import com.tahirslist.web.verificationRoutes
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.launch
import org.koin.core.module.Module
import org.koin.ktor.ext.get
import org.koin.ktor.plugin.Koin
import javax.sql.DataSource

private val log = KotlinLogging.logger("com.tahirslist.bootstrap.Application")

fun main() {
    val config = loadConfig()
    embeddedServer(CIO, host = config.host, port = config.port) { module(config) }
        .start(wait = true)
}

/**
 * Ktor application module: validate production configuration, compose the
 * object graph with Koin, apply pending Flyway migrations, install the web
 * plugins, and mount every route group.
 *
 * [extraModules] lets tests override infrastructure definitions (in-memory
 * storage, fake verification provider, test clock) without touching production
 * wiring; later modules override earlier ones (`allowOverride = true`).
 */
fun Application.module(
    config: AppConfig = loadConfig(),
    extraModules: List<Module> = emptyList(),
) {
    // Fail fast, before Koin or any connection pool/object store exists: a prod
    // profile still carrying dev-default credentials must not start at all.
    // One call site means the endpoint tests boot through the same guard.
    requireValidProductionConfig(config)

    // Non-prod profiles deliberately allow the development defaults (default DB
    // credentials, ephemeral JWT key, in-memory storage fallback). Make that
    // visible in the log so a preview stack is never mistaken for a guarded one.
    if (!config.isProduction) {
        log.warn {
            "APP_PROFILE='${config.profile}' is not a production profile: development defaults " +
                "are permitted (default DB/S3 credentials, ephemeral JWT signing key, in-memory " +
                "storage fallback). Never front real data with this configuration."
        }
    }

    install(Koin) {
        modules(*(appModules(config) + extraModules).toTypedArray())
        allowOverride(true)
    }

    // Koin does not close singletons and Ktor has no bean lifecycle: close the
    // connection pool explicitly when the server stops (tests start one server
    // per spec and would otherwise leak a pool each).
    val dataSource: DataSource = get()
    monitor.subscribe(ApplicationStopped) {
        (dataSource as? AutoCloseable)?.close()
    }

    // Migrations run before any repository resolves the pool (Boot's Flyway
    // auto-migration equivalent, now explicit).
    Migrations.migrate(
        jdbcUrl = config.database.jdbcUrl,
        user = config.database.user,
        password = config.database.password,
    )

    configureWebPlugins(
        json = get(),
        jwt = get(),
        rateLimit = get(),
        contentConverter = McpAwareJsonConverter(get()),
    )

    val auth: AuthUseCases = get()
    val listings: ListingUseCases = get()
    val favorites: FavoriteUseCases = get()
    val verification: VerificationUseCases = get()

    routing {
        healthRoutes()
        authRoutes(auth)
        meRoutes()
        listingRoutes(listings)
        favoriteRoutes(favorites)
        verificationRoutes(verification)
    }

    if (config.mcp.enabled) {
        installMcp(
            config = get(),
            useCases = get(),
            limiter = get(),
            originGuard = get(),
        )
    }

    if (config.seedIngest.enabled) {
        launch { get<SeedImageIngestRunner>().run() }
    }
}
