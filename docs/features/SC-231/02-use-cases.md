# SC-231 — Phase 2: Use cases

> **Purpose:** each actor goal, in the order it happens · **Derived from:** 00 + 01 · **Delta:** new UCs 01–04

**Actors.** DC = Data Curator (primary), U = User (beneficiary), RO/VRO = Owner
(may later override, out of scope), plus external read-only actors OSM (Overpass)
and Wikimedia Commons for UC-03.

**Phase 1 — inside the ingestion run (UC-01, UC-02).** Classification happens
after a row is fetched, translated and matched, before the candidate is written.
The reviewed load stays the only writer of the catalogue and the run directory
stays immutable.

**Phase 2 — after the load (UC-03).** The image store is keyed by listing id, so
a cover photo can only be attached once the listing exists. It is a separate
command with its own manifest, report and `--execute` gate.

**Phase 3 — not designed here.** Phone and website are in the story's scope
phase 3 but out-of-scope assigns phone to SC-202; that contradiction is Q-3 and
no use case is drawn for it until the owner decides.

---

## UC-01 · Classify a row's venue type

**Actor:** DC. **Goal:** every ingested row is identifiable as a restaurant, a
grocery, a butcher, something else — or honestly undecided.
**Trigger:** the ingestion run processes a matched row.

**Main scenario**

1. The run takes the row's source category (HMS publishes one per establishment;
   HFSAA/HMA publish an entry type).
2. If the category is decisive, it maps to the vocabulary and the decision ends.
3. Otherwise the run reads the matched OSM feature tag, when the geocoder
   supplies one (`amenity=restaurant` vs `shop=butcher`).
4. Otherwise the run looks for a decisive name word ("butcher", "meat market",
   "grocery", "bakery", "market").
5. The decision records the type and the evidence class that produced it.
6. The candidate carries the decision into the reviewed batch.

**Alternates**

- 2a. Category is *Retail Store* and the name narrows it (`Hamza Meat Shop` →
  `BUTCHER`, `Fresh Halal Grocery` → `GROCERY`); otherwise `OTHER`. Never
  `RESTAURANT`.
- 2b. Category is *Slaughter House* → `BUTCHER`; *Distributor* / *Processor* /
  *Misc* → `OTHER`.
- 3a. OSM tag present but not a venue type (a road, a boundary) → ignored; the
  name step still runs.
- 4a. Name says "market" but the category says restaurant (`Madina Market
  (Kitchen)`): the category wins.

**Exceptions → resulting state**

| Exception | Resulting state |
|---|---|
| No evidence at any step | `venueType = null`; the row is listed under "undecided" in the report |
| Two name words disagree ("market bakery") | First decisive branch in the fixed order wins; the report names the word and the class |

**Postconditions.** The candidate carries `venueType` and its evidence class (or
both null). The load writes them when the listing has no value yet.

**ACs:** 1, 2, 4 (phase-1 bullets 1, 2, 4).

---

## UC-02 · Assign a cuisine

**Actor:** DC (on behalf of U). **Goal:** a listing shows the cuisine it serves,
only when something actually says so.
**Trigger:** a row has been classified as a meal-serving type.

**Main scenario**

1. The run asks whether the source itself publishes a cuisine. None of HMS,
   HFSAA or HMA does today, so this step is a no-op for current sources.
2. Otherwise it consults the curated brand map (e.g. a chain whose cuisine is
   known).
3. Otherwise it reads the OSM `cuisine` tag, when the geocoder supplies one.
4. Otherwise it maps a distinctive name word through the curated keyword table
   ("biryani" → Pakistani, "pho" → Vietnamese) and marks the evidence **weak**.
5. Otherwise the cuisine stays null.

**Alternates**

- 2a/4a. Two different cuisine families are found ("Pizza & Biryani") → cuisine
  stays null and the conflict is listed in the review queue.
- 3a. The row is not a meal-serving type → cuisine stays null (INV-5).

**Exceptions → resulting state**

| Exception | Resulting state |
|---|---|
| Curated tables unavailable or malformed | The run fails before writing candidates; no partial classification |
| Keyword table yields several cuisines for one word | Treated as a conflict, never a coin flip |

**Postconditions.** `cuisine` is set with an evidence class, or null with a
recorded reason. An existing owner/curator cuisine is never touched.

**ACs:** 3, 4, 5 (phase-1 bullets 3, 4, 5).

---

## UC-03 · Attach a licensed cover photo

**Actor:** DC. **Goal:** a listing has one real photo, with a licence we can
honour and a credit we can show.
**Trigger:** the curator runs the photo pass for a loaded batch; the listing
already has an id.

**Main scenario**

1. The run takes the listings in scope and, for each, asks the photo sources:
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

**Actor:** DC. **Goal:** see, per run, what was decided, what was skipped and
what could not be decided — without opening the sources.
**Trigger:** the ingestion run completes, or the photo pass completes.

**Main scenario**

1. The run report gains an **Enrichment** section: counts by venue type, cuisines
   assigned with their evidence class, rows left undecided, cuisine conflicts,
   and — for the photo pass — photographed / skipped / failed / no-candidate.
2. Every decision names the row's claim key and the evidence that produced it.
3. The weak and conflicting decisions are grouped into a review queue.
4. Re-running the same batch reproduces the same decisions.

**Alternates**

- 1a. A run produced no enrichment at all (no evidence anywhere) → the section
  says so explicitly rather than being absent.
- 2a. A row's stored value differs from the decision (an owner edited it) → the
  report shows both and says the stored value was kept.

**Exceptions → resulting state**

| Exception | Resulting state |
|---|---|
| Report cannot be written | The run is not complete; nothing is loaded |

**Postconditions.** The reviewed batch is self-explanatory; nothing is silently
changed.

**ACs:** 4, 6 (phase-1 bullet 4, and the TDD bullet for the load test).

---

## Business rules (from the story, made testable)

| id | rule | source |
|---|---|---|
| ENR-1 | `venueType` is a closed vocabulary; undecided is `null`, never a guess | AC 1 |
| ENR-2 | Evidence order: source category → OSM feature → name keyword; a weaker source never overrides a stronger one | AC 2, 3 |
| ENR-3 | A non-restaurant source category can never yield `RESTAURANT` | AC 2 |
| ENR-4 | Cuisine needs evidence; conflicting families leave it `null` and record the conflict | AC 3 |
| ENR-5 | Enrichment fills nulls only; owner/curator values are never overwritten | AC 5 |
| ENR-6 | Enrichment is idempotent and records its evidence class | AC 4 |
| ENR-7 | A photo is stored only with an accepted licence and its required attribution; a missing photo is reported, never fabricated | phase 2 (Q-2) |
| ENR-8 | Enrichment never changes verification, hand-cut or cross-contamination | out of scope |

## Open questions

Q-1 (iteration), Q-2 (phase-2 ACs), Q-3 (phone/website vs SC-202), Q-4
(publishing non-restaurants), Q-5 (expose type in the API), Q-6 (photo sources),
Q-7 (cuisine review queue), Q-8 (report vs manual edit) — see `00-story.md`.
