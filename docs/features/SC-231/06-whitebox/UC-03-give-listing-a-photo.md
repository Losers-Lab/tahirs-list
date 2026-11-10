# SC-231 — Phase 6: White-box — UC-03 Give a listing a photo it is allowed to show

> **Purpose:** how the photo pass is served, class by class · **Derived from:** 05 + 04/UC-03 · **Delta:** new
> **Consistency:** one photo per listing; an owner photo is never touched; a Places photo is cache-only and never served after `expiresAt`; bytes are written only after every variant is produced.

```mermaid
sequenceDiagram
  autonumber
  actor DC as Data Curator
  participant CLI as EnrichmentConsole
  participant AP as AttachCoverPhoto
  participant CR as CoverPhotoRepository
  participant PS as PhotoCandidateSource
  participant OS as OsmPhotoSource
  participant CS as CommonsPhotoSource
  participant GS as GooglePlacesPhotoSource
  participant PL as PhotoLicence
  participant F as ImageFetcher
  participant IH as IngestHeroImage
  participant IP as ImagePort
  participant DB as PostgreSQL
  participant OBJ as MinIO
  DC->>CLI: 1. attachPhotos(scope, execute, replace)
  CLI->>AP: 2. preview(scope)
  AP->>CR: 3. find(listingId)
  CR->>DB: SELECT listing_cover_photos
  DB-->>CR: row | none
  AP->>PS: 4. freeCandidates(listing)
  PS->>OS: 5a. tag for providerRef
  OS-->>PS: image / wikimedia_commons | none
  PS->>CS: 5b. search venue or brand
  CS-->>PS: candidates + extmetadata
  PS-->>AP: List~PhotoCandidate~
  AP->>PL: 6. allowsReuse && attribution present?
  PL-->>AP: accept | reject(reason)
  AP->>GS: 7. placesCandidate(listing) — only when nothing free was accepted
  GS->>GOOG: Place Details + photo
  GS-->>AP: bytes + attributions + placeId + photoName
  AP->>F: 8. fetch(sourceUrl | photo name) — size cap
  F-->>AP: bytes | ImageFetchException
  AP->>IH: 9. ingest(listingId, StoredImage)
  IH->>IP: 10. save(FULL, THUMBNAIL_400/768/1280/1920)
  IP->>OBJ: PUT objects
  AP->>CR: 11. save(listingId, CoverPhoto, storageClass, expiresAt)
  CR->>DB: INSERT/UPDATE listing_cover_photos (one transaction)
  AP-->>CLI: 12. PhotoResult
  CLI-->>DC: 13. counts + per-row outcomes
```
**A1 — owner photo present** · short-circuit before any network call.
```mermaid
sequenceDiagram
  participant AP as AttachCoverPhoto
  participant CR as CoverPhotoRepository
  AP->>CR: find(listingId)
  CR-->>AP: row with sourceKind OWNER_UPLOAD
  AP-->>AP: skip — no fetch, no write (step 3)
```
**A2 — nothing found** · a gap.
```mermaid
sequenceDiagram
  participant AP as AttachCoverPhoto
  participant GS as GooglePlacesPhotoSource
  AP->>GS: placesCandidate(listing)
  GS-->>AP: none (no place id, or no photo)
  AP-->>AP: NoPhoto — gap recorded (step 7)
```
**A3 — non-free licence** · refused before bytes.
```mermaid
sequenceDiagram
  participant AP as AttachCoverPhoto
  participant PL as PhotoLicence
  AP->>PL: allowsReuse?
  PL-->>AP: false → reject("licence"), no fetch (step 6)
```
**A4 — two free candidates** · deterministic order.
```mermaid
sequenceDiagram
  participant AP as AttachCoverPhoto
  AP->>AP: OSM before Commons, then source order — first accepted wins (step 6)
```
**A5 — CC-BY without credit** · rejected.
```mermaid
sequenceDiagram
  participant AP as AttachCoverPhoto
  participant PL as PhotoLicence
  AP->>PL: requiresAttribution && attribution blank
  PL-->>AP: reject("attribution") (step 6)
```
**A6 — Places wins** · cache class, expiry set.
```mermaid
sequenceDiagram
  participant AP as AttachCoverPhoto
  participant CR as CoverPhotoRepository
  AP->>CR: save(storageClass = CACHE, expiresAt = now + 30d, placeId, photoName)
  CR-->>AP: cached row (step 11)
```
**E1 — fetch or decode fails** · reported, unexpired bytes kept.
```mermaid
sequenceDiagram
  participant AP as AttachCoverPhoto
  participant F as ImageFetcher
  participant IH as IngestHeroImage
  AP->>F: fetch(...)
  F-->>AP: ImageFetchException
  AP->>IH: not called — nothing persisted
  AP-->>AP: row failure, run continues (step 8)
```
**E2 — Places quota or key failure** · row-scoped.
```mermaid
sequenceDiagram
  participant AP as AttachCoverPhoto
  participant GS as GooglePlacesPhotoSource
  AP->>GS: placesCandidate(listing)
  GS-->>AP: PlacesUnavailable
  AP-->>AP: row reported, existing cache untouched (step 7)
```
**P1 — serving an expired cache** · refused at read time.
```mermaid
sequenceDiagram
  participant EP as ListingImageEndpoint
  participant CR as CoverPhotoRepository
  EP->>CR: find(listingId)
  CR-->>EP: CACHE with expiresAt in the past
  EP-->>EP: 404 — nothing served
```
**P2 — partial variants** · impossible.
```mermaid
sequenceDiagram
  participant IH as IngestHeroImage
  participant IP as ImagePort
  IH->>IH: resize every width first
  IH->>IP: then save each variant
```
**P3 — owner uploads over a cache** · the cache row is replaced.
```mermaid
sequenceDiagram
  participant OWN as OwnerUpload (SC-205)
  participant CR as CoverPhotoRepository
  OWN->>CR: save(sourceKind = OWNER_UPLOAD, storageClass = PERMANENT)
  CR-->>OWN: cache metadata cleared — the pass now skips this listing
```

