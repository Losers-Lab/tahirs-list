# SC-231 — Decisions and their reasons

> **Purpose:** why the design is shaped this way, including what was rejected · **Derived from:** 00–08 · **Delta:** record, not a spec — `07` and `08` are binding

**Status:** proposed 2026-11-09; binding only after the owner approves the design PR.

## D-1 · Classification inside the run, photos after the load

Venue type and cuisine are decided while the row is still a candidate, so the
reviewed load remains the only writer of the catalogue and the run directory
stays immutable. Photos cannot work that way: the image store is keyed by
listing id, which does not exist until the load runs. They are therefore a
separate command with its own manifest, report and `--execute` gate.

*Rejected:* a post-load command that re-reads the source snapshots for
classification — it duplicates the source parsing, needs a second audit path,
and puts a second writer on the catalogue for data the batch could have carried.

## D-2 · Brand-map cuisine is evidence, but not the certifier's word

A curated chain → cuisine table is stronger than a name keyword and weaker than
a published cuisine. It is labelled `BRAND_MAP` so a curator can tell the
three apart. It never overrides the source; nothing overrides the source.

*Rejected:* name keywords only — it would leave every chain row null,
including the ones whose cuisine is not in dispute.

## D-3 · A closed nine-value venue type plus "undecided"

`RESTAURANT`, `CAFE`, `FAST_FOOD`, `BAKERY`, `GROCERY`, `MARKET`, `BUTCHER`,
`CATERER`, `OTHER`, and null. `OTHER` means *decided: none of these* (a
distributor, a processor); null means *not decided*. The two are different
answers and the report treats them differently.

*Rejected:* a full OSM taxonomy — it is a large surface we cannot populate from
certifier categories, and the product question is only "can I eat here".

## D-4 · Evidence lives on the listing, as two enums

`venue_type_source` and `cuisine_source` are short CHECK-constrained enums.
The report is the detailed audit; the columns let the detail surface (and a
future curator tool) say *why* without re-reading a run directory that may be
archived.

*Rejected:* report-only provenance — the AC asks for it "on the listing".

## D-5 · `venueType` is not exposed in the API yet

Publishing non-restaurants is an open product decision (Q-4). Exposing the type
now would force a UI that either hides rows or presents groceries as
restaurants. It is stored, reported, and used for the index rule only.

*Rejected:* expose now and hide in the frontend — a hidden field is a contract
someone will depend on.

## D-6 · Non-serving types are stored but not indexed

The restaurant search reads `listing_search`. A butcher or a distributor is a
real row we want to keep and classify, but it is not a restaurant result. The
load simply does not write its mirror row.

*Rejected:* adding `venue_type` to the mirror and filtering in the search query
— that edits the search SQL (SC-199's quantization work) for a decision that is
still open, and the default outcome is identical.

## D-7 · Free photo sources only

OSM `image`/`wikimedia_commons` tags and Wikimedia Commons search, both with a
machine-readable licence in `extmetadata`. No keyed provider: no billing, no
extra terms to review, and every photo is attributable.

*Rejected:* Google Places photos — billed, and the terms govern caching and
attribution in ways we have not reviewed.

## D-8 · Credit on the detail page and on cards

CC-BY and CC-BY-SA require attribution wherever the work is shown. Cards show a
thumbnail of the work, so the credit travels with it as a small caption.

*Rejected:* detail-only credit — simpler UI, weaker licence compliance.

## D-9 · Bytes first, credit row second

Two stores means one of them can fail. Writing bytes first is recoverable: a
crash leaves orphan objects that the next run overwrites, and no listing points
at them. The reverse order could show a photo whose file is missing, or worse,
an unattributed one.

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

## D-12 · The pre-approval prototype is a spike, not a design

`VenueType` and `VenueClassifier` were written in this worktree before the
design existed, while the story was parked. They are kept as a reference for the
curated tables but are not cited by `00`–`08`. Implementation is TDD against the
approved design; anything the spike got right will come back as a test.

## Open questions carried forward

Q-1 iteration · Q-2 phase-2 ACs · Q-3 phone/website vs SC-202 · Q-4 publishing
non-restaurants · Q-5 exposing the type · Q-6 photo sources (closed by D-7
pending approval) · Q-7 review queue (closed by D-10 pending approval) · Q-8
report vs manual edit.
