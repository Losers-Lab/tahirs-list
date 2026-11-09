# SC-205 — Phase 5: System class diagram (conceptual)

> **Purpose:** the classes that serve UC-01, and nothing else · **Derived from:** 01–04 · **Delta:** +3 frontend classes (panel, dialog, controller), +1 backend control (`GetListingManage`), +2 client calls; existing upload use case reused and extended

## Overview

```mermaid
flowchart LR
  B1["RestaurantDetailPage<br/>boundary"] --> B2["ListingPhotoPanel"]
  B2 --> B3["PhotoUploadDialog"]
  B2 --> C1["PhotoUploadController<br/>frontend control"]
  C1 --> G1["ApiClient<br/>gateway"]
  G1 --> E1["PUT /v1/listings/{id}/image"]
  G1 --> E2["GET /v1/listings/{id}/manage"]
  G1 --> E3["GET /v1/listings/{id}"]
  E1 --> C2["AddListingImage<br/>backend control"]
  E2 --> C3["GetListingManage<br/>backend control"]
  C2 --> C4["IngestHeroImage"]
  C2 --> P1["ImagePort"]
  C2 --> P2["RestaurantListingRepository"]
  C3 --> P2
  P1 --> S3[("MinIO / S3")]
  P2 --> DB[("PostgreSQL")]
```

Two controls: `PhotoUploadController` owns the browser's state machine; `AddListingImage` owns the server's authorization and write. The `/manage` read is the only new endpoint (D-2).

## Domain (unchanged apart from one attribute)

```mermaid
classDiagram
  direction LR
  class RestaurantListing {
    +id: Uuid
    +ownerId: Uuid?
    +verificationStatus: VerificationStatus
    +imageVersion: Long
  }
  class ListingPhoto {
    +listingId: Uuid
    +version: Long
  }
  class VerificationStatus {
    <<enumeration>>
    UNVERIFIED
    VERIFIED
  }
  RestaurantListing --> ListingPhoto : derives
  RestaurantListing --> VerificationStatus
```

## Application (frontend)

```mermaid
classDiagram
  class PhotoUploadController {
    <<control, new>>
    +canManage: Boolean
    +state: idle|validating|uploading|success|error
    +lastError: UploadError?
    +open() void
    +selectFile(file) Promise~void~
    +reset() void
  }
  class ApiClient {
    <<gateway, existing>>
    +getListing(id) ListingDetail
    +getManage(id) ManageResponse
    +uploadListingImage(id, file) void
  }
  class SessionState {
    <<existing>>
    +current: Session?
    +restoring: Boolean
  }
  class UploadError {
    +kind: validation|auth|forbidden|tooLarge|notImage|notFound|server|network
    +message: String
  }
  PhotoUploadController --> ApiClient
  PhotoUploadController --> SessionState
  PhotoUploadController ..> UploadError
```

`PhotoUploadController` is a runes singleton scoped to the page, not a module-level store: the page owns one instance so SSR never shares upload state between requests.

## Application (backend)

```mermaid
classDiagram
  class AddListingImage {
    <<control, existing SC-53>>
    +execute(listingId, ownerId, original) void
  }
  class GetListingManage {
    <<control, new>>
    +execute(listingId, viewerId) ManageOutcome
  }
  class IngestHeroImage {
    <<existing>>
    +ingest(listingId, original) void
  }
  class ImagePort {
    <<interface, existing>>
    +save(listingId, variant, contentType, bytes)
    +load(listingId, variant) StoredImage?
    +delete(listingId, variant)
  }
  class RestaurantListingRepository {
    <<interface, existing>>
    +findById(id) RestaurantListing?
    +bumpImageVersion(id) Long?
  }
  class NotListingOwnerException
  class ListingNotFoundException

  AddListingImage --> ImagePort
  AddListingImage --> RestaurantListingRepository
  AddListingImage ..> IngestHeroImage
  AddListingImage ..> NotListingOwnerException
  AddListingImage ..> ListingNotFoundException
  GetListingManage --> RestaurantListingRepository
```

