# SC-211 — Phase 5: System class diagram (conceptual)

> **Purpose:** the classes and ports that serve the use cases · **Derived from:** 01–04 · **Delta:** new certifier libraries + translators; existing `GeocoderPort` reused

## Overview

```mermaid
flowchart LR
  B["IngestionConsole<br/>(boundary)"] --> C1[RunSampleIngestion]
  B --> C2[CompileManualBatch]
  B --> C3[LoadIngestionBatch]
  B --> C4[AuditIngestionRun]
  C1 --> L1[certifier-hms]
  C1 --> L2[certifier-hfsaa]
  C1 --> L3[certifier-hma]
  L1 --> T1[HmsTranslator]
  L2 --> T2[HfsaaTranslator]
  L3 --> T3[HmaTranslator]
  T1 --> SL[SourceListing]
  T2 --> SL
  T3 --> SL
  C1 --> P2[GeocoderPort]
  C1 --> P6[CheckGeocoderHealth]
  P6 --> P2
  C1 --> P3[ArtifactWriter]
  C2 --> P4[CuratedBatchSource]
  C3 --> P5[CatalogBatchWriter]
  P5 --> DB[("PostgreSQL + PostGIS")]
  C4 --> R[IngestReport]
  CORE[CertifierHttp] -.-> L1
  CORE -.-> L2
  CORE -.-> L3
```
## Domain

