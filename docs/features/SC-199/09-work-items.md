# SC-199 — Phase 9: Work items

> **Purpose:** the slices that implement the approved design, each with its tests first · **Derived from:** 07 (names), 02 (UC steps), decisions D-1…D-5 · **Delta:** new

Backend first, per the process. No frontend change exists in the design, so there is no
frontend work item. Every WI ships in the one story PR.

| WI | Layer | Covers | Tests first | Depends on | PR |
|---|---|---|---|---|---|
| WI-1 | domain | `CoordinatePrecision`, `LatLng.roundedTo` (UC-01.3; INV-1…6) | `CoordinatePrecisionTest.snapping is idempotent`, `two centres in one rounding basin…`, `the snapped shift never exceeds…`, `snapping is deterministic` · `LatLngRoundingTest.rounding near a pole / the antimeridian…`, `…at most N fractional digits` | — | #85 |
| WI-2 | application | `ListingSearchRequestParser.centre` (UC-01.3; INV-7) | `ListingSearchRequestParserTest.centre snaps the search centre before the query`, `centre leaves the radius untouched` | WI-1 | #85 |
| WI-3 | application | `SearchSettings` with a bound on both channels and their stale-while-revalidate (INV-10; D-1, D-2) | `SearchSettingsTest.a browser max-age above 600 s is rejected`, `max-age and stale-while-revalidate are bounded together`, `defaults are 4 dp…` | — | #85 |
| WI-4 | application / MCP | `SearchListingsForAgent` snaps through the shared parser (UC-01 A4; D-4) | `SearchListingsForAgentTest.snapsCoordinatesBeforeSearch…`, `snapsGeocodedCentreTooAtConfiguredPrecision` | WI-2 | #85 |
| WI-5 | boundary (REST) | `ListingRoutes.search` snaps; `SearchCachePolicy.applyTo` on 200 only (UC-01.3, .5–6; INV-2, INV-8, INV-9, INV-11) | `ListingSearchEndpointTest.a successful search advertises the freshness window`, `an error response keeps no-store…`, `two centres in one 4 dp rounding basin return the same body`, `the cacheable search response carries no cookie…`, `a non-finite centre returns 400…` · `ErrorMappingTest.an error replaces an opt-in cache policy…` | WI-2, WI-3 | #85 |
| WI-6 | wiring | `AppConfig` gains `search: SearchSettings`; Koin passes the precision to `SearchListingsForAgent` and the settings to `listingRoutes` | `SearchSettingsFromEnvTest` (env parsing, both bounds) · `ListingSearchWiringTest` (a 3 dp boot differs from the 4 dp default) | WI-3, WI-4, WI-5 | #85 |
| WI-7 | OpenAPI | Description of snapping and the 200 `Cache-Control` | JSON validity checked; **no drift test exists in the repo** (QA F4) | WI-5 | #85 |
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