`AddListingImage` keeps its guard order (exists → owner → decode → store → **stamp**). The stamp is last, so every failure path leaves the version untouched (INV-5).

## Boundary

```mermaid
classDiagram
  class RestaurantDetailPage {
    <<boundary, existing>>
    +load(id) PageData
    +render(photo, canManage)
  }
  class ListingPhotoPanel {
    <<boundary, new>>
    +props(photoUrl, srcset, fallbackUrl, canManage, alt)
    +onAdd() void
  }
  class PhotoUploadDialog {
    <<boundary, new>>
    +props(open, state, lastError)
    +onChooseFile(file) void
    +onTakePhoto(file) void
    +onCancel() void
    +onRetry() void
  }
  class ListingImageEndpoint {
    <<boundary, existing>>
    +put(id, multipart) 204|401|403|404|400|413|500
  }
  class ListingManageEndpoint {
    <<boundary, new>>
    +get(id) 200 ManageResponse|401|404
  }

  RestaurantDetailPage --> ListingPhotoPanel
  ListingPhotoPanel --> PhotoUploadDialog
  ListingPhotoPanel --> PhotoUploadController
  PhotoUploadController --> ListingImageEndpoint
  PhotoUploadController --> ListingManageEndpoint
```

## Ports and adapters

| Port | Operation | Adapter | Store / external |
|---|---|---|---|
| `ApiClient` (frontend gateway) | `uploadListingImage(id, file)` | `fetch` + `FormData`, same-origin `/v1` proxy | backend |
| `ApiClient` | `getManage(id)` | `fetch` GET, bearer token | backend |
| `RestaurantListingRepository` | `bumpImageVersion(id): Long?` | `ExposedRestaurantListingRepository` | PostgreSQL `restaurant_listings.image_version` |
| `ImagePort` (existing) | `save` / `load` / `delete` | `S3ImageStorage` | MinIO / S3 |
| `ImagePort` (existing) | same | `InMemoryImageStorage` (tests) | in-memory |
| `Clock` | `now()` | `kotlin.time.Clock` | — |

## Operation coverage

| UC step | Operation |
|---|---|
| 1–2 | `RestaurantDetailPage.load` → existing `getListing` + new `ApiClient.getManage` (only when a session exists) |
| 3 | `ListingPhotoPanel` renders photo or empty state; control iff `PhotoUploadController.canManage` |
| 4–5 | `PhotoUploadController.open` → `PhotoUploadDialog` (two inputs) |
| 6–7 | `PhotoUploadDialog.onChooseFile` / `onTakePhoto` |
| 8 | `PhotoUploadController.selectFile` → client size/type pre-check |
| 9 | `ApiClient.uploadListingImage` → `PUT /v1/listings/{id}/image` |
| 10 | Ktor `authenticate(JWT_AUTH_PROVIDER)` → `AddListingImage.execute` guard 1–2 |
| 11 | `IngestHeroImage.ingest` → `ImageResizer` → `ImagePort.save` ×5 |
| 12 | `RestaurantListingRepository.bumpImageVersion` → `204` |
| 13 | `ApiClient.getListing` re-read; versioned URLs from the DTO |
| 14 | `ListingPhotoPanel` shows the new URL; dialog closes; focus restored |
| A1 | step 3 label only; same operations |
| A2 | `onTakePhoto` uses the `capture="environment"` input |
| A3 | `PhotoUploadController.reset`; no request |
| E1 / E7 | `401` → controller `auth` error; sign-in link with `redirect` |
| E2 | `403 not_listing_owner` → controller `forbidden` error |
| E3 | client pre-check, or `413 payload_too_large` |
| E4 | `400 invalid_input` from `ImageResizer.contentType` |
| E5 | `404 not_found` |
| E6 | `500 internal_error`; controller `server` error, retry available |

**Existing collaborators reused unchanged:** `IngestHeroImage`, `ImagePort` + both adapters, `ListingImageEndpoint` route, `ListingReadDtos.imageUrl/srcset` (values only), `SessionState`, `RestaurantPhoto`.
