> **Purpose:** prove every WI landed and every AC is exercised · **Derived from:** 09 · **Delta:** implementation deltas recorded, no requirement changes

# 10 — Traceability: SC-209

Implementation branch: `feat/SC-209-mcp-server` · PR: _filled when opened_

## Work items

| WI | Delivered | Evidence |
|---|---|---|
| WI-01 | `:mcp` module + SDK 0.16.0; stateless transport, tools-only capability | `McpDiscoveryTest` (3 tests) |
| WI-02 | `ListingSearchRequestParser`, `CertificateFacts`, agent DTOs/exceptions | `ListingSearchRequestParserTest`, `CertificateFactsTest` |
| WI-03 | `SearchListingsForAgent` (geocode / coordinates, next offset) | `SearchListingsForAgentTest` (7 tests) |
| WI-04 | `GetListingForAgent` (review lookup only when verified) | `GetListingForAgentTest` (5 tests) |
| WI-05 | `GetRestaurantTool` + detail mapper (halal items, certificate) | `GetRestaurantToolTest` (6 tests) |
| WI-06 | `SearchRestaurantsTool` + card mapper (strict args) | `SearchRestaurantsToolTest` (14 tests) |
| WI-07 | `Bucket4jMcpRateLimiter` + pipeline refusal | `McpRateLimitTest` (3 tests) |
| WI-08 | `McpOriginGuard` (exact match) | `McpOriginTest` (4 tests) |
| WI-09 | Public `POST /mcp`; REST uses shared parser/facts (behaviour preserved) | `McpRegressionTest`, `McpPublicAccessTest`; existing REST suites |
| WI-10 | `McpSettings`/config guard, Koin graph, `PhotonGeocoder` wiring | `ConfigValidationTest`, `McpEnabledWiringTest`, `McpDisabledWiringTest` |
| WI-11 | PostGIS search semantics through MCP | `McpSearchPostgisTest` (5 tests) |
| WI-12 | Server identity, call logging, stateless concurrency | `McpDiscoveryTest`, `McpEndpointTest` (3 tests) |

## AC coverage

| AC | Evidence |
|---|---|
| AC-1 | `McpDiscoveryTest` (initialize, exactly two tools, schemas) |
| AC-2 | `McpSearchPostgisTest.filtersAndOrdersByDistance`; `McpEndpointTest.toolsCallOverStreamableHttp` |
| AC-3 | `SearchListingsForAgentTest.placeNotResolvedIsActionable`, `geocoderOutageIsDistinctAndActionable`; live Photon test |
| AC-4 | `SearchRestaurantsToolTest` (8 validation tests); `McpSearchPostgisTest` (all filters) |
| AC-5 | `GetRestaurantToolTest` (certificate, partial halal, errors) |
| AC-6 | `McpPublicAccessTest`, `McpRateLimitTest`, `McpEnabledWiringTest` |
| AC-7 | `McpOriginTest` (no origin, listed, disallowed, empty list) |
| AC-8 | `McpRegressionTest` (byte-identical repeat, shape, no `halalItems` on REST) |
| AC-9 | Tool error tests; `McpEndpointTest.statelessConcurrentCallsSucceed` |
| AC-10 | `McpDiscoveryTest` identity; call logging in both tools; stateless concurrency |

## Implementation deltas (recorded, no design change to the contract)

| Delta | Why |
|---|---|
| Application `Json` gains `classDiscriminatorMode = NONE` | MCP messages are polymorphic; REST DTOs are not, so the REST wire is unchanged (explicit nulls stay on). |
| `installMcp` mounts the transport manually instead of calling `mcpStatelessStreamableHttp` | The SDK helper installs `ContentNegotiation` app-wide; the route-scoped alternative throws `DuplicatePluginException`. Behaviour is identical: POST-only, no session id, 405 on GET/DELETE, one server session per request. |
| `McpConfig.enableDnsRebindingProtection` (default true) | In-process Ktor test clients send no `Host` header, so unit HTTP tests disable the guard; the embedded-server ITs and production keep it on with configured hosts. |
| `isPublicRequest` admits `GET`/`DELETE /mcp` | The stateless transport answers 405; without admission the deny-by-default guard would return 401 first. |
| `:mcp` tests use `testFixtures(project(":application"))` | The fakes are shared with the application unit tests instead of duplicated. |

## Commands run (containerized)

```text
docker run ... eclipse-temurin:21-jdk ./gradlew test        # BUILD SUCCESSFUL (all modules)
LIVE_TESTS=1 ./gradlew :geocoding:test --tests '*PhotonGeocoderLiveTest*'
                                                            # PASSED against photon.komoot.io
```

`LIVE_TESTS` is forwarded by `scripts/backend-test.sh`; the default run skips the
live test so no suite depends on a third-party network.

## Deferrals

- Public "connect your agent" docs page — out of scope (G-1).
- Partial-halal exclusivity/exhaustiveness — SC-216.
- Geocode caching and shared (multi-node) rate limiting — future work, not this story.
