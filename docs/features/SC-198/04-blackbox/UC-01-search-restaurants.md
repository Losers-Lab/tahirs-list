# SC-198 — Phase 4: Black-box sequence, UC-01 Search for Halal Restaurants

> **Purpose:** what the user and agent see, flow by flow · **Derived from:** 02 UC-01 · **Delta:** new warm branch + cache-outage behaviour
> **Security:** place text is untrusted caller input; the cache stores only its hash, never the raw text.

```mermaid
sequenceDiagram
  autonumber
  actor U as User
  actor AG as AI agent
  actor PH as Photon
  participant S as Tahir's List

  U->>AG: 1. halal food near "Main St"
  AG->>S: 2. search(place, radius, filters)
  Note over S: 3. validate radius, page, filters
  alt place already known (warm)
    S->>S: 4a. resolve from cache
  else place not known (cold)
    S->>PH: 4b. geocode "Main St"
    PH-->>S: 4c. coordinates + label
    S->>S: 4d. store the match
  end
  S->>S: 5-6. find, order, page listings
  S-->>AG: 7. results with distance
  AG-->>U: results
```

## Alternate — A1 coordinates supplied

```mermaid
sequenceDiagram
  autonumber
  actor U as User
  actor AG as AI agent
  participant S as Tahir's List

  U->>AG: 1. halal food near 43.65,-79.38
  AG->>S: 2. search(center, radius, filters)
  Note over S: 3. validate
  S->>S: 5-6. find, order, page
  S-->>AG: 7. results
```

No place resolution, so no cache read and no provider call.

## Exception — E2 no match (cold)

```mermaid
sequenceDiagram
  autonumber
  actor AG as AI agent
  actor PH as Photon
  participant S as Tahir's List

  AG->>S: 2. search(place, radius, filters)
  S->>PH: 4b. geocode place
  PH-->>S: 4c. no match
  S-->>AG: E2. "try a nearby city or town"
```

Nothing is stored: a miss stays a miss and is retried on the next request.

## Exception — E3 provider unavailable (cold), warm queries unaffected

```mermaid
sequenceDiagram
  autonumber
  actor AG as AI agent
  actor PH as Photon
  participant S as Tahir's List

  AG->>S: 2. search(place X, radius, filters)
  S->>PH: 4b. geocode X
  PH--xS: 4c. timeout / non-200
  S-->>AG: E3. "lookup unavailable; supply coordinates"
  Note over S: nothing stored for X
  AG->>S: 2'. search(place Y, radius, filters)
  Note over S: 4a. Y already cached
  S-->>AG: 7'. results for Y
```

A cache hit keeps search working during an outage; only never-seen places fail.

## Exceptions — E5 / E6 cache store trouble

| ID | Condition | What the actor sees | Store state |
|---|---|---|---|
| E5 | Cache read fails | Normal results (a provider call happens) | Unchanged |
| E6 | Cache write fails after a provider match | Normal results | No row; provider paid again later |

Both are logged at WARN; neither changes the visible outcome (D-7).

## Unchanged flows

E1 (invalid arguments), A3 (empty page), A4 (page full) and E4 (rate limit) behave exactly as in SC-209 `04-blackbox/UC-01`.
