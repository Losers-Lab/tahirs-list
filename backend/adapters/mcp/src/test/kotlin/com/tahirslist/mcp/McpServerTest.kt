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

    test("clientKeyPrefersCloudflareConnectingIp") {
        mcpClientKey("10.0.0.5", "203.0.113.9", cfConnectingIp = "198.51.100.7", trustProxy = true) shouldBe
            "mcp|198.51.100.7"
    }

    test("clientKeyDiscardsImplausibleForwardedFor") {
        mcpClientKey("10.0.0.5", "<script>", trustProxy = true) shouldBe "mcp|10.0.0.5"
        mcpClientKey("10.0.0.5", "1.2.3.4, 9.9.9.9", trustProxy = true) shouldBe "mcp|1.2.3.4"
    }

    test("parseErrorEnvelopeIsRecognisedAtTheStartOnly") {
        // Both SDK shapes, and a leading newline/space.
        """{"error":{"code":-32700,"message":"Parse error: secret"},"jsonrpc":"2.0"}"""
            .isParseErrorEnvelope() shouldBe true
        """{"jsonrpc":"2.0","id":null,"error":{"code":-32700,"message":"Parse error"}}"""
            .isParseErrorEnvelope() shouldBe true
        "  {\"jsonrpc\":\"2.0\",\"id\":7,\"error\":{\"code\":-32700,\"message\":\"x\"}}"
            .isParseErrorEnvelope() shouldBe true
    }

    test("nonParseErrorBodiesAreNotRewritten") {
        // Successful results, other JSON-RPC errors, and a listing string that
        // merely contains the code must never match.
        """{"jsonrpc":"2.0","id":1,"result":{"content":[]}}"""
            .isParseErrorEnvelope() shouldBe false
        """{"jsonrpc":"2.0","id":1,"error":{"code":-32602,"message":"Invalid params"}}"""
            .isParseErrorEnvelope() shouldBe false
        """{"jsonrpc":"2.0","id":1,"result":{"name":"x\"code\":-32700"}}"""
            .isParseErrorEnvelope() shouldBe false
        // The anchor is load-bearing: an unanchored matcher finds the nested
        // error object inside a valid result and rewrites a successful response.
        """{"jsonrpc":"2.0","id":1,"result":{"x":{"error":{"code":-32700,"message":"x"}}}}"""
            .isParseErrorEnvelope() shouldBe false
    }
})
