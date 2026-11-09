package com.tahirslist.bootstrap.config

import com.tahirslist.domain.restaurant.CoordinatePrecision
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** SC-199: the search precision and freshness window are environment configuration. */
class SearchSettingsFromEnvTest : FunSpec({

    fun config(vararg pairs: Pair<String, String>): AppConfig {
        val env = pairs.toMap()
        return loadConfig { name -> env[name] }
    }

    test("defaults to 4 dp and the ratified window when nothing is set") {
        val search = config().search
        search.centrePrecision shouldBe CoordinatePrecision.FOUR_DECIMALS
        search.cacheMaxAgeSeconds shouldBe 60
        search.sMaxAgeSeconds shouldBe 300
        search.staleWhileRevalidateSeconds shouldBe 60
    }

    test("reads 3 dp and a custom window from the environment") {
        val search = config(
            "APP_SEARCH_CENTRE_DECIMALS" to "3",
            "APP_SEARCH_CACHE_MAX_AGE_SECONDS" to "30",
            "APP_SEARCH_CACHE_S_MAXAGE_SECONDS" to "120",
            "APP_SEARCH_CACHE_SWR_SECONDS" to "30",
        ).search

        search.centrePrecision shouldBe CoordinatePrecision.THREE_DECIMALS
        search.cacheMaxAgeSeconds shouldBe 30
        search.sMaxAgeSeconds shouldBe 120
        search.staleWhileRevalidateSeconds shouldBe 30
    }

    test("rejects a decimals value other than 3 or 4, naming the variable") {
        val ex = shouldThrow<IllegalArgumentException> {
            config("APP_SEARCH_CENTRE_DECIMALS" to "5")
        }
        (ex.message.orEmpty().contains("APP_SEARCH_CENTRE_DECIMALS")) shouldBe true
    }

    test("rejects a non-numeric decimals value instead of silently defaulting") {
        shouldThrow<IllegalArgumentException> { config("APP_SEARCH_CENTRE_DECIMALS" to "four") }
    }

    test("rejects a freshness window above the 600 s delisting bound (INV-10)") {
        shouldThrow<IllegalArgumentException> {
            config(
                "APP_SEARCH_CACHE_S_MAXAGE_SECONDS" to "500",
                "APP_SEARCH_CACHE_SWR_SECONDS" to "200",
            )
        }
    }
})
