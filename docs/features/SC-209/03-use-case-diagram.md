# 03 — Use case diagram: SC-209

Two views: the feature's own use cases, then the updated system-wide diagram
(the Shortcut "Update Use Case Diagram" step).

**Legend:** `★` = new in SC-209. Everything else is existing and unchanged. The
authoritative system-wide use-case catalog lives in Shortcut; the existing set
below is derived from the workspace stories and is shown for placement, not
re-authored here.

## 3.1 Feature view

```mermaid
flowchart LR
    U([U — User])
    AG([AI agent client<br/>supporting actor ★])
    GEO([Geocoding provider<br/>external service])

    subgraph System["Tahir's List"]
        UC01([UC-01 Search for halal restaurants<br/>via an AI agent ★])
        UC02([UC-02 View a restaurant's details<br/>via an AI agent ★])
    end

    U --> UC01
    U --> UC02
    AG --> UC01
    AG --> UC02
    UC01 -.->|«include»<br/>resolve place text| GEO
```

- **U (User)** initiates the goal.
- **AI agent client** performs the MCP tool call on the user's behalf. It is a
  supporting external actor, not a README persona (open question Q-7).
- **Geocoding provider** is included by UC-01 only when place text is used
  (coordinates bypass it).

## 3.2 System-wide view (delta placement)

```mermaid
flowchart LR
    U([U — User])
    RO([RO — Restaurant Owner])
    VRO([VRO — Verified Restaurant Owner])
    VC([VC — Verification Committee])
    IC([IC — Issues Committee])
    ET([ET — Extension Trigger])
    AG([AI agent client<br/>supporting ★])

    subgraph System["Tahir's List"]
        direction TB
        subgraph Existing["Existing use cases (unchanged)"]
            E01([Create Account])
            E02([Log In])
            E03([Search for Halal Restaurants])
            E04([Filter Results])
            E05([View Restaurant])
            E06([Favorite / Unfavorite Restaurant])
            E07([View Favorited Restaurants])
            E08([Add Restaurant])
            E09([Claim a Listing])
            E10([Submit Halal Certification])
            E11([Decide a Verification Review])
            E12([View Halal Certification])
            E13([Add / Remove Image])
            E14([Edit Listing])
            E15([Surface Overlay on Google / Yelp])
        end
        subgraph New["New in SC-209"]
            N01([UC-01 Search for halal restaurants<br/>via an AI agent ★])
            N02([UC-02 View a restaurant's details<br/>via an AI agent ★])
        end
    end

    U --> E01
    U --> E02
    U --> E03
    U --> E05
    U --> E06
    U --> E07
    U --> N01
    U --> N02
    RO --> E08
    RO --> E09
    RO --> E14
    VRO --> E13
    VRO --> E14
    VC --> E11
    IC --> E11
    ET --> E15
    AG --> N01
    AG --> N02

    E03 -.->|«include»| E04
    E05 -.->|«include»| E12
    E09 -.->|«include»| E10
```

### Coverage and consistency checks

- **Phase 2 ↔ Phase 3:** exactly one diagram node per use case in
  `02-use-cases.md` (UC-01, UC-02). No orphan nodes.
- **Actors:** U, RO, VRO, VC, IC, ET match the README persona table. The AI
  agent client is the only added actor and is explicitly flagged as a supporting
  actor (Q-7).
- **Boundary:** named "Tahir's List".
- **`«include»`:** the new feature includes the geocoding provider (external)
  only for place text; it does **not** include the existing UI search use case —
  it reaches the same read model directly, without an HTTP hop.
- **New vs existing:** marked with `★` and grouped.
