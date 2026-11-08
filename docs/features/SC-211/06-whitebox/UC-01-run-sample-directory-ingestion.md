# SC-211 — Phase 6: White-box — UC-01 Run a sample directory ingestion

> **Purpose:** how the run is served, class by class · **Derived from:** 05 + 04/UC-01 · **Delta:** new; no DB transaction (files only)

```mermaid
sequenceDiagram
  autonumber
  actor DC as Data Curator
  participant CLI as IngestionConsole
  participant RUN as RunSampleIngestion
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
  participant A as FileArtifactWriter
  DC->>CLI: 1. runSample(selection, caps)
  CLI->>RUN: execute(sources, metros, caps, cachePolicy)
  RUN->>H: 2. certifiedListing() [cache → robots → 1 req/s]
  H->>X: GET certified-listing
  X-->>H: snapshot (10.7 MB)
  H-->>RUN: 370 HmsEstablishment
  RUN->>F: 3. entries() [one public RPC]
  F->>X: POST get_public_locator_entries
  X-->>F: snapshot (313 rows)
  F-->>RUN: 313 HfsaaEntry
  RUN->>M: 4. outlets() [one REST request]
  M->>X: GET wp/v2/pages?parent=21800
  X-->>M: snapshot (76 pages)
  M-->>RUN: 76 HmaCertifiedOutlet
  RUN->>T: 5. translate(pages)
  T-->>RUN: List<SourceListing>
  loop each listing
    RUN->>MF: 6. accepts(listing)
  end
  loop each accepted listing
    RUN->>G: 7. geocode("name, address, city, region")
    G-->>RUN: GeocodedPlace? / GeocodingException
    RUN->>MC: confirm(query, place, listing)
    MC-->>RUN: MatchConfirmation | RejectReason
    RUN->>C: 8. create/mergeWith + intendedStatus/verificationBasis + handCutFor(declaration)
  end
  RUN->>R: build(counts, rejects, duplicates, gaps)
  RUN->>A: 9. writeSnapshots / writeCandidates / writeReport
  RUN-->>CLI: IngestReport
  CLI-->>DC: 10. counts + rejects
```

**Consistency:** `ING-1` — candidates written only after every fetched row is classified; geocoder outage aborts before write. `ING-3` — `VERIFIED` + `CERTIFIER_REGISTRY` iff the winning claim is Tier-1; hand-cut is TRUE only when that claim declares hand-cut, FALSE otherwise (declared not or silent), never from the tier alone. Files are atomic (temp + rename); report is the commit marker.

| Phase-4 flow | Error / outcome |
|---|---|
| A1–A5 | Per-entry listings; cache marked; shortfall in `gaps` |
| E1 | `SourceUnavailableException` → `IngestReject(SOURCE_UNAVAILABLE)`; run continues |
| E2 | `GeocodingException` → `GeocoderUnavailableException`; abort before `writeCandidates` |
| E3 | `SourceParseException` → `IngestReject(SOURCE_PARSE_FAILED)`; source skipped |
| E4 | `MetroFilter.accepts` false for all → zero for that metro |
| E5 | `WRONG_REGION` / `AMBIGUOUS_GEOCODER_MATCH` reject; place never used |
| E6 | HFSAA `401` → `discoverKey()` → retry once; otherwise `SOURCE_BLOCKED` |
| E7 | HMS blank city → `deriveCity`; null if not derivable |

**Authorization:** local operator; no HTTP surface; anonymous throttled fetches with a configured User-Agent; libraries never write to the DB.
