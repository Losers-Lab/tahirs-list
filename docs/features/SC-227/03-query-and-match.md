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

- `street` — unit/suite/`#` noise removed **only when a unit value follows the
  marker** ("Suite 500", "Unit 7A", "#5"); a street named `Ste. Anne Avenue`
  is kept, with or without a house number. Suffix abbreviations are expanded
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

- a **name** with at least **two** distinctive tokens in common with the
  provider's own name for the hit — or the row's whole name is the single token
  that agrees ("Burgermania"). Tokens that are also the city/region/postal/street
  do not count ("Dallas Grill" must not match every Dallas POI); or
- the row's **house number** *and* a distinctive street token, with the city or
  postal code agreeing.

Two tokens rather than one is the rule that stops a partial name matching a
different business. Measured on the four-metro run: `Texas King Irving` was
accepted at a **Burger King 1.2 km away** and `Slappin Chick Hicksville` at a
**Chick-fil-A 681 m away** — one shared word each (`king`, `chick`). Both are now
rejected; the same change drops exactly those two candidates and no others (121
→ 119, venue-key diff).

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
| after provider-name requirement (`…20261109j`) | 121 | 89 |
| after street-vs-suite and one-word fixes (`…20261109m`) | 119 | 91 |

Run `m` is the head of this branch: 119 candidates, 91 rejected
(`AMBIGUOUS_GEOCODER_MATCH` 87, `NO_GEOCODER_MATCH` 4), 0 rows left out by caps
at `--caps=100`. Its candidate set is run `j`'s minus the two wrong-business
rows (`Texas King Irving`, `Slappin Chick Hicksville`); every other venue key
keeps its coordinate, and one row (`Jaffy's Premium Grill`) moved 60 m after the
suite fix. The reviewed load applied 119 upgrades with 0 conflicts.

Toronto went from 12 candidates to 35 of 35 fetched. Most remaining rejects are
rows whose house number Photon genuinely does not have, or whose address matched
a street but not the house number.

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

`AddressTextTest` (14) pins the parse, `MatchConfirmationPolicyTest` (18) pins
the confirmation evidence, `CandidateFactoryTest` covers the ladder and the
unconfirmed-answer reject, `PhotonGeocoderTest` covers the richer label, the
provider name and the street/venue distinction, and `LocalityNamesTest` pins the
country/state maps. Whole backend at the head: 131 suites, 985 tests, 0
failures, 1 deliberate skip.
