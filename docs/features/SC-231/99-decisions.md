# SC-231 — Decisions and their reasons

> **Purpose:** why the design is shaped this way, including what was rejected · **Derived from:** 00–08 · **Delta:** record, not a spec — `07` and `08` are binding

**Status:** proposed 2026-11-09; binding only after the owner approves the design PR.

**L-1 (owner, 2026-11-09): Tahir's List is concerned only with restaurants;
non-restaurants are ignored entirely.** D-3, D-5 and D-6 below follow from it.

**L-3 (owner, 2026-11-10): a bakery is a restaurant.** Bakeries are admitted and
typed `BAKERY`; "bakery" is a premises word, not a shop marker. D-3 records it.

**L-4 (owner, 2026-11-10): photos are owner upload → free-licensed → Google
Places as a proxied cache.** D-7 records it.

## D-1 · Admission and classification inside the run, photos after the load

Type and cuisine are decided while the row is still a candidate, so the reviewed
load remains the only writer of the catalogue and the run directory stays
immutable. Photos cannot work that way: the image store is keyed by listing id,
which does not exist until the load runs. They are therefore a separate command
with its own manifest, report and `--execute` gate.

*Rejected:* a post-load command that re-reads the source snapshots for
classification — it duplicates the source parsing, needs a second audit path,
and puts a second writer on the catalogue for data the batch could have carried.

## D-2 · Brand-map cuisine is evidence, but not the certifier's word

A curated chain → cuisine table is stronger than a name keyword and weaker than
a published cuisine. It is labelled `BRAND_MAP` so a curator can tell the three
apart. It never overrides the source; nothing overrides the source.

*Rejected:* name keywords only — it would leave every chain row null, including
the ones whose cuisine is not in dispute.

## D-3 · Two vocabularies: one to recognise, one to publish

`VenueClass` is what the evidence says, including the classes we refuse
(`GROCERY`, `MARKET`, `BUTCHER`, `CATERER`, `OTHER`). `VenueType` is what a
stored listing may be: `RESTAURANT`, `CAFE`, `FAST_FOOD`, `BAKERY`. Only
`VenueType` is persisted; `VenueClass` appears in the report.

`BAKERY` is an *admitted* type by owner decision L-3, so "bakery" is a premises
word: `Firin Bakery` is admitted as a bakery, and `Mannan Bakery Factory` is
admitted with "factory" queued for a curator.

*Rejected:* one nine-value enum stored on the row. It would either persist
`BUTCHER` on rows that must not exist (contradicting L-1) or force the classifier
to answer null for something it recognised perfectly well, losing the reason in
the report.

*Rejected (L-3):* treating a bakery as a shop and excluding it unless the name
also says grill/restaurant. It was the default before the owner ruled; it would
have removed three real rows the owner considers restaurants.

## D-4 · Evidence lives on the listing, as two enums

`venue_type_source` and `cuisine_source` are short CHECK-constrained enums. The
report is the detailed audit; the columns let a later surface say *why* without
re-reading a run directory that may be archived.

*Rejected:* report-only provenance — the AC asks for it "on the listing".

## D-5 · `venueType` is not exposed in the API yet

Every stored listing is now a restaurant (L-1), so the type only distinguishes
restaurant / cafe / fast food / bakery. That is useful, but the deck of cards is
already being reworked (SC-199) and adding a field to three DTOs for a UI nobody
has designed yet is speculative.

*Rejected:* expose now and hide in the frontend — a hidden field is a contract
someone will depend on.

## D-6 · Non-restaurants are excluded from the catalogue entirely

A non-restaurant is not stored, not indexed and not published. It becomes an
`IngestReject` with its recognised class and the deciding evidence, so the run
report can say "37 rows excluded, 22 of them because the name says butcher".

*Rejected:* store the row, hide it from search, publish later. That was the
design before L-1. It leaves rows in `restaurant_listings` that the product says
do not belong, forces every future query to remember to filter them, and makes
"is this row published?" a property of the query rather than of the data.

**Consequence, accepted:** a row the source files under "Restaurants" but whose
name says butcher (three such rows exist today) is excluded. A coarse category
loses to a specific shop word.

## D-7 · Photos: owner upload first, free-licensed second, Places as a cache

Three sources, in that order:

1. **Owner upload** (SC-205) — the business's own photo. Permanent, no licence
   question, never touched by the pass.
2. **Free-licensed** (OSM `image`/`wikimedia_commons`, Wikimedia Commons search) —
   permanent, with a licence and a credit line. Measured coverage on the 29-row
   seed was 4, so it cannot carry the catalogue alone.
