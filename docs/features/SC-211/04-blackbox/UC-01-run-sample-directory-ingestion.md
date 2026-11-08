# SC-211 — Phase 4: Black-box — UC-01 Run a sample directory ingestion

> **Purpose:** actor-visible behaviour of the Tier-1 sample run · **Derived from:** 02 UC-01 · **Delta:** new
> **Security:** external trust data enters here; no auth surface; no PII; integrity = no invented locations / no silent drops.

```mermaid
sequenceDiagram
  autonumber
  actor DC as Data Curator
  participant TL as Tahir's List
  participant HMS as HMS (external)
  participant HF as HFSAA (external)
  participant HM as HMA (external)
  participant PH as Photon (external)
  DC->>TL: 1. Run sample (Dallas, NYC, Toronto â€” caps)
  TL->>HMS: 2. Request certified restaurants (throttled)
  HMS-->>TL: Listing pages
  TL->>HF: 3. Discover chapters + request New York
  HF-->>TL: Certified locations
  TL->>HM: 4. Request GTA list + detail pages
  HM-->>TL: List + addresses
  TL->>TL: 5. Filter to metro restaurants
  TL->>PH: 6. Resolve "name, address, city, region"
  PH-->>TL: Place or no match
  TL->>TL: 7. Reconcile venues, merge claims, set trust outcome
  TL->>TL: 8. Write snapshots, candidates.json, report.md
  TL-->>DC: 9. Counts per source/metro + rejects
```
**A1 — several branches** · one source listing per entry.
```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: treat each branch/entry separately (step 4)
```
**A2 — HMS fallback** · ShariahBoard table used and recorded.
```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: primary page unavailable → fallback table (step 2)
```
**A3 — below cap** · continue, report the shortfall.
```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: keep confirmed rows only â€” no padding (step 6)
```
**A4 — repeat run** · cached snapshots, no fetch.
```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: read cache â€” mark cache-sourced
```
**E1 — source unreachable** · `SOURCE_UNAVAILABLE`, run continues.
```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: retry/backoff → mark source failed â€” report what was missed
```
**E2 — Photon down** · abort; no candidates artifact.
```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: abort before step 8 (`Aborted`)
```
**E3 — source format changed** · quarantine, run continues.
```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: `SOURCE_PARSE_FAILED` â€” follow-up required
```
**E4 — zero metro matches** · reported; no load.
```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: zero candidates for the metro (step 5)
```
**E5 — bad geocode** · reject, coordinates never taken.
```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: `WRONG_REGION` / `AMBIGUOUS_GEOCODER_MATCH`
```
