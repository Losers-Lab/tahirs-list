package com.tahirslist.geocoding

import com.sun.net.httpserver.HttpServer
import com.tahirslist.application.geo.GeocodedPlace
import com.tahirslist.application.geo.GeocodingException
import com.tahirslist.domain.restaurant.LatLng
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.ServerSocket
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest

/**
 * Proves the Photon adapter implements the GeocoderPort against a real HTTP
 * response shape (Photon's GeoJSON FeatureCollection), served by a pure-JDK
 * HttpServer — no real network and no extra test dependency. The adapter is now
 * suspend (Ktor client), so each scenario runs in [runTest].
 */
class PhotonGeocoderTest : FunSpec({

    // Handler behaviour the test under run can swap: (status, body, delayMillis).
    var status: Int = 200
    var body: String = ""
    var delayMillis: Long = 0
    var lastQuery: String? = null

    lateinit var baseUrl: String
    lateinit var server: HttpServer

    beforeSpec {
        server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/") { exchange ->
            lastQuery = exchange.requestURI.rawQuery // raw (still percent-encoded) form
            if (delayMillis > 0) Thread.sleep(delayMillis)
            val payload = body.toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(status, payload.size.toLong())
            exchange.responseBody.use { it.write(payload) }
        }
        server.start()
        baseUrl = "http://127.0.0.1:${server.address.port}/"
    }
    afterSpec { server.stop(0) }

    fun geocoder(timeout: Duration = 5.seconds) =
        PhotonGeocoder(baseUrl = baseUrl, timeout = timeout)

    fun setResponse(statusCode: Int, responseBody: String) {
        status = statusCode
        body = responseBody
        delayMillis = 0
    }

    val validGeoJson = """
        {
          "type": "FeatureCollection",
          "features": [
            {
              "type": "Feature",
              "geometry": { "type": "Point", "coordinates": [-74.006, 40.7128] },
              "properties": {
                "name": "Halal Grill",
                "osm_id": 123456,
                "osm_type": "N",
                "city": "New York",
                "country": "United States"
              }
            }
          ]
        }
    """.trimIndent()

    test("maps a Photon FeatureCollection to a GeocodedPlace") {
        runTest {
            setResponse(200, validGeoJson)

            val result = geocoder().geocode("Halal Grill, New York")

            result shouldNotBe null
            result!!.location shouldBe LatLng(40.7128, -74.006) // GeoJSON [lon, lat]
            result.displayName shouldBe "Halal Grill"
            result.providerRef shouldBe "N/123456"
        }
    }

    test("URL-encodes the address into the q query parameter") {
        runTest {
            setResponse(200, validGeoJson)
            geocoder().geocode("Café & Grill, 5th Ave")

            lastQuery shouldContain "q="
            lastQuery!!.shouldContain("Caf%C3%A9") // é percent-encoded
            lastQuery!!.shouldContain("%26")       // & encoded, not a query separator
        }
    }

    test("returns null when no feature is matched") {
        runTest {
            setResponse(200, """ { "type": "FeatureCollection", "features": [] } """)

            geocoder().geocode("nowhere, antarctica") shouldBe null
        }
    }

    test("returns null for a first feature with no geometry/coordinates") {
        runTest {
            setResponse(200, """
                { "type": "FeatureCollection", "features": [ { "type": "Feature", "properties": { "name": "Ghost" } } ] }
            """.trimIndent())

            geocoder().geocode("ghost place") shouldBe null
        }
    }

    test("returns null when the features member is not an array") {
        runTest {
            setResponse(200, """ { "type": "FeatureCollection", "features": {} } """)

            geocoder().geocode("shape anomaly") shouldBe null
        }
    }

    test("falls back to a street, city display name and null provider ref when name/osm absent") {
        runTest {
            setResponse(200, """
                {
                  "type": "FeatureCollection",
                  "features": [
                    {
                      "type": "Feature",
                      "geometry": { "type": "Point", "coordinates": [-73.9, 40.7] },
                      "properties": { "street": "Main St", "city": "Springfield" }
                    }
                  ]
                }
            """.trimIndent())

            val result = geocoder().geocode("Main St, Springfield")

            result shouldNotBe null
            result!!.displayName shouldBe "Main St, Springfield"
            result.providerRef shouldBe null
        }
    }

    test("throws GeocodingException on a non-200 response") {
        runTest {
            setResponse(429, """ { "message": "rate limited" } """)

            shouldThrowSuspend<GeocodingException> { geocoder().geocode("Halal Grill") }
        }
    }

    test("pins that only HTTP 200 counts as success (201 is a failure)") {
        runTest {
            setResponse(201, validGeoJson)

            shouldThrowSuspend<GeocodingException> { geocoder().geocode("Halal Grill") }
        }
    }

    test("throws GeocodingException on malformed JSON") {
        runTest {
            setResponse(200, "{ not json")

            shouldThrowSuspend<GeocodingException> { geocoder().geocode("Halal Grill") }
        }
    }

    test("throws GeocodingException on a 200 with an empty body") {
        runTest {
            setResponse(200, "")

            shouldThrowSuspend<GeocodingException> { geocoder().geocode("Halal Grill") }
        }
    }

    test("throws GeocodingException for out-of-range provider coordinates") {
        runTest {
            setResponse(200, """
                { "type": "FeatureCollection", "features": [
                  { "type": "Feature", "geometry": { "type": "Point", "coordinates": [10.0, 91.0] }, "properties": {} }
                ] }
            """.trimIndent())

            shouldThrowSuspend<GeocodingException> { geocoder().geocode("somewhere off the planet") }
        }
    }

    test("wraps an IO/connect failure in GeocodingException (does not leak IOException)") {
        runTest {
            // Release a previously-bound port so connecting to it is refused.
            val refusedPort = ServerSocket(0).use { it.localPort }
            val dead = PhotonGeocoder(baseUrl = "http://127.0.0.1:$refusedPort/", timeout = 2.seconds)

            val ex = shouldThrowSuspend<GeocodingException> { dead.geocode("Halal Grill") }

            // Provider outage is surfaced as a GeocodingException, not a raw IOException.
            (ex.cause is ConnectException) shouldBe true
        }
    }

    test("throws GeocodingException when the provider times out") {
        runTest {
            setResponse(200, validGeoJson)
            delayMillis = 2000 // provider responds too slowly for our short timeout

            shouldThrowSuspend<GeocodingException> {
                geocoder(timeout = 200.milliseconds).geocode("Halal Grill")
            }
        }
    }
})

/**
 * Kotest's [shouldThrow] takes a non-suspend block; the adapter's port is
 * suspend, so this bridges the call without weakening the assertion.
 */
private inline fun <reified T : Throwable> shouldThrowSuspend(crossinline block: suspend () -> Any?): T =
    shouldThrow<T> { runBlocking { block() } }
