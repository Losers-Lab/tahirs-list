# SC-231 — Phase 10: Traceability

> **Purpose:** the record of what was built, tested and verified, WI by WI · **Derived from:** 09 · **Delta:** new; updated as each WI lands

**Status:** implementation started 2026-11-10 on branch `feat/SC-231-enrichment`, PR #89. Design frozen at `0a59ee2`.

## Acceptance criteria → evidence

| AC (verbatim in `00`) | WI | Evidence | State |
|---|---|---|---|
| Every ingested candidate gets a venue type from a closed vocabulary, or null when the evidence does not decide | WI-2, WI-3, WI-7, WI-8 | `AdmissionPolicyTest` (14) + the `CandidateFactory` gate | **done** |
| A row whose source category is not a restaurant is never classified as a restaurant | WI-7, WI-8 | the three real HMS rows excluded by test; HFSAA `MEAT_MARKET` too | **done** |
| Cuisine is set only when evidence exists; otherwise null | WI-6, WI-9, WI-12 | `CuisineAssignerTest` (9) + `ExposedIngestionBatchTest` write path | **done** |
| The decision and its evidence are visible in the run report and on the listing, and a re-run is idempotent | WI-10, WI-12 | Enrichment section in every run report; `venue_type_source`/`cuisine_source` columns; idempotence tests | **done** |
| No existing owner/curator value is overwritten | WI-12 | `ExposedIngestionBatchTest.a re-run never overwrites a cuisine an owner set` | **done** |
| TDD: classifier unit tests per evidence source; load test proves the columns are written | WI-7, WI-9, WI-12 | domain 26 / ingestion 29 / persistence 26 green | **done** |
| *Proposed (Q-2):* a licensed or attributed photo, or an honest gap; no fabricated image; Places never permanent | WI-13…WI-16 | `AttachCoverPhotoTest` (9), `ExposedCoverPhotoRepositoryTest` (5), `ListingImageEndpointTest` expiry case | **done** (OSM photo source waits on SC-227) |

## Invariants → tests

| Invariant (`01`) | Test | State |
|---|---|---|
| INV-1 only admitted rows are written | `AdmissionPolicyTest` (the three rows) + the `CandidateFactory` gate | **done** — the gate runs before geocoding, so an excluded row never becomes a candidate |
| INV-2 shop word beats a restaurant category | `AdmissionPolicyTest.a poultry meat market filed under Restaurants is excluded` | **done** |
| INV-3 premises word rescues; bakery admitted | `AdmissionPolicyTest.a market with a kitchen in it is a restaurant` · `a pure bakery is admitted as a bakery` | **done** |
| INV-4 evidence order | `AdmissionPolicyTest.an unknown category uses the OSM feature` · `a name with only a premises word still admits` | **done** |
| INV-5 undecided is held | `AdmissionPolicyTest.nothing decides means held, never guessed` | **done** |
| INV-6 type always has a source | `RestaurantListingTest.imported() refuses a type with no evidence behind it` + the DB CHECK | **done** |
| INV-7 cuisine conflict stays null | `CuisineAssignerTest.two families in one name is a conflict` | **done** |
| INV-8 cuisine only for an admitted row | `CandidateFactory` asks it after admission only; `CuisineAssignerTest` | **done** |
| INV-9 fill nulls only, idempotent | `ExposedIngestionBatchTest.a re-run never overwrites a cuisine an owner set` · `a re-run keeps the first derived type` | **done** |
| INV-10 deterministic, side-effect free | `AdmissionPolicyTest.the decision is deterministic` | **done** |
| INV-11 one photo per listing | `ExposedCoverPhotoRepositoryTest.saving again replaces the row` | **done** |
| INV-12 free photo needs a licence | `AttachCoverPhotoTest.a candidate with no licence is refused before its bytes are fetched` · `CC-BY without an author is refused` | **done** |
| INV-13 Places is cache-only, never served after expiry | `CoverPhotoTest.a permanent Places photo is refused` · `ListingImageEndpointTest.a Places cache is served while it is live and 404s once it expires` | **done** |
| INV-14 precedence, owner photo untouched | `AttachCoverPhotoTest.an owner photo is never touched…` · `a free-licensed photo wins over Places` | **done** |
| INV-15 missing stays missing | `AttachCoverPhotoTest.nothing anywhere is a gap, not a fabricated photo` | **done** |
| INV-16 trust untouched | `ExposedIngestionBatchTest` trust suite — enrichment columns are separate from claim-derived trust | **done** |
| INV-17 report matches catalogue | the `IngestReport` Enrichment section (rendered every run) | **partial** — present, not asserted row-for-row |

