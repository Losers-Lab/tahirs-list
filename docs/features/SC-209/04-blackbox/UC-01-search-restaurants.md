> **Purpose:** actor-visible behavior of the MCP channel for existing UC-01 · **Derived from:** 02 · **Delta:** new channel flows · **Security:** public read-only; refusals are 429/403; listing text is untrusted

# 04 — Black-box: UC-01 Search for Halal Restaurants (MCP channel)

**Main** — validated search returns a distance-ordered page with facts and links.
```mermaid
sequenceDiagram
    actor U as User
    participant AG as AI agent
    participant GEO as Geocoding provider
    participant TL as Tahir's List
    U->>AG: 1. Ask for restaurants near a place, with filters
    AG->>TL: 2. Search (place or coordinates, radius, filters)
    TL->>TL: 3. Validate
    opt place text
        TL->>GEO: 4. Resolve place
        GEO-->>TL: 4a. Coordinates
    end
    TL->>TL: 5. Find matching listings
    TL-->>AG: 6. Ordered page + facts + links
    AG-->>U: 7. Present results
```
**A1** — coordinates skip the external lookup.
```mermaid
sequenceDiagram
    participant AG as AI agent
    participant TL as Tahir's List
    AG->>TL: 2. Search with coordinates
    TL->>TL: 3. Validate, then 5. Find listings
    TL-->>AG: 6. Ordered page + facts + links
```
**A2** — no match is a successful empty page.
```mermaid
sequenceDiagram
    participant AG as AI agent
    participant TL as Tahir's List
    AG->>TL: 2–4. Valid request
    TL->>TL: 5. Nothing matches
    TL-->>AG: 6. Empty page (count 0)
```
**A3** — a full page carries a next-offset hint.
```mermaid
sequenceDiagram
    participant AG as AI agent
    participant TL as Tahir's List
    AG->>TL: 2. Search (page size N)
    TL-->>AG: 6. N results + nextOffset
    AG->>TL: 2'. Same search, next offset
    TL-->>AG: 6'. Next page
```
**A4** — detail continues in UC-02 with the listing id.

**E1** — invalid input returns an actionable tool error.
```mermaid
sequenceDiagram
    participant AG as AI agent
    participant TL as Tahir's List
    AG->>TL: 2. Invalid value
    TL-->>AG: Tool error with the specific reason
```
**E2** — an unresolvable place returns guidance.
```mermaid
sequenceDiagram
    participant AG as AI agent
    participant GEO as Geocoding provider
    participant TL as Tahir's List
    AG->>TL: 2. Search by place
    TL->>GEO: 4. Resolve
    GEO-->>TL: 4a. No match
    TL-->>AG: Error: try a nearby city or coordinates
```
**E3** — a provider outage recommends coordinates.
```mermaid
sequenceDiagram
    participant AG as AI agent
    participant GEO as Geocoding provider
    participant TL as Tahir's List
    AG->>TL: 2. Search by place
    TL->>GEO: 4. Resolve
    GEO-->>TL: 4a. Provider failure
    TL-->>AG: Distinct error: supply coordinates
```
**E4–E7** — refusals and failures before or during work.

| Flow | Condition | Agent observes | State |
|------|-----------|----------------|-------|
| E4 | Budget exhausted | 429 before tool work; retry later | Unchanged |
| E5 | Malformed protocol | JSON-RPC error; no tool | Unchanged |
| E6 | Origin disallowed | 403; no tool | Unchanged |
| E7 | Store unavailable | Generic non-internal error | Unchanged |
