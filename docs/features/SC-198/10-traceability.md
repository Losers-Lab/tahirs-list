# SC-198 — Phase 10: Traceability

> **Purpose:** every AC to a proven test · **Derived from:** 00, 04, 06, 07, 09 · **Delta:** open matrix; PR and deploy columns fill during implementation

| AC | UC | Black-box | White-box | Classes | Tests | WI | PR | Deployed |
|---|---|---|---|---|---|---|---|---|
| AC-a | UC-01.4 · UC-02.6 | 04/UC-01 · 04/UC-02 | 06/UC-01 · 06/UC-02 | `GeocodedAddress`, `GeocodedAddresses`, `ExposedGeocodedAddressStore` | `GeocodedAddressesMigrationTest.uniqueQueryHash` · `ExposedGeocodedAddressStoreTest.saveThenFindRoundTrips` | WI-01, WI-03 | — | — |
| AC-b | UC-01.4.2–4.3 · UC-02.6 | 04/UC-01 · 04/UC-02 | 06/UC-01 · 06/UC-02 | `CachingGeocoder`, `GeocodedAddressStore` | `CachingGeocoderTest.hitReturnsStoredPlaceWithoutProviderCall` · `missThenHitCallsProviderOnce` | WI-04 | — | — |
| AC-c | UC-01.4.1 | 04/UC-01 | 06/UC-01 | `NormalizedQuery` | `NormalizedQueryTest.ignoresWhitespaceAndCase` · `collapsesInternalWhitespace` · `fullWidthFormsNormalize` | WI-02 | — | — |
| AC-d | UC-01.4.4, E2, E3 · UC-02.6 | 04/UC-01 · 04/UC-02 | 06/UC-01 · 06/UC-02 | `NormalizedQuery`, `CachingGeocoder`, `GeocodedAddress` | `NormalizedQueryTest.hashIsStable` · `knownHashVector` · `CachingGeocoderTest.noMatchIsNotCached` · `providerFailureLeavesNoCacheRow` | WI-02, WI-04 | — | — |

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
| Design approved | 2026-11-08 · commit `91fc71b` · PR #80 |
| Implementation PR | not opened yet — `feat/SC-198-cache-impl` |
| Containerized suites | not run yet |
| Deploy verification | not started — dev.tahirslist.com after merge |
