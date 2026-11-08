package com.tahirslist.persistence.brand

import com.tahirslist.persistence.PostgisTestDatabase
import com.tahirslist.persistence.count
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest

class ExposedBrandRepositoryTest : FunSpec() {

    private val postgis = PostgisTestDatabase()

    init {
        beforeSpec { postgis.start() }
        afterSpec { postgis.stop() }

        test("resolve finds a seeded brand case-insensitively") {
            runTest {
                val brands = ExposedBrandRepository(postgis.database)

                brands.resolve("The Halal Guys")?.name shouldBe "The Halal Guys"
                brands.resolve("  the halal guys  ")?.name shouldBe "The Halal Guys"
                brands.resolve("No Such Brand") shouldBe null
            }
        }

        test("resolveOrCreate creates once and returns the same brand afterwards") {
            runTest {
                val brands = ExposedBrandRepository(postgis.database)

                val first = brands.resolveOrCreate("Ingest Test Brand")
                val second = brands.resolveOrCreate("Ingest Test Brand")

                second.id shouldBe first.id
                postgis.count("SELECT count(*) FROM brands WHERE name = 'Ingest Test Brand'") shouldBe 1
            }
        }
    }
}
