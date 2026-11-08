# SC-198 — Phase 6: White-box sequence, UC-01 Search for Halal Restaurants

> **Purpose:** the classes, operations, transactions and stores behind 04/UC-01 · **Derived from:** 05 · **Delta:** `CachingGeocoder` sits between the use case and Photon
> **Security:** anonymous surface; the MCP per-client rate limit runs before this flow. Place text never reaches the database — only its SHA-256. All SQL binds parameters.

```mermaid
sequenceDiagram
  autonumber
  actor U as User
  actor AG as AI agent
  actor PH as Photon
  participant S as SearchRestaurantsTool
  participant A as SearchListingsForAgent
  participant CG as CachingGeocoder
  participant PG as PhotonGeocoder
  participant ST as ExposedGeocodedAddressStore
  participant DB@{ "type": "database" } as PostgreSQL

  U->>AG: 1. halal near "Main St"
  AG->>S: 2. call(search_restaurants)
  S->>A: execute(request) · UC-01.3
  A->>CG: 4a. geocode("Main St") · GeocoderPort.geocode

  Note over CG: NormalizedQuery.of(text) → hash (once per call)

  rect rgba(230, 240, 255, .35)
    Note over ST,DB: read transaction
    CG->>ST: findByQueryHash(hash, now − 90 days)(hash, notBefore = now − 90 days)
    ST->>DB: SELECT lat, lng, display_name, provider_ref, provider FROM geocoded_addresses<br/>WHERE normalized_query_hash = ? AND created_at >= ?
    DB-->>ST: row | none
    ST-->>CG: GeocodedAddress | null
  end
  Note over CG,PH: no transaction is open across the outbound call

  alt fresh hit (≤ 90 days) · UC-01.4.3
    CG-->>A: place (0 provider calls)
  else miss or stale row · UC-01.4.4
    CG->>PG: geocode(text) · upstream GeocoderPort
    PG->>PH: GET /api/?q=…
    PH-->>PG: GeoJSON FeatureCollection
    PG-->>CG: GeocodedPlace
    rect rgba(230, 255, 235, .35)
      Note over ST,DB: write transaction
      CG->>ST: save(entry)
      ST->>DB: INSERT INTO geocoded_addresses (normalized_query_hash, lat, lng,<br/>display_name, provider_ref, provider) VALUES (?,…,?)<br/>ON CONFLICT (normalized_query_hash) DO UPDATE SET<br/>lat = EXCLUDED.lat, lng = EXCLUDED.lng, display_name = EXCLUDED.display_name,<br/>provider_ref = EXCLUDED.provider_ref, provider = EXCLUDED.provider, created_at = now()
    end
    CG-->>A: place
  end

  A->>DB: searchNearby(centre, radius, filters) · ListingSearchQuery (existing)
  A-->>S: AgentSearchResult
  S-->>AG: 7. tool result
```

## Exception paths

```mermaid
sequenceDiagram
  autonumber
  actor PH as Photon
  participant A as SearchListingsForAgent
  participant CG as CachingGeocoder
  participant PG as PhotonGeocoder
  participant ST as ExposedGeocodedAddressStore
  participant DB@{ "type": "database" } as PostgreSQL

  Note over A,DB: E2 — cold, no match
  A->>CG: geocode(text)
  CG->>ST: findByQueryHash(hash, now − 90 days)
  ST-->>CG: null
  CG->>PG: geocode(text)
  PG->>PH: GET
  PH-->>PG: no features
  PG-->>CG: null
  CG-->>A: null (no save) → PlaceNotResolvedException

  Note over A,DB: E3 — cold, provider failure
  CG->>PG: geocode(text)
  PG->>PH: GET
  PH--xPG: timeout / non-200
  PG-->>CG: GeocodingException
  CG-->>A: GeocodingException (no save)
  Note over CG,DB: no row exists for this query

  Note over A,DB: E5 — cache read fails
  CG->>ST: findByQueryHash(hash, now − 90 days)
  ST--xCG: ExposedSQLException
  Note over CG: WARN "geocode cache read failed" — treat as a miss
  CG->>PG: geocode(text) → result returned

  Note over A,DB: E6 — cache write fails
  CG->>ST: save(entry)
  ST--xCG: ExposedSQLException
  Note over CG: WARN "geocode cache write failed" — result already in hand
  CG-->>A: place
```

## What this diagram pins

| Concern | Shown |
|---|---|
| Transaction boundary | Two short transactions (read, then write) with **no** transaction held across the HTTP call (INV-10) |
| State transition | `Absent`/`Stale` → `Stored` on a successful lookup; a stale row is refreshed in place; no-match/failure leaves the state unchanged |
| Authorization | None beyond the anonymous MCP gate — the cache key carries no identity, so no cross-user data can be reached by guessing a place |
| Concrete errors | `PlaceNotResolvedException` (null) · `GeocodingException` (provider) · WARN-and-continue (store read/write) |
| TTL | A row older than 90 days is not a hit; the next success rewrites it with `created_at = now()` (INV-11) |
| Idempotency | `ON CONFLICT … DO UPDATE` keeps one row per key and refreshes a stale one; the unique key is the idempotency |
| Data minimization | Only the hash, point, provider label/ref and provider name are written; the address text is not |
