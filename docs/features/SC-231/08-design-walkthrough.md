# SC-231 — Phase 8: Design walkthrough

> **Purpose:** read the design in ten minutes, then approve or name the diagram to change · **Derived from:** 00–07 · **Delta:** decision table at the end

Read in this order. Each section is three bullets: what the diagram **shows**,
what it **locks in**, and what I **need** from you. The decision table at the
end is what approval actually covers.

---

## 1 · Domain model — `01-domain-model.md`

- **Shows** a `RestaurantListing` that now knows *what it is* (`VenueType`, or
  null), *why* (`EnrichmentSource`), and carries at most one `CoverPhoto` with a
  `PhotoLicence`.
- **Locks in** "null is honest, not 'unknown restaurant'"; a non-restaurant
  category can never become `RESTAURANT`; cuisine only for meal-serving types;
  a photo only with a reusable licence; enrichment never touches trust.
- **Need** your call on whether a grocery/butcher may carry a cuisine (I said no,
  INV-5) and whether `OTHER` is the right bucket for distributors/processors.

## 2 · Use case diagram — `03-use-case-diagram.md`

- **Shows** four new use cases hanging off the existing ingestion: classification
  **extends** the run, cuisine **extends** classification, the photo pass
  **includes** a completed load, and the report **includes** both passes.
- **Locks in** that the user never triggers enrichment and the owner override is
  a later story — the dashed arrow records that the design must not block it.
- **Need** confirmation that the photo pass is a separate command after a load
  rather than part of the reviewed batch.

## 3 · Black-box, UC-01 classify — `04-blackbox/UC-01-…`

- **Shows** the curator's run, the directory's category, Photon's feature and the
  name, collapsing to one type and one evidence class, with `A1`–`A5` for the
  awkward rows (`Madina Market (Kitchen)`, `Hamza Meat Shop`, slaughter house,
  distributor, non-venue OSM hit).
- **Locks in** the evidence order and that an undecided row is a reported
  outcome, not an error.
- **Need** nothing unless one of the named edge rows should resolve differently.

## 4 · Black-box, UC-02 cuisine — `04-blackbox/UC-02-…`

- **Shows** the four-step cuisine ladder and the conflict case (`Pizza &
  Biryani` → null, queued).
- **Locks in** that a chain's cuisine comes from a curated map, not from the
  certifier — and is labelled as such.
- **Need** your acceptance that a brand-map cuisine is legitimate evidence
  (D-2) or that only the source/OSM may supply one.

## 5 · Black-box, UC-03 cover photo — `04-blackbox/UC-03-…`

- **Shows** OSM and Wikimedia Commons as read-only actors, licence acceptance
  before any bytes are fetched, and the credit stored beside the photo.
- **Locks in** "no licence → no photo"; "no credit for a CC-BY → no photo";
  "an existing photo is never replaced without `--replace`".
- **Need** the source decision (D-7) and whether cards must show the credit too
  (D-8) — that one changes the frontend.

## 6 · Black-box, UC-04 review — `04-blackbox/UC-04-…`

- **Shows** the Enrichment section, the per-row evidence and the review queue.
- **Locks in** that an absent photo pass is stated as absent, not implied to
  have succeeded.
- **Need** whether the review queue lives only in the report or also needs a
  Shortcut ticket per conflict (I designed report-only).

## 7 · System class diagram — `05-class-diagram-system.md`

- **Shows** one control per use case (`VenueTypeClassifier`, `CuisineAssigner`,
  `AttachCoverPhoto`, `RenderEnrichmentReport`), curated tables as data
  (`EnrichmentRules`), and the photo ports next to the existing image pipeline.
- **Locks in** that classification is pure and side-effect free, and that the
  image store keeps bytes while the catalogue keeps credit.
- **Need** nothing structural; it follows from sections 3–6.

## 8 · White-box, UC-01 and UC-02 — `06-whitebox/UC-01-…`, `UC-02-…`

- **Shows** the exact call path, including the short-circuit that stops a
  butcher from ever reaching the cuisine assigner.
- **Locks in** no transaction on this path (files only) and one concrete error
  per exception (malformed curated tables fail at startup, never mid-run).
- **Need** nothing.

## 9 · White-box, UC-03 and UC-04 — `06-whitebox/UC-03-…`, `UC-04-…`

- **Shows** the two-store write order for a photo — object bytes first, credit
  row second — and the report rendered from the same decisions the batch holds.
- **Locks in** that a crash can leave orphan bytes but never a visible photo
  with no credit; and that the photo pass never mutates the ingestion run's
  artifacts.
- **Need** your acceptance of that failure ordering (D-9).

## 10 · Complete class diagram — `07-class-diagram-complete.md`

- **Shows** the layer map, the final names, `V25`, the cover-photo table and the
  three API fields a licence forces.
- **Locks in** that non-restaurants are stored but not indexed (D-6), and that
  `venueType` is **not** exposed in the API yet (D-5).
- **Need** decisions D-4 through D-8; everything else is mechanical.

---

## Decision table — approval covers these

| # | Decision | Default in this design | Alternative if you say otherwise |
|---|---|---|---|
| D-1 | Classification runs **inside** the ingestion run; photos run **after** the load | as designed | classification as a separate pass (needs a second source parse) |
| D-2 | Brand-map cuisine counts as evidence, labelled `BRAND_MAP` | yes | only source/OSM may supply a cuisine |
| D-3 | `venueType` vocabulary: `RESTAURANT`, `CAFE`, `FAST_FOOD`, `BAKERY`, `GROCERY`, `MARKET`, `BUTCHER`, `CATERER`, `OTHER`, null | as listed | add `DELI`, `FOOD_TRUCK`, … (each is a schema change later) |
| D-4 | Evidence is stored on the listing as two enum columns (`venue_type_source`, `cuisine_source`) | yes | report-only provenance, no columns |
| D-5 | `venueType` stays out of the public API until the publishing decision | not exposed | expose on detail + cards now |
| D-6 | Non-serving types are **not written to the search mirror**; they remain in `restaurant_listings` | as designed | index them and filter in the search query (touches the search path) |
| D-7 | Photo sources: OSM + Wikimedia Commons only | free sources only | add a keyed provider (billed, needs a licence review) |
| D-8 | Credit is shown on the **detail page and on cards** | both | detail page only (weaker attribution) |
| D-9 | Photo write order: bytes then credit row; a crash may orphan bytes, never an uncredited visible photo | as designed | credit first (a crash would leave a visible photo with no file) |
| D-10 | Cuisine conflicts go to the report's review queue only | report only | also open a Shortcut story per conflict |
| D-11 | Phase 3 (phone/website) is **not designed** until Q-3 is answered — the story's phase 3 and its out-of-scope clause contradict each other | defer | design phone/website here (needs SC-202 coordination) |

## What is already true, and what is not

- The `:ingestion` prototype of `VenueType` + `VenueClassifier` written before
  this design exists **uncommitted** in this worktree. It is a spike, not the
  design: it is not referenced by these documents, and the implementation will
  follow the design (TDD), not the spike. Say if you want it kept or dropped.
- `SC-227` (PR #84) adds the OSM tag to `GeocodedPlace`. Until it merges,
  classification loses its middle evidence step; the report says so.
- No implementation code will be written before you reply `approved`.
