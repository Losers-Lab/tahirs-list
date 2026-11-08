# SC-211 · Resolving decisions and configuring metros

Two operational interfaces a curator or agent uses after a run exists:

1. **`resolutions.json`** — how a blocked candidate becomes loadable.
2. **`metros.json`** — how "which places we cover" is configured, without code.

## When you decide

A run never writes to the catalogue. The sequence is:

```
ingest-sample                      # fetch + geocode -> run awaiting review
load-ingest-batch --run-id=...     # preview: shows the diff and any findings
   ... answer one question per finding ...
load-ingest-batch --run-id=... --execute
```

You decide **at preview time**, always **before** `--execute`. If the preview
has no findings, `--execute` applies the diff immediately. If it has findings,
`--execute` refuses; nothing is written until every finding has a decision.

## The decision sheet is generated, not authored

`load-ingest-batch` writes `runs/<run-id>/resolutions.json` from the preview.
Each entry carries the claim or venue key, the two listing ids with their names
and addresses, and the question. You never invent a schema or an id:

```json
{
  "runId": "sample-20261108T120000Z",
  "claims": [
    {
      "source": "hms",
      "record": "H-1",
      "decision": "unresolved",
      "listingId": null,
      "reason": "",
      "question": "Claim hms::H-1 already belongs to X, but this venue resolves to Y. What is true?",
      "evidence": ["claim currently on: \"X\" — ... [<id>]", "venue resolves to: \"Y\" — ... [<id>]"]
    }
  ],
  "duplicates": []
}
```

Re-running the preview fills in entries for new findings and **never erases an
answer you already gave**.

## The decisions

### Claim conflict — a claim already points at a different listing

| Decision | Meaning | Effect in the load transaction |
|---|---|---|
| `same-venue` + `listingId` | The two listings are the same restaurant; keep the one you name and retire the other | Moves claims, cuisines, halal items, favourites and verification reviews to the keeper, recomputes the keeper's trust from the union (a folded-in Tier-1 claim restores `VERIFIED`), removes the duplicate from the search mirror, deletes the duplicate row, then writes the candidate into the keeper |
| `attach-to` + `listingId` | The claim was attached to the wrong listing; it belongs to the listing the venue resolves to | Re-points exactly that claim (`(source, source_record_ref)` → new `listing_id`), then writes the candidate into that listing |
| `bad-citation` (no `listingId`) | The citation is wrong and must not be written | Drops the claim from this batch. If it was the candidate's only claim the candidate is rejected (`MISSING_CITATION`); the existing claim row is left untouched as audit evidence |

`attach-to` must name the listing the venue resolves to. Naming the claim's
current owner is refused — use `same-venue` when the two listings are one
venue.

### Suspected duplicate — same location, no identity match

| Decision | Meaning | Effect |
|---|---|---|
| `same-venue` + `listingId` | Same restaurant as the listing you name | Upgrades that listing in place (no merge: there is nothing to retire) |
| `distinct` (no `listingId`) | A different business at the same address/suite (e.g. a food court) | Inserts the candidate as a new listing. Two businesses may share one point; identity is the claim/venue key, and only the seed corpus keeps a per-point unique index |

## Interactive or agent-driven

- **Interactive:** `scripts/load-ingest-batch.sh` passes `--interactive` when
  it is attached to a terminal (piped/CI runs do not, so they fail instead of
  blocking). The CLI asks one question per finding, numbered, with the evidence
  above it, then requires a reason. Each answer is written to `resolutions.json`
  immediately, so an interrupted session keeps what it already decided. Use
  `--no-interactive` to force the printed instructions.
- **Agent / batch / PR review:** run with `--no-interactive`, read the generated
  `resolutions.json` plus the printed questions, fill `decision`/`listingId`/
  `reason`, and re-run. The same preview validates the file and applies the
  answers. `--resolutions=<path>` reads and writes a file outside the run
  directory.

## Stale or invalid decisions are refused

- A decision whose finding no longer exists (the catalogue changed between
  previews) → `StaleResolutionException`; nothing is applied. Re-run the
  preview and decide again.
- `attach-to` / `same-venue` naming a listing that is not one of the two in the
  conflict → `InvalidResolutionException`.
- A decided entry without a reason → refused before any database work.
- A resolution file for a different run → refused.
- At execute time the merge/re-point is re-validated against the current rows;
  a mismatch fails the whole batch (`BatchPlanException`) rather than
  half-applying it.

The applied decisions are printed in the diff, stored in `resolutions.json`,
and their checksum (`resolutionsSha256`) is recorded in `loaded.json`.

## Metros

`backend/ingestion/src/main/resources/metros.json` (or `--metros-file=<path>`):

```json
{
  "metros": [
    {
      "name": "Toronto",
      "center": { "lat": 43.6532, "lng": -79.3832 },
      "radiusKm": 50.0
    }
  ]
}
```

- **Membership is the circle.** A point is in the metro when it is within
  `radiusKm` of the centre. There is no list of city names, no aliases and no
  string matching anywhere: a place that did not exist when the config was
  written is covered as soon as it is inside a circle.
- **Rows that carry coordinates** are filtered by distance without spending a
  geocoder call. A row outside every circle is skipped and counted in the run's
  gaps (`N row(s) outside every configured metro`), never silently dropped.
- **Rows without coordinates** (HMA, curated manual rows) are geocoded once to
  decide their metro; the candidate build reuses that result.
- **The accepted location is still the geocoded one.** Directory coordinates
  are only the cheap prefilter; the candidate's point always comes from the
  geocoder, and the confirmed point is re-checked against the circle
  (`OUTSIDE_METRO` with the distance if the source coordinates were wrong).
- **Adding a metro is data.** Add an entry (and mount the file with
  `--metros-file` if it is not the default) — no code change, no place-name
  list to keep in sync.
- `--metros` selects configured metros by name. A mistyped name fails the run
  instead of silently selecting nothing.
