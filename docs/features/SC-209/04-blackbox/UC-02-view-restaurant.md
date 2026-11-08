> **Purpose:** actor-visible behavior of the MCP channel for existing UC-02 · **Derived from:** 02 · **Delta:** new channel flows · **Security:** public read-only; certificate facts only for verified + approved + archived

# 04 — Black-box: UC-02 View Restaurant (MCP channel)

**Main** — validated detail request returns trust facts and links.

```mermaid
sequenceDiagram
    actor U as User
    participant AG as AI agent
    participant TL as Tahir's List
    U->>AG: 1. Ask for details about a restaurant
    AG->>TL: 2. Detail request with the listing id
    TL->>TL: 3. Validate id, then 4. Load listing
    TL->>TL: 5. Derive certificate facts if eligible
    TL-->>AG: 6. Full detail + image + certificate/link
    AG-->>U: 7. Present the detail
```

**A1** — ineligible certificate: detail without a certificate block.

```mermaid
sequenceDiagram
    participant AG as AI agent
    participant TL as Tahir's List
    AG->>TL: 2. Detail request
    TL->>TL: 4. Found, then 5. No eligible certificate
    TL-->>AG: 6. Detail without certificate or link
```

**A2** — partial halal: scope and items reported independently of verification.

```mermaid
sequenceDiagram
    participant AG as AI agent
    participant TL as Tahir's List
    AG->>TL: 2. Detail request
    TL-->>AG: 6. Partial scope + halal items + status
```

**E1** — unknown id returns "not found".

```mermaid
sequenceDiagram
    participant AG as AI agent
    participant TL as Tahir's List
    AG->>TL: 2. Unknown id
    TL-->>AG: Tool error: restaurant not found
```

**E2** — malformed id returns an actionable error.

```mermaid
sequenceDiagram
    participant AG as AI agent
    participant TL as Tahir's List
    AG->>TL: 2. Malformed id
    TL->>TL: 3. Format check fails
    TL-->>AG: Tool error: id must be valid
```

**E3–E6** — refusals and failures before or during work.

| Flow | Condition | Agent observes | State |
|------|-----------|----------------|-------|
| E3 | Budget exhausted | 429 before tool work | Unchanged |
| E4 | Malformed protocol | JSON-RPC error; no tool | Unchanged |
| E5 | Origin disallowed | 403; no tool | Unchanged |
| E6 | Store unavailable | Generic non-internal error | Unchanged |
