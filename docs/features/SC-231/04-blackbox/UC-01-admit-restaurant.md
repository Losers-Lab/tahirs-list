# SC-231 — Phase 4: Black-box — UC-01 Add a row only if it is a restaurant

> **Purpose:** actor-visible behaviour of the admission gate · **Derived from:** 02 UC-01 · **Delta:** new step inside the SC-211 run
> **Security:** the gate decides what enters a public directory. A wrong *admission* publishes a shop as a restaurant; a wrong *exclusion* silently loses a real one. Both are reported, and the decision always names its evidence.

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
  TL->>TL: 4. Shop word with no premises word? → exclude
  TL->>TL: 5. Else a non-restaurant category? → exclude
  TL->>TL: 6. Else a restaurant category? → admit, narrowing to cafe / fast food
  TL->>TL: 7. Else the OSM amenity tag? → admit
  TL->>TL: 8. Else a premises word? → admit
  TL->>TL: 9. Else undecided → hold, do not load
  TL-->>DC: 10. Batch (admitted only) + report: admitted, excluded, undecided
```
**A1 — a butcher under a restaurant category** · the real rows in today's catalogue.
```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: "Restaurants" + "Jamaica Live Poultry Meat Market [Butcher Dept. Only]" → EXCLUDED (step 4)
```
**A2 — a market with a kitchen** · premises word rescues it.
```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: "Restaurants" + "Madina Market (Kitchen)" → ADMITTED, RESTAURANT (step 6)
```
**A3 — a bakery with a grill** · shop word plus premises word.
```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: "Mannan Bakery and Grill" → ADMITTED (step 2 does not trigger)
```
**A4 — a pure bakery** · a bakery is a restaurant (L-3).
```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: "Restaurants" + "Firin Bakery" → ADMITTED, BAKERY (step 6)
```
**A5 — a retail store named like a butcher** · both agree.
```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: "Retail Store" + "Hamza Meat Shop" → EXCLUDED (steps 4, 5)
```
**A6 — a slaughter house, distributor or processor** · excluded by category.
```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: "Slaughter House" / "Distributor" / "Processor" → EXCLUDED (step 5)
```
**A7 — a plain restaurant** · admitted, type narrowed when the name says cafe.
```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: "Restaurants" + "Corner Coffee" → ADMITTED, CAFE (step 6)
```
**E1 — nothing decides** · held, never guessed.
```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: no category, no OSM tag, no marker → UNDECIDED, listed for a curator (step 9)
```
**E2 — both marker families** · premises wins, both words recorded.
```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: "Bakery Restaurant" → ADMITTED, RESTAURANT (step 4 not triggered)
```
**P1 — OSM tag unavailable** · category + name only.
```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: steps 7 and 8 only — still deterministic
```
**P2 — the source changes its categories** · visible as a shift in the excluded share.
```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: report states admitted / excluded / undecided counts and top reasons (step 10)
```
