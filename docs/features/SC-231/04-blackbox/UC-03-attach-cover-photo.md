# SC-231 — Phase 4: Black-box — UC-03 Attach a licensed cover photo

> **Purpose:** actor-visible behaviour of the photo pass · **Derived from:** 02 UC-03 · **Delta:** new post-load command
> **Security:** an external, untrusted image is fetched and republished. The risks are licence breach and hostile bytes: only accepted licences are used, attribution is mandatory, the content type is derived from the bytes, and the image endpoint already serves `img-src 'self'`.

```mermaid
sequenceDiagram
  autonumber
  actor DC as Data Curator
  actor OSM as OSM / Overpass
  actor WC as Wikimedia Commons
  participant TL as Tahir's List
  DC->>TL: 1. Run the photo pass (scope, --execute)
  TL->>TL: 2. List the loaded listings in scope
  loop each listing without a photo
    TL->>OSM: 3a. image / wikimedia_commons for the matched feature
    OSM-->>TL: tag or nothing
    TL->>WC: 3b. search the venue or brand
    WC-->>TL: candidate + licence + author + credit line
    TL->>TL: 4. Accept only CC0 / public domain / CC-BY / CC-BY-SA
    TL->>TL: 5. Fetch, decode, store FULL + thumbnails
    TL->>TL: 6. Store the credit against the listing
  end
  TL-->>DC: 7. photographed / skipped / failed / no candidate, per listing
```
**A1 — no candidate anywhere** · a gap, not a placeholder.
```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: listing stays without a photo, reported (step 3)
```
**A2 — licence unknown or non-free** · refuse before fetching bytes.
```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: candidate dropped, reason recorded (step 4)
```
**A3 — several acceptable candidates** · deterministic ordering.
```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: OSM before Commons, then the source's own order (step 4)
```
**A4 — CC-BY candidate with no author or credit line** · a licence breach is not a nit.
```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: candidate rejected — attribution is mandatory (step 4)
```
**E1 — fetch or decode fails** · reported, existing photo kept.
```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: failed row, run continues (step 5)
```
**E2 — partial variant write** · impossible: thumbnails are produced before anything is stored.
```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: resize all variants first, then persist (step 5)
```
**P1 — the listing already has a photo** · untouched without `--replace`.
```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: skipped, no fetch (step 2)
```
**P2 — a hostile or oversized image** · bounded and type-derived.
```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: size cap, decode check, content type from bytes (step 5)
```
