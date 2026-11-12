# SC-199 — Phase 6: White-box sequence, UC-01 Search listings by location

> **Purpose:** the classes, operations, transactions and SQL behind 04/UC-01 · **Derived from:** 05 · **Delta:** `ListingSearchRequestParser.centre` snaps before the query; `SearchCachePolicy` sets the 200 header
> **Security:** anonymous, read-only. The snapped centre is a parameter, never concatenated. No identity reaches the cache policy, so the cacheable body is user-invariant (INV-11).

## REST path

```mermaid
sequenceDiagram
  autonumber
  actor C as Client
  participant R as ListingRoutes
  participant P as ListingSearchRequestParser
  participant CP as CoordinatePrecision
  participant Q as ExposedListingSearchQuery
  participant CACHE as SearchCachePolicy
  participant DB@{ "type": "database" } as PostgreSQL

  C->>R: 1. GET /v1/listings/search?center=…&radius=…
  R->>P: 2. parse centre (lat,lng) · radius · filters · paging
  alt malformed / out of range
    P-->>R: IllegalArgumentException
    R-->>C: 400 invalid_input · no-store (E-1…E-3)
  end
  R->>P: 3. centre(center, precision)
  P->>CP: 4. snap(center)
  CP-->>P: LatLng rounded to N dp (LatLng.roundedTo re-checks WGS84)
  P-->>R: snapped LatLng

  R->>Q: 5. searchNearby(snapped, radiusMiles, filters, offset, limit)
  Note over Q,DB: read-only, the adapter owns its transaction
  Q->>DB: 6. read-only query, all values bound as parameters (SQL in the note below)
  Note over Q,DB: SELECT id, name, …, ST_DistanceSphere(location::geometry, ST_MakePoint(:lng, :lat)) / 1609.344 AS distance_miles FROM listing_search WHERE ST_DWithin(location, ST_MakePoint(:lng, :lat)::geography, :radiusMetres) AND cross_contamination = 'NO_CROSS_CONTAMINATION' plus the optional filter clauses, ORDER BY the same distance, LIMIT :limit OFFSET :offset
  DB-->>Q: rows
  Q-->>R: List<ListingSearchResult>

  R->>CACHE: 7. applyTo(call, 200)
  CACHE-->>R: freshness header set
  R-->>C: 8. 200 JSON (distanceMiles from snapped centre)

  Note over R,DB: DB failure → 500 internal_error · no-store · nothing cached (E-4)
```

## MCP path (`search_restaurants` with `center`)

```mermaid
sequenceDiagram
  autonumber
  actor AG as AI agent
  participant S as SearchRestaurantsTool
  participant A as SearchListingsForAgent
  participant P as ListingSearchRequestParser
  participant Q as ExposedListingSearchQuery
  participant DB@{ "type": "database" } as PostgreSQL

  AG->>S: 1. call(search_restaurants, center={lat,lng}, radiusMiles)
  S->>S: 2. parse arguments (existing strict contract)
  S->>A: 3. execute(request with SearchLocation.Coordinates)
  A->>P: 4. centre(location.center, precision)
  P-->>A: snapped LatLng
  A->>Q: 5. searchNearby(snapped, radiusMiles, filters, offset, limit)
  Q->>DB: 6. same parameterized query as the REST path
  DB-->>Q: rows
  Q-->>A: results
  A-->>S: AgentSearchResult
  S-->>AG: 7. tool result · no HTTP header (MCP has no cache semantics)
```

Place-text searches (`SearchLocation.Place`) snap **after** geocoding, through the same
`centre` call, so SC-198's cache and SC-199's snap compose without either knowing the other.

## Exception paths

| Concern | Where | Concrete outcome |
|---|---|---|
| Malformed centre / non-finite | `ListingRoutes` parse step | `IllegalArgumentException` → `400 invalid_input`, `no-store` |
| Lat/lng out of WGS84 | `LatLng` init, re-checked in `roundedTo` | `400 invalid_input`, `no-store` |
| Bad radius / filter | `ListingSearchRequestParser` | `400 invalid_input`, `no-store` (unchanged) |
| Database failure | `ExposedListingSearchQuery` | `500 internal_error`, `no-store` (generic envelope, unchanged) |
| MCP invalid centre | `SearchRestaurantsTool` | `isError` tool result with the parser's reason (unchanged) |

## What this diagram pins

| Concern | Shown |
|---|---|
| Transaction boundary | One read-only transaction inside `ExposedListingSearchQuery`; the snap is pure and outside it |
| State transition | None: no write, no state change |
| Authorization | None (public surface); the snapped centre carries no identity |
| Query shape | Centre is bound as `:lng,:lat` parameters; the snap changes the value, not the SQL |
| Concrete errors | Each row above maps to one class and one status |
| Cache decision | Header only on step 8; error paths never reach `SearchCachePolicy` |
