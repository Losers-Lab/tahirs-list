# SC-199 — Phase 10: Traceability

> **Purpose:** every AC and decision traced to a test and a commit · **Derived from:** 09 · **Delta:** new
> Branch `feat/SC-199-search-quantization` · **PR #85** · Design: PR #83 (merged `9b3091f`) · Head at review: `c152e57` before the F1–F6 fixes

## Decisions → implementation

| Decision | Implemented by | Proven by |
|---|---|---|
| D-1 4 dp (3 allowed) | `CoordinatePrecision`, `APP_SEARCH_CENTRE_DECIMALS` in `AppConfig` | `CoordinatePrecisionTest` · `SearchSettingsFromEnvTest` |
| D-2 window 60/300/60, ≤ 600 s on both channels | `SearchSettings.init` + `cacheControl()` | `SearchSettingsTest` · `SearchSettingsFromEnvTest` |
| D-3 option A (no origin cache) | no component added; the OpenAPI text states the limit | design (00, 08) |
| D-4 both channels | `ListingSearchRequestParser.centre` used by `ListingRoutes` and `SearchListingsForAgent` | `ListingSearchRequestParserTest` · `SearchListingsForAgentTest` |
| D-5 Cache Rule | operator step, outside the repo | **not applied**; WI-8 open |

## ACs → tests

| AC | Test | Where |
|---|---|---|
| AC-1 centre snapped | `CoordinatePrecisionTest.snap keeps the configured number of decimals` · `ListingSearchRequestParserTest.centre snaps the search centre` | `:domain` · `:application` |
| AC-2 freshness header | `ListingSearchEndpointTest.a successful search advertises the freshness window` | `:bootstrap` |
| AC-3 distance/boundary from snapped centre | `CoordinatePrecisionTest.the snapped shift never exceeds the advertised maximum`; boundary flip reproduced live by QA (see below) | `:domain` · QA |
| AC-4 basin-equal bodies | `ListingSearchEndpointTest.two centres in one 4 dp rounding basin return the same body` · `CoordinatePrecisionTest.two centres in one rounding basin snap to the same centre` | `:bootstrap` · `:domain` |
| AC-5 errors stay `no-store` | `ListingSearchEndpointTest.an error response keeps no-store…` · `ErrorMappingTest.an error replaces an opt-in cache policy…` · existing `SecurityHeadersTest` | `:bootstrap` · `:web-api` |
| AC-6 no cross-URL hit | guaranteed by construction (the cache key is the client URL) | design |

## Invariants → tests

| Invariant | Test |
|---|---|
| INV-1 idempotent | `CoordinatePrecisionTest.snapping is idempotent` · `LatLngRoundingTest.a value already at the precision is unchanged` |
| INV-2 one basin | `CoordinatePrecisionTest.two centres in one rounding basin snap to the same centre` |
| INV-3 shift bound | `CoordinatePrecisionTest.the snapped shift never exceeds the advertised maximum` |
| INV-4 WGS84 after rounding | `LatLngRoundingTest.rounding near a pole / the antimeridian stays inside WGS84` |
| INV-5 fraction digits | `LatLngRoundingTest.the rounded result has at most N fractional digits` |
| INV-6 deterministic | `CoordinatePrecisionTest.snapping is deterministic` |
| INV-7 radius untouched | `ListingSearchRequestParserTest.centre leaves the radius untouched` |
| INV-8 returned distance within the shift | **QA spot check**, no unit assertion: delta 0.001458 mi against a 0.004892 mi bound |
| INV-9 header on 200 only | `ListingSearchEndpointTest.a successful search…` · `…an error response keeps no-store` · `ErrorMappingTest` |
| INV-10 staleness bound | `SearchSettingsTest` (max-age+swr and s-maxage+swr each ≤ 600, overflow rejected) · `SearchSettingsFromEnvTest` |
| INV-11 user-invariant body | `ListingSearchEndpointTest.the cacheable search response carries no cookie and no per-caller variance` |
| INV-12 basin-equal bodies | `ListingSearchEndpointTest.two centres in one 4 dp rounding basin…` |

