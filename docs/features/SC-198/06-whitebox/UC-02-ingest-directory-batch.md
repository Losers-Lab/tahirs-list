# SC-198 — Phase 6: White-box sequence, UC-02 Run a sample directory ingestion

> **Purpose:** the classes, operations, transactions and stores behind 04/UC-02 · **Derived from:** 05 · **Delta:** pre-flight bypasses the cache; per-row geocoding is cached
> **Security:** operator-initiated local run (SC-211); no HTTP identity. Address text is never stored — only its hash. Reads/writes are parameter-bound.

## 1. Pre-flight (uncached), then cached per-row geocoding

```mermaid
sequenceDiagram
  autonumber
  actor DC as Data Curator
  actor PH as Photon
  participant B as IngestionConsole
  participant R as RunSampleIngestion
  participant H as CheckGeocoderHealth
  participant CG as CachingGeocoder
  participant PG as PhotonGeocoder
  participant ST as ExposedGeocodedAddressStore
  participant AW as FileRunArtifacts
  participant DB@{ "type": "database" } as PostgreSQL

  DC->>B: runSample(metros, caps)
  B->>R: execute(selection)

  Note over R,PG: pre-flight — wired to the upstream binding (D-2)
  R->>H: execute() · UC-02.3
  H->>PG: geocode(probe) · GeocoderPort (upstream)
  PG->>PH: GET /api/?q=New%20York%2C%20USA
  PH-->>PG: place
  H-->>R: GeocoderHealth.Available

  R->>R: fetch directories, translate, metro filter (existing) · UC-02.4–5

  loop each row address · UC-02.6
    R->>CG: geocode(address)
    rect rgba(230, 240, 255, .35)
      Note over ST,DB: read transaction
      CG->>ST: findByQueryHash(hash)
      ST->>DB: SELECT lat, lng, display_name, provider_ref FROM geocoded_addresses<br/>WHERE normalized_query_hash = ?
    end
    alt warm hit
      ST-->>CG: GeocodedAddress
      CG-->>R: place (no provider call)
    else cold miss
      CG->>PG: geocode(address) · upstream
      PG->>PH: GET /api/?q=…
      PH-->>PG: GeoJSON
      PG-->>CG: GeocodedPlace
      rect rgba(230, 255, 235, .35)
        Note over ST,DB: write transaction
        CG->>ST: save(entry)
        ST->>DB: INSERT … ON CONFLICT (normalized_query_hash) DO NOTHING
      end
      CG-->>R: place
    end
    R->>R: MatchConfirmationPolicy.confirm (existing) · UC-02.6e
  end

  R->>R: reconcile venues and claims (existing) · UC-02.7
  R->>AW: complete(runId, snapshots, candidates, report)
  AW-->>R: pending run
  R-->>B: IngestReport
  B-->>DC: counts, rejects, gaps
```

## 2. Failure paths

```mermaid
sequenceDiagram
  autonumber
  actor DC as Data Curator
  actor PH as Photon
  participant B as IngestionConsole
  participant R as RunSampleIngestion
  participant H as CheckGeocoderHealth
  participant CG as CachingGeocoder
  participant PG as PhotonGeocoder
  participant ST as ExposedGeocodedAddressStore

  Note over DC,ST: P2 — pre-flight fails
  DC->>B: runSample(…)
  B->>R: execute(selection)
  R->>H: execute()
  H->>PG: geocode(probe)
  PG--xH: GeocodingException → Unavailable
  H-->>R: GeocoderHealth.Unavailable
  R-->>B: abort (no directory request, no artifact, no cache write)

  Note over DC,ST: E2 — provider dies mid-run
  R->>CG: geocode(address N)
  CG->>ST: findByQueryHash
  ST-->>CG: null
  CG->>PG: geocode(address N)
  PG--xCG: GeocodingException
  CG-->>R: throw (no save)
  R-->>B: abort before publishing candidates
  Note over R,ST: rows 1…N-1 are each a complete cached match — not partial output

  Note over DC,ST: E5/E6 — cache store trouble
  CG->>ST: findByQueryHash
  ST--xCG: SQLException
  Note over CG: WARN — treat as a miss, call the provider
```

## What this diagram pins

| Concern | Shown |
|---|---|
| Transaction boundary | Short read and write transactions per row; the provider call is never inside one |
| State transition | Each successfully geocoded address: Absent → Stored; rejected rows may still be stored (the cache records the provider's answer) |
| Authorization | Operator CLI only; no user identity exists to scope. The cache key has no tenant dimension |
| Concrete errors | Pre-flight `Unavailable` → abort; provider `GeocodingException` → abort; store errors → WARN + continue |
| No dependency change | SC-211's guard, fetch, translate, confirm, reconcile and artifact contract are untouched |
| Cost | A warm row costs one indexed `SELECT`; only the first sighting of an address reaches Photon |
