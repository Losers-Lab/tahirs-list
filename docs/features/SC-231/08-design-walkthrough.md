# SC-231 — Phase 8: Design walkthrough

> **Purpose:** read the design in ten minutes, then approve or name the diagram to change · **Derived from:** 00–07 · **Delta:** decision table at the end

Read in this order. Each section is three bullets: what the diagram **shows**,
what it **locks in**, and what I **need** from you. The decision table at the
end is what approval actually covers.

**The one thing that changed after your directive:** Tahir's List holds
restaurants only, so the classifier is no longer a label — it is a **gate**. A
row that is not a restaurant never becomes a candidate, never reaches the
catalogue, and never reaches search. That is L-1, and it is what sections 1, 2,
3 and 10 now show.

---

## 1 · Domain model — `01-domain-model.md`

- **Shows** two vocabularies on purpose: `VenueClass` (what the evidence says,
  including butcher/grocery/distributor) and `VenueType` (what a *stored*
  restaurant is: restaurant, cafe, fast food, bakery). The gate's verdict is a
  sealed `AdmissionDecision` — admitted, excluded, or undecided.
- **Locks in** that non-restaurants are never stored; that a shop word with no
  premises word wins over a coarse "Restaurants" category; and that a photo
  needs a reusable licence.
- **Need** your answer on Q-9: is a pure bakery a restaurant for us? Default is
  no, which removes three more rows from today's catalogue.

## 2 · Use case diagram — `03-use-case-diagram.md`

- **Shows** UC-01 as a gate that **extends** the ingestion run, cuisine
  **extending** it only for admitted rows, the photo pass **including** a
  completed load, and the report **including** both passes.
- **Locks in** that the user never triggers enrichment, and everything a user
  sees is a restaurant by construction.
- **Need** confirmation that the photo pass stays a separate command after a
  load.

## 3 · Black-box, UC-01 admit — `04-blackbox/UC-01-…`

- **Shows** the evidence arriving (category, OSM feature, name) and one verdict
  per row, with `A1`–`A6` covering the rows that actually exist today: the three
  butcher/grocery rows, `Madina Market (Kitchen)`, `Mannan Bakery and Grill`, a
  plain restaurant, a slaughter house, a distributor.
- **Locks in** the precedence — shop-marker check first, then category, then OSM,
  then premises words — and that an undecided row is *held and reported*, not
  guessed into the catalogue.
- **Need** nothing unless one of those real rows should resolve differently.

## 4 · Black-box, UC-02 cuisine — `04-blackbox/UC-02-…`

- **Shows** the four-step cuisine ladder and the conflict case (`Pizza &
  Biryani` → null, queued).
- **Locks in** that a chain's cuisine comes from a curated map, not from the
  certifier — and is labelled as such.
- **Need** your acceptance that a brand-map cuisine is legitimate evidence (D-2).

## 5 · Black-box, UC-03 cover photo — `04-blackbox/UC-03-…`

- **Shows** OSM and Wikimedia Commons as read-only actors, licence acceptance
  before any bytes are fetched, and the credit stored beside the photo.
- **Locks in** "no licence → no photo"; "no credit for a CC-BY → no photo"; "an
  existing photo is never replaced without `--replace`".
- **Need** the source decision (D-7) and whether cards must show the credit too
  (D-8).

## 6 · Black-box, UC-04 review — `04-blackbox/UC-04-…`

- **Shows** the Enrichment section: admitted by type, **excluded by class with
  the deciding word**, undecided rows held, cuisines with evidence, and the
  review queue.
- **Locks in** that a large excluded share is visible — a classifier bug or a
  source change cannot hide — and that an absent photo pass is stated as absent.
- **Need** whether the review queue lives only in the report (I designed
  report-only, D-10).

## 7 · System class diagram — `05-class-diagram-system.md`

