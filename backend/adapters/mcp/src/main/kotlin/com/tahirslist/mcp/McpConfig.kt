package com.tahirslist.mcp

/**
 * Everything the MCP surface needs from configuration. Supplied by the
 * composition root; the adapter never reads environment variables.
 */
data class McpConfig(
    /** Public site base, used for the listing deep link (`websiteUrl`). */
    val publicWebBaseUrl: String = "http://localhost:5173",
    /** Public API base, used for image and certificate URLs. */
    val publicApiBaseUrl: String = "http://localhost:8080",
    /** Exact `Origin` values a browser MCP client may use. Empty means none. */
    val allowedOrigins: Set<String> = emptySet(),
    /**
     * DNS-rebinding protection validates the `Host` header. Production keeps it
     * on; in-process Ktor test clients do not send a Host header, so unit HTTP
     * tests disable it and the embedded-server integration tests exercise it.
     */
    val enableDnsRebindingProtection: Boolean = true,
    val rateLimit: McpRateLimitSettings = McpRateLimitSettings(),
)

/** Per-client token-bucket settings for the public MCP endpoint. */
data class McpRateLimitSettings(
    val capacity: Long = 60,
    val refillTokens: Long = 60,
    val refillWindowSeconds: Long = 60,
    val maxKeys: Int = 100_000,
    val trustProxy: Boolean = false,
)
