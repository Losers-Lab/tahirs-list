# SC-231 — Phase 4: Black-box — UC-03 Give a listing a photo it is allowed to show

> **Purpose:** actor-visible behaviour of the photo pass · **Derived from:** 02 UC-03 · **Delta:** new post-load command; Google Places as a licensed cache
> **Security:** an external image is fetched and republished. Risks are licence breach, hostile bytes, and a key leaking to the browser: only accepted sources are used, attribution is mandatory, the content type comes from the bytes, and the Places key stays server-side.

```mermaid
sequenceDiagram
  autonumber
  actor DC as Data Curator
  actor RO as Restaurant Owner
  actor OSM as OSM / Overpass
  actor WC as Wikimedia Commons
  actor GOOG as Google Places API (external)
  participant TL as Tahir's List
  DC->>TL: 1. Run the photo pass (scope, --execute)
  TL->>TL: 2. Listings needing a photo, or a cache past its window
  TL->>TL: 3. Owner photo exists? → skip, never touched
  TL->>OSM: 4a. image / wikimedia_commons for the matched feature
  OSM-->>TL: tag or nothing
  TL->>WC: 4b. search the venue or brand
  WC-->>TL: candidate + licence + author + credit line
  TL->>TL: 5. Accept only CC0 / public domain / CC-BY / CC-BY-SA
  TL->>GOOG: 6. Place Details + photo, when nothing free was found
  GOOG-->>TL: photo bytes + author attributions
  TL->>TL: 7. Store free bytes permanently, or Places bytes as a cache with an expiry
  TL-->>DC: 8. photographed (permanent) / cached / skipped / no candidate / failed
  RO-.->TL: (any time) owner upload through SC-205 — replaces either kind
```
**A1 — the owner already uploaded** · the pass steps back.
```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: owner photo present → skipped, no fetch (step 3)
```
**A2 — no candidate anywhere** · a gap, not a placeholder.
```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: no owner, no free, no place photo → gap recorded (steps 4–6)
```
**A3 — non-free licence** · refuse before fetching bytes.
```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: candidate dropped, reason recorded (step 5)
```
**A4 — two free candidates** · deterministic order.
```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: OSM before Commons, then the source's own order (step 5)
```
**A5 — CC-BY with no credit line** · a licence breach is not a nit.
```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: candidate rejected — attribution is mandatory (step 5)
```
**A6 — no free photo, Places has one** · cached, not kept.
```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: place id + photo name + attribution + expiry ≤ 30 days (steps 6–7)
```
**E1 — fetch or decode fails** · reported, unexpired bytes kept.
```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: failed row, run continues (step 7)
```
**E2 — Places quota exhausted or key rejected** · one row's failure, not the run's.
```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: row reported, existing cache kept until expiry (step 6)
```
**P1 — the cache expired before the pass ran** · not served.
```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: image endpoint returns no photo — the UI shows its placeholder
```
**P2 — a hostile or oversized image** · bounded and type-derived.
```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: size cap, decode check, content type from bytes (step 7)
```
**P3 — an owner uploads over a cached photo** · last write wins, cache metadata cleared.
```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: SC-205 replaces the photo — the pass will never touch it (step 3)
```