## Suite evidence

| Run | Result |
|---|---|
| `:domain` + `:application` after WI-1…4 | green |
| `ListingSearchEndpointTest`, forced `--rerun-tasks` | 30/30, then 32/32 after the review fixes |
| `:mcp` `:application` `:web-api` `:domain` after WI-6 | green |
| Full suite `--rerun-tasks`, merged with `main` (pre-review head) | **958 tests, 0 failures, 0 errors, 1 skipped**, 8m46s |
| Full suite on the review-fix tree (committed as `0bdc7b8`) | **966 tests, 0 failures, 0 errors, 1 skipped**, 6m40s |
| Red evidence | QA reverted each implementation file in turn and observed 14 targeted failures before restoring |

## Review findings

| # | Reviewer | Outcome |
|---|---|---|
| 1 | omar | max-age outside the 600 s bound — **fixed** (`SearchSettings`, env test) |
| 2 | omar | cached `verificationStatus` vs the KB's 5 s verified-freshness target — **open: founder decision D-6** |
| 3 | omar | Long overflow in the guard — **fixed** (subtraction check + test) |
| 4 | omar | an opted-in header surviving onto an error — **fixed** (body first, plugin adds `no-store` on non-2xx + test) |
| 5 | omar | no variance assertions — **fixed** (`Set-Cookie`/`Vary`/body-equality test) |
| 6 | omar | absolute URLs from the request Host in a public body — **accepted**: the edge keys on host; recorded in the runbook note |
| 7 | omar | unparseable cache env values defaulted silently — **fixed** (`seconds()`, test) |
| 8 | omar / yusuf | `radius=Infinity` accepted (pre-existing) — **not fixed**; follow-up story |
| 9 | omar | no NaN centre test — **fixed** (`a non-finite centre returns 400`) |
| F1 | yusuf | "cell" overstated what rounding merges — **fixed**: wording is now "rounding basin" in 00, 01, 02, 04, 08, code docs and test names |
| F2 | yusuf | radius `Infinity` (same as finding 8) — follow-up story |
| F3 | yusuf | a late 500 keeps `public` alongside `no-store` (RFC 9111 makes it unstorable) — **accepted**, see finding 4 |
| F4 | yusuf | traceability drift (PR number, test names, non-existent OpenAPI test) — **fixed** |
| F5 | yusuf | no proof the config reaches the route — **fixed** (`ListingSearchWiringTest` boots at 3 dp) |
| F6 | yusuf | INV-8 evidence — **recorded above** |
| N1 | omar (round 2) | D-6's premise was false: the certificate/image proxies are `public, max-age=86400`, so the authoritative certificate surface already exceeds the 5 s target by 24 h — **corrected**; the 24 h cache is a pre-existing gap that needs its own story |
| N2 | omar (round 2) | `max-age + swr` could exceed 600 s under extreme config — **fixed** (both channels bounded together, test added) |
| N3 | omar (round 2) | docs said an error carries no freshness header — **fixed** in 02 BR-4 and 06/UC-02 (a late 500 carries both; `no-store` wins) |
| N4 | omar (round 2) | stale test-name citations in 01/07/09 — **fixed** to the names that exist |
| N5 | omar (round 2) | the 966-test run was not tied to a commit — **fixed** above |
| N6 | omar (round 2) | orphaned KDoc in `AppConfig` — **fixed** |

## Not done

- WI-8 (Cloudflare Cache Rule) is an operator action on the Cloudflare zone; not applied from this branch.
- No frontend change: the frontend does not call `/v1/listings/search` yet.
- Option B (origin cache keyed on the snapped request) is not implemented (D-3 A); recommended follow-up.
- No test covers the MCP channel's *wiring* of the precision (the use case's own snapping is tested; `AppModules` passing `config.search` is verified by reading, not by a test).
