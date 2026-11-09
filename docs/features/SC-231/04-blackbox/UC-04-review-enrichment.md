# SC-231 — Phase 4: Black-box — UC-04 Review enrichment outcomes

> **Purpose:** actor-visible behaviour of reviewing what enrichment decided · **Derived from:** 02 UC-04 · **Delta:** new report section
> **Security:** the report is the audit surface. It must not claim more than the catalogue holds, and it must show what was skipped as loudly as what was decided.

```mermaid
sequenceDiagram
  autonumber
  actor DC as Data Curator
  participant TL as Tahir's List
  DC->>TL: 1. Open the run's report
  TL-->>DC: 2. Enrichment section: counts by type, cuisine assignments, undecided, conflicts
  TL-->>DC: 3. Per-row evidence: claim key + the class that decided each value
  TL-->>DC: 4. Review queue: weak and conflicting decisions
  DC->>TL: 5. (photo pass) open its report
  TL-->>DC: 6. photographed / skipped / failed / no candidate, per listing
  DC->>TL: 7. Re-run the same batch
  TL-->>DC: 8. identical decisions, no catalogue change
```
**A1 — nothing could be decided** · said out loud.
```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: "no decisive evidence in this run" (step 2)
```
**A2 — a stored value differs from the decision** · the owner edited it.
```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: show decision and stored value, state that the stored one was kept (step 3)
```
**E1 — the report cannot be written** · the run is incomplete.
```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: run is not presented as reviewable — nothing loadable (step 2)
```
**P1 — a photo pass never run** · absence is not success.
```mermaid
sequenceDiagram
  participant TL as Tahir's List
  TL->>TL: the section says "photo pass not run for this batch" (step 6)
```
