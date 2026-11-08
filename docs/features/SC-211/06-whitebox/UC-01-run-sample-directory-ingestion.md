# SC-211 — Phase 6: White-box — UC-01 Run a sample directory ingestion

> **Purpose:** how the run is served, class by class · **Derived from:** 05 + 04/UC-01 · **Delta:** new; no DB transaction (files only)

```mermaid
sequenceDiagram
  autonumber
  actor DC as Data Curator
  actor HMSAPI as HMS (external)
  actor HFSAAAPI as HFSAA (external)
  actor HMAAPI as HMA (external)
  actor PHAPI as Photon API
  participant CLI as IngestionConsole
  participant RUN as RunSampleIngestion
  participant AR as FileRunArtifacts
  participant PRE as CheckGeocoderHealth
  participant X as CertifierHttp
  participant H as HmsDirectory
  participant F as HfsaaLocator
  participant M as HmaCertifiedOutlets
  participant T as Translators
  participant MF as MetroFilter
  participant G as PhotonGeocoder
  participant MC as MatchConfirmationPolicy
  participant C as IngestionCandidate
  participant R as IngestReport
  DC->>CLI: 1. runSample(selection, caps, onPending)
  CLI->>RUN: execute(sources, metros, caps, cachePolicy, onPending)
  RUN->>AR: 2. begin(runId, onPending) [list pending runs — abort or archive]
  alt pending run and policy = ABORT
    AR-->>RUN: PendingRunsException
    RUN-->>CLI: pending ids
    CLI-->>DC: review/load, or re-run with --on-pending=archive
  end
  RUN->>PRE: 3. execute() [test query "New York, USA"]
  PRE->>G: geocode(probe)
  G->>PHAPI: GET /api?q=...
  PHAPI-->>G: place / no match / outage
  G-->>PRE: GeocodedPlace? / GeocodingException
  PRE-->>RUN: Available | Unavailable
  alt pre-flight failed
    RUN-->>CLI: GeocoderUnavailableException
    CLI-->>DC: abort — no fetch, incomplete run only
  end
  RUN->>H: 4. certifiedListing() [cache → robots → 1 req/s]
  H->>X: GET certified-listing
  X->>HMSAPI: HTTP GET
  HMSAPI-->>X: page / failure
  X-->>H: snapshot (10.7 MB)
  H-->>RUN: 370 HmsEstablishment
  RUN->>F: 5. entries() [one public RPC]
  F->>X: POST get_public_locator_entries
  X->>HFSAAAPI: HTTP POST
  HFSAAAPI-->>X: rows / 401
  X-->>F: snapshot (313 rows)
  F-->>RUN: 313 HfsaaEntry
  RUN->>M: 6. outlets() [one REST request]
  M->>X: GET wp/v2/pages?parent=21800
  X->>HMAAPI: HTTP GET
  HMAAPI-->>X: pages / failure
  X-->>M: snapshot (76 pages)
  M-->>RUN: 76 HmaCertifiedOutlet
  RUN->>T: 7. translate(pages)
  T-->>RUN: List<SourceListing>
  loop each listing
    RUN->>MF: 8. accepts(listing)
  end
  loop each accepted listing
    RUN->>G: 9. geocode("name, address, city, region")
    G->>PHAPI: GET /api?q=...
    PHAPI-->>G: place / no match / outage
    G-->>RUN: GeocodedPlace? / GeocodingException
    RUN->>MC: confirm(query, place, listing)
    MC-->>RUN: MatchConfirmation | RejectReason
    RUN->>C: 10. create/mergeWith + intendedStatus/verificationBasis + handCutFor(declaration)
  end
  RUN->>R: build(counts, rejects, duplicates, gaps)
  RUN->>AR: 11. complete(runId, snapshots, candidates, report) [run.json written last]
  AR-->>RUN: PendingRun (immutable dir + checksums)
  RUN-->>CLI: IngestReport
  CLI-->>DC: 12. counts + rejects
```

**Consistency:** `ING-1` — no directory request happens until the pending-run guard and the geocoder pre-flight pass; every URL (including redirect targets) is HTTPS and allow-listed; candidates are written only after every fetched row is classified. `ING-3` — `VERIFIED` + `CERTIFIER_REGISTRY` iff the winning claim is Tier-1; hand-cut is TRUE only when that claim declares hand-cut, FALSE otherwise (declared not or silent), never from the tier alone. Files are atomic (temp + rename); `run.json` is the commit marker. A run directory without `run.json` is incomplete and is never reported as pending.

| Phase-4 flow | Error / outcome |
|---|---|
| A1–A5 | Per-entry listings; cache marked; shortfall in `gaps` |
| P1 | Pending run + `ABORT` → `PendingRunsException` before any fetch; `ARCHIVE` moves each to a unique `archive/<runId>-<timestamp>-<token>/` |
| P2 | Pre-flight `Unavailable` → `GeocoderUnavailableException`; abort before any fetch; no manifest |
| E1 | `SourceUnavailableException` → `IngestReject(SOURCE_UNAVAILABLE)`; run continues |
| E2 | `GeocodingException` mid-run → `GeocoderUnavailableException`; abort before `complete`, so no manifest |
| E3 | `SourceParseException` → `IngestReject(SOURCE_PARSE_FAILED)`; source skipped |
| E4 | `MetroFilter.accepts` false for all → zero for that metro |
| E5 | `WRONG_REGION` / `AMBIGUOUS_GEOCODER_MATCH` reject; place never used |
| E6 | HFSAA `401` → `discoverKey()` → retry once; `403` → `SOURCE_BLOCKED` immediately (never re-discover); otherwise NYC draws from HMS only |
| E7 | HMS blank city → `deriveCity`; null if not derivable |
| E8 | Off-allow-list host, plain HTTP, redirect escape, oversized body, or denied robots → blocked/too-large, source fails honestly; no partial trust data |
| E9 | `candidates.json` tampered after completion → the load's checksum check refuses it |

**Authorization:** local operator; no HTTP surface; anonymous throttled fetches with a configured User-Agent; libraries never write to the DB.
