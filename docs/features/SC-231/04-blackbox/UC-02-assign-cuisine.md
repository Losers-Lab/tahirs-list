# SC-231 — Phase 4: Black-box — UC-02 Assign a cuisine

> **Purpose:** actor-visible behaviour of assigning a cuisine · **Derived from:** 02 UC-02 · **Delta:** new step after classification
> **Security:** no new input; the risk is *fabricated* data, so every value carries evidence and conflicting evidence means null.

```mermaid
sequenceDiagram
  autonumber
  actor DC as Data Curator
  participant TL as Tahir's List
  DC->>TL: 1. (same run as UC-01)
  TL->>TL: 2. Read the source's published cuisine (none today — no-op)
  TL->>TL: 3. Else look up the curated brand map
  TL->>TL: 4. Else read the OSM cuisine tag
  TL->>TL: 5. Else map a distinctive name word (weak evidence)
  TL->>TL: 6. Else leave null
  TL-->>DC: 7. Report line: cuisine + evidence class, or "none", or "conflict"
```
**A1 — the row is a butcher or grocery** · cuisine is not asked.
```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: non-serving type → cuisine null (step 2)
```
**A2 — a chain on the brand map** · strong-ish evidence, but not the source's own claim.
```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: brand "Paramount Fine Foods" → Lebanese, evidence BRAND_MAP (step 3)
```
**A3 — two families in the name** · conflict, not a coin flip.
```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: "Pizza & Biryani" → cuisine null, conflict in the review queue (step 5)
```
**E1 — nothing says anything** · null.
```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: cuisine null, row counted under "no cuisine evidence" (step 6)
```
**E2 — curated tables malformed** · fail loudly, do not half-classify.
```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: abort before candidates are written — run incomplete
```
**P1 — the row already has a cuisine (owner/curator)** · untouched.
```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: existing value kept — report shows both (step 7)
```
