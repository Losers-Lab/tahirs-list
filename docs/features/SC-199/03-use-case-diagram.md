# SC-199 — Phase 3: Use case diagram

> **Purpose:** place the modified and new use cases against the agreed model · **Derived from:** 02 · **Delta:** UC-01 marked modified; UC-02 (edge serving) marked new; no new human actor

## Feature scope

```mermaid
flowchart LR
  U([User]); ET([Extension Trigger<br/>AI agent]); CDN([Cloudflare edge<br/>cache])
  subgraph S["Tahir's List"]
    UC1([UC-01 Search listings by location])
  end
  CR[["Cache Rule<br/>operator config"]]

  U --> UC1; ET --> UC1
  U --> CDN; ET --> CDN
  CDN -. serves fresh copy .-> U
  CDN --> UC1
  CR -. enables .-> CDN

  classDef modified fill:#fff8e1,stroke:#f9a825,stroke-width:2px
  classDef new fill:#e8f5e9,stroke:#2e7d32,stroke-width:2px
  class UC1 modified
  class CDN,CR new
```

UC-02 is the edge's behaviour, so it is an actor (the CDN) rather than a bubble inside
the system: the origin never sees it. The Cache Rule is operator configuration outside
the codebase, drawn as a dependency so the coupling is visible.

## System-wide (changed items amber; new items green; everything else unchanged)

```mermaid
flowchart LR
  U([User]); ET([Extension Trigger]); CDN([Edge cache])
  subgraph System["Tahir's List"]
    S1([Search listings by location])
    S2([Filter Results by Distance]); S3([Filter by Food Cutting Method]); S4([Filter Results by Price]); S5([Filter Results by Cuisine]); S6([Filter Results by Rating])
    V1([View Restaurant]); V2([View Restaurant Halal Certification])
    A1([Create Account]); A2([Log In]); F1([Favorite Restaurant]); F2([Unfavorite Restaurant]); F3([View Favorited Restaurants])
    I1([Add Image]); I2([Edit Unverified Restaurant]); I3([Report Issue]); R1([Add Restaurant]); R2([Restaurant Verification]); R3([Edit Verified Restaurant]); R4([Remove Image]); R5([Restaurant Listing Analytics]); R6([Restaurant Removal]); R7([Restaurant Blacklisting])
    E1([Browser Extension — List View]); E2([Browser Extension — Individual View]); C1([Accept/Deny Restaurant Edit]); C2([Accept/Deny Restaurant Verification])
    N1([Run sample ingest])
  end
  CR[["Cache Rule 🆕"]]

  U --> S1; U --> V1; U --> A1; U --> A2; U --> F1; U --> F2; U --> F3; U --> I1; U --> I2; U --> I3; U --> R1
  ET --> S1; ET --> E1; ET --> E2
  S1 -. include .-> S2; S1 -. include .-> S3; S1 -. include .-> S4; S1 -. include .-> S5; S1 -. include .-> S6
  U --> CDN; ET --> CDN; CDN --> S1
  CR -. configures .-> CDN

  classDef changed fill:#fff8e1,stroke:#f9a825,stroke-width:2px
  classDef new fill:#e8f5e9,stroke:#2e7d32,stroke-width:2px
  class S1 changed
  class CDN,CR new
```

| Mark | Meaning |
|---|---|
| Amber | Changed by SC-199 (UC-01: centre snapping, freshness header) |
| Green | New by SC-199 (edge serving; the Cache Rule as operator config) |
| Solid arrow | Actor association / request path |
| Dotted arrow | `«include»` or configuration |

All other use cases, actors and associations are unchanged. No new owned data store: the
feature adds no table, and the edge copy is not Tahir's List state.
