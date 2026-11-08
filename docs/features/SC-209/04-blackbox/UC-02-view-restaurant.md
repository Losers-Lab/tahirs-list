# 04 — Black-box sequence: UC-02 View a restaurant's details (via an AI agent)

**Security-relevant:** public anonymous read surface; certificate facts are only
advertised for verified listings with an approved, archived certificate (never a
broken or private URL).

Participants are only the actors and **one** lifeline for Tahir's List.

## Main success scenario

```mermaid
sequenceDiagram
    actor U as User
    participant AG as AI agent client
    participant TL as Tahir's List

    U->>AG: 1. Ask for details about a restaurant found earlier
    AG->>TL: 2. Detail request with the listing id
    TL->>TL: 3. Validate the id format
    TL->>TL: 4. Load the listing
    TL->>TL: 5. Derive certificate facts when verified and approved
    TL-->>AG: 6. Full detail with trust facts, image, certificate + listing links
    AG-->>U: 7. Present the detail
```

## Alternate flows

### A1 — Unverified, or approved without an archived certificate (from step 5)

```mermaid
sequenceDiagram
    participant AG as AI agent client
    participant TL as Tahir's List

    AG->>TL: 2. Detail request
    TL->>TL: 4. Listing found
    TL->>TL: 5. No eligible certificate facts
    TL-->>AG: 6. Full detail without a certificate block or certificate link
    Note over AG: No broken link is ever advertised
```

### A2 — Partially halal listing (from step 5)

```mermaid
sequenceDiagram
    participant AG as AI agent client
    participant TL as Tahir's List

    AG->>TL: 2. Detail request
    TL->>TL: 4. Listing found (partially halal)
    TL-->>AG: 6. Detail states partial scope and halal items
    Note over AG,TL: Verification status is reported independently of scope
```

## Exception flows

### E1 — No listing with that id (from step 4)

```mermaid
sequenceDiagram
    participant AG as AI agent client
    participant TL as Tahir's List

    AG->>TL: 2. Detail request with an unknown id
    TL->>TL: 4. Nothing found
    TL-->>AG: Tool error: restaurant not found
    Note over TL: No state changed
```

### E2 — Id is not a valid identifier (from step 3)

```mermaid
sequenceDiagram
    participant AG as AI agent client
    participant TL as Tahir's List

    AG->>TL: 2. Detail request with a malformed id
    TL->>TL: 3. Format validation fails
    TL-->>AG: Tool error: the id must be a valid identifier
    Note over TL: No lookup performed
```

### E3–E6 — Refusals and failures (from steps 2 and 4)

| Flow | Condition | What the agent observes | State |
|------|-----------|-------------------------|-------|
| E3 | Per-client budget exhausted | HTTP 429 refusal before any tool work | Unchanged |
| E4 | Malformed protocol request | Protocol-level error; no tool executes | Unchanged |
| E5 | Browser origin not allowed | HTTP 403 refusal; no tool executes | Unchanged |
| E6 | Data store unavailable | Generic, non-internal error result | Unchanged |
