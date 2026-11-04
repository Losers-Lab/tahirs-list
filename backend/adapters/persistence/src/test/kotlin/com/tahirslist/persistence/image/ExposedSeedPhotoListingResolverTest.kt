package com.tahirslist.persistence.image

import com.tahirslist.application.image.SeedHeroPhoto
import com.tahirslist.application.image.SeedPhotoListingResolver
import com.tahirslist.application.image.SeedPhotoResolution
import com.tahirslist.persistence.PostgisTestDatabase
import com.tahirslist.persistence.count
import com.tahirslist.persistence.row
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

/**
 * [ExposedSeedPhotoListingResolver] against a real PostGIS database with the full
 * seed migration applied (docs/design/sc-157-image-variants.md §"Manifest →
 * listing resolution"). Proves:
 *
 *  - an exact seed name resolves to that seeded listing;
 *  - the name match is normalized (case + whitespace) on both sides;
 *  - a multi-location name ("The Halal Guys", 3 seed rows) is AMBIGUOUS by name
 *    and stays Unresolved unless the address discriminates exactly one row;
 *  - the address fallback resolves a row whose manifest name differs from the
 *    seeded name, and an ambiguous address is itself Unresolved.
 *
 * Resolution is deliberate, never a guess: attaching a hero to the wrong
 * restaurant is worse than skipping (and logging) the row.
 */
class ExposedSeedPhotoListingResolverTest : FunSpec() {

    private val postgis = PostgisTestDatabase()
    private lateinit var resolver: SeedPhotoListingResolver

    private suspend fun seededId(name: String): Uuid =
        (postgis.row("SELECT id FROM restaurant_listings WHERE name = ? LIMIT 1", name)
            .values.single() as UUID).toKotlinUuid()

    /** Resolves, asserts the result type, and returns the id (or fails the test). */
    private suspend fun resolvedId(photo: SeedHeroPhoto): Uuid {
        val resolution = resolver.resolve(photo)
        resolution.shouldBeInstanceOf<SeedPhotoResolution.Resolved>()
        return (resolution as SeedPhotoResolution.Resolved).listingId
    }

    private suspend fun assertUnresolved(photo: SeedHeroPhoto) {
        resolver.resolve(photo).shouldBeInstanceOf<SeedPhotoResolution.Unresolved>()
    }

    init {
        beforeSpec {
            postgis.start()
            resolver = ExposedSeedPhotoListingResolver(postgis.database)
        }
        afterSpec { postgis.stop() }

        test("resolves a seeded row by its exact name") {
            runTest {
                val photo = SeedHeroPhoto(name = "Aroma Fine Indian Cuisine", city = "Toronto", addressGiven = null, heroUrl = "http://img/a")

                resolvedId(photo) shouldBe seededId("Aroma Fine Indian Cuisine")
            }
        }

        test("name match is normalized (case + whitespace) on both sides") {
            runTest {
                val photo = SeedHeroPhoto(name = "   aroma fine indian cuisine  ", city = "Toronto", addressGiven = null, heroUrl = "http://img/a2")

                resolvedId(photo) shouldBe seededId("Aroma Fine Indian Cuisine")
            }
        }

        test("a multi-location name with a non-discriminative address yields Unresolved") {
            runTest {
                // 'The Halal Guys' appears 3x (Toronto, NYC, Dallas); the name alone
                // cannot be unique, and 'Somewhere Else' matches no seed address.
                val photo = SeedHeroPhoto(name = "The Halal Guys", city = null, addressGiven = "Somewhere Else", heroUrl = "http://img/h")

                assertUnresolved(photo)
            }
        }

        test("a multi-location name with a unique address resolves to exactly that location") {
            runTest {
                val photo = SeedHeroPhoto(name = "The Halal Guys", city = null, addressGiven = "563 Yonge St", heroUrl = "http://img/h2")

                val id = resolvedId(photo)

                // '563 Yonge St' is the Toronto location; the other two locations carry
                // different addresses, so this is unambiguous.
                postgis.row("SELECT address FROM restaurant_listings WHERE id = ?", id.toJavaUuid())
                    .values.single() shouldBe "563 Yonge St"
            }
        }

        test("address fallback resolves a row whose manifest name differs from the seeded name") {
            runTest {
                // e.g. a manifest "Iqbal Foods Birchmount"-style mismatch: the name is
                // unknown, but the address uniquely identifies the seed row.
                val photo = SeedHeroPhoto(name = "Unknown Display Name", city = null, addressGiven = "The Queensway", heroUrl = "http://img/p")

                resolvedId(photo) shouldBe seededId("Paramount Fine Foods")
            }
        }

        test("an ambiguous address ('Overlea Blvd': two seed rows) yields Unresolved") {
            runTest {
                val photo = SeedHeroPhoto(name = "Unknown", city = null, addressGiven = "Overlea Blvd", heroUrl = "http://img/o")

                assertUnresolved(photo)
            }
        }

        test("an unknown name and address yields Unresolved (never a guessed listing)") {
            runTest {
                val photo = SeedHeroPhoto(name = "Ghost Restaurant", city = null, addressGiven = "Nowhere Ave", heroUrl = "http://img/g")

                assertUnresolved(photo)
            }
        }

        test("the resolved id names an existing seeded listing (sanity)") {
            runTest {
                val photo = SeedHeroPhoto(name = "Bamiyan Kabob", city = null, addressGiven = null, heroUrl = "http://img/b")

                val id = resolvedId(photo)

                postgis.count(
                    "SELECT count(*) FROM restaurant_listings WHERE id = ? AND provenance = 'research-seed / photon-geocode'",
                    id.toJavaUuid(),
                ) shouldBe 1
            }
        }

        test("sc-178: formerly-UNRESOLVED names now resolve to their exact seed listing") {
            runTest {
                // sc-178 re-aligned the manifest names to the seeded DB names so each
                // unique seed row resolves by name. Locking in the specific rows the
                // live boot previously reported as UNRESOLVED.
                val cases = mapOf(
                    "Iqbal Foods" to "Iqbal Foods",
                    "Madina Naan & Kabob" to "Madina Naan & Kabob",
                    "Paramount Fine Foods" to "Paramount Fine Foods",
                    "Al-Amir Lebanese Restaurant & Club" to "Al-Amir Lebanese Restaurant & Club",
                    "Andalous Mediterranean Buffet" to "Andalous Mediterranean Buffet",
                    "Halal Wings" to "Halal Wings",
                )
                cases.forEach { (manifestName, seedName) ->
                    val id = resolvedId(SeedHeroPhoto(manifestName, null, null, "http://img/" + seedName))
                    postgis.count(
                        "SELECT count(*) FROM restaurant_listings WHERE id = ? AND name = ?",
                        id.toJavaUuid(),
                        seedName,
                    ) shouldBe 1
                }
            }
        }

        test("sc-178: The Halal Guys Manhattan (307 E 14th St) resolves by its unique address") {
            runTest {
                // Name is shared 3x (Toronto/NYC/Dallas); the corrected manifest
                // carries the exact seed address so address discriminates exactly one.
                val photo = SeedHeroPhoto(name = "The Halal Guys", city = null, addressGiven = "307 E 14th St", heroUrl = "http://img/hg")
                val id = resolvedId(photo)
                postgis.row("SELECT address FROM restaurant_listings WHERE id = ?", id.toJavaUuid())
                    .values.single() shouldBe "307 E 14th St"
            }
        }
    }
}
