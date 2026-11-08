# SC-198 — Phase 10: Traceability

> **Purpose:** every AC to a proven test · **Derived from:** 00, 04, 06, 07, 09 · **Delta:** ACs proven; PR filled; deploy column open

| AC | UC | Black-box | White-box | Classes | Tests | WI | PR | Deployed |
|---|---|---|---|---|---|---|---|---|
| AC-a | UC-01.4 · UC-02.6 | 04/UC-01 · 04/UC-02 | 06/UC-01 · 06/UC-02 | `GeocodedAddress`, `GeocodedAddresses`, `ExposedGeocodedAddressStore` | `GeocodedAddressesMigrationTest` "V23 creates…" ✅ · "normalized_query_hash is the primary key" ✅ · `ExposedGeocodedAddressStoreTest` round-trip ✅ · duplicate/out-of-range rejected ✅ | WI-01, WI-03 | #81 | — |
| AC-b | UC-01.4.2–4.3 · UC-02.6 | 04/UC-01 · 04/UC-02 | 06/UC-01 · 06/UC-02 | `CachingGeocoder`, `GeocodedAddressStore` | `CachingGeocoderTest` "missThenHitCallsProviderOnce" ✅ · "hitReturnsStoredPlaceWithoutProviderCall" ✅ · "lookupAndWriteUseSameKey" ✅ | WI-04 | #81 | — |
| AC-c | UC-01.4.1 | 04/UC-01 | 06/UC-01 | `NormalizedQuery` | `NormalizedQueryTest` "ignores case and surrounding whitespace" ✅ · "collapses internal whitespace" ✅ · "folds compatibility forms before hashing" ✅ | WI-02 | #81 | — |
| AC-d | UC-01.4.4, E2, E3 · UC-02.6 | 04/UC-01 · 04/UC-02 | 06/UC-01 · 06/UC-02 | `NormalizedQuery`, `CachingGeocoder`, `GeocodedAddress` | `NormalizedQueryTest` "hash is stable across calls" ✅ · "pins the algorithm with a known vector" ✅ · `CachingGeocoderTest` "noMatchIsNotCached" ✅ · "providerFailureLeavesNoCacheRow" ✅ | WI-02, WI-04 | #81 | — |

Empty cell = finding, not formatting.

## Decisions with their own proof

| Decision | Obligation | Tests | WI |
|---|---|---|---|
| D-2 | Pre-flight never reads the cache | `GeocodingWiringTest` "healthProbeBypassesCache" ✅ | WI-05 |
| D-6 | A row older than 90 days is not served and is refreshed in place | `CachingGeocoderTest` "expiredRowIsRefetchedAndRefreshed" ✅ · `ExposedGeocodedAddressStoreTest` "saveRefreshesStaleRow" ✅ · "oldRowIsNotServed" ✅ | WI-03, WI-04 |
| D-7 | Store read/write failure degrades, never fails the request | `CachingGeocoderTest` "readFailureFallsBackToProvider" ✅ · "writeFailureStillReturnsPlace" ✅ | WI-04 |

## Status

| Item | State |
|---|---|
| Design approved | 2026-11-08 · commit `91fc71b` · PR #80 (merged) |
| Implementation PR | #81 · `feat/SC-198-cache-impl` · WI-01…WI-05 done |
| Containerized suites | **full backend suite from scratch after merging main: 832 passed, 0 failed** (`gradlew test --rerun-tasks`, 67/67 tasks, `eclipse-temurin:21-jdk`, 2026-11-08) |
| Reviews | `[omar]` APPROVE · `[yusuf]` APPROVE — round 2, both blocking findings fixed |
| Deploy verification | not started — dev.tahirslist.com after merge |

## Deferrals (filed, not TODOs)

| Story | Scope |
|---|---|
| SC-221 | Purge and minimize the geocode cache: deletion path, `display_name` retention/ODbL decision, provider-swap runbook |
| SC-222 | Cache failure hardening: narrow fail-open (DB timeout vs cancellation), log amplification, hash-format CHECK, provider label from composition |
