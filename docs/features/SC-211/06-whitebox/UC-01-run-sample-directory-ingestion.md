# SC-211 — Phase 6: White-box — UC-01 Run a sample directory ingestion

> **Purpose:** how the run is served, class by class · **Derived from:** 05 + 04/UC-01 · **Delta:** new; no DB transaction (files only)

```mermaid
sequenceDiagram
  autonumber
  actor DC as Data Curator
  participant CLI as IngestionConsole
  participant RUN as RunSampleIngestion
  participant H as HmsDirectoryAdapter
  participant F as HfsaaDirectoryAdapter
  participant M as HmaDirectoryAdapter
  participant MF as MetroFilter
  participant G as PhotonGeocoder
  participant MC as MatchConfirmationPolicy
  participant C as IngestionCandidate
  participant R as IngestReport
  participant A as FileArtifactWriter
  DC->>CLI: 1. runSample(selection, caps)
  CLI->>RUN: execute(sources, metros, caps, cachePolicy)
  RUN->>H: 2. fetch(metro) [HalalDirectorySource]
  H-->>RUN: List<SourceListing> + snapshot
  RUN->>F: 3. fetch(metro) [discover chapters]
  F-->>RUN: List<SourceListing> + snapshot
  RUN->>M: 4. fetch(metro) [list + details]
  M-->>RUN: List<SourceListing> + snapshot
  loop each listing
    RUN->>MF: 5. accepts(listing)
  end
  loop each accepted listing
    RUN->>G: 6. geocode("name, address, city, region")
    G-->>RUN: GeocodedPlace? / GeocodingException
    RUN->>MC: confirm(query, place, listing)
    MC-->>RUN: MatchConfirmation | RejectReason
    RUN->>C: 7. create/mergeWith + set intendedStatus/verificationBasis
  end
  RUN->>R: build(counts, rejects, duplicates, gaps)
  RUN->>A: 8. writeSnapshots / writeCandidates / writeReport
  RUN-->>CLI: IngestReport
  CLI-->>DC: 9. counts + rejects
```

**Consistency:** `ING-1` — candidates written only after every fetched row is classified; geocoder outage aborts before write. `ING-3` — `VERIFIED` + `CERTIFIER_REGISTRY` iff the winning claim is Tier-1; else `UNVERIFIED`. Files are atomic (temp + rename); report is the commit marker.

| Phase-4 flow | Error / outcome |
|---|---|
| A1–A4 | Per-entry listings; fallback recorded; shortfall in `gaps`; cache marked |
| E1 | `SourceUnavailableException` → `IngestReject(SOURCE_UNAVAILABLE)`; run continues |
| E2 | `GeocodingException` → `GeocoderUnavailableException`; abort before `writeCandidates` |
| E3 | `SourceParseException` → `IngestReject(SOURCE_PARSE_FAILED)`; source skipped |
| E4 | `MetroFilter.accepts` false for all → zero for that metro |
| E5 | `WRONG_REGION` / `AMBIGUOUS_GEOCODER_MATCH` reject; place never used |

**Authorization:** local operator; no HTTP surface; anonymous throttled fetches with a configured User-Agent; adapters never write to the DB.
