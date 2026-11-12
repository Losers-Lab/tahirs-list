# SC-231 — Phase 7: Complete class diagram (the ultimate design)

> **Purpose:** the implementation design — layers, names, persistence, API · **Derived from:** 05–06 · **Delta:** new `:domain` vocabulary + `:application/photo`; modified `:ingestion/pipeline`, `:persistence`, `:web-api`; new `:bootstrap` CLI

## Layer map

```mermaid
flowchart TB
  FE["Frontend — SvelteKit (modified: photo credit)"] --> API["OpenAPI 3.1 (modified: photo credit fields)"]
  API --> WEB[":web-api (modified: detail + card DTOs)"]
  WEB --> APP[":application — + photo use case/ports, + enrichment fields on the candidate"]
  APP --> DOM[":domain — + VenueType, EnrichmentSource, CoverPhoto, PhotoLicence"]
  APP --> PER[":persistence — V27 · enrichment columns · cover-photo repository"]
  BOOT[":bootstrap — + AttachCoverPhotosMain + attachCoverPhotos"] --> APP
  BOOT --> IMGF["HttpImageFetcher (existing)"]
  ING[":ingestion — + enrich package (classifier, rules, report section)"]
  BOOT -.-> ING
  ING --> SRC["certifier libraries (existing)"]
  APP -.-> GEO[":geocoding (existing)"]
  PER -.-> PG[("PostgreSQL + PostGIS")]
  APP -.-> OBJ[("MinIO / S3 (existing image store)")]
```

## Module map

| Module | Status | Contents |
|---|---|---|
| `:domain` | **modified** | new `VenueType` (published restaurant kinds + `servesMeals`), `VenueClass` (recogniser, incl. non-restaurant classes), `EnrichmentSource` (+`isWeak`), `AdmissionDecision` (sealed: Admitted / Excluded / Undecided), `PhotoLicence`, `CoverPhoto`; `RestaurantListing` gains `venueType`, `venueTypeSource`, `cuisineSource`, `coverPhoto`; `imported(...)` accepts them |
| `:application` | **modified** | `IngestionCandidate` gains `admission` and `cuisine`; new `com.tahirslist.application.photo`: `AttachCoverPhoto` (`preview`/`execute`), `PhotoCandidateSource` (free + Places), `CoverPhotoRepository`, `PhotoCandidate`, `PhotoReview`/`PhotoResult`, `PlacesClient` port; `RestaurantListingRepository.applyBatch` writes the enrichment fields |
| `:ingestion` | **modified** | new `com.tahirslist.ingestion.enrich`: `RestaurantAdmission`, `AdmissionPolicy`, `CuisineAssigner`, `EnrichmentRules` (curated tables), `AdmissionDecision`; `CandidateFactory` gates on admission before building a candidate; `IngestReport` renders the Enrichment section and counts exclusions as rejects |
| `:persistence` | **modified** | `V27__sc_231_enrichment.sql`; `ExposedCoverPhotoRepository`; `applyBatch` writes `venue_type`, `venue_type_source`, `cuisine_source`; image endpoint checks `expires_at`; `Tables.kt` gains `CoverPhotos` |
| `:web-api` | **modified** | `DetailResponse` gains `photoAttribution`, `photoLicence`, `photoSource`, `photoSourceUrl`; `BrowseCard`/`SearchCard` gain `photoAttribution`; OpenAPI regenerated |
| `:bootstrap` | **modified** | `AttachCoverPhotosMain` + `:bootstrap:attachCoverPhotos`; `scripts/attach-cover-photos.sh`; `OsmPhotoSource`, `CommonsPhotoSource`, `GooglePlacesPhotoSource` (HTTP adapter over the `PlacesClient` port) wired to the existing `HttpImageFetcher`; `GOOGLE_MAPS_API_KEY` read from the environment only |
| frontend | **modified** | detail hero shows the credit line; cards show a small credit caption when present |
| `:geocoding` · certifier libraries | **unchanged** | reads only |

## Verified API surface (live key, 2026-11-10)

| Call | SKU | Free cap / month | Result on `Afghan Bbq & Pizza` |
|---|---|---|---|
| `POST places:searchText` (field mask `id,displayName,formattedAddress,photos`) | Text Search (New) | ≥1,000 | 1 place, place id returned, **10 photos**, exact address match |
| `GET {photo.name}/media?maxWidthPx=400` | Place Details Photos | 1,000 | `image/jpeg`, 65 823 bytes, `authorAttributions: "Md Abu Rihan"` |
| `GET places/{id}` (field mask `photos`) — refreshes only | Place Details | 1,000–10,000 by field tier | not needed for the first pass |

At 118 listings a full cold pass is ~236 calls across two SKUs, and a refresh is
~118 photo calls by stored place id. All inside the free tiers; the design is
constrained by the licence terms, not by cost.

**Match discipline.** The first search result is not accepted blindly: the
returned `displayName` and `formattedAddress` must agree with the listing (the
same rule the Photon matcher already applies). A near-miss is reported, not
pinned — a wrong place id would put another business's photo on the listing.

## Persistence delta

