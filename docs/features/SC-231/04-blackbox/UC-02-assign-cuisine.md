# SC-231 — Phase 4: Black-box — UC-02 Assign a cuisine

> **Purpose:** actor-visible behaviour of assigning a cuisine · **Derived from:** 02 UC-02 · **Delta:** new step after admission
> **Security:** no new input; the risk is *fabricated* data, so every value carries evidence and conflicting evidence means null.

```mermaid
sequenceDiagram
  autonumber
  actor DC as Data Curator
  participant TL as Tahir's List
  DC->>TL: 1. (same run as UC-01)
  TL->>TL: 2. Source-published cuisine? none of the three sources does — no-op
  TL->>TL: 3. Curated brand map
  TL->>TL: 4. OSM cuisine tag, when the match carries one
  TL->>TL: 5. Distinctive name word (weak evidence)
  TL->>TL: 6. First decisive source wins — otherwise null
  TL-->>DC: 7. Report line: cuisine + evidence, or "none", or "conflict"
```
**A1 — the row is a butcher or grocery** · cuisine is not asked.
```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: not admitted → UC-02 never runs (step 1)
```
**A2 — a chain on the brand map** · strong-ish evidence, not the source's own claim.
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
  TL->>TL: cuisine null, counted under "no cuisine evidence" (step 6)
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
