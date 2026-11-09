# SC-231 — Phase 4: Black-box — UC-01 Classify a row's venue type

> **Purpose:** actor-visible behaviour of classifying a row · **Derived from:** 02 UC-01 · **Delta:** new step inside the SC-211 run
> **Security:** no new trust input; classification reads text a certifier already published. A wrong type must never change verification or hand-cut. No auth surface, no PII.

```mermaid
sequenceDiagram
  autonumber
  actor DC as Data Curator
  actor DIR as Directory source (HMS/HFSAA/HMA)
  actor PH as Photon
  participant TL as Tahir's List
  DC->>TL: 1. Run ingestion (metros, caps)
  TL->>DIR: 2. Fetch certified rows
  DIR-->>TL: row + the source's own category
  TL->>PH: 3. Resolve the address
  PH-->>TL: point, and the OSM feature when the hit is a venue
  TL->>TL: 4. Map category → type (decisive for most rows)
  TL->>TL: 5. Else map the OSM feature → type
  TL->>TL: 6. Else read a decisive name word
  TL->>TL: 7. Record the type and the evidence class
  TL-->>DC: 8. Batch + report line: type, evidence, or "undecided"
```
**A1 — a retail store named like a butcher** · name narrows the non-restaurant category.
```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: "Retail Store" + "Hamza Meat Shop" → BUTCHER (step 4)
```
**A2 — a slaughter house** · never a restaurant.
```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: "Slaughter House" → BUTCHER (step 4)
```
**A3 — name contradicts category** · the category is stronger.
```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: "Madina Market (Kitchen)" + "Restaurants" → RESTAURANT (step 4)
```
**A4 — a distributor or processor** · decided, but not a place to eat.
```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: "Distributor" / "Processor" / "Misc" → OTHER (step 4)
```
**A5 — the OSM hit is not a venue** · a road or boundary says nothing.
```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: highway / place / boundary → ignored, fall through (step 5)
```
**E1 — no evidence decides** · honest null.
```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: venueType = null, row listed under "undecided" (step 7)
```
**E2 — two name words disagree** · fixed order, reported.
```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: first decisive word wins, word + class recorded (step 6)
```
**P1 — OSM tag unavailable** · SC-227 not merged, or no match detail.
```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: category + name only — still deterministic (steps 4, 6)
```
