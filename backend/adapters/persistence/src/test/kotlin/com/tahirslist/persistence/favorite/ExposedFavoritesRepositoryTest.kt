package com.tahirslist.persistence.favorite

import com.tahirslist.application.favorite.FavoritesRepository
import com.tahirslist.domain.account.Account
import com.tahirslist.domain.account.Email
import com.tahirslist.domain.restaurant.Cuisine
import com.tahirslist.domain.restaurant.LatLng
import com.tahirslist.domain.restaurant.RestaurantListing
import com.tahirslist.domain.restaurant.VerificationStatus
import com.tahirslist.persistence.PostgisTestDatabase
import com.tahirslist.persistence.account.ExposedAccountRepository
import com.tahirslist.persistence.count
import com.tahirslist.persistence.listing.ExposedRestaurantListingRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

/**
 * Persistence adapter test: proves V12 adds the favorites table and that
 * [ExposedFavoritesRepository] round-trips the user↔listing relation against a
 * real PostGIS container, enforcing the idempotent POST/DELETE contract and the
 * FK integrity at the DB layer.
 */
class ExposedFavoritesRepositoryTest : FunSpec() {

    private val postgis = PostgisTestDatabase()
    private lateinit var favorites: FavoritesRepository
    private lateinit var accounts: ExposedAccountRepository
    private lateinit var listings: ExposedRestaurantListingRepository

    private suspend fun newOwner(): Uuid =
        accounts.save(Account.new(email = Email("owner-${Uuid.random()}@example.com"), passwordHash = "argon2id\$h")).id

    private suspend fun aListing(ownerId: Uuid): Uuid =
        listings.save(
            RestaurantListing.new(
                name = "Halal Grill ${Uuid.random()}",
                address = "123 Main St",
                location = LatLng(40.7128, -74.0060),
                cuisine = Cuisine("mediterranean"),
                isHandCut = true,
                ownerId = ownerId,
            ),
        ).id

    init {
        beforeSpec {
            postgis.start()
            favorites = ExposedFavoritesRepository(postgis.database)
            accounts = ExposedAccountRepository(postgis.database)
            listings = ExposedRestaurantListingRepository(postgis.database)
        }
        afterSpec { postgis.stop() }

        test("V12 migration creates the favorites table") {
            runTest {
                postgis.count("SELECT count(*) FROM information_schema.tables WHERE table_name = 'favorites'") shouldBe 1
            }
        }

        test("add is idempotent: favouriting the same listing twice persists one row") {
            runTest {
                val user = newOwner()
                val listing = aListing(user)

                favorites.add(user, listing)
                favorites.add(user, listing) // duplicate — must be a no-op

                countRows(user) shouldBe 1
            }
        }

        test("remove is idempotent: unfavouriting twice leaves zero rows and does not throw") {
            runTest {
                val user = newOwner()
                val listing = aListing(user)
                favorites.add(user, listing)

                favorites.remove(user, listing)
                favorites.remove(user, listing) // already gone — no-op

                countRows(user) shouldBe 0
            }
        }

        test("removing a listing that was never favourited is a no-op") {
            runTest {
                val user = newOwner()
                val listing = aListing(user)

                favorites.remove(user, listing)
                favorites.remove(user, listing)

                countRows(user) shouldBe 0
            }
        }

        test("findFavoriteListings returns the favourited listing rows for that user only") {
            runTest {
                val user = newOwner()
                val otherUser = newOwner()
                val listingA = aListing(user)
                val listingB = aListing(user)
                val otherListing = aListing(user)

                favorites.add(user, listingA)
                favorites.add(user, listingB)
                // The second user favourites only the third listing.
                favorites.add(otherUser, otherListing)

                val mine = favorites.findFavoriteListings(user)

                val ids = mine.map { it.id }
                ids.toSet() shouldNotBe emptySet<Uuid>()
                ids.toSet() shouldBe setOf(listingA, listingB)
                mine.all { it.verificationStatus == VerificationStatus.UNVERIFIED } shouldBe true
                mine.all { it.name.startsWith("Halal Grill") } shouldBe true
                // The other user's favourite is not leaked.
                favorites.findFavoriteListings(otherUser).map { it.id }.toSet() shouldBe setOf(otherListing)
            }
        }

        test("favouriting a non-existent listing is rejected by the FK") {
            runTest {
                val user = newOwner()
                val phantom = Uuid.random()

                shouldThrow<ExposedSQLException> {
                    favorites.add(user, phantom)
                }
            }
        }
    }

    private suspend fun countRows(userId: Uuid): Int =
        postgis.count("SELECT count(*) FROM favorites WHERE user_id = ?", userId.toJavaUuid())
}
