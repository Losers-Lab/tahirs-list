# SC-231 — Phase 2: Use cases

> **Purpose:** each actor goal, in the order it happens · **Derived from:** 00 + 01 · **Delta:** new UCs 01–04; UC-01 is now an admission gate (L-1)

**Actors.** DC = Data Curator (primary), U = User (beneficiary), RO/VRO = Owner
(may later override, out of scope), plus external read-only actors OSM (Overpass)
and Wikimedia Commons for UC-03.

**Phase 1 — inside the ingestion run (UC-01, UC-02).** Admission and
classification happen after a row is fetched, translated and matched, before the
candidate is written. The reviewed load stays the only writer of the catalogue
and the run directory stays immutable.

**Phase 2 — after the load (UC-03).** The image store is keyed by listing id, so
a cover photo can only be attached once the listing exists. It is a separate
command with its own manifest, report and `--execute` gate.

**Phase 3 — not designed here.** Phone and website are in the story's scope
phase 3 but out-of-scope assigns phone to SC-202; that contradiction is Q-3 and
no use case is drawn for it until the owner decides.

---

## UC-01 · Add a row only if it is a restaurant

**Actor:** DC. **Goal:** the catalogue contains restaurants and nothing else.
**Trigger:** the ingestion run processes a matched row.

**Main scenario**

1. The run gathers what the evidence says about the place: the source's own
   category, the matched OSM feature, and the name.
2. If a **shop marker** is present and no **premises marker** is, the row is
   *excluded* — even when the source category says *Restaurants*. The exclusion
   names the word and the class.
3. Otherwise a non-restaurant category (Retail Store, Slaughter House,
   Distributor, Processor, Further Processor, Caterer, Misc) *excludes* the row.
4. Otherwise a restaurant category *admits* it as `RESTAURANT`, narrowed to
   `CAFE` or `FAST_FOOD` when the name or OSM tag says so.
5. Otherwise an OSM venue tag (`amenity=restaurant|fast_food|cafe`) admits it
   with that type.
6. Otherwise a premises word ("restaurant", "grill", "kitchen", "cafe") admits
   it as `RESTAURANT`.
7. Otherwise the row is *undecided*: not loaded, held for a curator, reported.
8. The decision — admitted with type and evidence, or excluded/undecided with a
   reason — travels with the candidate into the reviewed batch.

**Alternates**

- 2a. `Madina Market (Kitchen)`: "market" is a shop marker, "kitchen" is a
  premises marker → admitted, `RESTAURANT`.
- 2b. `Mannan Bakery and Grill`: "bakery" plus "grill" → admitted.
- 2c. `Firin Bakery` with category *Restaurants* → admitted as `BAKERY`; a bakery
  is a restaurant (L-3), so "bakery" is a premises word, not a shop marker.
- 3a. `Hamza Meat Shop` with category *Retail Store* → excluded (shop marker and
  non-restaurant category agree).
- 4a. Category *Restaurants*, name `Jamaica Live Poultry Meat Market [Butcher
  Dept. Only]` → excluded by step 2 (shop marker, no premises marker).
- 4b. Category *Restaurants*, name `Corner Coffee` → admitted as `CAFE`.

**Exceptions → resulting state**

| Exception | Resulting state |
|---|---|
| The curated marker tables are malformed | The run fails at startup; no batch is produced |
| A row matches both marker families equally ("Bakery Restaurant") | Premises wins (step 2 is not triggered); the decision records both words |

**Postconditions.** Every processed row has exactly one verdict: `Admitted`
(with type + evidence), `Excluded` (with class + reason), or `Undecided` (with
reason). Only `Admitted` rows become candidates that can be loaded.

**ACs:** 1, 2, 4; owner directive L-1.

---

## UC-02 · Assign a cuisine

**Actor:** DC (on behalf of U). **Goal:** a listing shows the cuisine it serves,
only when something actually says so.
**Trigger:** a row has been admitted as a meal-serving type.

**Main scenario**

1. The run asks whether the source itself publishes a cuisine. None of HMS,
   HFSAA or HMA does today, so this step is a no-op for current sources.
2. Otherwise it consults the curated brand map.
3. Otherwise it reads the OSM `cuisine` tag, when the geocoder supplies one.
4. Otherwise it maps a distinctive name word through the curated keyword table
   ("biryani" → Pakistani, "pho" → Vietnamese) and marks the evidence **weak**.
5. Otherwise the cuisine stays null.

**Alternates**

- 2a/4a. Two cuisine families are found ("Pizza & Biryani") → cuisine stays
  null and the conflict is listed in the review queue.
- 3a. The admitted type does not serve meals → cuisine stays null (INV-8).
- 5a. The row is not admitted at all → UC-02 never runs for it.

**Exceptions → resulting state**

| Exception | Resulting state |
|---|---|
| Curated tables unavailable or malformed | The run fails before writing candidates; no partial classification |
| A keyword maps to several cuisines | Treated as a conflict, never a coin flip |

