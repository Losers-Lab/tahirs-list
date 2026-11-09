# SC-205 — Phase 3: Use case diagram

> **Purpose:** where the photo goal sits in the product's use-case model · **Derived from:** 02 · **Delta:** the existing *Add Image* goal becomes reachable from the web app; no new actor, no new use case

## Feature scope

```mermaid
flowchart LR
  RO([Restaurant Owner])
  VRO([Verified Restaurant Owner])
  U([User])
  subgraph S["Tahir's List"]
    UC1([Add or replace the listing photo])
    UC2([View Restaurant])
  end
  OBJ[("MinIO / S3<br/>hero + variants")]
  PG[("PostgreSQL<br/>listings · owners · photo version")]

  RO --> UC1
  VRO --> UC1
  U --> UC2
  UC1 -. include .-> UC2
  UC1 --> OBJ
  UC1 --> PG

  classDef changed fill:#fff8e1,stroke:#f9a825,stroke-width:2px
  class UC1 changed
```

`«include»`: the owner can only add a photo *from the restaurant page*, so the photo goal always includes rendering the listing. There is no `«extend»` — replacing a photo is an alternate flow inside the same goal, not a variant use case (and SC-206's delete is a separate goal, drawn below as untouched).

## System-wide (changed item amber; everything else unchanged)

```mermaid
flowchart LR
  RO([Restaurant Owner]); VRO([Verified Restaurant Owner]); U([User]); VC([Verification Committee]); IC([Issues Committee]); ET([Extension Trigger])

  subgraph System["Tahir's List"]
    S1([Search for Halal Restaurants]); S2([Filter Results by Distance]); S3([Filter by Food Cutting Method]); S4([Filter Results by Price]); S5([Filter Results by Cuisine]); S6([Filter Results by Rating])
    V1([View Restaurant]); V2([View Restaurant Halal Certification])
    A1([Create Account]); A2([Log In]); F1([Favorite Restaurant]); F2([Unfavorite Restaurant]); F3([View Favorited Restaurants])
    I1([Add Image]); I2([Remove Image]); I3([Report Issue])
    R1([Add Restaurant]); R2([Restaurant Verification]); R3([Edit Verified Restaurant]); R4([Restaurant Listing Analytics]); R5([Restaurant Removal]); R6([Restaurant Blacklisting])
    C1([Approve or Deny Restaurant Edits]); C2([Approve or Deny Restaurant Verifications])
    E1([Browser Extension — List View]); E2([Browser Extension — Individual View])
  end
  OBJ[("MinIO / S3")]
  PG[("PostgreSQL")]

  U --> S1; U --> V1; U --> A1; U --> A2; U --> F1; U --> F2; U --> F3; U --> I3
  RO --> I1; VRO --> I1; VRO --> I2; VRO --> R3; RO --> R1
  VC --> C1; VC --> C2; IC --> I3
  ET --> S1; ET --> E1; ET --> E2
  S1 -. include .-> S2; S1 -. include .-> S3; S1 -. include .-> S4; S1 -. include .-> S5; S1 -. include .-> S6
  I1 -. include .-> V1
  I1 --> OBJ; I1 --> PG; V1 --> PG

  classDef changed fill:#fff8e1,stroke:#f9a825,stroke-width:2px
  class I1 changed
```

| Mark | Meaning |
|---|---|
| Amber | Reached by real UI for the first time in SC-205 (the goal existed since SC-53; its backend is unchanged) |
| Solid arrow | Actor association / store access |
| Dotted arrow | `«include»` |

Every other bubble, actor and association is unchanged. `I2 Remove Image` keeps its own frontend story (SC-206) and is deliberately not amber.
