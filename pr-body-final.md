## Summary

SC-227 started as "pace and retry Photon requests". Running the real four-metro ingestion on this branch then exposed four defects that stopped the catalogue from being replaced, so this PR carries their fixes as well. Every one was found by executing the pipeline, not by a unit test.

| # | Defect | Fix |
|---|---|---|
| 1 | Photon 503s under load stalled runs | One in-flight request, 1 s spacing, `limit=1`, project UA, retry 408/429/5xx/IO with bounded backoff |
| 2 | **191 of 202 rows rejected as `NO_GEOCODER_MATCH`** — Photon matches every token, so `suite c200` and unexpanded `Rd` emptied the result; the old rule also accepted any POI in the same city | Address cleaning + suffix expansion, a two-step query (name+street, then street), and confirmation by house number + street. City alone is no longer evidence; name evidence is read only from the label's name slot, not from the state/city the label also carries |
| 3 | HMA candidates carried `Canada`, `L6Z2WL`, `Ont. L7L 0B6`, `ON L4T 1A8 Canada` as the **city** | Parser reads back from the end, strips country/province/postal anywhere, lifts a postal before the city, leaves city null when it is glued to the street |
| 4 | The load aborted the whole batch: `Listing country must be 16 characters or fewer` (HMS publishes `United States Of America`) | HMS translator maps to `United States`; also maps state names to two-letter codes so HMS `New York` and HFSAA `NY` produce the same venue key |

Round-2 review follow-ups are included: accent folding, whole-token postal matching, hyphen-bounded province/country (`Niagara-on-the-Lake`), a 400-character query cap, numeric street names matched against ordinals (`40` vs `40th`), and a shared `LocalityNames`.

## Evidence from the real runs

Run `sample-dallas-nyc-austin-toronto-20261109i`, four metros, `--caps=100`, all sources:

| | before | final |
|---|---:|---:|
| candidates | 36 | 121 |
| rejected | 202 | 89 |
| Toronto | 12 | 35 of 35 fetched |

All 121 candidates are Tier-1 (HMS/HFSAA/HMA), `VERIFIED` on `CERTIFIER_REGISTRY`, hand-cut `TRUE`, `NO_CROSS_CONTAMINATION`. The load applied them in one transaction: **121 inserted/upgraded, 0 conflicts, 0 unresolved findings**. The catalogue holds 121 listings (NY 55, ON 32, TX 25, NJ 6, 3 with no province in the source), 118 brands, 121 claims. Public search: NYC 54, Dallas 24, Toronto 35, Austin 1.

A spot check of HMS candidates against HMS's own map pins: 48/61 within 100 m, 56/61 within 250 m. The one large outlier is a source row whose published address is "New York, NY, USA" (no street), matched by name to a Jackson Heights POI.

A separate measurement of the fallback candidates on 60 in-metro HMS rows (Geoapify 49/60 ≤100 m, median 2 m; geocod.io 24/60, median 170 m) is in `docs/features/SC-227/04-fallback-measurement.md` and became story **SC-229**.

## Review status

Round 1 (on `192fc88`) approved the pacing/retry policy. Omar's round 2 on `b9a8e1d` requested changes on two blockers — both fixed in `efaca9b` with regression tests. Yusuf's round 2 passed at `5e60844`; his two HMA postal findings are fixed in `b374773`. Final rounds are on the head of this PR.

## Tests

Whole backend at the head: `./gradlew build` in `eclipse-temurin:21-jdk` with Testcontainers PostGIS — **BUILD SUCCESSFUL, 131 suites, 973 tests, 0 failures / 0 errors, 1 deliberate skip**.

New/updated coverage: `AddressTextTest` (8), `MatchConfirmationPolicyTest` (13), `CandidateFactoryTest` (ladder, caching, unconfirmed answer), `PhotonGeocoderTest` (25), `HmaParserTest` (dirty-address shapes, postal), `HmsTranslatorTest` (country and state normalisation), `LocalityNamesTest`.

## Docs

- `docs/features/SC-227/01-request-policy.md` — what komoot publishes and the client policy
- `docs/features/SC-227/02-geocoder-alternatives.md` — free provider comparison with vendor citations
- `docs/features/SC-227/03-query-and-match.md` — the query/match defect and evidence
- `docs/features/SC-227/04-fallback-measurement.md` — measured Geoapify/geocod.io quality

## Operational notes

- The public Photon demo remains best-effort; a long outage still stops a run. Geoapify is the measured fallback (SC-229).
- The load was applied to the local dev catalogue after a verified `pg_dump` backup; users and the geocode cache were untouched.
- Open follow-ups from QA, recorded but not blocking: one shared name token can still confirm a same-city POI (e.g. "King's …"); HMA postal codes are not used for matching when the city is present.

Author: hamza
<!-- attribution: hamza -->
