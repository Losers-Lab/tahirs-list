# SC-211 — Phase 3: Use case diagram

> **Purpose:** place the new use cases against the agreed system model · **Derived from:** 02 · **Delta:** new UC-01…UC-04 + new actor DC

## Feature scope

```mermaid
flowchart LR
  DC([Data Curator<br/>new, D-1])
  subgraph S["Tahir's List"]
    UC1([UC-01 Run sample ingestion])
    UC2([UC-02 Compile cited manual batch])
    UC3([UC-03 Load reviewed batch])
    UC4([UC-04 Audit run + evidence])
  end
  HMS[certifier-hms]; HFSAA[certifier-hfsaa]; HMA[certifier-hma]; ZAB[Zabihah<br/>manual]; PH[Photon]
  DC --> UC1; DC --> UC2; DC --> UC3; DC --> UC4
  UC1 --> HMS; UC1 --> HFSAA; UC1 --> HMA; UC1 --> PH
  UC2 --> ZAB; UC2 --> PH
  UC4 --> ZAB
```

Each Tier-1 dependency is a standalone module with its own domain model; the `:ingestion` translators sit between it and the app (D-12).

No `«include»`: the shared geocoding step is internal to UC-01/02, not an actor goal. UC-04 is independently triggered.

## System-wide (new items green)

```mermaid
flowchart LR
  U([User]); RO([Restaurant Owner]); VRO([Verified Restaurant Owner]); VC([Verification Committee]); IC([Issues Committee]); ET([Extension Trigger]); DC([Data Curator 🆕])
  subgraph System["Tahir's List"]
    S1([Search for Halal Restaurants]); S2([Filter Results by Distance]); S3([Filter by Food Cutting Method]); S4([Filter Results by Price]); S5([Filter Results by Cuisine]); S6([Filter Results by Rating])
    V1([View Restaurant]); V2([View Restaurant Halal Certification])
    A1([Create Account]); A2([Log In]); F1([Favorite Restaurant]); F2([Unfavorite Restaurant]); F3([View Favorited Restaurants])
    I1([Add Image]); I2([Edit Unverified Restaurant]); I3([Report Issue]); R1([Add Restaurant]); R2([Restaurant Verification]); R3([Edit Verified Restaurant]); R4([Remove Image]); R5([Restaurant Listing Analytics]); R6([Restaurant Removal]); R7([Restaurant Blacklisting])
    E1([Browser Extension — List View]); E2([Browser Extension — Individual View]); C1([Accept/Deny Restaurant Edit]); C2([Accept/Deny Restaurant Verification])
    N1([UC-01 Run sample ingest 🆕]); N2([UC-02 Compile manual batch 🆕]); N3([UC-03 Load reviewed batch 🆕]); N4([UC-04 Audit run 🆕])
  end
  U --> S1; U --> V1; U --> A1; U --> A2; U --> F1; U --> F2; U --> F3; U --> I1; U --> I2; U --> I3; U --> R1
  RO --> R2; RO --> R6; RO --> R7; VRO --> R3; VRO --> R4; VRO --> R5; VC --> C1; VC --> C2; IC --> I3; ET --> E1; ET --> E2
  DC --> N1; DC --> N2; DC --> N3; DC --> N4
  S1 -. include .-> S2; S1 -. include .-> S3; S1 -. include .-> S4; S1 -. include .-> S5; S1 -. include .-> S6
  S1 --> GM[Google Maps]; S2 --> GM; S6 --> GB[Google Business]; S6 --> YB[Yelp Business]; V1 -. include .-> V2
  N1 --> HMS[HMS]; N1 --> HFSAA[HFSAA]; N1 --> HMA[HMA]; N1 --> PH[Photon]; N2 --> ZAB[Zabihah<br/>manual]; N2 --> PH
  classDef new fill:#e8f5e9,stroke:#2e7d32,stroke-width:2px
  class DC,N1,N2,N3,N4,HMS,HFSAA,HMA,ZAB,PH new
```

| Mark | Meaning |
|---|---|
| 🆕 / green | New in SC-211 |
| Solid arrow | Actor association / external dependency |
| Dotted arrow | `«include»` |

Known gap: the Data Curator is a new internal actor; README/Shortcut persona table update follows D-1.