```mermaid
classDiagram
  direction LR
  class RestaurantListing
  class Brand
  class CertificationClaim {
    +declaredHandCut
  }
  class IngestionRun
  class IngestionCandidate {
    +mergeWith(other)
    +handCutFor(declaration)
  }
  class SourceListing
  class DirectoryStandard {
    +tier
    +certifier
    +handCut
  }
  class IngestReject
  class MetroFilter {
    +accepts(listing)
  }

  IngestionRun "1" o-- "0..*" SourceListing
  IngestionRun "1" o-- "0..*" IngestionCandidate
  IngestionRun "1" o-- "0..*" IngestReject
  IngestionRun "1" --> "1" IngestReport
  IngestionCandidate "1" *-- "1..*" CertificationClaim
  IngestionCandidate --> DirectoryStandard
  IngestionCandidate --> MetroFilter
  RestaurantListing "1" --> "0..*" CertificationClaim
  RestaurantListing "*" --> "1" Brand
  IngestReport "1" o-- "0..*" IngestReject
  IngestReject --> RejectReason
```
## Application, libraries and boundary

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
  class CheckGeocoderHealth {
    <<control>>
    +execute() GeocoderHealth
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
  class SourceTranslator~T~ {
    <<interface>>
    +translate(page) List~SourceListing~
  }
  class HmsDirectory {
    +certifiedListing(refresh) CertifierPage~HmsEstablishment~
  }
  class HfsaaLocator {
    +entries(chapter, refresh) CertifierPage~HfsaaEntry~
  }
  class HmaCertifiedOutlets {
    +outlets(refresh) CertifierPage~HmaCertifiedOutlet~
  }
  class CertifierHttp {
    +get(url, accept, refresh) FetchResult
    +postJson(url, json, headers, refresh) FetchResult
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
  class ListingSearchQuery {
    <<interface>>
    +search(metro, filters)
  }
  class CatalogueDatabase {
    <<database>>
  }

  IngestionConsole ..> RunSampleIngestion
  IngestionConsole ..> CompileManualBatch
  IngestionConsole ..> LoadIngestionBatch
  IngestionConsole ..> AuditIngestionRun
  RunSampleIngestion --> HmsDirectory
  RunSampleIngestion --> HfsaaLocator
  RunSampleIngestion --> HmaCertifiedOutlets
  RunSampleIngestion --> CheckGeocoderHealth
  CheckGeocoderHealth --> GeocoderPort
  RunSampleIngestion --> GeocoderPort
  RunSampleIngestion --> ArtifactWriter
  HmsDirectory --> SourceTranslator
  HfsaaLocator --> SourceTranslator
  HmaCertifiedOutlets --> SourceTranslator
  HmsDirectory ..> CertifierHttp
  HfsaaLocator ..> CertifierHttp
  HmaCertifiedOutlets ..> CertifierHttp
  CompileManualBatch --> CuratedBatchSource
  CompileManualBatch --> GeocoderPort
  LoadIngestionBatch --> CatalogBatchWriter
  LoadIngestionBatch --> CatalogueDatabase
  CatalogBatchWriter --> CatalogueDatabase
  ListingSearchQuery --> CatalogueDatabase
  AuditIngestionRun ..> IngestReport
```

## Ports, libraries and adapters

| Type | Purpose | Implementation |
|---|---|---|
| `certifier-hms` | HMS certified listing -> `HmsEstablishment` | `HmsDirectory` + `HmsParser` |
| `certifier-hfsaa` | HFSAA locator -> `HfsaaEntry` | `HfsaaLocator` + `HfsaaParser` |
| `certifier-hma` | HMA outlets -> `HmaCertifiedOutlet` | `HmaCertifiedOutlets` + `HmaParser` |
| `certifier-core` | shared politeness, cache, robots, snapshots | `CertifierHttp`, `CertifierPage`, `HandCutDeclaration` |
| `SourceTranslator<T>` | library model -> `SourceListing` | `HmsTranslator`, `HfsaaTranslator`, `HmaTranslator` |
| `CuratedBatchSource` | read the cited manual file | `CuratedBatchFileAdapter` |
| `GeocoderPort` (existing) | confirm a venue's coordinates | `PhotonGeocoder` |
| `CheckGeocoderHealth` | one pre-flight query before any fetch | application control |
| `ArtifactWriter` | persist snapshots/candidates/report | `FileArtifactWriter` |
| `CatalogBatchWriter` | emit the reviewed catalogue update | `SqlMigrationBatchWriter` |
| `CatalogueDatabase` | owned catalogue store | PostgreSQL + PostGIS |

## Operation coverage

| UC step | Operation |
|---|---|
| UC-01.1 | `IngestionConsole.runSample` → `RunSampleIngestion.execute` |
| UC-01.2 | `CheckGeocoderHealth.execute` (pre-flight; abort on failure) |
| UC-01.3–5 | `HmsDirectory.certifiedListing` · `HfsaaLocator.entries` · `HmaCertifiedOutlets.outlets` |
| UC-01.6 | `HmsTranslator.translate` · `HfsaaTranslator.translate` · `HmaTranslator.translate` |
| UC-01.7 | `MetroFilter.accepts` |
| UC-01.8 | `GeocoderPort.geocode` + `MatchConfirmationPolicy.confirm` |
| UC-01.9 | `IngestionCandidate.mergeWith` + `intendedStatus`/`verificationBasis`/`handCutFor(declaration)` |
| UC-01.10–11 | `ArtifactWriter.write*`; `IngestReport` |
| UC-02.1–5 | `CompileManualBatch` → `CuratedBatchSource.read` → `GeocoderPort.geocode` → `mergeWith` |
| UC-02.6–7 | `ArtifactWriter.writeCandidates/writeReport` |
| UC-03.1–5 | `LoadIngestionBatch.generate` → `CatalogBatchWriter.generate` → `CatalogUpdateReview` |
| UC-03.6 | existing `ListingSearchQuery` → `CatalogueDatabase` |
| UC-04.1–5 | `IngestionReportDocument.*`; `AuditIngestionRun.execute` |

**Existing collaborators:** `Migrations` (UC-03.4) · `ListingSearchQuery`/`ExposedListingSearchQuery` (UC-03.6) · `RestaurantListingRepository` (reference only).

**Revisions:** 2026-11-07 initial · 2026-11-08 verified libraries replace the single `HalalDirectorySource` port; `HandCutDeclaration` replaces the old standard vocabulary (D-12) · 2026-11-08 diagram conventions: external actors left, `CatalogueDatabase` explicit on the right.
