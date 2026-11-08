package com.tahirslist.mcp

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe

class McpServerTest : FunSpec({

    test("allowedHostsIncludesLoopbackBaseUrlsAndExplicitHosts") {
        val config = McpConfig(
            publicWebBaseUrl = "https://tahirslist.com",
            publicApiBaseUrl = "https://api.tahirslist.com",
            allowedHosts = setOf("mcp.tahirslist.com"),
        )

        allowedHosts(config) shouldContainAll listOf(
            "localhost",
            "127.0.0.1",
            "[::1]",
            "tahirslist.com",
            "api.tahirslist.com",
            "mcp.tahirslist.com",
        )
    }

    test("allowedHostsToleratesMalformedBaseUrls") {
        val config = McpConfig(
            publicWebBaseUrl = "not a url",
            publicApiBaseUrl = "http://localhost:8080",
        )

        allowedHosts(config) shouldContain "localhost"
    }

    test("clientKeyIgnoresForwardedForUnlessTrusted") {
        mcpClientKey("10.0.0.5", "203.0.113.9", trustProxy = false) shouldBe "mcp|10.0.0.5"
        mcpClientKey("10.0.0.5", "203.0.113.9", trustProxy = true) shouldBe "mcp|203.0.113.9"
    }

    test("clientKeyDiscardsImplausibleForwardedFor") {
        mcpClientKey("10.0.0.5", "<script>", trustProxy = true) shouldBe "mcp|10.0.0.5"
        mcpClientKey("10.0.0.5", "1.2.3.4, 9.9.9.9", trustProxy = true) shouldBe "mcp|1.2.3.4"
    }
})
