package com.tahirslist.web.ratelimit

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * sc-136 review fix: the rate-limit key must not be client-spoofable via the
 * X-Forwarded-For header. By default (no trusted proxy configured) the key
 * derives from the socket peer only; trusting XFF is opt-in, and then only the
 * first hop is used.
 */
class AuthRateLimitKeyTest : FunSpec({

    test("spoofed X-Forwarded-For is ignored by default (remote host keys the bucket)") {
        val key = authRateLimitKey(
            requestUri = "/v1/auth/login",
            remoteHost = "203.0.113.9",
            forwardedFor = "1.2.3.4, 10.0.0.1",
            trustProxy = false,
        )

        key shouldBe "/v1/auth/login|203.0.113.9"
    }

    test("without XFF the immediate peer keys the bucket") {
        val key = authRateLimitKey(
            requestUri = "/v1/auth/login",
            remoteHost = "203.0.113.9",
            forwardedFor = null,
            trustProxy = false,
        )

        key shouldBe "/v1/auth/login|203.0.113.9"
    }

    test("with trust-proxy enabled the first XFF hop keys the bucket") {
        val key = authRateLimitKey(
            requestUri = "/v1/auth/login",
            remoteHost = "203.0.113.9",
            forwardedFor = "1.2.3.4, 10.0.0.1",
            trustProxy = true,
        )

        key shouldBe "/v1/auth/login|1.2.3.4"
    }

    test("with trust-proxy enabled a blank XFF falls back to the remote host") {
        val key = authRateLimitKey(
            requestUri = "/v1/auth/login",
            remoteHost = "203.0.113.9",
            forwardedFor = "   ",
            trustProxy = true,
        )

        key shouldBe "/v1/auth/login|203.0.113.9"
    }

    test("login and refresh never share a bucket (the route is part of the key)") {
        val login = authRateLimitKey("/v1/auth/login", "203.0.113.9", null, trustProxy = false)
        val refresh = authRateLimitKey("/v1/auth/refresh", "203.0.113.9", null, trustProxy = false)

        login shouldBe "/v1/auth/login|203.0.113.9"
        refresh shouldBe "/v1/auth/refresh|203.0.113.9"
    }
})
