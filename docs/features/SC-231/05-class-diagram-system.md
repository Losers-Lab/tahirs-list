# SC-231 — Phase 5: System class diagram (conceptual)

> **Purpose:** the classes and ports that serve UC-01…UC-04 · **Derived from:** 01–04 · **Delta:** new `:domain` vocabulary, a new admission control in `:ingestion`, new photo use case/ports in `:application`, two photo-source adapters; image pipeline reused

## Overview

```mermaid
flowchart LR
  B1["IngestionConsole<br/>(existing, SC-211)"] --> C0[CandidateFactory]
  C0 --> C1[RestaurantAdmission]
  C1 --> C11[AdmissionPolicy]
  C11 --> R1[EnrichmentRules]
  C0 --> C2[CuisineAssigner]
  C2 --> R1

  B2["EnrichmentConsole<br/>(new boundary)"] --> C3[AttachCoverPhoto]
  B2 --> C4[RenderEnrichmentReport]

  C3 --> P1[PhotoCandidateSource]
  C3 --> P2[ImageFetcher]
  C3 --> C5[IngestHeroImage]
  C3 --> P3[CoverPhotoRepository]
  C5 --> P4[ImagePort]
  C4 --> P5[RunArtifacts]

  C1 --> D1[AdmissionDecision]
  P1 --> A1[OsmPhotoSource]
  P1 --> A2[CommonsPhotoSource]
  P1 --> A5[GooglePlacesPhotoSource]
  A1 --> X1["OSM / Overpass"]
  A2 --> X2["Wikimedia Commons"]
  A5 --> X4["Google Places API"]
  P3 --> A3[ExposedCoverPhotoRepository]
  P2 --> X3["HTTPS origin"]
  P4 --> A4[S3ImagePort]
  A3 --> DB[("PostgreSQL + PostGIS")]
  A4 --> OBJ[("MinIO / S3")]
```

## Domain

```mermaid
classDiagram
  direction LR
  class RestaurantListing {
    +venueType
    +venueTypeSource
    +cuisine
    +cuisineSource
  }
  class VenueType {
    <<enumeration>>
    +servesMeals
  }
  class VenueClass {
    <<enumeration>>
    +isShop
    +isPremises
  }
  class AdmissionDecision {
    <<sealed>>
  }
  class EnrichmentSource {
    <<enumeration>>
    +isWeak
  }
  class Cuisine
  class CoverPhoto {
    +sourceUrl
    +licence
    +attribution
    +author
    +retrievedAt
    +contentHash
  }
  class PhotoLicence {
    <<enumeration>>
    +allowsReuse
    +requiresAttribution
  }

  RestaurantListing "1" --> "1" VenueType
  RestaurantListing "1" --> "1" EnrichmentSource
  RestaurantListing "1" --> "0..1" Cuisine
  RestaurantListing "1" *-- "0..1" CoverPhoto
  CoverPhoto --> PhotoLicence
  AdmissionDecision --> VenueType
  AdmissionDecision --> VenueClass
  AdmissionDecision --> EnrichmentSource
```

**Where the rules live.** `VenueType`, `VenueClass`, `EnrichmentSource`,
`PhotoLicence`, `CoverPhoto` and `AdmissionDecision` are domain types: "only
restaurants are published", "a shop word with no premises word is not a
restaurant", "a bakery is a restaurant" (L-3), and "a photo needs a reusable
licence" are product rules, not adapter detail. The *curated tables* (marker
words, category → class, brand → cuisine, keyword → cuisine) are data and live in
`:ingestion` beside the policy.

## Application

