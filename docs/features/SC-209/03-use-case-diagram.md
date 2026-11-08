> **Purpose:** place the MCP channel in the system · **Derived from:** 02 · **Delta:** existing Search/View use cases extended by a new actor and channel; no new use-case goals

# 03 — Use case diagram: SC-209

## Feature view

```mermaid
flowchart LR
    U([U — User])
    AG([AG — AI agent client ★])
    GEO([Geocoding provider])
    subgraph System["Tahir's List"]
        UC01([UC-01 Search — existing ★])
        UC02([UC-02 View — existing ★])
    end
    U --> UC01
    U --> UC02
    AG --> UC01
    AG --> UC02
    UC01 -.->|include| GEO
```

## System-wide view

```mermaid
flowchart LR
    U([U — User])
    RO([RO — Restaurant Owner])
    VRO([VRO — Verified Restaurant Owner])
    VC([VC — Verification Committee])
    IC([IC — Issues Committee])
    ET([ET — Extension Trigger])
    AG([AG — AI agent client ★])
    subgraph System["Tahir's List"]
        subgraph Existing["Existing — unchanged"]
            E01([Create Account])
            E02([Log In])
            E03([Search])
            E04([Filter Results])
            E05([View Restaurant])
            E06([Favorites])
            E08([Add Restaurant])
            E09([Claim Listing])
            E10([Submit Certification])
            E11([Decide Review])
            E12([View Certification])
            E13([Images])
            E14([Edit Listing])
            E15([Extension Overlay])
        end
        subgraph Extended["Existing — extended by SC-209 ★"]
            N01([UC-01 Search — MCP channel])
            N02([UC-02 View — MCP channel])
        end
    end
    U --> E01 & E02 & E03 & E05 & E06 & N01 & N02
    RO --> E08 & E09 & E14
    VRO --> E13 & E14
    VC --> E11
    IC --> E11
    ET --> E15
    AG --> N01 & N02
    E03 -.->|include| E04
    E05 -.->|include| E12
    E09 -.->|include| E10
```

## Legend

| Mark | Meaning |
|------|---------|
| ★ | New actor or channel; the use-case goals themselves are existing |
| Extended | Existing goal with new MCP flows (actor + channel added) |
| `«include»` | Required sub-flow, not a top-level actor goal |

**Actors:** U = User · RO = Restaurant Owner · VRO = Verified Restaurant Owner · VC = Verification Committee · IC = Issues Committee · ET = Extension Trigger · AG = AI agent client (supporting, new).

The agent introduces **no new goal**: it is a supporting actor on the existing
Search and View use cases; MCP is a new channel. **Actor → use case** means "may
initiate", not "benefits from".
