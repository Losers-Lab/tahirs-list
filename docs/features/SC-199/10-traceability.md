# SC-199 — Phase 10: Traceability

> **Purpose:** every AC and decision traced to a test and a commit · **Derived from:** 09 · **Delta:** new
> Branch `feat/SC-199-search-quantization` · PR #84 · Design: PR #83 (merged `9b3091f`)

## Decisions → implementation

| Decision | Implemented by | Proven by |
|---|---|---|
| D-1 4 dp (3 allowed) | `CoordinatePrecision`, `APP_SEARCH_CENTRE_DECIMALS` in `AppConfig` | `CoordinatePrecisionTest` · `SearchSettingsFromEnvTest` |
| D-2 window 60/300/60, ≤ 600 s | `SearchSettings` (`init` guard, `cacheControl()`) | `SearchSettingsTest` · `SearchSettingsFromEnvTest` |
| D-3 option A (no origin cache) | no component added; the OpenAPI text states the limit | `ListingSearchEndpointTest` (edge-key behaviour documented) |
| D-4 both channels | `ListingSearchRequestParser.centre` used by `ListingRoutes` and `SearchListingsForAgent` | `ListingSearchRequestParserTest` · `SearchListingsForAgentTest` |
| D-5 Cache Rule | operator step, outside the repo | manual dev check (below) |

## ACs → tests

| AC | Test | Where |
|---|---|---|
| AC-1 centre snapped | `CoordinatePrecisionTest.snapsToConfiguredDecimals` · `ListingSearchRequestParserTest.centre snaps the search centre` | `:domain` · `:application` |
| AC-2 freshness header | `ListingSearchEndpointTest.a successful search advertises the freshness window` | `:bootstrap` |
| AC-3 distance/boundary from snapped centre | `CoordinatePrecisionTest.the snapped shift never exceeds the advertised maximum` | `:domain` |
| AC-4 cell-equal bodies | `ListingSearchEndpointTest.two centres in one 4 dp cell return the same body` · `CoordinatePrecisionTest.two centres in one cell snap to the same centre` | `:bootstrap` · `:domain` |
| AC-5 errors stay `no-store` | `ListingSearchEndpointTest.an error response keeps no-store` · existing `SecurityHeadersTest` | `:bootstrap` |
| AC-6 no cross-cell hit | guaranteed by construction (the cache key is the client URL); stated in `00`/`08` | design |

## Invariants → tests

| Invariant | Test |
|---|---|
| INV-1 idempotent | `CoordinatePrecisionTest.snapping is idempotent` · `LatLngRoundingTest.a value already at the precision is unchanged` |
| INV-2 same cell | `CoordinatePrecisionTest.two centres in one cell snap to the same centre` |
| INV-3 shift bound | `CoordinatePrecisionTest.the snapped shift never exceeds the advertised maximum` |
| INV-4 WGS84 after rounding | `LatLngRoundingTest.rounding near a pole / the antimeridian stays inside WGS84` |
| INV-5 fraction digits | `LatLngRoundingTest.the rounded result has at most N fractional digits` |
| INV-6 deterministic | `CoordinatePrecisionTest.snapping is deterministic` |
| INV-7 radius untouched | `ListingSearchRequestParserTest.centre leaves the radius untouched` |
| INV-9 header on 200 only | `ListingSearchEndpointTest.a successful search…` · `…an error response keeps no-store` |
| INV-10 staleness bound | `SearchSettingsTest.a window whose s-maxage plus… exceeds 600 s is rejected` · `SearchSettingsFromEnvTest.rejects a freshness window above the 600 s bound` |

INV-8 (per-result distance within the shift) and INV-11/12 (user-invariant, byte-identical
bodies) are covered by AC-3 and AC-4 respectively; INV-8 has no separate assertion, which is
recorded as a residual rather than claimed.

## Suite evidence

| Run | Result |
|---|---|
| `:domain` + `:application` after WI-1…4 | green |
| `:bootstrap` `ListingSearchEndpointTest` after WI-5 (forced `--rerun-tasks`) | 30/30 pass |
| `:mcp` `:application` `:web-api` `:domain` after WI-6 | green |
| Full `test --rerun-tasks`, merged with `main` | see the PR body |

## Not done

- WI-8 (Cloudflare Cache Rule) is an operator action on the Cloudflare zone; it is not applied from this branch.
- No frontend change: the frontend does not call `/v1/listings/search` yet.
- Option B (origin cache keyed on the snapped request) is not implemented (D-3 A); recommended as a follow-up story.
