# SC-211 — Phase 1: Domain model

> **Purpose:** the domain vocabulary the use cases rely on · **Derived from:** 00 · **Delta:** extends (`CertificationClaim`, `VerificationBasis`) + changes (`VERIFIED` route); external library models stay outside this model (D-12)

**ARCHITECTURE.md §1.2 impact: changes** — `VERIFIED` gains a second route (`CERTIFIER_REGISTRY`); `Restaurant ↔ 0..* CertificationClaim`. Verification follows the tier; hand-cut follows the certifier's own declaration. Committee flow unchanged.

```mermaid
classDiagram
  direction LR
  class RestaurantListing {
    +verificationStatus
    +isHandCut
  }
  class CertificationClaim {
    +source
    +tier
    +certifier
    +declaredHandCut
    +sourceRef
  }
  class IngestionRun {
    +runId
    +selection
  }
  class IngestionCandidate {
    +name
    +location
    +tier
    +intendedStatus
    +verificationBasis
    +handCutDeclaration
  }
  class SourceListing {
    +name
    +addressLines
    +city
    +region
    +sourceRef
  }
  class DirectoryStandard {
    +tier
    +certifier
    +handCut
  }
  class IngestReject
  class IngestReport
  class MetroFilter

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

## Entities

| Entity | Role | Lifecycle |
|---|---|---|
| RestaurantListing (existing) | Catalogue root; trust + halal attributes | Unverified → Verified (two routes) |
| Brand (existing) | Location grouping | Persistent |
| CertificationClaim (new) | Cited certifier observation; verification + hand-cut evidence | Observed → ReObserved |
| IngestionRun (new) · IngestionCandidate (new) · SourceListing (new) | Audit unit · proposed listing · raw library record | Planned → … → Loaded; record immutable |
| IngestReject (new) · IngestReport (new) | Excluded row (closed reason) · run accounting + gaps | Immutable; produced once |
| DirectoryStandard · Provenance · SourceTier · SourceRef · VerificationBasis · MetroFilter · MatchConfirmation · GeocoderHealth · VenueIdentity | Value objects | Immutable |

**Shared write path (D-15):** `RestaurantListing.imported(...)` is the only new aggregate factory. It allows a null owner/cuisine, a brand, provenance, and a `VERIFIED` status only when a `VerificationBasis` is supplied. `LoadIngestionBatch` builds aggregates from reviewed candidates and calls the same `RestaurantListingRepository` used by Add Listing; it never emits SQL. The persistence adapter owns one transactional write routine for insert/update + child rows + claims + search mirror.

**Identity (D-18):** `VenueIdentity` is a SHA-256 over normalized name + street address + city + region + country (diacritics/punctuation/case removed; coordinates deliberately excluded). `CertificationClaim` is unique by `(source, source_record_ref)` globally. The load matches in this order: existing claim → `venue_key` → normalized-location fallback. Only the first two may update/attach silently; the location fallback is reported as a suspected duplicate.

## Invariants

| # | Invariant | Becomes test |
|---|---|---|
| INV-1 | Ingested listing is `VERIFIED` iff a recognized Tier-1 claim backs it; otherwise `UNVERIFIED` | `LoadIngestionBatchTest.loadsTierOneAsVerifiedWithClaims` |
| INV-2 | Every ingested listing carries a closed-vocabulary provenance stamp | `ProvenanceTest.rejectsUnknownVocabulary` |
| INV-3 | `isHandCut` is TRUE iff the backing claim declares hand-cut; FALSE for every other Tier-1 declaration (declared not or silent); NULL only where there is no certifier claim. Every `VERIFIED` row has a cited Tier-1 claim | `LoadIngestionBatchTest.handCutFollowsDeclaredStandard` · `refusesVerifiedWithoutClaim` |
| INV-4 | The run's first step is the geocoder pre-flight; `RunSampleIngestion` (pipeline PR) must call it before any source, and every candidate location came from a confirmed geocoder match | `CheckGeocoderHealthTest` · Phase 6 `ING-1` · `MatchConfirmationPolicyTest.rejectsWrongRegion` |
| INV-5 | Claims are unique per (listing, source, record) and cite a source URL | `CertificationClaimTest.requiresCitation` |
| INV-6 | Same venue → one candidate; highest tier wins provenance; all claims retained | `RunSampleIngestionTest.mergesTierPriority` |
| INV-7 | Loading is idempotent (no duplicate listing or claim) | `LoadIngestionBatchTest.isIdempotent` |
| INV-8 | Expiry/first-seen/last-seen never changes status or hand-cut in this feature | — (open flag D-10) |
| INV-9 | A run never emits outside its metros or above the per-metro cap | `RunSampleIngestionTest.filtersToMetroAndCap` |
| INV-10 | Every fetched row is a candidate or a reject with a reason | `AuditIngestionRunTest.flagsUnaccountedRow` |
| INV-11 | A Tier-1 match upgrades the existing row; never duplicates or skips | `LoadIngestionBatchTest.upgradesMatchedSeedRow` · `doesNotDuplicateSeedRows` |
| INV-12 | The committee path is untouched; registry-verified is distinguishable | `VerificationCommitteeTest` (existing) |
| INV-13 | Ingestion writes through the same aggregate and repository as Add Listing; it does not emit SQL or bypass the index gate | `LoadIngestionBatchTest.usesSharedWritePath` · `ExposedRestaurantListingRepositoryTest` |
| INV-14 | A Tier-1 ingested row is index-qualified (`NO_CROSS_CONTAMINATION`); manual/web rows keep their explicit value, default `UNCERTAIN` | `LoadIngestionBatchTest.certifiedRowsAreIndexQualified` |
| INV-15 | A source record maps to exactly one listing across runs: claims are globally unique by `(source, source_record_ref)` | `CertificationClaimTest.sourceRecordIsGloballyUnique` · `LoadIngestionBatchTest.replaysArchivedBatchWithoutDuplicates` |
| INV-16 | An ingested listing has a deterministic `venue_key`; the same normalized name/address/city/region maps to one listing even when coordinates drift | `VenueIdentityTest` · `LoadIngestionBatchTest.coordinateDriftDoesNotDuplicate` |
| INV-17 | A location-only near-match is reported as a suspected duplicate, never silently inserted or merged | `LoadIngestionBatchTest.locationOnlyMatchIsReported` |

## States

| From | Event | To | Guard |
|---|---|---|---|
| Planned | Pre-flight passed | Fetching | Geocoder reachable |
| Planned | Pre-flight failed | Aborted | No directories fetched |
| Fetching | Sources fetched (or failures recorded) | Normalizing | — |
| Normalizing | Metro/category filtered | Geocoding | — |
| Geocoding | All matched or rejected | Reconciling | — |
| Reconciling | Dedupe + counts | Reported | — |
| Reported | Artifacts written | AwaitingReview | — |
| AwaitingReview | Approved + applied | Loaded | Reviewed batch |
| AwaitingReview | Batch rejected | Abandoned | — |
| Fetching | Geocoder unavailable before candidates | Aborted | No candidates artifact |
| Observed | Claim seen again | ReObserved | Same (listing, source, record) |
| Unverified | Committee approves owner claim | Verified | `COMMITTEE_REVIEW` |
| Unverified | Tier-1 registry claim attached | Verified | `CERTIFIER_REGISTRY` + cited claim |

## Glossary

| Term | Meaning |
|---|---|
| Directory source · source library | Public registry (HMS/HFSAA/HMA) or curated platform (Zabihah) · standalone certifier module with its own model |
| Translator | Explicit mapping from a source library model to `SourceListing` + `DirectoryStandard` |
| Tier | 1 = audited certifier registry · 2 = vetted platform · 3 = crowdsourced/curated |
| Source listing · candidate | One record as published by one directory · proposed listing after geocoding + reconciliation |
| Claim | A source's dated, cited certification and hand-cut assertion |
| Match confirmation | Country/region agreement + name-token overlap evidence |
| Provenance · verification basis | Listing's winning origin stamp · why `VERIFIED`: `COMMITTEE_REVIEW` or `CERTIFIER_REGISTRY` |
| Hand-cut declaration · hand-cut | The certifier's own position (hand-cut · not hand-cut · silent) · product boolean: TRUE only on declared hand-cut, FALSE otherwise for Tier-1, `NULL` without a certifier claim |
| Registry verification | `VERIFIED` conferred by a recognized Tier-1 entry + cited claim |
| Batch · suspected duplicate | One reviewed run's candidates as a catalogue update · a candidate at an existing row's location |
