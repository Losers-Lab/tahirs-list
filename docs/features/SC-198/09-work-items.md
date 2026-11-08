# SC-198 — Phase 9: Work items

> **Purpose:** the implementation slices, in merge order · **Derived from:** 07, 08 (approved) · **Delta:** 6 backend WIs; no frontend work

Backend only — the cache has no API or UI surface. Each WI is one review-sized slice (all six ship in PR #81), tests first.

> **State 2026-11-08:** WI-01…WI-05 implemented; the full containerized backend suite is green (649 passed, 0 failed). WI-06 evidence is in `10-traceability.md`.

| WI | Layer | Covers | Tests first | Depends on | PR |
|---|---|---|---|---|---|
| **WI-01** | persistence | AC-a · table + checks | `GeocodedAddressesMigrationTest.uniqueQueryHash` · `rejectsOutOfRangePoint` | — | #81 |
| **WI-02** | application | AC-c, AC-d · normalization + key | `NormalizedQueryTest.ignoresWhitespaceAndCase` · `collapsesInternalWhitespace` · `fullWidthFormsNormalize` · `hashIsStable` · `knownHashVector` | — | #81 |
| **WI-03** | application + persistence | AC-a, INV-11 · store port + adapter | `ExposedGeocodedAddressStoreTest.saveThenFindRoundTrips` · `saveRefreshesStaleRow` · `oldRowIsNotServed` | WI-01 | #81 |
| **WI-04** | application | AC-b, AC-d, INV-9 · decorator | `CachingGeocoderTest.missThenHitCallsProviderOnce` · `hitReturnsStoredPlaceWithoutProviderCall` · `noMatchIsNotCached` · `providerFailureLeavesNoCacheRow` · `expiredRowIsRefetchedAndRefreshed` · `lookupAndWriteUseSameKey` · `readFailureFallsBackToProvider` · `writeFailureStillReturnsPlace` | WI-02, WI-03 | #81 |
| **WI-05** | bootstrap | D-2, INV-7 · bindings + config | `GeocodingWiringTest.healthProbeBypassesCache` | WI-04 | #81 |
| **WI-06** | all | verification + evidence | containerized `backend-test.sh`; traceability filled; dev smoke | WI-05 | #81 |

## Two-way coverage — use case steps

| UC step | WI |
|---|---|
| UC-01.4.1 (normalize, hash) | WI-02 |
| UC-01.4.2–4.3 (read, fresh hit) | WI-03, WI-04 |
| UC-01.4.4 (provider, store, stale refresh) | WI-04 |
| UC-01.E5 / E6 (store read/write fail) | WI-04 |
| UC-02.3 (pre-flight bypass) | WI-05 |
| UC-02.6a–6d (per-row cache) | WI-04 |
| UC-02.E2 / E3 (provider fail, no match) | WI-04 (unchanged provider semantics) |
| UC-01.5–7, UC-02.4–5, 6e, 7–8 | no code change — existing SC-209/SC-211 paths |

## Two-way coverage — Phase 7 classes

| Class / object | WI |
|---|---|
| `GeocodedAddresses` (Exposed) · `V22__sc_198_geocoded_addresses.sql` | WI-01 |
| `NormalizedQuery` | WI-02 |
| `GeocodedAddress` · `GeocodedAddressStore` · `ExposedGeocodedAddressStore` | WI-03 |
| `CachingGeocoder` | WI-04 |
| `GeocodingQualifiers.UPSTREAM` · `AppModules` wiring · TTL config | WI-05 |

## Per-item notes

- **WI-01** — the only schema change. `created_at` carries both first-sighting and freshness; no extra index beyond the primary key.
- **WI-02** — pure Kotlin, no I/O. `knownHashVector` pins the exact SHA-256 so a silent normalization change breaks the build.
- **WI-03** — `findByQueryHash(hash, notBefore)` filters by `created_at`; `save` is an upsert that refreshes the row. Own short transactions, one statement each.
- **WI-04** — the decorator only; no framework, no socket. Fake store + fake provider cover every branch, including fail-open.
- **WI-05** — two bindings of `GeocoderPort`: cached (default) and `@Named(UPSTREAM)`. `CheckGeocoderHealth` gets the upstream binding.
- **WI-06** — no CI; record the containerized suite output as evidence. Deploy verification follows the merge, per the workflow.
