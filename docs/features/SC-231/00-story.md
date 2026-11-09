# SC-231 — Enrich ingested listings: type, cuisine and cover photos

> **Purpose:** lock the requirement and its verbatim ACs · **Derived from:** SC-231 (Shortcut) + owner direction 2026-11-09 · **Delta:** new story; extends the SC-211 candidate → load path

| Field | Value |
|---|---|
| Shortcut | https://app.shortcut.com/halal-food-finder/story/231 |
| Epic | M1 Core Search & Discovery (111) |
| Iteration | none assigned — flagged Q-1 |
| Type · state | feature · In Development |
| Priority | Owner-directed 2026-11-09. The story was parked on 2026-11-09 ("not until ingestion is successful and PR #84 is ready"); the owner re-opened it explicitly. |
| Depends on | SC-211 (ingestion) merged · SC-227 (PR #84) for OSM tag evidence · SC-157/SC-183 (image pipeline + variants) |
| Workflow | `@tahirs-feature-workflow`; design approval before implementation |

## Story (verbatim from Shortcut)

> As someone searching for halal food, I want each listing to say **what kind of
> place it is** (restaurant, grocery, butcher…), **what cuisine it serves**, and
> **show a real cover photo**, so I can decide whether it is what I am looking
> for before I travel.

## Why now (verbatim from Shortcut)

> SC-211's ingestion publishes verified rows with name, address, coordinates and
> claims only. The sources already carry more than we store:
>
> - HMS publishes a category per establishment (Restaurants, Retail Store,
>   Slaughter House, Distributor, Processor, Caterer…), a phone, and sometimes
>   coordinates.
> - HMA publishes phone and website.
> - HFSAA publishes a website and a Google place id.
> - The geocoder knows the OSM feature type (amenity=restaurant vs
>   shop=butcher/supermarket) for the matched point.
>
> The catalogue supports cuisine (free text), price, rating, delivery, halal
> scope/items, alcohol, and listing images (FULL + thumbnails, already used by
> the seed-photo ingest), but the ingestion writes none of it. Result: 121
> verified listings with no cuisine, no cover photo, and a butcher-shop row or
> two sitting in a restaurant directory.

## Scope (verbatim from Shortcut)

> 1. **Venue type + cuisine, derived and recorded with evidence.** A
>    deterministic classifier over the source category, the matched OSM tag, and
>    the name; it writes a closed-vocabulary venue type and a cuisine (or leaves
>    them null). Every decision records why. Non-restaurants must at least be
>    identifiable; whether they are published is an open product decision (see
>    below).
> 2. **Cover photos.** A manifest-based ingest (reusing the SC-157 image
>    pipeline) from sources that licence reuse — OSM image tags and Wikimedia
>    Commons — with licence and attribution stored; no photo is invented, and a
>    missing photo stays missing.
> 3. **Remaining supported metadata.** Phone and website where the source
>    publishes them; price, rating, delivery, alcohol, halal scope/items only
>    from a source that actually claims them (owner or verifier), never guessed.

## Acceptance criteria (verbatim from Shortcut — phase 1)

> - Every ingested candidate gets a venue type from a closed vocabulary, or null
>   when the evidence does not decide.
> - A row whose source category is not a restaurant (e.g. HMS "Retail Store"/
>   "Slaughter House") is never classified as a restaurant.
> - Cuisine is set only when evidence exists (source category, OSM tag, or a
>   curated brand mapping); otherwise null.
> - The enrichment decision and its evidence are visible in the run report and
>   on the listing (provenance), and a re-run is idempotent.
> - No existing owner/curator value is overwritten.
> - TDD: classifier unit tests per evidence source; load test proves the columns
>   are written.

The Shortcut story carries ACs for phase 1 only. Phase 2 (photos) has no ACs
yet — this design proposes them in `02-use-cases.md` (UC-03) and marks them
**proposed, not ratified** (Q-2); the owner must ratify them before they bind
implementation.

## Out of scope (verbatim from Shortcut)

> - Reviews, ratings from third parties, owner-uploaded photos, and business
>   hours (SC-202 owns phone/hours/reviews).
> - Paying for a places API.
> - Changing the verification or hand-cut rules.

## Personas

| Prefix | Persona | Relevance |
|---|---|---|
| **DC** | Data Curator — internal operator | Runs enrichment, reviews the report, resolves the review queue |
| U | User | Sees venue type, cuisine and a cover photo on search and detail |
| RO / VRO | Restaurant Owner | May later override a cuisine/type on their own listing (out of scope here) |
| VC | Verification Committee | Not a participant; enrichment never changes verification |
| A | Adapter/actor: OSM (Overpass), Wikimedia Commons | External read-only sources for photos |

## Edge cases

| # | Edge case | Handling |
|---|---|---|
| E-1 | 「Madina Market (Kitchen)」 certified with HMS category *Restaurants* | Source category wins → `RESTAURANT`; the name word "market" never overrides it |
| E-2 | HMS *Retail Store* named "Hamza Meat Shop" | `BUTCHER`; a non-restaurant category can never yield `RESTAURANT` |
| E-3 | HMS *Slaughter House* | `BUTCHER`, never a restaurant |
| E-4 | HMS *Distributor* / *Processor* / *Misc* | `OTHER`, listed in the report |
| E-5 | Name "Pizza & Biryani" — two cuisine families | Cuisine stays null; conflict reported |
| E-6 | Chain with a curated brand cuisine | Cuisine from the brand map; evidence says `BRAND_MAP` |
| E-7 | No OSM tag (SC-227 not merged, or the hit is not a POI) | Category + name evidence only; still deterministic |
| E-8 | Photo candidate with an unknown or non-free licence | Skipped and reported; no bytes stored |
| E-9 | Existing owner-set cuisine | Untouched; the fill-nulls rule wins |
| E-10 | Photo fetch or decode fails | Row keeps any existing photo; gap reported |
| E-11 | Two runs classify the same row differently | The later run's value wins only if the existing value is null; the report shows both |
| E-12 | A listing already has a cover photo and a re-run finds another | Never replaced without `--replace` |

## Open questions

| # | Unknown | Owner | Needed by |
|---|---|---|---|
| Q-1 | Iteration/sprint placement | Adnan | Sprint sign-off |
| Q-2 | **Phase 2 ACs are not ratified in Shortcut** — this design proposes them | Founder | Design approval |
| Q-3 | **Phone/website conflict:** scope phase 3 includes them, but out-of-scope assigns phone/hours/reviews to SC-202. Which story owns phone/website? | Founder | Before any phase-3 WI |
| Q-4 | **Publishing non-restaurants:** PRD BR-1 says restaurants only. Do grocery/butcher/market rows become a published listing type, or stay out of restaurant search? Default until decided: classified, stored, not searchable (D-6). | Founder | Design approval |
| Q-5 | **Exposing `venueType` in the public API** before Q-4 lands | Founder | Design approval |
| Q-6 | Photo sources: free only (OSM + Wikimedia) or a keyed provider? Default: free only (D-7). | Founder | Design approval |
| Q-7 | Cuisine from name keywords is a heuristic — accept a review queue (confidence + evidence) rather than silent assignment? Default: yes (D-5). | Founder | Design approval |
| Q-8 | Does the run report or the listing win when they disagree after a manual edit? | Curator | Design approval |
