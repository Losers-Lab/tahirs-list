> **Purpose:** prove every WI landed and every AC is exercised · **Derived from:** 09 · **Delta:** implementation deltas recorded, no requirement changes

# 10 — Traceability: SC-209

Implementation branch: `feat/SC-209-mcp-server` · PR: [#76](https://github.com/Losers-Lab/tahirs-list/pull/76)

## Work items

| WI | Delivered | Evidence |
|---|---|---|
| WI-01 | `:mcp` module + SDK 0.16.0; stateless transport, tools-only capability | `McpDiscoveryTest` (3 tests) |
| WI-02 | `ListingSearchRequestParser`, `CertificateFacts`, agent DTOs/exceptions | `ListingSearchRequestParserTest`, `CertificateFactsTest` |
| WI-03 | `SearchListingsForAgent` (geocode / coordinates, next offset) | `SearchListingsForAgentTest` (7 tests) |
| WI-04 | `GetListingForAgent` (review lookup only when verified) | `GetListingForAgentTest` (5 tests) |
| WI-05 | `GetRestaurantTool` + detail mapper (halal items, certificate) | `GetRestaurantToolTest` (6 tests) |
| WI-06 | `SearchRestaurantsTool` + card mapper (strict args) | `SearchRestaurantsToolTest` (18 tests) |
| WI-07 | `Bucket4jMcpRateLimiter` + pipeline refusal + per-call batch budget | `McpRateLimitTest` (4 tests) |
| WI-08 | `McpOriginGuard` (exact match) + CORS/preflight | `McpOriginTest` (7 tests) |
| WI-09 | Public `POST /mcp`; REST uses shared parser/facts (behaviour preserved) | `McpRegressionTest`, `McpPublicAccessTest`; existing REST suites |
| WI-10 | `McpSettings`/config guard, Koin graph, `PhotonGeocoder` wiring | `ConfigValidationTest`, `McpEnabledWiringTest`, `McpDisabledWiringTest` |
| WI-11 | PostGIS search semantics through MCP | `McpSearchPostgisTest` (7 tests) |
| WI-12 | Server identity, stateless concurrency; call logging verified in QA; 16 KiB body cap | `McpDiscoveryTest`, `McpEndpointTest`; QA run (logs, 413) |

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
| AC-9 | Malformed JSON (sanitized), unknown tool, missing args, batch cap, body cap (413, QA); `statelessConcurrentCallsSucceed` is AC-10 |
| AC-10 | `McpDiscoveryTest` identity; stateless concurrency; call logging verified in QA (name/outcome/duration, hashed client key) |

## Implementation deltas (recorded, no design change to the contract)

| Delta | Why |
|---|---|
| Application `Json` gains `classDiscriminatorMode = NONE` | MCP messages are polymorphic; REST DTOs are not, so the REST wire is unchanged (explicit nulls stay on). |
| `installMcp` mounts the transport manually instead of calling `mcpStatelessStreamableHttp` | The SDK helper installs `ContentNegotiation` app-wide; the route-scoped alternative throws `DuplicatePluginException`. Behaviour is identical: POST-only, no session id, 405 on GET/DELETE, one server session per request. |
| `McpConfig.enableDnsRebindingProtection` (default true) | In-process Ktor test clients send no `Host` header, so unit HTTP tests disable the guard; the embedded-server ITs and production keep it on with configured hosts. |
| `isPublicRequest` admits `GET`/`DELETE /mcp` | The stateless transport answers 405; without admission the deny-by-default guard would return 401 first. |
| `:mcp` tests use `testFixtures(project(":application"))` | The fakes are shared with the application unit tests instead of duplicated. |

## Review rounds

| Round | Reviewer | Findings | Resolution |
|---|---|---|---|
| 1 | Omar (security) | 3 blocking: parser `IllegalArgumentException` not mapped to actionable tool errors; JSON-RPC batch could bypass the per-request budget; SDK parse errors echoed exception text. Nits: serializer used REST `Json` for MCP, XFF key unvalidated, no CORS/OPTIONS, untested Host allow-list, swallowed cancellation, test gaps, log hygiene. | All fixed: typed IAE catch; per-call budget token (batch bounded); sanitized parse errors (matched on `-32700` envelope, not the wrong prefix); `McpAwareJsonConverter` (MCP uses `McpJson`, REST keeps explicit nulls); CF-Connecting-IP preferred + XFF validated; exact-origin CORS + preflight; `APP_MCP_ALLOWED_HOSTS`; cancellation rethrown; new negative/CORS/batch/no-null tests; hashed/sanitized refusal logs. |
| 1 | Yusuf (QA) | 1 blocking: the parse-error sanitizer matched the wrong envelope shape, so parser text still leaked; its test passed for the wrong reason. Nits: string→number/boolean coercion, non-representable numbers yield `-32602`, thin output schemas, traceability counts overstated, no log assertion, no `deliveryOnly`/paging ITs, Q-3 sign-off unrecorded, test isolation leftovers. | Fixed: sanitizer matches `"code":-32700` and replaces the whole envelope; test asserts absence of parser internals; string primitives rejected for number/integer/boolean; schemas enriched; counts corrected; `deliveryOnly` + paging ITs added; fixture users cleaned up; test geocoder points at an unroutable port. Remaining accepted/documented: `-32602` for non-representable numbers, no automated log capture, Q-3 copy sign-off still open, ties ordered by distance only (pre-existing). |
| 2 | Omar (security) | 1 blocking: batches of *non-tool* methods could still materialise huge responses (one POST token, ~70k `tools/list` replies). Medium: `CF-Connecting-IP` preference is deployment-coupled. Lows: refusal-log volume, charset delta, 32-bit key hash, broad IAE catch, cuisine cap after decode. | Fixed: 16 KiB body cap on the anonymous transport; refusal logs at debug; salted key hash; IAE reflection scoped to parsing only; cuisine count checked before normalisation; oversized-body test. Documented: CF header is trusted only under `APP_RATELIMIT_TRUST_PROXY` (origin must be tunnel-only); `-32602` for non-representable numbers; no automated log capture. |
| 3 | Omar (security) | **Verdict: APPROVE.** No blocking. Low: residual batch amplification still 6.4 MB at a 64 KiB cap (S3-1). Low: parse-error matcher keyed on an unanchored substring, so a listing name containing `"code":-32700` could flip a 200 into a 400 (S3-2). Low: the S2-6 scoping had no regression test (S3-3). Info: 32-bit log hash (S3-4), thin oversized-body assertions (S3-5). | Fixed: cap lowered to 16 KiB (~1.6 MB worst case) and recorded as an explicit acceptance; matcher anchored to the error envelope **and** requires the outgoing `TextContent` status `400`, with `toolResultContainingParseErrorCodeIsNotRewritten` proving no false positive; `useCaseIllegalArgumentExceptionIsMasked` guards the parse/execute split; oversized test asserts the body is not a result and `largeButLegitimateBodyIsAccepted` covers the near-cap path. S3-4 accepted (debug breadcrumb); size-shaped residual documented below. |
| 2 | Yusuf (QA) | _pending_ | _pending_ |

`certificateUrl` remains gated by the REST proxy (verified + approved + archived);
existence in object storage is checked at fetch time, so a since-deleted archive can
still 404. Accepted as best-effort; no data leak.

Open items carried to the owner:
- **Q-3** (LLM-facing wording, Fatima) has no recorded sign-off; the shipped text
  matches the approved `07` literals.
- Non-representable JSON numbers (`1e400`) are refused by the SDK as `-32602`
  before the tool runs; AC-4's "tool error" applies to representable values.
- `ExposedListingSearchQuery` orders by distance only (pre-existing); exact ties
  are not order-stable. Not introduced here.
- **Accepted residual (S3-1):** a 16 KiB POST can still expand to roughly 1.6 MB
  of `tools/list` responses (constant, no longer scaling with attacker bandwidth).
  The largest legitimate body is ~1.6 KB. Tightening further would need a
  batch-count or response-size cap in the SDK.
- **Accepted (S2-4):** the SDK decodes request bodies as UTF-8 regardless of a
  declared charset; a non-UTF-8 body fails closed with the sanitized parse error.
- **Deployment invariant (S2-2):** `APP_RATELIMIT_TRUST_PROXY=true` trusts
  `CF-Connecting-IP`/`X-Forwarded-For`, so the origin must be reachable only
  through the tunnel/proxy; otherwise a direct caller can choose its bucket key.

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
