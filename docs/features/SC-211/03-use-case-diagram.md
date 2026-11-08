# SC-211 — Phase 3: Use case diagram

**System boundary:** "Tahir's List".

## 1. This feature's use cases

```mermaid
flowchart LR
  DC([Data Curator DC<br/>NEW internal actor])

  subgraph System["Tahir's List"]
    direction TB
    UC01([UC-01 Run a sample directory ingestion])
    UC02([UC-02 Compile a cited manual Tier-3 batch])
    UC03([UC-03 Load a reviewed ingestion batch into the catalog])
    UC04([UC-04 Audit an ingestion run and record evidence])
  end

  HMS[HMS Directory<br/>external]
  HFSAA[HFSAA Directory<br/>external]
  HMA[HMA Directory<br/>external]
  ZAB[Zabihah<br/>external — manual, cited]
  PH[Photon Geocoder<br/>external]

  DC --> UC01
  DC --> UC02
  DC --> UC03
  DC --> UC04

  UC01 --> HMS
  UC01 --> HFSAA
  UC01 --> HMA
  UC01 --> PH
  UC02 --> ZAB
  UC02 --> PH
  UC03 --> PH
  UC04 --> ZAB
```

Notes:

- **No `«include»` relationships.** The geocoding/confirmation step is shared by
  UC-01 and UC-02, but it is an internal step of those goals, not an actor goal
  in its own right; modelling it as an included use case would add a diagram
  without adding meaning. `UC-04` reads the artifacts of `UC-01`/`UC-02` but is
  independently triggered by the curator, so it is an association, not an
  extend.
- UC-03's external participant is the live search surface, which is *inside*
  Tahir's List; the diagram shows only the external directory/geocoder
  dependencies.
- The Data Curator talks to the system through the containerized sample
  command, the curated batch file, the generated catalog update, and the run
  report — all internal tooling surfaces, not end-user product routes.

## 2. Updated system-wide use case diagram

New items are marked **🆕**; everything else is the existing, agreed model from
Shortcut's *Use Case Diagram* document.

```mermaid
flowchart LR
  U([User])
  RO([Restaurant Owner])
  VRO([Verified Restaurant Owner])
  VC([Verification Committee])
  IC([Issues Committee])
  ET([Extension Trigger])
  DC([Data Curator 🆕])

  GM[Google Maps<br/>external]
  GB[Google Business<br/>external]
  YB[Yelp Business<br/>external]
  HMS[HMS Directory 🆕<br/>external]
  HFSAA[HFSAA Directory 🆕<br/>external]
  HMA[HMA Directory 🆕<br/>external]
  ZAB[Zabihah 🆕<br/>external — manual]
  PH[Photon Geocoder 🆕<br/>external]

  subgraph System["Tahir's List"]
    direction TB
    S1([Search for Halal Restaurants])
    S2([Filter Results by Distance])
    S3([Filter by Food Cutting Method])
    S4([Filter Results by Price])
    S5([Filter Results by Cuisine])
    S6([Filter Results by Rating])
    V1([View Restaurant])
    V2([View Restaurant Halal Certification])
    A1([Create Account])
    A2([Log In])
    F1([Favorite Restaurant])
    F2([Unfavorite Restaurant])
    F3([View Favorited Restaurants])
    I1([Add Image])
    I2([Edit Unverified Restaurant])
    I3([Report Issue])
    R1([Add Restaurant])
    R2([Restaurant Verification])
    R3([Edit Verified Restaurant])
    R4([Remove Image])
    R5([Restaurant Listing Analytics])
    R6([Restaurant Removal])
    R7([Restaurant Blacklisting])
    E1([Browser Extension — List View])
    E2([Browser Extension — Individual View])
    C1([Accept/Deny Restaurant Edit])
    C2([Accept/Deny Restaurant Verification])
    N1([UC-01 Run a sample directory ingestion 🆕])
    N2([UC-02 Compile a cited manual Tier-3 batch 🆕])
    N3([UC-03 Load a reviewed ingestion batch into the catalog 🆕])
    N4([UC-04 Audit an ingestion run and record evidence 🆕])
  end

  U --> S1
  U --> V1
  U --> A1
  U --> A2
  U --> F1
  U --> F2
  U --> F3
  U --> I1
  U --> I2
  U --> I3
  U --> R1
  RO --> R2
  RO --> R6
  RO --> R7
  VRO --> R3
  VRO --> R4
  VRO --> R5
  VC --> C1
  VC --> C2
  IC --> I3
  ET --> E1
  ET --> E2
  DC --> N1
  DC --> N2
  DC --> N3
  DC --> N4

  S1 -. include .-> S3
  S1 -. include .-> S4
  S1 -. include .-> S5
  S1 -. include .-> S6
  S1 --> GM
  S1 -. include .-> S2
  S2 --> GM
  S6 --> GB
  S6 --> YB
  V1 -. include .-> V2

  N1 --> HMS
  N1 --> HFSAA
  N1 --> HMA
  N1 --> PH
  N2 --> ZAB
  N2 --> PH
  N4 --> ZAB

  classDef new fill:#e8f5e9,stroke:#2e7d32,stroke-width:2px;
  class DC,N1,N2,N3,N4,HMS,HFSAA,HMA,ZAB,PH new;
```

## 3. Legend

| Mark | Meaning |
|---|---|
| 🆕 / green | New in SC-211 |
| plain | Existing, agreed model (unchanged) |
| dotted arrow | `«include»` |
| solid arrow | actor association / external dependency |

## 4. Known gaps

- The **Data Curator** is a new actor. The README persona table has no internal
  operations persona; D-1 asks the founder to ratify the naming and home of this
  actor.
- `Compose candidate batch`, `Generate catalog update` are *steps*, not
  use cases; they are intentionally not drawn at this level.
