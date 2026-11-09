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
  AP->>CR: 3. find(listingId) [existing photo?]
  CR->>DB: SELECT listing_cover_photos
  DB-->>CR: row | none
  alt photo exists and not --replace
    CR-->>AP: skip
  else needs a photo
    AP->>PS: 4. candidatesFor(listing)
    PS->>OS: 5a. tag for providerRef
    OS-->>PS: image / wikimedia_commons | none
    PS->>CS: 5b. search venue or brand
    CS-->>PS: candidates + extmetadata licence
    PS-->>AP: List~PhotoCandidate~
    AP->>PL: 6. allowsReuse && attribution present?
    alt refused
      PL-->>AP: reject(reason)
    else accepted
      AP->>F: 7. fetch(sourceUrl) [size cap]
      F-->>AP: bytes | ImageFetchException
      AP->>IH: 8. ingest(listingId, StoredImage)
      IH->>IP: 9. save(FULL, THUMBNAIL_400/768/1280/1920)
      IP->>OBJ: PUT objects
      AP->>CR: 10. save(listingId, CoverPhoto)
      CR->>DB: INSERT/UPDATE listing_cover_photos (one transaction)
    end
  end
  AP-->>CLI: 11. PhotoResult
  CLI-->>DC: 12. counts + per-row outcomes
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
