# SC-231 — Phase 9: Work items

> **Purpose:** the implementation plan, WI by WI, in the order it is built · **Derived from:** 07 + 08 · **Delta:** new; every WI ships in **PR #89**

**Rules.** Backend first: migration → domain → ingestion → application → persistence → boundary → security. Frontend after the contract lands. Each WI starts with its failing test. Two-way coverage: every UC step below is owned by a WI, and every class in Phase 7 belongs to one.

| WI | Layer | Covers | Tests first | Depends on | PR |
|---|---|---|---|---|---|
| WI-1 | `:persistence` | the three `restaurant_listings` columns and `listing_cover_photos` that WI-5/12/15 write | migration applies on a clean DB; CHECKs reject a permanent Places row and a cache without an expiry (`EnrichmentSchemaTest`) | — | #89 |
| WI-2 | `:domain` | `VenueType` (published) + `VenueClass` (recognised) and their predicates | enum round-trip, `servesMeals`, `isShop`/`isPremises` (`VenueTypeTest`) | — | #89 |
| WI-3 | `:domain` | `EnrichmentSource`, sealed `AdmissionDecision` (Admitted/Excluded/Undecided) | every non-null value names a source; `Admitted` requires a type (`AdmissionDecisionTest`) | WI-2 | #89 |
| WI-4 | `:domain` | `PhotoSourceKind`, `PhotoStorage`, `PhotoLicence`, `CoverPhoto` | a Places photo can only be `CACHE` with an expiry; a free photo needs a licence; attribution rules (`CoverPhotoTest`) | WI-2 | #89 |
| WI-5 | `:domain` | `RestaurantListing` gains `venueType`, `venueTypeSource`, `cuisineSource`, `coverPhoto`; `imported(...)` accepts them | factory rejects a listing whose type/source disagree; existing identity fields preserved (`RestaurantListingTest`) | WI-2, WI-3, WI-4 | #89 |
| WI-6 | `:ingestion` | `EnrichmentRules`: shop/premises marker tables, category → class, OSM → type, brand/keyword cuisines | marker tables are closed, lowercase, non-overlapping; a malformed table fails at construction (`EnrichmentRulesTest`) | WI-2 | #89 |
| WI-7 | `:ingestion` | UC-01 steps 2–7 — `AdmissionPolicy.decide` | the three real rows are excluded; `Madina Market (Kitchen)` and `Mannan Bakery and Grill` admitted; a pure bakery is `BAKERY`; no evidence → undecided (`AdmissionPolicyTest`) | WI-6 | #89 |
| WI-8 | `:ingestion` | UC-01 steps 1, 8 — `RestaurantAdmission`, wired into `CandidateFactory` | an excluded row becomes an `IngestReject` with its class; an admitted row carries the decision; no candidate without an admission (`RestaurantAdmissionTest`) | WI-7 | #89 |
| WI-9 | `:ingestion` | UC-02 — `CuisineAssigner` | brand beats OSM beats name; conflict → null + queued; an unadmitted row never reaches it (`CuisineAssignerTest`) | WI-6, WI-8 | #89 |
| WI-10 | `:ingestion` | UC-04 — the Enrichment section of `IngestReport` | counts by type, exclusions by class with evidence, undecided listed, conflicts queued, "photo pass not run" stated (`IngestReportTest`) | WI-8, WI-9 | #89 |
| WI-11 | `:application` | `IngestionCandidate` carries `admission` and `cuisine` | JSON round-trip; a candidate without an admitted decision is refused (`CandidateBatchJsonTest`) | WI-3, WI-8 | #89 |
| WI-12 | `:application` | UC-01/02 postconditions — the load writes type, sources and cuisine, fill-nulls-only | an owner-set cuisine survives; a re-run is a no-op; a non-admitted row is never written (`LoadIngestionBatchTest`) | WI-5, WI-11 | #89 |
| WI-13 | `:application` | UC-03 steps 1–7 — `AttachCoverPhoto`, `PlacesClient`, `PhotoCandidateSource`, `CoverPhotoRepository` | precedence owner > free > Places; owner photo untouched; licence/attribution refused when missing; Places always `CACHE` (`AttachCoverPhotoTest`) | WI-4, WI-5 | #89 |
| WI-14 | `:bootstrap` | UC-03 steps 4–6 — the three adapters, the CLI and `scripts/attach-cover-photos.sh` | adapters map a fixture response; a missing key reports per row instead of aborting (`PlacesAdapterTest`) | WI-13 | #89 |
| WI-15 | `:persistence` | UC-03 step 7 — `ExposedCoverPhotoRepository` and the endpoint's expiry rule | a cache past `expires_at` is not served; a permanent photo has no expiry (`ListingImageEndpointTest`) | WI-1, WI-13 | #89 |
| WI-16 | `:web-api` | UC-03 credit surface — `photoAttribution`, `photoLicence`, `photoSource`, `photoSourceUrl`; OpenAPI | fields present/absent per source; contract test against the schema (`DetailResponseTest`) | WI-15 | #89 |
| WI-17 | `:bootstrap` + ops | D-12 — the one-off purge of the three non-restaurant rows | dry run lists exactly those rows and changes nothing; `--execute` needs a backup marker (`PurgeNonRestaurantsTest`) | WI-7 | #89 |
| WI-18 | frontend | UC-03 credit line on detail and cards | credit renders for a free photo and for Google; absent when there is none (`page.test.ts`, card test) | WI-16 | #89 |
| WI-19 | review | Step 11 — Omar (static) and Yusuf (dynamic) to convergence | both verdicts on #89; two clean rounds | WI-1…WI-18 | #89 |

## Coverage check

**Every UC step has a WI.**

| UC | Steps | WIs |
|---|---|---|
| UC-01 admit | 1–8 | WI-2, WI-3, WI-6, WI-7, WI-8, WI-12 |
| UC-02 cuisine | 1–5 | WI-6, WI-9, WI-12 |
| UC-03 photo | 1–7 | WI-4, WI-13, WI-14, WI-15, WI-16 |
| UC-04 report | 1–5 | WI-10 |

**Every Phase-7 class belongs to a WI.** `VenueType`, `VenueClass` → WI-2 · `EnrichmentSource`, `AdmissionDecision` → WI-3 · `PhotoSourceKind`, `PhotoStorage`, `PhotoLicence`, `CoverPhoto` → WI-4 · `RestaurantListing` → WI-5/WI-12 · `EnrichmentRules` → WI-6 · `AdmissionPolicy` → WI-7 · `RestaurantAdmission`, `CandidateFactory` → WI-8 · `CuisineAssigner` → WI-9 · `RenderEnrichmentReport`/`IngestReport` → WI-10 · `IngestionCandidate` → WI-11 · `AttachCoverPhoto`, `PhotoCandidate`, ports → WI-13 · `OsmPhotoSource`, `CommonsPhotoSource`, `GooglePlacesPhotoSource`, `EnrichmentConsole` → WI-14 · `ExposedCoverPhotoRepository` → WI-15 · DTOs → WI-16.

## Build order and the first red test

WI-1 and WI-2 have no dependency, so they start together: the migration proves the schema, the two enums prove the vocabulary. WI-7 is the first WI that can be *seen* to be right — it is where the three real rows in the catalogue get excluded.

## Out of work items

- Phase 3 (phone/website) — Q-3 unresolved, see `00-story.md`.
- Exposing `venueType` in the API — D-5, deferred.
- Owner photo upload itself — SC-205, separate story; this one only respects it.