## Work item progress

| WI | Commit | Test count added | State |
|---|---|---|---|
| WI-1 | `a27486e` | 9 (`EnrichmentSchemaMigrationTest`) | **done** |
| WI-2 | `a27486e` | 6 (`VenueTypeTest`) | **done** |
| WI-3 | this commit | 4 (`AdmissionDecisionTest`) | **done** |
| WI-4 | this commit | 11 (`CoverPhotoTest`) | **done** |
| WI-5 | this commit | 5 (`RestaurantListingTest`) | **done** |
| WI-6 | this commit | 4 (`EnrichmentRulesTest`) | **done** |
| WI-7 | this commit | 14 (`AdmissionPolicyTest`) | **done** |
| WI-8 | this commit | wired into `CandidateFactory`; covered by the existing pipeline suite | **done** |
| WI-9 | this commit | 9 (`CuisineAssignerTest`) | **done** |
| WI-10 | this commit | report section; rendered in every run report | **done** |
| WI-11 | this commit | `IngestionCandidate` carries the verdict; JSON round-trip via `CandidateDto` | **done** |
| WI-12 | `ffe899d`+ | 3 (`ExposedIngestionBatchTest`) — type, sources and cuisine written; owner cuisine wins; a re-run keeps the first type | **done** |
| WI-13 | this commit | 9 (`AttachCoverPhotoTest`) — precedence, licence/credit refusal, Places cache, expiry refresh | **done** |
| WI-14 | this commit | 5 (`PhotoSourceMappingTest`) — venue match, address corroboration, Commons licence table; `AttachCoverPhotosMain` + `scripts/attach-cover-photos.sh` | **done** |
| WI-15 | this commit | 5 (`ExposedCoverPhotoRepositoryTest`) + 1 (`ListingImageEndpointTest` expired cache) | **done** |
| WI-16 | this commit | credit fields on `DetailResponse`/`BrowseCard` (`DetailResponseTest` contract fields) | **done** |
| WI-17 | this commit | 3 (`PurgeNonRestaurantsTest`); `PurgeNonRestaurantsMain` + `scripts/purge-non-restaurants.sh` | **done** |
| WI-18 | this commit | 3 (`PhotoCredit.test.ts`); `PhotoCredit` on the detail hero and browse cards | **done** |
| WI-19 | — | — | not started |

## Runtime proof (2026-11-10)

Re-processed end to end against a clean database (`halal_sc231`), because the shared dev
database is on `feat/SC-205-photo-upload`'s `V25`/`V26` and could not boot this branch.

| step | command | result |
|---|---|---|
| ingest | `./scripts/ingest-sample.sh` (`--caps=100`, 4 metros) | run `sample-20261110T160204Z`: **44 admitted**, **6 excluded** (butchers), 0 undecided, 192 rejected, 5 cuisines, 0 conflicts |
| load | `:bootstrap:loadIngestBatch --execute` | `load-20261110T161700Z`: inserted 43, upgraded 1 (Paramount Queensway → the seed row), claims 44 |
| verify | psql | 44 rows carry `venue_type`; 5 carry `cuisine` + `cuisine_source`; the 5 also have `restaurant_listing_cuisines` rows; `listing_search` = 73 |
| purge | `:bootstrap:purgeNonRestaurants` | **0 rows**: the three meat markets never entered, because the gate ran first. The purge remains the remediation for the existing catalogue; its selection is proven by `PurgeNonRestaurantsTest` |
| photos | `:bootstrap:attachCoverPhotos --execute --replace` | **53 attached** (51 Google Places as 30-day caches, 2 Wikimedia permanent), **20 refused with reasons**, 0 silent gaps |

