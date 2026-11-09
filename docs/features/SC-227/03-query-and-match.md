# SC-227 · Query formulation and match confirmation

The pacing/retry change (`01-request-policy.md`) fixed the 503s. The first real
four-metro run then exposed a second, larger defect: **191 of 202 rejected rows
were rejected as `NO_GEOCODER_MATCH`, and most of them were real businesses with
real addresses.** Photon was not down; it was being asked a question it could
not answer.

## Evidence from the run

Run `sample-dallas-nyc-austin-toronto-20261109`, four metros, `--caps=100`:

| metric | value |
|---|---:|
| rows selected into a metro | 146 |
| candidates | 36 |
| rejected | 202 |
| — of which `NO_GEOCODER_MATCH` | 191 |
| — `WRONG_REGION` / `AMBIGUOUS` / `INVALID` | 7 / 2 / 2 |

Probing the live service with the exact rows made the cause plain. For
`Holy Burger Dallas, 18111 Dallas Pkwy suite c200, Dallas, TX`:

| query | result |
|---|---|
| the query the pipeline built | **0 features** |
| address only, suite still present | **0 features** |
| `Holy Burger Dallas, Dallas, TX` | 1 — the right POI |
| `Holy Burger Dallas, 18111 Dallas Pkwy, Dallas, TX` after cleaning | 1 — the right POI |

Photon matches **every token** of the query. `suite c200` is not in its index,
and neither is the abbreviation `Pkwy` (it stores `Dallas Parkway`); one
unmatched token empties the result. Six sampled rejects went from 0 to a hit as
soon as the unit noise was cut and `Rd`/`Ave`/`St` were expanded.

The second half of the defect was in the old confirmation rule: it accepted a
hit when **only the city** matched. Photon answers a long query with *some* POI
in that city — `Cloud Naan, Richardson` returned "Muslim Legal Fund of America",
and a query for `SPICE WOK` returned a bank tower. Accepting those would put
restaurants at the wrong address.

## What changed

### Query

`AddressText` parses one directory address into:

- `street` — unit/suite/`#` noise removed, suffix abbreviations expanded
  (`Rd` → `Road`, `Pkwy` → `Parkway`, `Ave` → `Avenue`, …), directions expanded
  (`N` → `North`), a street named "St Marks" left alone;
- `houseNumber` — `18111`, `258-03`;
- `streetTokens` — distinctive words only (`dallas`, `hillside`), generic road
  words dropped;
- `postalCode` — lifted from the line when the source does not pass one, but
  never from the leading house number (`18111 Dallas Pkwy` is a street, not a
  ZIP).

`CandidateFactory` then asks twice, most precise first:

1. `name, street, city, region` — pins the POI when OSM has it;
2. `street, city, region` — pins the house when the business is not in OSM.

A variant is used only if confirmation accepts it, so coverage can never be
bought with precision. A row that is asked once is still asked once.

### Confirmation

`MatchConfirmationPolicy` now accepts a hit only when the label carries either:

- a distinctive **name** token that is not also the city/region/street
  ("Dallas Grill" must not match every Dallas POI); or
- the row's **house number** *and* a distinctive street token, with the city or
  postal code agreeing.

A shared city alone is no longer evidence. A street-level hit without the house
number is rejected (measured: Photon's street centroid was 1.3–3.1 km from the
real venue in four sampled rows). Queens-style hyphenated numbers (`258-01` vs
`258-03`) are accepted within the same block, since Photon interpolates them.
The reject detail says which evidence was missing.

### Label

`PhotonGeocoder` now returns every part it has — `name, house street, postcode
city, state, country` — so the policy can check the address and an error message
shows what was matched. The label is also what the geocode cache stores.

## Result after the fix

Same four metros, same caps, same sources:

| run | candidates | rejected |
|---|---:|---:|
| before (`…20261109`) | 36 | 202 |
| after query + match (`…20261109c`) | 118 | 93 |
| after HMA city parse (`…20261109d`) | 120 | 91 |
| after stricter name evidence (`…20261109g`) | 118 | 92 |
| after numeric street names (`…20261109h`) | 121 | 89 |

Toronto went from 12 candidates to 35 of 35 fetched. Of the 91 remaining
rejects, 70 are `AMBIGUOUS_GEOCODER_MATCH` and 17 `WRONG_REGION` — mostly rows
that are **not in any selected metro** and are geocoded only to decide their
metro (Chicago, Ohio, Missouri), plus rows whose house number Photon genuinely
does not have.

## Other defects the real run exposed

Both were found only when the batch was fed through the reviewed load, not by
the pipeline tests:

1. **HMA city fields held garbage.** `HmaParser` assumed the last
   comma-separated segment was the city and only understood one
   province/postal shape, so candidates carried `Canada`, `L6Z2WL`,
   `L4W 4GI`, `Ont. L7L 0B6` or `ON L4T 1A8 Canada` as the city. The parser now
   reads back from the end, strips country/province/postal wherever they sit,
   lifts a postal written before the city, and leaves the city null when the
   source glues it to the street. Fixed in `81bf80d`.
2. **HMS's country name did not fit the catalogue.** HMS publishes
   `United States Of America` (23 chars) and the domain caps country at 16, so
   the load aborted the whole batch on
   `IllegalArgumentException: Listing country must be 16 characters or fewer`.
   The HMS translator now maps it to `United States`, the form HFSAA already
   uses, and drops an unexpected long name with a warning instead of
   truncating it.
3. **State names were mixed with codes.** HMS publishes `New York`/`Texas`,
   HFSAA publishes `NY`/`TX`. Region is part of the venue key, so the same venue
   seen in both directories could never merge. The HMS translator now maps US
   states and territories to their two-letter code.

## What this does not solve

- Photon's OSM coverage of US business addresses is incomplete. Rows whose exact
  house number is absent are rejected honestly (`NO_GEOCODER_MATCH` /
  `AMBIGUOUS_GEOCODER_MATCH`), not given a street centroid.
- The public demo is still one small server. `02-geocoder-alternatives.md`
  covers the free fallback options and recommends Geoapify as the keyed
  fallback, with self-hosted Photon as the only vendor-free option. Neither is
  implemented here.

## Tests

`AddressTextTest` (8) pins the parse, `MatchConfirmationPolicyTest` (13) pins
the confirmation evidence, `CandidateFactoryTest` covers the ladder and the
unconfirmed-answer reject, `PhotonGeocoderTest` covers the richer label, and
`LocalityNamesTest` pins the country/state maps. Whole backend at the head:
131 suites, 973 tests, 0 failures, 1 deliberate skip.