**Postconditions.** `cuisine` is set with an evidence class, or null with a
recorded reason. An existing owner/curator cuisine is never touched.

**ACs:** 3, 4, 5.

---

## UC-03 · Attach a licensed cover photo

**Actor:** DC. **Goal:** a listing has one real photo, with a licence we can
honour and a credit we can show.
**Trigger:** the curator runs the photo pass for a loaded batch; the listing
already has an id.

**Main scenario**

1. The run takes the admitted listings in scope and, for each, asks the photo
   sources:
   a. OSM: the `image` / `wikimedia_commons` tag on the matched feature, by the
   geocoder's provider reference;
   b. Wikimedia Commons: a search for the venue or its brand.
2. Each candidate carries a source URL, a licence and the author/attribution
   text.
3. The run accepts a candidate only when the licence permits reuse (CC0, public
   domain, CC-BY, CC-BY-SA); anything else is dropped and reported.
4. It fetches the bytes, decodes them, and stores FULL + every thumbnail variant
   through the existing image pipeline.
5. It stores the credit — source URL, licence, attribution, author, retrieval
   time, content hash — against the listing.
6. The listing serves the photo through the existing image endpoint; the credit
   is served with the listing detail.

**Alternates**

- 1a. No candidate from any source → the listing stays without a photo and is
  listed as a gap.
- 2a. Candidate licence unknown or non-free → skipped, reported, no bytes
  fetched.
- 3a. Several acceptable candidates → the first by source priority (OSM before
  Commons), then by the source's own ordering; noted in the report.
- 4a. The listing already has a photo → untouched unless `--replace` is given.

**Exceptions → resulting state**

| Exception | Resulting state |
|---|---|
| Fetch fails (non-2xx, timeout, I/O) | Row reported as failed; existing photo kept; run continues |
| Bytes do not decode as an image | Same as a fetch failure; nothing persisted (variants are resized before the original is written) |
| Attribution text missing for a licence that requires it | Candidate rejected — a CC-BY photo without credit is a licence breach |

**Postconditions.** At most one cover photo per listing, with its licence and
credit; or no photo and a reported gap. No invented image.

**ACs:** *proposed, not yet ratified in Shortcut* (Q-2) — a licensed photo with
attribution or an honest gap; no fabricated image; a re-run is idempotent and
never replaces an existing photo without `--replace`.

---

## UC-04 · Review what enrichment decided

**Actor:** DC. **Goal:** see, per run, what was admitted, what was excluded and
why, and what could not be decided — without opening the sources.
**Trigger:** the ingestion run completes, or the photo pass completes.

**Main scenario**

1. The run report gains an **Enrichment** section: admitted (by venue type),
   **excluded (by venue class, with the deciding word or category)**, undecided
   (held), cuisines assigned with their evidence class, and cuisine conflicts.
2. Every decision names the row's claim key and the evidence that produced it.
3. Weak and conflicting decisions are grouped into a review queue; excluded and
   undecided rows are listed in full so the curator can see what the catalogue
   will not contain.
4. The photo pass adds photographed / skipped / failed / no-candidate.
5. Re-running the same batch reproduces the same decisions.

**Alternates**

- 1a. A run excluded a large share of rows → the section states the share and
  the top reasons, so a bad classifier or a source change is visible.
- 2a. A stored value differs from the decision (an owner edited it) → the report
  shows both and says the stored value was kept.

**Exceptions → resulting state**

| Exception | Resulting state |
|---|---|
| Report cannot be written | The run is not complete; nothing is loaded |

**Postconditions.** The reviewed batch is self-explanatory; nothing is silently
changed or silently dropped.

**ACs:** 4, 6.

---

## Business rules (from the story, made testable)

| id | rule | source |
|---|---|---|
| ENR-1 | Only restaurants are published; every other row is excluded and reported | L-1 |
| ENR-2 | Evidence order: hard exclusion → source category → OSM feature → name; a weaker source never overrides a stronger one | AC 2, 3 |
| ENR-3 | A shop marker with no premises marker excludes, even under a restaurant category | L-1, AC 2 |
| ENR-4 | No decisive evidence → undecided, never loaded | L-1 |
| ENR-5 | Cuisine needs evidence; conflicting families leave it `null` and record the conflict | AC 3 |
| ENR-6 | Enrichment fills nulls only; owner/curator values are never overwritten | AC 5 |
| ENR-7 | Enrichment is idempotent and records its evidence class | AC 4 |
| ENR-8 | A photo is stored only with an accepted licence and its required attribution; a missing photo is reported, never fabricated | phase 2 (Q-2) |
| ENR-9 | Enrichment never changes verification, hand-cut or cross-contamination | out of scope |

## Open questions

Q-1 (iteration), Q-2 (phase-2 ACs), Q-3 (phone/website), Q-5 (expose the type),
Q-6 (photo sources), Q-7 (review queue), Q-8 (report vs manual edit) — see
`00-story.md`. Q-4 is closed by L-1 and Q-9 by L-3.
