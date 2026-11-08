# SC-198 — Phase 3: Use case diagram

> **Purpose:** place the two modified use cases against the agreed model · **Derived from:** 02 · **Delta:** UC-01 and UC-02 marked modified; no new use case, no new actor

## Feature scope

```mermaid
flowchart LR
  U([User]); ET([Extension Trigger<br/>AI agent]); DC([Data Curator])
  subgraph S["Tahir's List"]
    UC1([UC-01 Search for Halal Restaurants])
    UC2([UC-02 Run sample directory ingestion])
  end
  PH[["Photon<br/>public API"]]
  CA[("geocoded_addresses")]

  U --> UC1; ET --> UC1; DC --> UC2
  UC1 --> PH; UC2 --> PH
  UC1 --> CA; UC2 --> CA

  classDef modified fill:#fff8e1,stroke:#f9a825,stroke-width:2px
  class UC1,UC2 modified
```

No `«include»`: the shared geocoding step stays internal to both use cases, the same stance SC-211 ratified. The cache is a design component of that step, not an actor goal, so it is absent from the bubble model and present only as the store both flows touch.

## System-wide (changed items amber; everything else unchanged)

```mermaid
flowchart LR
  U([User]); ET([Extension Trigger]); DC([Data Curator])
  subgraph System["Tahir's List"]
    S1([Search for Halal Restaurants])
    S2([Filter Results by Distance]); S3([Filter by Food Cutting Method]); S4([Filter Results by Price]); S5([Filter Results by Cuisine]); S6([Filter Results by Rating])
    V1([View Restaurant]); V2([View Restaurant Halal Certification])
    A1([Create Account]); A2([Log In]); F1([Favorite Restaurant]); F2([Unfavorite Restaurant]); F3([View Favorited Restaurants])
    I1([Add Image]); I2([Edit Unverified Restaurant]); I3([Report Issue]); R1([Add Restaurant]); R2([Restaurant Verification]); R3([Edit Verified Restaurant]); R4([Remove Image]); R5([Restaurant Listing Analytics]); R6([Restaurant Removal]); R7([Restaurant Blacklisting])
    E1([Browser Extension — List View]); E2([Browser Extension — Individual View]); C1([Accept/Deny Restaurant Edit]); C2([Accept/Deny Restaurant Verification])
    N1([UC-02 Run sample ingest])
  end
  CA[("geocoded_addresses 🆕")]
  PH[["Photon"]]

  U --> S1; U --> V1; U --> A1; U --> A2; U --> F1; U --> F2; U --> F3; U --> I1; U --> I2; U --> I3; U --> R1
  ET --> S1; ET --> E1; ET --> E2; DC --> N1
  S1 -. include .-> S2; S1 -. include .-> S3; S1 -. include .-> S4; S1 -. include .-> S5; S1 -. include .-> S6
  S1 --> PH; N1 --> PH
  S1 --> CA; N1 --> CA

  classDef changed fill:#fff8e1,stroke:#f9a825,stroke-width:2px
  class S1,N1,CA changed
```

| Mark | Meaning |
|---|---|
| Amber | Changed by SC-198 (UC-01 search, UC-02 ingest, new cache store) |
| Solid arrow | Actor association / external dependency / store access |
| Dotted arrow | `«include»` |

All other use cases, actors and associations are unchanged. The store is owned infrastructure, drawn as a data store (`[(...)]`) on the right.
