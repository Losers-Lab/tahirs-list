# SC-231 — Phase 5: System class diagram (conceptual)

> **Purpose:** the classes and ports that serve UC-01…UC-04 · **Derived from:** 01–04 · **Delta:** new `:domain` vocabulary, new enrichment controls in `:ingestion` and `:application`, two photo-source adapters; image pipeline reused

## Overview

```mermaid
flowchart LR
  B1["IngestionConsole<br/>(existing, SC-211)"] --> C0[CandidateFactory]
  C0 --> C1[EnrichCandidate]
  C1 --> C11[VenueTypeClassifier]
  C1 --> C12[CuisineAssigner]
  C11 --> R1[EnrichmentRules]
  C12 --> R1

  B2["EnrichmentConsole<br/>(new boundary)"] --> C3[AttachCoverPhoto]
  B2 --> C4[RenderEnrichmentReport]

  C3 --> P1[PhotoCandidateSource]
  C3 --> P2[ImageFetcher]
  C3 --> C5[IngestHeroImage]
  C3 --> P3[CoverPhotoRepository]
  C5 --> P4[ImagePort]
  C4 --> P5[RunArtifacts]

  C1 --> D1[EnrichmentDecision]
  P1 --> A1[OsmPhotoSource]
  P1 --> A2[CommonsPhotoSource]
  A1 --> X1["OSM / Overpass"]
  A2 --> X2["Wikimedia Commons"]
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
  class EnrichmentSource {
    <<enumeration>>
    +isWeak
  }
  class EnrichmentDecision {
    +venueType
    +venueTypeSource
    +cuisine
    +cuisineSource
    +conflicts
    +isUndecided
  }
  class Cuisine
  class CoverPhoto {
    +sourceUrl
    +sourceKind
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

  RestaurantListing "1" --> "0..1" VenueType
  RestaurantListing "1" --> "0..1" EnrichmentSource
  RestaurantListing "1" --> "0..1" Cuisine
  RestaurantListing "1" *-- "0..1" CoverPhoto
  CoverPhoto --> PhotoLicence
  EnrichmentDecision --> VenueType
  EnrichmentDecision --> EnrichmentSource
  EnrichmentDecision --> Cuisine
```

**Where the rules live.** `VenueType`, `EnrichmentSource`, `PhotoLicence`,
`CoverPhoto` and `EnrichmentDecision` are domain types: the closed vocabulary,
the "serving types may carry a cuisine" rule, and "a photo needs a reusable
licence" are product rules, not adapter detail. The *curated tables* (brand →
cuisine, keyword → cuisine, category → type, OSM tag → type) are data, and live
in `:ingestion` beside the classifier.

## Application

```mermaid
classDiagram
  direction LR
  class CandidateFactory {
    +build(listing, metro)
  }
  class EnrichCandidate {
    +enrich(sourceListing, match) EnrichmentDecision
  }
  class VenueTypeClassifier {
    +classify(category, osmFeature, name) TypeDecision
  }
  class CuisineAssigner {
    +assign(type, name, osmCuisine) CuisineDecision
  }
  class EnrichmentRules {
    +typeForCategory(category)
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
    +candidatesFor(listing) List~PhotoCandidate~
  }
  class CoverPhotoRepository {
    <<interface>>
    +find(listingId) CoverPhoto?
    +save(listingId, photo)
  }
  class PhotoCandidate {
    +sourceUrl
    +sourceKind
    +licence
    +attribution
    +author
  }

  CandidateFactory --> EnrichCandidate
  EnrichCandidate --> VenueTypeClassifier
  EnrichCandidate --> CuisineAssigner
  VenueTypeClassifier --> EnrichmentRules
  CuisineAssigner --> EnrichmentRules
  AttachCoverPhoto --> PhotoCandidateSource
  AttachCoverPhoto --> CoverPhotoRepository
  AttachCoverPhoto --> PhotoCandidate
  RenderEnrichmentReport --> EnrichmentDecision
```

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
    +candidatesFor(listing)
  }
  class ExposedCoverPhotoRepository {
    +find(listingId)
    +save(listingId, photo)
  }
  class JdkImageFetcher {
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
  CoverPhotoRepository <|.. ExposedCoverPhotoRepository
  ImageFetcher <|.. JdkImageFetcher
  ImagePort <|.. S3ImagePort
  AttachCoverPhoto --> IngestHeroImage
```

## Notes that the diagrams cannot carry

- **No new geocoder call.** UC-01/02 read the tag from the match the SC-211 run
  already made. Where SC-227 (PR #84) is not merged, the tag is absent
  (`GeocodedPlace` currently carries `providerRef` only), so classification
  degrades to category + name. This is a merge-order dependency, not a fallback
  design.
- **One photo per listing** is enforced by the repository (PK on listing id) and
  by the use case; a second candidate never overwrites silently.
- **The image store keeps bytes only.** Licence and credit are catalogue data,
  because the API must serve them and a bucket tag would not survive an adapter
  swap to R2.
