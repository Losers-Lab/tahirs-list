# 04 — Black-box sequence: UC-01 Search for halal restaurants (via an AI agent)

**Security-relevant:** public anonymous read surface; rate limiting and Origin
validation are refusal paths; listing text is untrusted community content shown
to a model.

Participants are only the actors, **one** lifeline for Tahir's List, and the
external geocoding provider. No internal names appear.

## Main success scenario

```mermaid
sequenceDiagram
    actor U as User
    participant AG as AI agent client
    participant TL as Tahir's List
    participant GEO as Geocoding provider (external)

    U->>AG: 1. Ask for halal restaurants near a place, with optional filters
    AG->>TL: 2. Search (place or coordinates, radius in miles, filters)
    TL->>TL: 3. Validate radius, page and filters
    opt place text was supplied
        TL->>GEO: 4. Resolve place text to coordinates
        GEO-->>TL: 4a. Coordinates, or no match, or unavailable
    end
    TL->>TL: 5. Find index-qualified listings in the radius matching the filters
    TL-->>AG: 6. Ordered page with halal/verification facts and listing links
    AG-->>U: 7. Present the results
```

## Alternate flows

### A1 — Coordinates supplied instead of place text (from step 2)

```mermaid
sequenceDiagram
    actor U as User
    participant AG as AI agent client
    participant TL as Tahir's List

    U->>AG: 1. Ask near a known location
    AG->>TL: 2. Search with coordinates, radius and filters
    TL->>TL: 3. Validate
    Note over TL: Step 4 is skipped — no external lookup
    TL->>TL: 5. Find matching listings
    TL-->>AG: 6. Ordered page with facts and links
    AG-->>U: 7. Present the results
```

### A2 — No listing matches (from step 5)

```mermaid
sequenceDiagram
    participant AG as AI agent client
    participant TL as Tahir's List

    AG->>TL: 2–4. Valid search request
    TL->>TL: 5. No listing qualifies
    TL-->>AG: 6. Empty page (count 0)
    Note over AG: Success — the agent tells the user nothing matched
```

### A3 — More matches than the page size (from step 6)

```mermaid
sequenceDiagram
    participant AG as AI agent client
    participant TL as Tahir's List

    AG->>TL: 2. Search request (page size N)
    TL-->>AG: 6. Page of N results
    Note over AG,TL: The page carries a next-offset hint
    AG->>TL: 2'. Same search with the next offset
    TL-->>AG: 6'. The next page
```

### A4 — The agent needs more detail on one result (from step 6)

```mermaid
sequenceDiagram
    participant AG as AI agent client
    participant TL as Tahir's List

    TL-->>AG: 6. Search result with a listing id
    AG->>TL: Continue in UC-02 with that id
    Note over AG,TL: See 04-blackbox/UC-02-view-restaurant.md
```

## Exception flows

### E1 — Invalid argument (from step 3)

```mermaid
sequenceDiagram
    participant AG as AI agent client
    participant TL as Tahir's List

    AG->>TL: 2. Search request with an invalid value
    TL->>TL: 3. Validation fails
    TL-->>AG: Tool error: the specific reason and how to correct it
    Note over TL: No search performed; no state changed
```

### E2 — Place has no match (from step 4)

```mermaid
sequenceDiagram
    participant AG as AI agent client
    participant TL as Tahir's List
    participant GEO as Geocoding provider (external)

    AG->>TL: 2. Search by place text
    TL->>GEO: 4. Resolve
    GEO-->>TL: 4a. No match
    TL-->>AG: Tool error: try a nearby city or supply coordinates
    Note over TL: No search performed
```

### E3 — Geocoding provider unavailable (from step 4)

```mermaid
sequenceDiagram
    participant AG as AI agent client
    participant TL as Tahir's List
    participant GEO as Geocoding provider (external)

    AG->>TL: 2. Search by place text
    TL->>GEO: 4. Resolve
    GEO-->>TL: 4a. Provider failure
    TL-->>AG: Distinct tool error: supply coordinates instead
    Note over TL: No search performed
```

### E4–E7 — Refusals and failures (from steps 2 and 5)

| Flow | Condition | What the agent observes | State |
|------|-----------|-------------------------|-------|
| E4 | Per-client budget exhausted | HTTP 429 refusal before any tool work; may retry after the window | Unchanged |
| E5 | Malformed protocol request | Protocol-level error response; no tool executes | Unchanged |
| E6 | Browser origin not allowed | HTTP 403 refusal; no tool executes | Unchanged |
| E7 | Data store unavailable | Generic, non-internal error result | Unchanged |
