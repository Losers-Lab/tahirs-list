package com.tahirslist.bootstrap.modules

import com.tahirslist.application.account.AccountRepository
import com.tahirslist.application.account.AuthenticateAccount
import com.tahirslist.application.account.CreateAccount
import com.tahirslist.application.account.LogoutSession
import com.tahirslist.application.account.PasswordHasher
import com.tahirslist.application.account.RefreshSession
import com.tahirslist.application.account.RefreshTokenStore
import com.tahirslist.application.account.TokenIssuer
import com.tahirslist.application.agent.GetListingForAgent
import com.tahirslist.application.agent.SearchListingsForAgent
import com.tahirslist.application.favorite.FavoriteListing
import com.tahirslist.application.favorite.FavoritesRepository
import com.tahirslist.application.favorite.ListFavorites
import com.tahirslist.application.favorite.UnfavoriteListing
import com.tahirslist.application.geo.GeocoderPort
import com.tahirslist.application.image.ImageFetcher
import com.tahirslist.application.image.ImagePort
import com.tahirslist.application.image.InMemoryImagePort
import com.tahirslist.application.image.IngestHeroImage
import com.tahirslist.application.image.IngestSeedHeroPhotos
import com.tahirslist.application.image.SeedPhotoListingResolver
import com.tahirslist.application.listing.AddListingImage
import com.tahirslist.application.listing.CreateListing
import com.tahirslist.application.listing.ListingSearchQuery
import com.tahirslist.application.listing.RemoveListingImage
import com.tahirslist.application.listing.RestaurantListingRepository
import com.tahirslist.application.listing.UpdateListing
import com.tahirslist.application.verification.CertificationImageStorage
import com.tahirslist.application.verification.ClaimListing
import com.tahirslist.application.verification.DeferToHumanProvider
import com.tahirslist.application.verification.HalalCertificationReviewRepository
import com.tahirslist.application.verification.InMemoryCertificationImageStorage
import com.tahirslist.application.verification.RequestVerification
import com.tahirslist.application.verification.VerificationCommittee
import com.tahirslist.application.verification.VerificationProvider
import com.tahirslist.bootstrap.Argon2idPasswordHasher
import com.tahirslist.bootstrap.JwtRsaKeyPairLoader
import com.tahirslist.bootstrap.JwtTokenIssuer
import com.tahirslist.bootstrap.config.AppConfig
import com.tahirslist.bootstrap.image.HttpImageFetcher
import com.tahirslist.bootstrap.image.SeedImageIngestRunner
import com.tahirslist.bootstrap.image.SeedPhotoManifestParser
import com.tahirslist.geocoding.PhotonGeocoder
import com.tahirslist.mcp.Bucket4jMcpRateLimiter
import com.tahirslist.mcp.McpConfig
import com.tahirslist.mcp.McpOriginGuard
import com.tahirslist.mcp.McpRateLimitSettings
import com.tahirslist.mcp.McpRateLimiter
import com.tahirslist.mcp.McpUseCases
import com.tahirslist.persistence.account.ExposedAccountRepository
import com.tahirslist.persistence.account.ExposedRefreshTokenStore
import com.tahirslist.persistence.db.DatabaseFactory
import com.tahirslist.persistence.favorite.ExposedFavoritesRepository
import com.tahirslist.persistence.image.ExposedSeedPhotoListingResolver
import com.tahirslist.persistence.listing.ExposedListingSearchQuery
import com.tahirslist.persistence.listing.ExposedRestaurantListingRepository
import com.tahirslist.persistence.verification.ExposedHalalCertificationReviewRepository
import com.tahirslist.storage.s3.S3StorageConfig
import com.tahirslist.storage.s3.createCertificationStorage
import com.tahirslist.storage.s3.createImagePort
import com.tahirslist.storage.s3.createS3Client
import com.tahirslist.verification.ai.HostedVisionAdapter
import com.tahirslist.verification.ai.RestVisionModelClient
import com.tahirslist.verification.ai.VisionModelClient
import com.tahirslist.web.AuthUseCases
import com.tahirslist.web.FavoriteUseCases
import com.tahirslist.web.JwtConfig
import com.tahirslist.web.ListingUseCases
import com.tahirslist.web.RateLimitConfig
import com.tahirslist.web.RefreshCookieConfig
import com.tahirslist.web.VerificationUseCases
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.jdbc.Database
import org.koin.core.module.Module
import org.koin.dsl.module
import java.security.KeyPair
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.time.Duration
import javax.sql.DataSource
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

