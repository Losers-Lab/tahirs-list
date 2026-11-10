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

## UC-03 · Give a listing a photo it is allowed to show

**Actor:** DC. **Goal:** every listing shows one real photo, from a source we are
allowed to use, with the credit that source requires.
**Trigger:** the curator runs the photo pass for a loaded batch; the listing
already has an id. The same command also refreshes expired caches.

**Main scenario**

1. The run lists the admitted listings that need attention: no photo at all, or
   a Google Places cache that has passed its 30-day limit. An owner-uploaded or
   free-licensed photo is out of scope and is never touched.
2. It asks the owner photo first: has the business uploaded one through SC-205?
   If so, that photo stays and the row is skipped.
3. It asks the free-licensed sources:
   a. OSM: the `image` / `wikimedia_commons` tag on the matched feature, by the
   geocoder's provider reference;
   b. Wikimedia Commons: a search for the venue or its brand.
4. A free candidate is accepted only with a reusable licence (CC0, public domain,
   CC-BY, CC-BY-SA) and the attribution that licence requires. Its bytes are
   stored permanently through the existing image pipeline.
5. Else it asks Google Places: by the stored place id, or by a text search for the
   venue's name and address when no id is stored yet. The returned place must
   match the listing's name and address or nothing is taken. From it the run
   reads the first photo, its author attributions, and the place id to keep.
6. A Places photo is stored as a **cache, not an asset**: the place id, the photo
   name, the attribution and an expiry at most 30 days away. The bytes are held
   only for that window.
7. The listing detail carries the credit for whichever source won; the image
   endpoint serves the stored bytes while they are inside their window.

**Alternates**

- 2a. An owner photo exists → skipped, no candidate is fetched.
- 3a. No free candidate → falls through to step 5.
- 4a. Several free candidates → OSM before Commons, then the source's own order.
- 5a. No place id, and a text search finds no place that matches the name and
  address → the listing stays without a photo and is listed as a gap.
- 6a. A Places photo is already cached and still fresh → nothing to do.
- 7a. `--replace` on a free-licensed or Places photo → refetched; an owner photo
  is still never replaced.

**Exceptions → resulting state**

| Exception | Resulting state |
|---|---|
| Fetch fails (non-2xx, timeout, I/O) | Row reported as failed; any unexpired bytes kept; run continues |
| Bytes do not decode as an image | Same as a fetch failure; nothing persisted (variants are resized before the original is written) |
| Attribution missing for a licence that requires it | Candidate rejected — a CC-BY photo without credit is a licence breach |
| Places quota exhausted or key rejected | That row is reported; the cache it already has is kept until expiry; the run does not abort |
| A cached Places photo passes its expiry before the pass runs | It is not served; the UI shows its placeholder until the refresh succeeds |

**Postconditions.** At most one cover photo per listing, from a source with a
licence and a credit; or no photo and a reported gap. Nothing scraped, nothing
fabricated, and no Places photo held beyond its window.

**ACs:** *proposed, not yet ratified in Shortcut* (Q-2) — a licensed or
attributed photo, or an honest gap; no fabricated or scraped image; a re-run is
idempotent; an owner photo is never replaced; a Places photo is never stored
permanently and never served after expiry.

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
