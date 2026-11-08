# SC-198 — Phase 10: Traceability

> **Purpose:** every AC to a proven test · **Derived from:** 00, 04, 06, 07, 09 · **Delta:** open matrix; PR and deploy columns fill during implementation

| AC | UC | Black-box | White-box | Classes | Tests | WI | PR | Deployed |
|---|---|---|---|---|---|---|---|---|
| AC-a | UC-01.4 · UC-02.6 | 04/UC-01 · 04/UC-02 | 06/UC-01 · 06/UC-02 | `GeocodedAddress`, `GeocodedAddresses`, `ExposedGeocodedAddressStore` | `GeocodedAddressesMigrationTest` "V22 creates…" ✅ · "normalized_query_hash is the primary key" ✅ · `ExposedGeocodedAddressStoreTest` (WI-03) | WI-01, WI-03 | #81 | — |
| AC-b | UC-01.4.2–4.3 · UC-02.6 | 04/UC-01 · 04/UC-02 | 06/UC-01 · 06/UC-02 | `CachingGeocoder`, `GeocodedAddressStore` | `CachingGeocoderTest.hitReturnsStoredPlaceWithoutProviderCall` · `missThenHitCallsProviderOnce` | WI-04 | — | — |
| AC-c | UC-01.4.1 | 04/UC-01 | 06/UC-01 | `NormalizedQuery` | `NormalizedQueryTest` "ignores case and surrounding whitespace" ✅ · "collapses internal whitespace" ✅ · "folds compatibility forms before hashing" ✅ | WI-02 | #81 | — |
| AC-d | UC-01.4.4, E2, E3 · UC-02.6 | 04/UC-01 · 04/UC-02 | 06/UC-01 · 06/UC-02 | `NormalizedQuery`, `CachingGeocoder`, `GeocodedAddress` | `NormalizedQueryTest` "hash is stable across calls" ✅ · "pins the algorithm with a known vector" ✅ · `CachingGeocoderTest` (WI-04) | WI-02, WI-04 | #81 | — |

Empty cell = finding, not formatting.

## Decisions with their own proof

| Decision | Obligation | Tests | WI |
|---|---|---|---|
| D-2 | Pre-flight never reads the cache | `GeocodingWiringTest.healthProbeBypassesCache` | WI-05 |
| D-6 | A row older than 90 days is not served and is refreshed in place | `CachingGeocoderTest.expiredRowIsRefetchedAndRefreshed` · `ExposedGeocodedAddressStoreTest.saveRefreshesStaleRow` · `oldRowIsNotServed` | WI-03, WI-04 |
| D-7 | Store read/write failure degrades, never fails the request | `CachingGeocoderTest.readFailureFallsBackToProvider` · `writeFailureStillReturnsPlace` | WI-04 |

## Status

| Item | State |
|---|---|
| Design approved | 2026-11-08 · commit `91fc71b` · PR #80 (merged) |
| Implementation PR | #81 (draft) · `feat/SC-198-cache-impl` · WI-01, WI-02 green |
| Containerized suites | targeted tests green; full `./scripts/backend-test.sh` not run yet |
| Deploy verification | not started — dev.tahirslist.com after merge |