- **Shows** one control per use case (`RestaurantAdmission`, `CuisineAssigner`,
  `AttachCoverPhoto`, `RenderEnrichmentReport`), with the curated marker tables
  as data under `AdmissionPolicy`.
- **Locks in** that the gate is pure and side-effect free, and that
  `CandidateFactory` cannot build a candidate without an admitted verdict.
- **Need** nothing structural.

## 8 · White-box, UC-01 and UC-02 — `06-whitebox/UC-01-…`, `UC-02-…`

- **Shows** the exact call path, including the shop-marker check that runs
  *before* the category rule — the thing that catches a butcher filed under
  "Restaurants".
- **Locks in** no transaction here (files only), exclusion as a counted
  `IngestReject` rather than a silent drop, and one concrete error per exception.
- **Need** nothing.

## 9 · White-box, UC-03 and UC-04 — `06-whitebox/UC-03-…`, `UC-04-…`

- **Shows** the two-store write order for a photo — bytes first, credit row
  second — and the report rendered from the same decisions the batch holds.
- **Locks in** that a crash can leave orphan bytes but never a visible photo
  with no credit.
- **Need** your acceptance of that failure ordering (D-9).

## 10 · Complete class diagram — `07-class-diagram-complete.md`

- **Shows** the layer map, the final names, `V25`, the cover-photo table, the
  three API fields a licence forces, and the **one-off purge** of the rows that
  are already in the catalogue but are not restaurants.
- **Locks in** that nothing non-restaurant can reach `restaurant_listings` or
  `listing_search`, and that `venueType` is not exposed in the API yet (D-5).
- **Need** decisions D-4 through D-9 and Q-9.

---

## Decision table — approval covers these

| # | Decision | Default in this design | Alternative if you say otherwise |
|---|---|---|---|
| D-1 | Admission and classification run **inside** the ingestion run; photos run **after** the load | as designed | classification as a separate pass (needs a second source parse) |
| D-2 | Brand-map cuisine counts as evidence, labelled `BRAND_MAP` | yes | only source/OSM may supply a cuisine |
| D-3 | Stored `venueType`: `RESTAURANT`, `CAFE`, `FAST_FOOD`, `BAKERY`; the recogniser's non-restaurant classes are not stored | as listed | narrow to the three and drop `BAKERY` from the column |
| D-4 | Evidence stored on the listing as two enum columns (`venue_type_source`, `cuisine_source`) | yes | report-only provenance, no columns |
| D-5 | `venueType` stays out of the public API for now | not exposed | expose on detail + cards |
| D-6 | **Non-restaurants are excluded from the catalogue entirely** — not stored, not indexed, counted in the report | as designed (L-1) | store them hidden for a later listing type |
| D-7 | Photo sources: OSM + Wikimedia Commons only | free sources only | add a keyed provider (billed, needs a licence review) |
| D-8 | Credit is shown on the **detail page and on cards** | both | detail page only (weaker attribution) |
| D-9 | Photo write order: bytes then credit row; a crash may orphan bytes, never an uncredited visible photo | as designed | credit first |
| D-10 | Cuisine conflicts go to the report's review queue only | report only | also open a Shortcut story per conflict |
| D-11 | Phase 3 (phone/website) is **not designed** until Q-3 is answered | defer | design phone/website here (needs SC-202 coordination) |
| D-12 | Existing non-restaurant rows are purged in a **separate, reported** step after a `pg_dump` — not in a migration | as designed | purge inside `V25` (rejected: data deletion hidden in a schema change) |

## What is already true, and what is not

- The `:ingestion` prototype of `VenueType` + `VenueClassifier` written before
  this design exists **uncommitted** in this worktree. It predates the gate and
  the two-vocabulary split, so it is a spike, not the design. Say if you want it
  kept or dropped.
- `SC-227` (PR #84) adds the OSM tag to `GeocodedPlace`. Until it merges, the
  gate loses its OSM step and rests on category + name; the report says so.
- No implementation code will be written before you reply `approved`.