3. **Google Places API** — the photo is fetched with the venue's place id and
   held as a **cache for at most 30 days**. Only the place id is kept
   indefinitely; the bytes and the photo name are not. Attribution is stored and
   shown.

At our size the Places free tier covers a full refresh several times over (1,000
free calls per SKU per month; 118 listings use ~236), so this is a licensing
design, not a cost one.

*Rejected:* free sources only. Honest, but a cover photo on roughly one listing
in seven is not the product.

*Rejected:* storing Places photos as permanent assets. The terms allow display
with attribution and limited caching, not a permanent copy — the very thing the
current image pipeline does.

*Rejected:* scraping Google listing images from the Maps CDN or Google Images —
what the 25-of-29 seed photos actually did. Those are the uploader's copyright,
there is no licence to republish, and Google's terms forbid it. "It is on the
public internet" is not a licence.

*Rejected:* proxying Places at request time. It puts a third-party dependency in
the page's hot path and makes a Google outage a site outage. The pass refreshes;
the endpoint only reads.

**Measured on a live key (2026-11-10).** `places:searchText` for
`Afghan Bbq & Pizza, Kingston Road, Scarborough` returned exactly one place —
`4379 Kingston Rd Unit 4, Scarborough, ON M1E 2M9`, the same venue already in the
catalogue — with **10 photos**, the first 3600×4800. The photo media endpoint
returned `image/jpeg`, 65 823 bytes at `maxWidthPx=400`, with
`authorAttributions: "Md Abu Rihan"`.

Three things follow from that:

1. **One search gets the place id *and* the photos.** `places.photos` comes back
   in the Text Search field mask, so a listing needs one search plus one photo
   fetch — not a search, a details call and a photo fetch.
2. **The place id is worth storing.** A refresh can go straight to Place Details
   by id instead of searching again, which is both cheaper and more stable than
   re-resolving a name.
3. **The attribution is a real person's name**, so it is displayed, not stored and
   forgotten — that is the licence, in one field.

## D-8 · Credit on the detail page and on cards

CC-BY and CC-BY-SA require attribution wherever the work is shown. Cards show a
thumbnail of the work, so the credit travels with it as a small caption.

*Rejected:* detail-only credit — simpler UI, weaker licence compliance.

## D-9 · Bytes first, credit row second

Two stores means one of them can fail. Writing bytes first is recoverable: a
crash leaves orphan objects that the next run overwrites, and no listing points
at them. The reverse order could show a photo whose file is missing, or worse,
an unattributed one.

This matters more for a Places cache than for a permanent photo: orphaned cache
bytes are simply overwritten on the next refresh, and because the row carries the
expiry, a cache whose row was never written is never served at all.

## D-10 · Conflicts go to the report, not to a ticket per conflict

The review queue is a section of the run report. Opening a Shortcut story per
conflict would flood the board for a queue a curator clears by editing a table.

*Rejected:* ticket per conflict — revisit if conflicts become frequent.

## D-11 · Phase 3 is not designed yet

The story's scope phase 3 includes phone and website; its out-of-scope section
gives phone/hours/reviews to SC-202. Both cannot be true. Phone and website need
a storage decision (there is no column today), and the API does not carry them
either. Designing them here would invent an answer to a product question, so
they wait for Q-3.

## D-12 · The purge of existing rows is not a migration

Three rows in the catalogue are not restaurants. Deleting data inside a Flyway
migration hides a destructive act in a schema change that gets applied
automatically. The purge is therefore a separate, reported step: re-run the gate
over stored rows, print the list, take a `pg_dump`, delete the confirmed rows and
their claims.

*Rejected:* `DELETE` in `V27` — irreversible on upgrade, invisible in review.

## D-13 · The pre-approval prototype is a spike, not a design

`VenueType` and `VenueClassifier` were written in this worktree before the design
existed, while the story was parked. They predate the gate and the
two-vocabulary split. They are kept as a reference for the curated tables but are
not cited by `00`–`08`. Implementation is TDD against the approved design;
anything the spike got right will come back as a test.

## Open questions carried forward

Q-1 iteration · Q-2 phase-2 ACs · Q-3 phone/website vs SC-202 · Q-5 exposing the
type · Q-6 photo sources (D-7) · Q-7 review queue (D-10) · Q-8 report vs manual
edit. Q-4 is closed by L-1 and Q-9 by L-3.
