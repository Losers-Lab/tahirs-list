> **Purpose:** place the new use cases in the system · **Derived from:** 02 · **Delta:** +UC-01, +UC-02; no existing use case changes

# 03 — Use case diagram: SC-209

## Feature view

```mermaid
flowchart LR
    U([U — User])
    AG([AI agent client ★])
    GEO([Geocoding provider])
    subgraph System["Tahir's List"]
        UC01([UC-01 Search ★])
        UC02([UC-02 Detail ★])
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
    U([U])
    RO([RO])
    VRO([VRO])
    VC([VC])
    IC([IC])
    ET([ET])
    AG([AI agent ★])
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
        subgraph New["New — SC-209"]
            N01([UC-01 Search via agent ★])
            N02([UC-02 Detail via agent ★])
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
| ★ | New in SC-209 |
| Existing | Derived from Shortcut stories; the authoritative catalog lives in Shortcut |
| `«include»` | Required sub-flow, not a top-level actor goal |

**Actor → use case** means "may initiate", not "benefits from". The AI agent is a
supporting external actor, not a README persona (D-9). The agent surface reaches
the read model directly — it does not include the UI search use case.