**Transaction boundary.** Two stores, so the order is fixed and recoverable:

1. object bytes are written first (idempotent per key — a retry overwrites);
2. the credit row is written in its own DB transaction.

A crash between 1 and 2 leaves orphan bytes and no row, so nothing is served and
the next run rewrites them. A crash inside 2 leaves no row — never a visible
photo with no credit.

**Serving rule.** `ListingImageEndpoint` serves a `CACHE` photo only while
`now < expiresAt`; after that it returns no photo until the pass refreshes. It
never calls Google itself, so a Places outage cannot slow a page load.

**Authorization:** local operator; `--execute` is the only gate. The Places key
is read from the server environment and never reaches the browser.

| Phase-4 flow | Class operation and error |
|---|---|
| main | `AttachCoverPhoto.preview/execute` → `PhotoCandidateSource` → `PhotoLicence` → `GooglePlacesPhotoSource` → `ImageFetcher` → `IngestHeroImage` → `CoverPhotoRepository.save` |
| A1 owner photo | repository returns `OWNER_UPLOAD`; the use case returns `Skipped` before any source is queried |
| A2 nothing found | every source empty → `NoPhoto`, gap |
| A3 non-free licence | `reject("licence")`, no bytes fetched |
| A4 several candidates | OSM → Commons → Places, first accepted |
| A5 missing credit | `reject("attribution")` |
| A6 Places wins | `storageClass = CACHE`, `expiresAt = now + 30d` |
| E1 fetch/decode failure | `ImageFetchException` / `ImageResizer` rejection → row failure, unexpired bytes kept |
| E2 Places failure | `PlacesUnavailable` → row reported; existing cache untouched; run continues |
| P1 expired cache | the endpoint refuses to serve; placeholder |
| P2 partial variants | resize-all-then-save |
| P3 owner upload | SC-205 writes `OWNER_UPLOAD`/`PERMANENT`; the pass's `find` then short-circuits |
