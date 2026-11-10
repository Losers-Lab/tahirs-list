# SC-231 — Phase 6: White-box — UC-03 Attach a licensed cover photo

> **Purpose:** how the photo pass is served, class by class · **Derived from:** 05 + 04/UC-03 · **Delta:** new
> **Consistency:** one photo per listing (repository PK), credit mandatory for licences that require it, and the object store is written only after every variant has been produced.

```mermaid
sequenceDiagram
  autonumber
  actor DC as Data Curator
  participant CLI as EnrichmentConsole
  participant AP as AttachCoverPhoto
  participant PS as PhotoCandidateSource
  participant OS as OsmPhotoSource
  participant CS as CommonsPhotoSource
  participant CR as CoverPhotoRepository
  participant PL as PhotoLicence
  participant F as ImageFetcher
  participant IH as IngestHeroImage
  participant IP as ImagePort
  participant DB as PostgreSQL
  participant OBJ as MinIO
  DC->>CLI: 1. attachPhotos(scope, execute, replace)
  CLI->>AP: 2. preview(scope)
  AP->>CR: 3. find(listingId) — skip if one exists and no --replace
  CR->>DB: SELECT listing_cover_photos
  DB-->>CR: row | none
  AP->>PS: 4. candidatesFor(listing)
  PS->>OS: 5a. tag for providerRef
  OS-->>PS: image / wikimedia_commons | none
  PS->>CS: 5b. search venue or brand
  CS-->>PS: candidates + extmetadata licence
  PS-->>AP: List~PhotoCandidate~
  AP->>PL: 6. allowsReuse && attribution present?
  PL-->>AP: accept | reject(reason)
  AP->>F: 7. fetch(sourceUrl) — size cap
  F-->>AP: bytes | ImageFetchException
  AP->>IH: 8. ingest(listingId, StoredImage)
  IH->>IP: 9. save(FULL, THUMBNAIL_400/768/1280/1920)
  IP->>OBJ: PUT objects
  AP->>CR: 10. save(listingId, CoverPhoto)
  CR->>DB: INSERT/UPDATE listing_cover_photos (one transaction)
  AP-->>CLI: 11. PhotoResult
  CLI-->>DC: 12. counts + per-row outcomes
```
**A1 — no candidate anywhere** · a gap, not a placeholder.
```mermaid
sequenceDiagram
  participant AP as AttachCoverPhoto
  participant PS as PhotoCandidateSource
  AP->>PS: candidatesFor(listing)
  PS-->>AP: empty
  AP-->>AP: NoPhoto — gap recorded (step 4)
```
**A2 — licence unknown or non-free** · refuse before fetching bytes.
```mermaid
sequenceDiagram
  participant AP as AttachCoverPhoto
  participant PL as PhotoLicence
  AP->>PL: allowsReuse?
  PL-->>AP: false → reject("licence"), no fetch (step 6)
```
**A3 — several acceptable candidates** · deterministic ordering.
```mermaid
sequenceDiagram
  participant AP as AttachCoverPhoto
  AP->>AP: OSM before Commons, then the source's own order — first accepted wins (step 6)
```
**A4 — CC-BY with no credit line** · a licence breach is not a nit.
```mermaid
sequenceDiagram
  participant AP as AttachCoverPhoto
  participant PL as PhotoLicence
  AP->>PL: requiresAttribution && attribution blank
  PL-->>AP: reject("attribution") (step 6)
```
**E1 — fetch or decode fails** · reported, existing photo kept.
```mermaid
sequenceDiagram
  participant AP as AttachCoverPhoto
  participant F as ImageFetcher
  participant IH as IngestHeroImage
  AP->>F: fetch(url)
  F-->>AP: ImageFetchException
  AP->>IH: not called — nothing persisted
  AP-->>AP: row failure, run continues (step 7)
```
**E2 — partial variant write** · impossible.
```mermaid
sequenceDiagram
  participant IH as IngestHeroImage
  participant IP as ImagePort
  IH->>IH: resize all variants first
  IH->>IP: then save each variant (step 9)
```
**P1 — the listing already has a photo** · untouched without `--replace`.
```mermaid
sequenceDiagram
  participant AP as AttachCoverPhoto
  participant CR as CoverPhotoRepository
  AP->>CR: find(listingId)
  CR-->>AP: row
  AP-->>AP: skip — no fetch (step 3)
```
**P2 — a hostile or oversized image** · bounded and type-derived.
```mermaid
sequenceDiagram
  participant AP as AttachCoverPhoto
  participant F as ImageFetcher
  AP->>F: fetch(url)
  F-->>AP: bytes, size cap enforced
  AP->>AP: contentType derived from bytes, never the caller (step 7)
```

**Transaction boundary.** Two stores, so the order is fixed and must be
recoverable:

1. object bytes are written first (idempotent per key — a retry overwrites);
2. the credit row is written in its own DB transaction.

A crash between 1 and 2 leaves orphan object bytes and no credit row: the next
run refetches and overwrites them. A crash inside 2 leaves no row, so the photo
is not visible — never a visible photo with no credit.

**State transition:** `Loaded → PhotoPending → Photographed`, or `→ NoPhoto`.
The listing's trust state is untouched.

**Authorization:** local operator; `--execute` is the only gate (preview writes
nothing). No HTTP surface.

| Phase-4 flow | Class operation and error |
|---|---|
| main | `AttachCoverPhoto.preview/execute` → `PhotoCandidateSource.candidatesFor` → `PhotoLicence.allowsReuse` → `ImageFetcher.fetch` → `IngestHeroImage.ingest` → `CoverPhotoRepository.save` |
| A1 no candidate | both sources return empty; `NoPhoto`, gap recorded |
| A2 non-free licence | `PhotoLicence` has no member for it → `reject("licence")`; no bytes fetched |
| A3 several candidates | ordered OSM → Commons, then source order; first accepted wins |
| A4 CC-BY without credit | `PhotoLicence.requiresAttribution` true and attribution blank → `reject("attribution")` |
| E1 fetch/decode failure | `ImageFetchException` or `IllegalArgumentException` from `ImageResizer` → row failure, existing photo kept, run continues |
| E2 partial variants | `IngestHeroImage` resizes every width before the first `ImagePort.save` |
| P1 existing photo | `CoverPhotoRepository.find` short-circuits before any network call |
| P2 hostile image | `ImageFetcher` size cap; `ImageResizer.contentType` derives the media type from bytes, never the caller |
