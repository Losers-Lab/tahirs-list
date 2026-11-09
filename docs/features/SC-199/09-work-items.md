# SC-199 — Phase 9: Work items

> **Purpose:** the slices that implement the approved design, each with its tests first · **Derived from:** 07 (names), 02 (UC steps), decisions D-1…D-5 · **Delta:** new

Backend first, per the process. No frontend change exists in the design, so there is no
frontend work item. Every WI ships in the one story PR.

| WI | Layer | Covers | Tests first | Depends on | PR |
|---|---|---|---|---|---|
| WI-1 | domain | `CoordinatePrecision`, `LatLng.roundedTo` (UC-01.3; INV-1…6) | `CoordinatePrecisionTest` (idempotent, same-cell, shift bound, deterministic) · `LatLngTest.roundedStaysInRange`, `roundedHasAtMostNDecimals` | — | #83 |
| WI-2 | application | `ListingSearchRequestParser.centre` (UC-01.3; INV-7) | `ListingSearchRequestParserTest.centreSnapsBeforeQuery`, `radiusIsUntouched` | WI-1 | #83 |
| WI-3 | application | `SearchSettings` with env parsing and the `s-maxage + swr ≤ 600` guard (INV-10; D-1, D-2) | `SearchSettingsTest.rejectsWindowAbove600`, `rejectsDecimalsOtherThan3or4`, `defaults` | — | #83 |
| WI-4 | application / MCP | `SearchListingsForAgent` snaps through the shared parser (UC-01 A4; D-4) | `SearchListingsForAgentTest.snapsCoordinatesBeforeSearch` (existing tests stay green via default precision) | WI-2 | #83 |
| WI-5 | boundary (REST) | `ListingRoutes.search` snaps; `SearchCachePolicy.applyTo` on 200 only (UC-01.3, .5–6; INV-2, INV-8, INV-9, INV-11) | `SearchCachePolicyTest.headerMatchesSettings` · `ListingSearchEndpointTest.successCarriesFreshnessHeader`, `errorResponsesStayNoStore`, `sameCellSameBody`, `boundaryShiftBoundedToPrecision`, `cacheableWithoutUserVariance` | WI-2, WI-3 | #83 |
| WI-6 | wiring | `AppConfig` gains `search: SearchSettings`; Koin wires precision into `SearchListingsForAgent` and the policy | `ConfigValidationTest.searchSettingsFromEnv` · `GeocodingWiringTest`-style wiring check for the agent's precision | WI-3, WI-4, WI-5 | #83 |
| WI-7 | OpenAPI | Description of snapping and the 200 `Cache-Control`; regenerate `frontend/src/lib/api/schema.d.ts` (`pnpm codegen:api`) | `OpenApiDocumentTest` (existing drift check) must pass | WI-5 | #83 |
| WI-8 | operations | Cloudflare Cache Rule for `/v1/listings/search*` (D-5) and a runbook note in the KB | Manual: `curl -I` twice on dev shows `cf-cache-status: HIT` on the second request for an identical URL | WI-5 deployed | #83 (runbook via KB MCP) |

## Two-way coverage

**UC steps → WIs**

| UC step | WI |
|---|---|
| UC-01.2 validation | existing (unchanged) |
| UC-01.3 snap | WI-1, WI-2, WI-5 |
| UC-01.4 query from snapped centre | WI-5 (no SQL change) |
| UC-01.5–6 mapping and header | WI-5 |
| UC-01.A4 MCP | WI-4 |
| UC-02 edge serving | WI-8 (operator), WI-5 (header) |
| E-1…E-4 errors | WI-5 (`no-store` assertions) |

**Phase 7 classes → WIs**

| Class | WI |
|---|---|
| `CoordinatePrecision` | WI-1 |
| `LatLng.roundedTo` | WI-1 |
| `ListingSearchRequestParser.centre` | WI-2 |
| `SearchSettings` | WI-3 |
| `SearchCachePolicy` | WI-5 |
| `ListingRoutes` (modified) | WI-5 |
| `SearchListingsForAgent` (unchanged source, new constructor argument) | WI-4 |
| `AppConfig` (new field) | WI-6 |
| `SecurityHeadersPlugin` (unchanged) | WI-5 test only |
| `ExposedListingSearchQuery` (unchanged) | — (regression via existing `ExposedListingSearchQueryTest`) |

## Order

WI-1 → WI-2 → WI-3 → WI-4 → WI-5 → WI-6 → WI-7 → WI-8. Each step: failing test, red
confirmed, implement, green, commit `[SC-199] …`.