/**
 * The composition root: explicit Koin modules that replace the old Spring
 * `@Configuration` classes 1:1. Adapters and use cases are wired here; nothing
 * below this module reaches into a DI container.
 */
fun appModules(config: AppConfig): List<Module> = listOf(
    infrastructureModule(config),
    useCaseModule(config),
    webModule(config),
    mcpModule(config),
)

private fun infrastructureModule(config: AppConfig): Module = module {
    // --- Database (HikariCP pool bound to Exposed) ---
    // Registered as the DataSource interface: Koin resolves exact types, so a
    // `single<HikariDataSource>` would not satisfy `connect(DataSource)`.
    single<DataSource> {
        DatabaseFactory.dataSource(
            jdbcUrl = config.database.jdbcUrl,
            user = config.database.user,
            password = config.database.password,
            maxPoolSize = config.database.maxPoolSize,
        )
    }
    single<Database> { DatabaseFactory.connect(get()) }

    // --- Identity / crypto ---
    single<Clock> { Clock.System }
    single<KeyPair> {
        JwtRsaKeyPairLoader.loadKeyPair(
            privateKeyB64 = config.jwt.privateKeyBase64,
            prodProfile = config.isProduction,
        )
    }
    single<PasswordHasher> { Argon2idPasswordHasher() }
    single<TokenIssuer> {
        JwtTokenIssuer(
            signingKey = get<KeyPair>().private as RSAPrivateKey,
            issuer = config.jwt.issuer,
            accessTokenTtl = Duration.ofSeconds(config.jwt.accessTtlSeconds),
        )
    }

    // --- Persistence ports ---
    single<AccountRepository> { ExposedAccountRepository(get()) }
    single<RefreshTokenStore> { ExposedRefreshTokenStore(get()) }
    single<FavoritesRepository> { ExposedFavoritesRepository(get()) }
    single<RestaurantListingRepository> { ExposedRestaurantListingRepository(get()) }
    single<ListingSearchQuery> { ExposedListingSearchQuery(get()) }
    single<HalalCertificationReviewRepository> { ExposedHalalCertificationReviewRepository(get()) }
    single<SeedPhotoListingResolver> { ExposedSeedPhotoListingResolver(get()) }

    // --- Object storage: real S3-compatible store when configured, else in-memory ---
    val storage = config.storage
    if (storage != null) {
        single {
            createS3Client(
                S3StorageConfig(
                    endpoint = storage.endpoint,
                    region = storage.region,
                    accessKey = storage.accessKey,
                    secretKey = storage.secretKey,
                    bucket = storage.bucket,
                ),
            )
        }
        single<ImagePort> { createImagePort(get(), storage.bucket) }
        single<CertificationImageStorage> { createCertificationStorage(get(), storage.bucket) }
    } else {
        single<ImagePort> { InMemoryImagePort() }
        single<CertificationImageStorage> { InMemoryCertificationImageStorage() }
    }

    // --- Outbound HTTP / verification provider ---
    single<ImageFetcher> { HttpImageFetcher() }
    single { SeedPhotoManifestParser() }

    // --- Geocoding (SC-209 is the first production consumer of the port) ---
    single<GeocoderPort> { PhotonGeocoder(baseUrl = config.mcp.geocoderBaseUrl) }
    val verification = config.verification
    if (verification != null) {
        single<VisionModelClient> { RestVisionModelClient(verification) }
        single<VerificationProvider> { HostedVisionAdapter(get()) }
    } else {
        single<VerificationProvider> { DeferToHumanProvider() }
    }
}

