package com.tahirslist.geocoding

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import kotlinx.coroutines.runBlocking

/**
 * The one live test: calls the real public Photon service and proves the
 * production adapter can resolve a real place end-to-end.
 *
 * Opt-in only — the default suites must never depend on a third-party network.
 * Run with `LIVE_TESTS=1 ./scripts/backend-test.sh` (the script forwards the
 * variable into the container). The public service is best-effort, so the
 * assertion is deliberately loose: a non-null place, coordinates in range, and a
 * non-blank display name.
 */
class PhotonGeocoderLiveTest : FunSpec({

    val enabled = System.getenv("LIVE_TESTS") == "1"

    test("resolvesARealPlaceAgainstPhoton").config(enabled = enabled) {
        val place = runBlocking { PhotonGeocoder().geocode("Toronto, Ontario, Canada") }

        place.shouldNotBeNull()
        (place.location.lat in 43.0..44.0) shouldBe true
        (place.location.lng in -80.0..-79.0) shouldBe true
        place.displayName.shouldNotBeBlank()
    }
})
