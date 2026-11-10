package com.tahirslist.web

import com.tahirslist.application.account.AuthenticateAccount
import com.tahirslist.application.account.CreateAccount
import com.tahirslist.application.account.LogoutSession
import com.tahirslist.application.account.RefreshSession
import com.tahirslist.application.favorite.FavoriteListing
import com.tahirslist.application.favorite.ListFavorites
import com.tahirslist.application.favorite.UnfavoriteListing
import com.tahirslist.application.image.ImagePort
import com.tahirslist.application.listing.AddListingImage
import com.tahirslist.application.listing.CreateListing
import com.tahirslist.application.listing.GetListingManage
import com.tahirslist.application.listing.ListingSearchQuery
import com.tahirslist.application.listing.RemoveListingImage
import com.tahirslist.application.listing.RestaurantListingRepository
import com.tahirslist.application.listing.UpdateListing
import com.tahirslist.application.verification.CertificationImageStorage
import com.tahirslist.application.verification.ClaimListing
import com.tahirslist.application.verification.HalalCertificationReviewRepository
import com.tahirslist.application.verification.VerificationCommittee
import java.security.interfaces.RSAPublicKey
import kotlin.time.Duration

/**
 * Everything the web adapter needs to verify an access token, handed in by the
 * composition root (bootstrap). The adapter never reads configuration itself.
 *
 * [publicKey] is the RSA public half of the same pair the [JwtConfig.issuer]
 * signs with; [clockSkewSeconds] matches the previously ratified 60-second
 * tolerance for `exp`.
 */
data class JwtConfig(
    val issuer: String,
    val publicKey: RSAPublicKey,
    val clockSkewSeconds: Long = 60,
)

/**
 * In-process auth rate-limit budget (signup, login + refresh). Keys are bounded
 * so a client cannot grow the bucket map without limit; [trustProxy] opts in to
 * deriving the client IP from the first `X-Forwarded-For` hop.
 */
data class RateLimitConfig(
    val capacity: Long,
    val refillTokens: Long,
    val refillWindow: Duration,
    val trustProxy: Boolean,
    val maxKeys: Int,
)

/**
 * The refresh-token cookie contract (sc-133). [secure] is configuration-driven
 * (false only for plain-HTTP local development); the remaining attributes are
 * fixed by the ratified contract.
 */
data class RefreshCookieConfig(
    val secure: Boolean,
    val path: String = "/v1/auth",
    val maxAgeSeconds: Long = 2_592_000,
)

/** Account use cases plus the cookie policy the auth routes must honour. */
data class AuthUseCases(
    val signup: CreateAccount,
    val login: AuthenticateAccount,
    val refresh: RefreshSession,
    val logout: LogoutSession,
    val cookie: RefreshCookieConfig,
)

/** Listing use cases and the read/storage ports the routes need. */
data class ListingUseCases(
    val create: CreateListing,
    val update: UpdateListing,
    val addImage: AddListingImage,
    val removeImage: RemoveListingImage,
    val manage: GetListingManage,
    val listings: RestaurantListingRepository,
    val search: ListingSearchQuery,
    val images: ImagePort,
    val certificates: CertificationImageStorage,
    val reviews: HalalCertificationReviewRepository,
)

/** Favourites use cases (sc-50/51/52). */
data class FavoriteUseCases(
    val favorite: FavoriteListing,
    val unfavorite: UnfavoriteListing,
    val list: ListFavorites,
)

/** Owner-claim + Verification Committee use cases (sc-46/sc-73). */
data class VerificationUseCases(
    val claim: ClaimListing,
    val committee: VerificationCommittee,
)