| Element | Status | Detail |
|---|---|---|
| `V27__sc_231_enrichment.sql` | **new** | `restaurant_listings.venue_type VARCHAR(16) NULL` CHECK in (`RESTAURANT`,`CAFE`,`FAST_FOOD`,`BAKERY`); `venue_type_source VARCHAR(24) NULL` CHECK in (`SOURCE_CATEGORY`,`OSM_FEATURE`,`BRAND_MAP`,`NAME_KEYWORD`); `cuisine_source VARCHAR(24) NULL` same CHECK; partial index on `venue_type` where not null. Nullable because seed/owner rows predate the gate — an enriched ingested row always has one |
| `listing_cover_photos` | **new table** | `listing_id UUID PRIMARY KEY REFERENCES restaurant_listings(id) ON DELETE CASCADE`; `source_kind VARCHAR(16)` CHECK (`OWNER_UPLOAD`,`OSM`,`WIKIMEDIA`,`GOOGLE_PLACES`); `storage_class VARCHAR(16)` CHECK (`PERMANENT`,`CACHE`); `licence VARCHAR(16) NULL` CHECK (`CC0`,`PUBLIC_DOMAIN`,`CC_BY`,`CC_BY_SA`); `attribution VARCHAR(1024) NOT NULL`; `author VARCHAR(512)`; `source_url VARCHAR(1024)`; `google_place_id VARCHAR(255)`; `google_photo_name VARCHAR(1024)`; `expires_at TIMESTAMPTZ NULL`; `content_hash CHAR(64)`; `width INT`, `height INT`; `created_at TIMESTAMPTZ`. CHECKs: a Places row is always `CACHE` with `expires_at`; a `CACHE` row always has `expires_at`; a free-licensed row always has a licence. PK gives INV-11 for free |
| `listing_search` | **unchanged** | no change: nothing that is not a restaurant can reach it, because it never reaches `restaurant_listings` |
| `restaurant_listings` (other columns) | **unchanged** | `cuisine` already exists and holds the derived value; `price`/`rating`/`is_delivery`/`alcohol_served`/`halal_scope` are untouched (no source claims them) |
| `Tables.kt` | **modified** | add `CoverPhotos` |

## Operational delta — rows that already exist

L-1 applies to what is already stored, not only to new runs. The current
catalogue holds three rows the gate refuses:

| row | recognised as | action |
|---|---|---|
| `Alladin Halal Meat & Grocery [Butcher Dept. ONLY]` | `BUTCHER` (meat + grocery, no premises word) | remove, with claims |
| `Hamza Meat Shop [Butcher Dept. ONLY]` | `BUTCHER` | remove, with claims |
| `Jamaica Live Poultry Meat Market [Butcher Dept. Only]` | `BUTCHER` (poultry + meat market) | remove, with claims |

Bakeries are **not** in that list: a bakery is a restaurant (L-3), so
`Firin Bakery`, `Mannan Bakery`, `Mannan Bakery Factory`, `Mannan Bakery and
Grill` and `Mannan Sweets & Restaurant` all stay, typed `BAKERY` or
`RESTAURANT`. "Factory" is recorded in the decision and queued for a curator,
since a factory can be production rather than a shopfront.

The purge is a **one-off, reported** step: re-run the gate over stored listings,
print the list, take a `pg_dump`, then delete the confirmed rows and their
claims. It is not a migration (data deletion never hides inside a schema
change), and it is a work item, not an automatic part of the load.

## API delta (OpenAPI 3.1)

| Element | Status | Detail |
|---|---|---|
| `DetailResponse.photoAttribution` | **new** | string, nullable — the credit line a licence requires; for a Places photo, Google's attribution |
| `DetailResponse.photoLicence` | **new** | enum string, nullable — `CC0`/`PUBLIC_DOMAIN`/`CC_BY`/`CC_BY_SA`; null for owner and Places photos |
| `DetailResponse.photoSource` | **new** | enum string — `OWNER_UPLOAD`/`OSM`/`WIKIMEDIA`/`GOOGLE_PLACES`, so the UI can style the credit correctly |
| `DetailResponse.photoSourceUrl` | **new** | string (uri), nullable — where a free-licensed photo came from; never a Places CDN URL |
| `BrowseCard.photoAttribution` / `SearchCard.photoAttribution` | **new** | string, nullable — rendered as a small caption |
| `venueType`, `cuisineSource` | **not exposed** | Q-5 open; stored and reported only |
| `GET /v1/listings/{id}/image` | **modified** | serves a `CACHE` photo only while `expires_at` is in the future; otherwise 404. Never calls Google |

## Frontend delta

| Element | Status | Detail |
|---|---|---|
| `routes/restaurants/[id]/+page.svelte` | modified | a credit line under the hero when `photoAttribution` is present; Google-sourced photos use Google's required attribution wording |
| `lib/components/listing/ListingCard.svelte` (and search card) | modified | a small caption/overlay when `photoAttribution` is present |
| `lib/api/schema.d.ts` | regenerated | `pnpm codegen:api` |

## Behaviour deltas the diagrams imply

| # | Delta | Where |
|---|---|---|
| D-b1 | A row is admitted, excluded or held before it can become a candidate | `:ingestion` |
| D-b2 | Only admitted rows reach the catalogue and therefore the search index | `:persistence` |
| D-b3 | The report always contains an Enrichment section, including exclusions and undecided rows | `:ingestion` |
| D-b4 | A listing may have exactly one credit row | `:persistence` |
| D-b5 | Cards and detail render a credit line when the licence requires one | frontend |
| D-b6 | Existing non-restaurant rows are purged in a one-off, reported step after a backup | operations |
| D-b7 | A Places photo is cache-only: never permanent, never served after `expires_at` | `:persistence` + endpoint |

## Dependency recorded, not designed around

`GeocodedPlace` on `main` carries `location`, `displayName`, `providerRef`. The
OSM `osm_key`/`osm_value` used by UC-01 step 3 is added by **SC-227 (PR #84)**.
Until it merges, `typeForOsm` is never reached and classification rests on
category + name — a real reduction in evidence quality, stated in the report as
`OSM_FEATURE: not available in this run` rather than silently degraded.