private fun useCaseModule(config: AppConfig): Module = module {
    // Auth
    single { CreateAccount(get(), get()) }
    single { AuthenticateAccount(get(), get(), get(), get(), get()) }
    single { RefreshSession(get(), get(), get(), get()) }
    single { LogoutSession(get()) }

    // Listings
    single { CreateListing(get(), get()) }
    single { UpdateListing(get()) }
    single { AddListingImage(get(), get()) }
    single { RemoveListingImage(get(), get()) }
    single { IngestHeroImage(get()) }

    // Favorites
    single { FavoriteListing(get(), get()) }
    single { UnfavoriteListing(get()) }
    single { ListFavorites(get()) }

    // Verification
    single { RequestVerification(get()) }
    single { ClaimListing(get(), get(), get(), get()) }
    single { VerificationCommittee(get(), get()) }
    single { IngestSeedHeroPhotos(get(), get(), get()) }

    // Agent surface (SC-209)
    single { SearchListingsForAgent(get(), get()) }
    single { GetListingForAgent(get(), get()) }

    // Seed ingest runner (launched from Application when enabled)
    single { SeedImageIngestRunner(get(), get(), config.seedIngest.manifest) }
}

private fun webModule(config: AppConfig): Module = module {
    // MCP messages are polymorphic and rely on kotlinx's default discriminator
    // being DISABLED (`ClassDiscriminatorMode.NONE`); REST DTOs are unaffected.
    // Explicit nulls stay ON: the REST wire contract includes them.
    single {
        Json {
            encodeDefaults = true
            classDiscriminatorMode = kotlinx.serialization.json.ClassDiscriminatorMode.NONE
        }
    }

    single {
        JwtConfig(
            issuer = config.jwt.issuer,
            publicKey = get<KeyPair>().public as RSAPublicKey,
            clockSkewSeconds = 60,
        )
    }
    single {
        RateLimitConfig(
            capacity = config.rateLimit.capacity,
            refillTokens = config.rateLimit.refillTokens,
            refillWindow = config.rateLimit.refillWindowSeconds.seconds,
            trustProxy = config.rateLimit.trustProxy,
            maxKeys = config.rateLimit.maxKeys,
        )
    }
    single { RefreshCookieConfig(secure = config.cookie.secure) }

    single { AuthUseCases(get(), get(), get(), get(), get()) }
    single {
        ListingUseCases(
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
        )
    }
    single { FavoriteUseCases(get(), get(), get()) }
    single { VerificationUseCases(get(), get()) }
}

private fun mcpModule(config: AppConfig): Module = module {
    single {
        McpConfig(
            publicWebBaseUrl = config.mcp.publicWebBaseUrl,
            publicApiBaseUrl = config.mcp.publicApiBaseUrl,
            allowedOrigins = config.mcp.allowedOrigins,
            rateLimit = McpRateLimitSettings(
                capacity = config.mcp.capacity,
                refillTokens = config.mcp.refillTokens,
                refillWindowSeconds = config.mcp.refillWindowSeconds,
                maxKeys = config.mcp.maxKeys,
                trustProxy = config.rateLimit.trustProxy,
            ),
        )
    }
    single<McpRateLimiter> {
        Bucket4jMcpRateLimiter(
            capacity = config.mcp.capacity,
            refillTokensPerWindow = config.mcp.refillTokens,
            refillWindow = config.mcp.refillWindowSeconds.seconds,
            maxKeys = config.mcp.maxKeys.toLong(),
        )
    }
    single { McpOriginGuard(config.mcp.allowedOrigins) }
    single { McpUseCases(get(), get()) }
}
