# SC-231 — Phase 10: Traceability

> **Purpose:** the record of what was built, tested and verified, WI by WI · **Derived from:** 09 · **Delta:** new; updated as each WI lands

**Status:** implementation started 2026-11-10 on branch `feat/SC-231-enrichment`, PR #89. Design frozen at `0a59ee2`.

## Acceptance criteria → evidence

| AC (verbatim in `00`) | WI | Evidence | State |
|---|---|---|---|
| Every ingested candidate gets a venue type from a closed vocabulary, or null when the evidence does not decide | WI-2, WI-3, WI-7, WI-8 | — | not started |
| A row whose source category is not a restaurant is never classified as a restaurant | WI-7, WI-8 | — | not started |
| Cuisine is set only when evidence exists; otherwise null | WI-6, WI-9, WI-12 | — | not started |
| The decision and its evidence are visible in the run report and on the listing, and a re-run is idempotent | WI-10, WI-12 | — | not started |
| No existing owner/curator value is overwritten | WI-12 | — | not started |
| TDD: classifier unit tests per evidence source; load test proves the columns are written | WI-7, WI-9, WI-12 | — | not started |
| *Proposed (Q-2):* a licensed or attributed photo, or an honest gap; no fabricated image; Places never permanent | WI-13…WI-16 | — | not started |

## Invariants → tests

| Invariant (`01`) | Test | State |
|---|---|---|
| INV-1 only admitted rows are written | `LoadIngestionBatchTest.nonRestaurantsAreNeverWritten` | not started |
| INV-2 shop word beats a restaurant category | `AdmissionPolicyTest.restaurantCategoryLosesToShopName` | not started |
| INV-3 premises word rescues; bakery admitted | `AdmissionPolicyTest.premisesWordRescuesShopName` · `bakeryIsAdmitted` | not started |
| INV-4 evidence order | `AdmissionPolicyTest.evidenceOrder` | not started |
| INV-5 undecided is held | `AdmissionPolicyTest.undecidedIsHeld` | not started |
| INV-6 type always has a source | `VenueTypeTest.everyTypeHasASource` | not started |
| INV-7 cuisine conflict stays null | `CuisineAssignerTest.conflictStaysNull` | not started |
| INV-8 cuisine only for meal-serving | `CuisineAssignerTest.nonServingTypeHasNoCuisine` | not started |
| INV-9 fill nulls only, idempotent | `LoadIngestionBatchTest.enrichmentFillsNullsOnly` | not started |
| INV-10 deterministic, side-effect free | `AdmissionPolicyTest.isDeterministic` | not started |
| INV-11 one photo per listing | `CoverPhotoTest.onePerListing` | not started |
| INV-12 free photo needs a licence | `AttachCoverPhotoTest.refusesUnknownLicence` | not started |
| INV-13 Places is cache-only, never served after expiry | `CoverPhotoTest.placesPhotoIsCacheOnly` · `ListingImageEndpointTest.expiredCacheIsNotServed` | not started |
| INV-14 precedence, owner photo untouched | `AttachCoverPhotoTest.ownerPhotoIsNeverTouched` · `precedence` | not started |
| INV-15 missing stays missing | `AttachCoverPhotoTest.missingStaysMissing` | not started |
| INV-16 trust untouched | `LoadIngestionBatchTest.enrichmentLeavesTrustAlone` | not started |
| INV-17 report matches catalogue | `LoadIngestionBatchTest.reportMatchesCatalogue` | not started |

## Work item progress

| WI | Commit | Test count added | State |
|---|---|---|---|
| WI-1 | `[SC-231] Add V25 schema and venue vocabularies` | 9 (`EnrichmentSchemaMigrationTest`) | **done** |
| WI-2 | same commit | 6 (`VenueTypeTest`) | **done** |
| WI-3 | — | — | not started |
| WI-4 | — | — | not started |
| WI-5 | — | — | not started |
| WI-6…WI-18 | — | — | not started |

## Notes

- The pre-approval spike (`VenueType.kt`, `ingestion/enrich/*`) was **removed** on approval: it modelled one nine-value enum, which the approved design replaced with `VenueClass` (recognise) + `VenueType` (publish). Keeping it would have left two contradictory vocabularies in the tree.
- `SC-227` (PR #84) adds `GeocodedPlace.osmKey/osmValue`. Until it merges, WI-7's OSM step cannot fire and the report states it, as designed.
