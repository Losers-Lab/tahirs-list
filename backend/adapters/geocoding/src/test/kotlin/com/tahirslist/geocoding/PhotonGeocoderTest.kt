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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest

/**
 * Proves the Photon adapter implements the GeocoderPort against a real HTTP
 * response shape (Photon's GeoJSON FeatureCollection), served by a pure-JDK
 * HttpServer — no real network and no extra test dependency. The adapter is now
 * suspend (Ktor client), so each scenario runs in [runTest].
 */
private class FakeClock(var millis: Long = 0L)

private data class Stub(
    val status: Int,
    val body: String,
    val retryAfter: String? = null,
    val delayMillis: Long = 0,
)

class PhotonGeocoderTest : FunSpec({

    // Handler behaviour the test under run can swap: (status, body, delayMillis).
    var status: Int = 200
    var body: String = ""
    var delayMillis: Long = 0
    var retryAfter: String? = null
    var lastQuery: String? = null

    val queue = ArrayDeque<Stub>()
    var requests: Int = 0
    var inFlight: Int = 0
    var maxInFlight: Int = 0
    val requestTimes = mutableListOf<Long>()
    var requestClock: FakeClock? = null

    lateinit var baseUrl: String
    lateinit var server: HttpServer

    beforeSpec {
        server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/") { exchange ->
            lastQuery = exchange.requestURI.rawQuery // raw (still percent-encoded) form
            val stub = if (queue.isNotEmpty()) {
                queue.removeFirst()
            } else {
                Stub(status, body, retryAfter, delayMillis)
            }
            requests += 1
            requestTimes += requestClock?.millis ?: 0L
            inFlight += 1
            maxInFlight = maxOf(maxInFlight, inFlight)
            try {
                if (stub.delayMillis > 0) Thread.sleep(stub.delayMillis)
                stub.retryAfter?.let { exchange.responseHeaders.add("Retry-After", it) }
                val payload = stub.body.toByteArray(Charsets.UTF_8)
                exchange.sendResponseHeaders(stub.status, payload.size.toLong())
                exchange.responseBody.use { it.write(payload) }
            } finally {
                inFlight -= 1
            }
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

    /** Adds a one-off response; queued responses win over `setResponse`. */
    fun respond(statusCode: Int, responseBody: String, header: String? = null) {
        queue.addLast(Stub(statusCode, responseBody, header))
    }

    /**
     * A geocoder on a virtual clock, so pacing and backoff are asserted without
     * real waiting. The sleeper advances the same clock the adapter reads.
     */
    fun politeGeocoder(
        clock: FakeClock,
        minInterval: Duration = 1.seconds,
        maxAttempts: Int = 4,
        sleeper: (suspend (Duration) -> Unit)? = null,
    ) = PhotonGeocoder(
        baseUrl = baseUrl,
        timeout = 5.seconds,
        minInterval = minInterval,
        maxAttempts = maxAttempts,
        nowMillis = { clock.millis },
        sleeper = sleeper ?: { clock.millis += it.inWholeMilliseconds },
    )

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

    beforeTest {
        queue.clear()
        requests = 0
        inFlight = 0
        maxInFlight = 0
        requestTimes.clear()
        requestClock = null
        lastQuery = null
        status = 200
        body = validGeoJson
        delayMillis = 0
        retryAfter = null
    }

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

    test("asks for one feature, so the demo server does the least work") {
        runTest {
            politeGeocoder(FakeClock()).geocode("Halal Grill, New York")

            lastQuery shouldContain "limit=1"
        }
    }

    test("retries a transient 503 and returns the next 200") {
        runTest {
            respond(503, """{"message":"busy"}""")
            respond(200, validGeoJson)
            val clock = FakeClock()

            val result = politeGeocoder(clock).geocode("Halal Grill, New York")

            result shouldNotBe null
            requests shouldBe 2
            // first retry backs off one second on the virtual clock
            clock.millis shouldBe 1_000
        }
    }

    test("honours Retry-After within the policy cap") {
        runTest {
            respond(429, """{"message":"slow down"}""", header = "7")
            respond(200, validGeoJson)
            val clock = FakeClock()

            politeGeocoder(clock).geocode("Halal Grill, New York")

            clock.millis shouldBe 7_000
            requests shouldBe 2
        }
    }

    test("an absurd Retry-After is capped instead of sleeping for hours") {
        runTest {
            respond(503, "", header = "3600")
            respond(200, validGeoJson)
            val clock = FakeClock()

            politeGeocoder(clock).geocode("Halal Grill, New York")

            clock.millis shouldBe 60_000
        }
    }

    test("does not retry a permanent client error") {
        runTest {
            respond(400, """{"message":"bad request"}""")
            val clock = FakeClock()

            shouldThrowSuspend<GeocodingException> {
                politeGeocoder(clock).geocode("Halal Grill, New York")
            }
            requests shouldBe 1
            clock.millis shouldBe 0
        }
    }

    test("does not retry a malformed 200 response") {
        runTest {
            respond(200, "{ not json")
            val clock = FakeClock()

            shouldThrowSuspend<GeocodingException> {
                politeGeocoder(clock).geocode("Halal Grill, New York")
            }
            requests shouldBe 1
        }
    }

    test("gives up after the attempt budget, with a useful error") {
        runTest {
            repeat(4) { respond(503, "") }
            val clock = FakeClock()

            val error = shouldThrowSuspend<GeocodingException> {
                politeGeocoder(clock).geocode("Halal Grill, New York")
            }

            requests shouldBe 4
            error.message!!.contains("503") shouldBe true
            // backoff 1s + 2s + 4s, no wait after the last attempt
            clock.millis shouldBe 7_000
        }
    }

    test("spaces consecutive requests by the configured interval") {
        runTest {
            requestClock = FakeClock()
            respond(200, validGeoJson)
            respond(200, validGeoJson)
            val geocoder = politeGeocoder(requestClock!!)

            geocoder.geocode("Halal Grill, New York")
            geocoder.geocode("Another Grill, New York")

            requests shouldBe 2
            (requestTimes[1] - requestTimes[0]) shouldBe 1_000
        }
    }

    test("concurrent callers are serialized: one in flight, spaced by the interval") {
        runTest {
            requestClock = FakeClock()
            respond(200, validGeoJson)
            respond(200, validGeoJson)
            val geocoder = politeGeocoder(
                requestClock!!,
                sleeper = { requestClock!!.millis += it.inWholeMilliseconds },
            )

            val first = async { geocoder.geocode("Grill One, New York") }
            val second = async { geocoder.geocode("Grill Two, New York") }
            first.await()
            second.await()

            maxInFlight shouldBe 1
            (requestTimes[1] - requestTimes[0]) shouldBe 1_000
        }
    }

    test("cancellation during backoff stops the retry loop") {
        runTest {
            respond(503, "")
            val slept = CompletableDeferred<Unit>()
            val clock = FakeClock()
            val geocoder = politeGeocoder(
                clock,
                sleeper = {
                    slept.complete(Unit)
                    awaitCancellation()
                },
            )

            val job = launch(Dispatchers.Default) { geocoder.geocode("Halal Grill, New York") }
            slept.await()
            job.cancelAndJoin()

            job.isCancelled shouldBe true
            requests shouldBe 1
        }
    }
})

/**
 * Kotest's [shouldThrow] takes a non-suspend block; the adapter's port is
 * suspend, so this bridges the call without weakening the assertion.
 */
private inline fun <reified T : Throwable> shouldThrowSuspend(crossinline block: suspend () -> Any?): T =
    shouldThrow<T> { runBlocking { block() } }
