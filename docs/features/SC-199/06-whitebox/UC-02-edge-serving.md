# SC-199 — Phase 6: White-box sequence, UC-02 Serve a repeated search from the edge cache

> **Purpose:** what the origin does so the edge can cache safely · **Derived from:** 05 · **Delta:** `SearchCachePolicy` decides the header; the edge's own cache is external and drawn opaque here
> **Security:** the cached body must be public and identity-free. A `Set-Cookie` or `Vary` header would split or leak the entry, so neither is ever set.

```mermaid
sequenceDiagram
  autonumber
  participant E as Cloudflare edge
  participant R as ListingRoutes
  participant CP as SearchCachePolicy
  participant SP as SecurityHeadersPlugin
  participant Q as ExposedListingSearchQuery

  E->>R: 1. forward GET /v1/listings/search?… (miss or revalidation)
  R->>Q: 2. UC-01 steps 3–5 (snapped centre)
  Q-->>R: rows
  R->>CP: 3. applyTo(call, 200)
  CP->>CP: 4. Cache-Control: public, max-age=M, s-maxage=S, stale-while-revalidate=W
  Note over CP: no Set-Cookie · no Vary · no per-user field
  CP-->>R: header set
  R->>SP: 5. response ready for send
  SP->>SP: 6. Cache-Control already present → no-store not appended
  SP-->>E: 7. 200 (cacheable)

  Note over E,SP: failure branch
  R-->>SP: 500 / 400 (policy not applied)
  SP->>SP: 6'. no Cache-Control → append no-store
  SP-->>E: 6''. not stored at edge
```

## What this diagram pins

| Concern | Shown |
|---|---|
| Header decision | Set on step 4, only for the `200` branch (INV-9) |
| Default preserved | Step 6′: an unset header still becomes `no-store` |
| Freshness bound | `S + W ≤ 600` enforced at settings load, not per request (INV-10) |
| Identity | No cookie, no `Vary` (INV-11) |
| Transaction / state | None on the origin side; no write |