```mermaid
classDiagram
  direction LR
  class CandidateFactory {
    +build(listing, metro)
  }
  class RestaurantAdmission {
    +admit(sourceListing, match) AdmissionDecision
  }
  class AdmissionPolicy {
    +decide(category, osmFeature, name) AdmissionDecision
  }
  class CuisineAssigner {
    +assign(type, name, osmCuisine) CuisineDecision
  }
  class EnrichmentRules {
    +shopMarkersIn(name)
    +premisesMarkersIn(name)
    +classForCategory(category)
    +typeForOsm(key, value)
    +typeForName(name)
    +cuisineFor(brand)
    +cuisineForKeyword(word)
  }
  class AttachCoverPhoto {
    +preview(scope) PhotoReview
    +execute(review, replace) PhotoResult
  }
  class RenderEnrichmentReport {
    +section(decisions, results) String
  }
  class PhotoCandidateSource {
    <<interface>>
    +freeCandidates(listing) List~PhotoCandidate~
    +placesCandidate(listing) PhotoCandidate?
  }
  class CoverPhotoRepository {
    <<interface>>
    +find(listingId) CoverPhoto?
    +save(listingId, photo)
  }
  class PhotoCandidate {
    +sourceKind
    +storageClass
    +sourceUrl
    +licence
    +attribution
    +author
    +placeId
    +photoName
  }

  CandidateFactory --> RestaurantAdmission
  CandidateFactory --> CuisineAssigner
  RestaurantAdmission --> AdmissionPolicy
  AdmissionPolicy --> EnrichmentRules
  CuisineAssigner --> EnrichmentRules
  AttachCoverPhoto --> PhotoCandidateSource
  AttachCoverPhoto --> CoverPhotoRepository
  AttachCoverPhoto --> PhotoCandidate
  RenderEnrichmentReport --> AdmissionDecision
```

`CandidateFactory` asks `RestaurantAdmission` first and only calls
`CuisineAssigner` when the verdict is `Admitted` — the dependency arrow is the
gate: no admitted type, no cuisine.

## Boundary and adapters

```mermaid
classDiagram
  direction LR
  class EnrichmentConsole {
    +attachPhotos(scope, execute, replace)
  }
  class OsmPhotoSource {
    +candidatesFor(listing)
  }
  class CommonsPhotoSource {
    +freeCandidates(listing)
  }
  class GooglePlacesPhotoSource {
    +placesCandidate(listing)
  }
  class ExposedCoverPhotoRepository {
    +find(listingId)
    +save(listingId, photo)
  }
  class HttpImageFetcher {
    +fetch(url)
  }
  class S3ImagePort {
    +save / load / delete
  }
  class IngestHeroImage {
    +ingest(listingId, original)
  }

  EnrichmentConsole --> AttachCoverPhoto
  PhotoCandidateSource <|.. OsmPhotoSource
  PhotoCandidateSource <|.. CommonsPhotoSource
  PhotoCandidateSource <|.. GooglePlacesPhotoSource
  CoverPhotoRepository <|.. ExposedCoverPhotoRepository
  ImageFetcher <|.. HttpImageFetcher
  ImagePort <|.. S3ImagePort
  AttachCoverPhoto --> IngestHeroImage
```

## Notes that the diagrams cannot carry

- **No new geocoder call.** Admission reads the tag from the match the SC-211
  run already made. Where SC-227 (PR #84) is not merged, the tag is absent
  (`GeocodedPlace` currently carries `providerRef` only), so the gate rests on
  category + name. This is a merge-order dependency, not a fallback design.
- **Exclusion is a reject, not a silent drop.** The pipeline already has an
  `IngestReject` with a reason; a non-restaurant becomes one, so the SC-211
  "every fetched row is accounted for" invariant still holds.
- **One photo per listing** is enforced by the repository (PK on listing id) and
  by the use case; a second candidate never overwrites silently.
- **Two classes of bytes.** Owner and free-licensed photos are permanent; a
  Places photo is a cache with an expiry. The store key is the same either way,
  so the row's `storageClass` is what tells the reader whether those bytes may
  still be served.
- **The image endpoint never calls a third party.** It reads the store and checks
  the expiry; only the photo pass talks to Google, so a Places outage cannot slow
  a page.
- **The Places key is server-side only.** It is read from the environment, used by
  the adapter, and never reaches the browser or an API response.
