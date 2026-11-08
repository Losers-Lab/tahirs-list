# SC-198 — Phase 4: Black-box sequence, UC-02 Run a sample directory ingestion

> **Purpose:** what the curator sees, warm vs cold, pre-flight included · **Derived from:** 02 UC-02 (SC-211 UC-01 delta) · **Delta:** step 3 probes uncached; step 6 splits into cache hit / provider miss
> **Security:** addresses come from third-party directories; only hashes are cached. The run stays operator-initiated and local, as SC-211 designed it.

```mermaid
sequenceDiagram
  autonumber
  actor DC as Data Curator
  actor DS as Directory sources<br/>HMS · HFSAA · HMA
  actor PH as Photon
  participant S as Tahir's List

  DC->>S: 1. run sample ingestion (metros, caps)
  S->>S: 2. pending-run guard

  S->>PH: 3. pre-flight geocode (always, uncached)
  alt pre-flight fails
    S-->>DC: abort — geocoder unavailable, nothing fetched
  else pre-flight passes
    S->>DS: 4. fetch directory records
    DS-->>S: records
    S->>S: 5. filter to metro allowlists

    loop each row's address
      alt address already resolved (warm)
        S->>S: 6a. look up the normalized address
      else address never resolved (cold)
        S->>PH: 6b. geocode address
        PH-->>S: 6c. coordinates + display name
        S->>S: 6d. store the match (even if later rejected)
      end
      S->>S: 6e. confirm the match
    end

    S->>S: 7. reconcile venues, claims, intended trust
    S->>S: 8. write per-run artifacts and report
    S-->>DC: run report (counts, rejects, gaps)
  end
```

## Flows

```mermaid
sequenceDiagram
  autonumber
  actor DC as Data Curator
  actor DS as Directory sources
  actor PH as Photon
  participant S as Tahir's List

  Note over DC,S: A1 — repeat run: every address warm, zero provider calls after pre-flight
  DC->>S: 1. run again
  S->>PH: 3. pre-flight (the only provider call)
  S->>DS: 4. fetch
  S->>S: 6a. every row resolves from cache
  S-->>DC: same artifact shape

  Note over DC,S: P2 — pre-flight fails: abort before any fetch
  DC->>S: 1. run
  S->>PH: 3. pre-flight
  PH--xS: unavailable
  S-->>DC: aborted — no snapshots, no candidates, no cache write

  Note over DC,S: E2 — Photon dies mid-run
  S->>PH: 6b. geocode row N
  PH--xS: timeout
  S-->>DC: aborted before candidates are published
  Note over S: rows 1..N-1 are complete cache entries, not partial output

  Note over DC,S: E3 — cold, no match
  S->>PH: 6b. geocode
  PH-->>S: no match
  S->>S: row rejected with reason — nothing stored
```

## Caption

- **Visible change:** a repeat run costs one pre-flight call instead of one per row; a cold run behaves exactly as SC-211 specified.
- **Locks in:** the pre-flight is *always* a real provider call — a cached probe could hide an outage and let a run start with no usable geocoder.
- **Unchanged:** guard, fetch, translation, metro filter, confirmation, reconciliation, artifacts, report.
