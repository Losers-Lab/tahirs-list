> **Purpose:** show how the MCP channel serves existing UC-01 internally · **Derived from:** 02, 05 · **Delta:** new channel flows
> **Transaction:** one read-only `suspendTransaction` in `ExposedListingSearchQuery`; nothing writes.

# 06 — White-box: UC-01 Search for Halal Restaurants (MCP channel)

```mermaid
sequenceDiagram
    participant AG as AI agent
    participant EP as McpHttpEndpoint
    participant GATE as AgentRequestGate
    participant RL as Bucket4jAgentRateLimiter
    participant TOOL as SearchRestaurantsTool
    participant PARSER as SearchRequestParser
    participant CTRL as SearchListingsForAgent
    participant GEO as GeocoderPort
    participant PHOTON as PhotonGeocoder
    participant Q as ListingSearchQuery
    participant SQL as ExposedListingSearchQuery
    participant MAP as AgentResultMapper
    AG->>EP: 2. tools/call search_restaurants
    Note over EP: no authn/authz — public read-only
    EP->>GATE: 2a. allow(clientKey)
    GATE->>RL: tryAcquire(key)
    RL-->>GATE: true
    EP->>TOOL: 2b. call(arguments)
    TOOL->>PARSER: 3. parse(arguments)
    alt place text
        TOOL->>CTRL: 4. execute(request)
        CTRL->>GEO: geocode(place)
        GEO->>PHOTON: geocode(place)
        PHOTON-->>CTRL: GeocodedPlace
    else coordinates
        TOOL->>CTRL: 4. execute(request)
    end
    CTRL->>Q: 5. searchNearby(centre, radius, filters, offset, limit)
    Q->>SQL: searchNearby(...)
    Note over SQL: read-only transaction
    SQL-->>CTRL: ListingSearchResult[]
    CTRL-->>TOOL: AgentSearchResult
    TOOL->>MAP: 6. toCards(result)
    MAP-->>TOOL: StructuredSearchResult
    TOOL-->>EP: CallToolResult
    EP-->>AG: 6. JSON-RPC result
```

**Authorization:** none; `AgentRequestGate` is a budget, not identity, and runs
before tool execution. **State transitions:** none.

## Delta notes

| Flow | Concrete cause | Result |
|---|---|---|
| E1 | `InvalidSearchArgumentsException` | `isError=true`, specific reason |
| E2 | `GeocoderPort.geocode` → null → `PlaceNotResolvedException` | `isError=true`, "try a nearby city or coordinates" |
| E3 | `GeocoderPort.geocode` throws `GeocodingException` | `isError=true`, "location lookup unavailable; supply coordinates" |
| E4 | `AgentRateLimiter.tryAcquire` false | HTTP 429 + `Retry-After`, no tool work |
| E5 | SDK JSON-RPC failure | JSON-RPC error, no tool work |
| E6 | Origin not in allow-list | HTTP 403, no tool work |
| E7 | Persistence failure | `isError=true`, generic message; logged only |

**A1** — coordinates: the `place text` branch is skipped; the rest is unchanged.
**A2** — empty result: `AgentSearchResult([], 0, null)` maps to success.
**A3** — full page: `nextOffset = offset + results.size`; a follow-up call repeats the path.
**A4** — detail: the tool result carries the listing id; see UC-02 white-box.

**Error hygiene:** no exception text, SQL, stack trace or server path reaches the agent.