Then the app was booted from this branch (`:3003` → backend `:8081`) and driven with
Playwright. The script reads the API first and asserts the browser text contains what the
API says — a screenshot alone is not evidence.

| assertion | result |
|---|---|
| `search?q=<name>` card shows `lebanese` | **true** (`search-cuisine.png`) |
| detail page shows `lebanese` | **true** (`detail-cuisine.png`) |
| detail page shows the credit | **true** (`detail-photo-credit.png`, `Google Maps, Drew Hayes`) |
| search card shows the same credit over the photo | **true** (`search-photo-credit.png`) |
| the hero `<img>` really decoded (`/v1/listings/{id}/image?variant=full`) | **true**, `naturalWidth = 1600` |

The four cuisine rows that also got a photo (Imperial Biryani, Kababjees, Khorasan Kabab,
Samosa Paradise) are Places caches expiring `2026-12-12`; a served photo therefore carries
its credit and its cache window in the same row. `Paramount Fine Foods Queensway` was
refused — Places calls it *Paramount Middle Eastern Kitchen - The Queensway*, a different
name whose address the listing's `New York, NY, USA` line cannot corroborate. That is the
near-miss rule working, not a missing feature.

Machine-readable: `runtime-proof/facts.json`.

**A defect this proof caught.** The first pass attached 11 Commons photos and several were
the wrong business: `Ali Baba Mediterranean` (Richardson, TX) got a Birmingham storefront,
`Moon Restaurant` (Queens) got a Hong Kong sign, `Yemen Cafe` (Brooklyn) got Hamtramck.
The name rule had accepted them. `CommonsPlace` now requires the file's own GPS within
**2 km**, or — when it has no GPS — the listing's city named in the file's Commons
categories/description. Re-running refused all nine with the distance or the missing city,
and kept the two that are actually of the place.

## Notes

- **Migration renumbered V25 → V27 (2026-11-10).** `feat/SC-205-photo-upload` already claims `V25__sc_205_image_version.sql` and `V26__add_admin_role.sql`, and the shared dev database has both applied. Two files called `V25` would either collide at merge time or fail Flyway validation on the next boot, so this branch moved to **V27** and left the gap. Verified by `EnrichmentSchemaMigrationTest` against a fresh database.
- The pre-approval spike (`VenueType.kt`, `ingestion/enrich/*`) was **removed** on approval: it modelled one nine-value enum, which the approved design replaced with `VenueClass` (recognise) + `VenueType` (publish). Keeping it would have left two contradictory vocabularies in the tree.
- `SC-227` (PR #84) adds `GeocodedPlace.osmKey/osmValue`. Until it merges, WI-7's OSM step cannot fire and the report states it, as designed.
- **The same dependency reaches the photo pass:** `OsmPhotoSource` needs the matched feature's `image` / `wikimedia_commons` tags, so the pass asks Commons then Places. The CLI prints `osm: unavailable on this build`.
- **A load-blocking source defect surfaced during re-processing (fixed in scope).** HMS publishes `United States Of America` (24 chars) while the catalogue column is `VARCHAR(16)` (V20), so the load refused 17 valid rows with *"Listing country must be 16 characters or fewer"*. `CountryNames.canonical` maps the known aliases (`United States Of America`/`US` → `United States`) and leaves anything unknown untouched, so a genuinely new value still fails loudly rather than being truncated into a different country.
- **Search cards carry the thumbnail but not the credit.** `SearchCard` reads the `listing_search` index, which has no licence or attribution columns. Detail + browse cards (the two surfaces in the design) do show it. Widening the index for four credit strings is a follow-up, not a silent claim of full coverage.
