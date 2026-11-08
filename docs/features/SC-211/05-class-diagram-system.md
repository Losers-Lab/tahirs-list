# SC-211 — Phase 5: System class diagram (conceptual)

> **Purpose:** the classes and ports that serve the use cases · **Derived from:** 01–04 · **Delta:** new ingestion controls/ports; existing `GeocoderPort` reused

## Overview

```mermaid
flowchart LR
  B["IngestionConsole<br/>(boundary)"] --> C1[RunSampleIngestion]
  B --> C2[CompileManualBatch]
  B --> C3[LoadIngestionBatch]
  B --> C4[AuditIngestionRun]
  C1 --> P1[HalalDirectorySource]
  C1 --> P2[GeocoderPort]
  C1 --> P3[ArtifactWriter]
  C2 --> P4[CuratedBatchSource]
  C3 --> P5[CatalogBatchWriter]
  C4 --> R[IngestReport]
```
## Domain

```mermaid
classDiagram
  direction LR
  class RestaurantListing
  class Brand
  class CertificationClaim
  class IngestionRun
  class IngestionCandidate {
    +mergeWith(other)
  }
  class SourceListing
  class IngestReject
  class IngestReport
  class MetroFilter {
    +accepts(listing)
  }

  IngestionRun "1" o-- "0..*" SourceListing
  IngestionRun "1" o-- "0..*" IngestionCandidate
  IngestionRun "1" o-- "0..*" IngestReject
  IngestionRun "1" --> "1" IngestReport
  IngestionCandidate "1" *-- "1..*" CertificationClaim
  IngestionCandidate --> MetroFilter
  RestaurantListing "1" --> "0..*" CertificationClaim
  RestaurantListing "*" --> "1" Brand
  IngestReport "1" o-- "0..*" IngestReject
  IngestReject --> RejectReason
```
## Application and boundary

```mermaid
classDiagram
  direction LR
  class IngestionConsole {
    <<boundary>>
    +runSample(selection, caps) IngestReport
    +compileManualBatch(file) IngestReport
    +generateCatalogUpdate(candidates) CatalogUpdateReview
    +openReport(runId) IngestionReportDocument
  }
  class IngestionReportDocument {
    <<boundary>>
    +countsBySourceAndMetro()
    +gaps()
  }
  class CatalogUpdateReview {
    <<boundary>>
    +diffSummary()
    +appliedCounts()
  }
  class RunSampleIngestion {
    <<control>>
    +execute(sources, metros, caps, cachePolicy) IngestReport
  }
  class CompileManualBatch {
    <<control>>
    +execute(batchFile, existingCandidates) IngestReport
  }
  class LoadIngestionBatch {
    <<control>>
    +generate(candidates, runId) CatalogUpdateReview
  }
  class AuditIngestionRun {
    <<control>>
    +execute(report) AuditEvidence
  }
  class HalalDirectorySource {
    <<interface>>
    +fetch(metro, fetcher) List~SourceListing~
  }
  class CuratedBatchSource {
    <<interface>>
    +read(path) List~SourceListing~
  }
  class GeocoderPort {
    <<interface>>
    +geocode(address) GeocodedPlace?
  }
  class ArtifactWriter {
    <<interface>>
    +writeSnapshots() +writeCandidates() +writeReport()
  }
  class CatalogBatchWriter {
    <<interface>>
    +generate(candidates, runId) CatalogBatch
  }

  IngestionConsole ..> RunSampleIngestion
  IngestionConsole ..> CompileManualBatch
  IngestionConsole ..> LoadIngestionBatch
  IngestionConsole ..> AuditIngestionRun
  RunSampleIngestion --> HalalDirectorySource
  RunSampleIngestion --> GeocoderPort
  RunSampleIngestion --> ArtifactWriter
  CompileManualBatch --> CuratedBatchSource
  CompileManualBatch --> GeocoderPort
  LoadIngestionBatch --> CatalogBatchWriter
  AuditIngestionRun ..> IngestReport
```

## Ports and adapter stubs

| Port | Purpose | Adapter stubs |
|---|---|---|
| `HalalDirectorySource` | Fetch one directory's listings | `HmsDirectoryAdapter`, `HfsaaDirectoryAdapter`, `HmaDirectoryAdapter` |
| `CuratedBatchSource` | Read the cited manual file | `CuratedBatchFileAdapter` |
| `GeocoderPort` (existing) | Confirm a venue's coordinates | `PhotonGeocoder` |
| `ArtifactWriter` | Persist snapshots/candidates/report | `FileArtifactWriter` |
| `CatalogBatchWriter` | Emit the reviewed catalogue update | `SqlMigrationBatchWriter` |

## Operation coverage

| UC step | Operation |
|---|---|
| UC-01.1 | `IngestionConsole.runSample` → `RunSampleIngestion.execute` |
| UC-01.2–4 | `HalalDirectorySource.fetch` (one adapter per directory) |
| UC-01.5 | `MetroFilter.accepts` |
| UC-01.6 | `GeocoderPort.geocode` + `MatchConfirmationPolicy.confirm` |
| UC-01.7 | `IngestionCandidate.mergeWith` + `intendedStatus`/`verificationBasis` |
| UC-01.8–9 | `ArtifactWriter.write*`; `IngestReport` |
| UC-02.1–5 | `CompileManualBatch` → `CuratedBatchSource.read` → `GeocoderPort.geocode` → `mergeWith` |
| UC-02.6–7 | `ArtifactWriter.writeCandidates/writeReport` |
| UC-03.1–5 | `LoadIngestionBatch.generate` → `CatalogBatchWriter.generate` → `CatalogUpdateReview` |
| UC-03.6 | existing `ListingSearchQuery` |
| UC-04.1–5 | `IngestionReportDocument.*`; `AuditIngestionRun.execute` |

**Existing collaborators:** `Migrations` (UC-03.4) · `ListingSearchQuery`/`ExposedListingSearchQuery` (UC-03.6) · `RestaurantListingRepository` (reference only).

**Revisions:** 2026-11-07 initial; added existing collaborators; added `VerificationBasis` + `intendedStatus` (founder ruling).
