> **Purpose:** show how the MCP channel serves existing UC-02 internally · **Derived from:** 02, 05 · **Delta:** new channel flows
> **Transaction:** two read-only `suspendTransaction` calls (listing, review); no writes.

# 06 — White-box: UC-02 View Restaurant (MCP channel)

```mermaid
sequenceDiagram
    actor AG as AI agent
    participant EP as McpHttpEndpoint
    participant GATE as AgentRequestGate
    participant RL as Bucket4jAgentRateLimiter
    participant TOOL as GetRestaurantTool
    participant PARSER as SearchRequestParser
    participant CTRL as GetListingForAgent
    participant REPO as RestaurantListingRepository
    participant SQL as ExposedRestaurantListingRepository
    participant REV as HalalCertificationReviewRepository
    participant REVSQL as ExposedHalalCertificationReviewRepository
    participant POLICY as CertificateFactsPolicy
    participant MAP as AgentResultMapper
    participant DB@{ "type": "database" } as PostgreSQL/PostGIS
    AG->>EP: 2. tools/call get_restaurant {id}
    Note over EP: no authn/authz — public read-only
    EP->>GATE: 2a. allow(clientKey)
    GATE->>RL: tryAcquire(key)
    RL-->>GATE: true
    EP->>TOOL: 2b. call(arguments)
    TOOL->>PARSER: 3. parseListingId(id)
    PARSER-->>TOOL: ListingId
    TOOL->>CTRL: 4. execute(id)
    CTRL->>REPO: findById(id)
    REPO->>SQL: findById(id)
    Note over SQL: read-only transaction
    SQL->>DB: SELECT listing by id
    DB-->>SQL: listing row
    SQL-->>REPO: RestaurantListing
    REPO-->>CTRL: RestaurantListing
    alt verified
        CTRL->>REV: 5. findLatestApprovedByListing(id)
        REV->>REVSQL: findLatestApprovedByListing(id)
        REVSQL->>DB: SELECT latest approved review
        DB-->>REVSQL: review row?
        REVSQL-->>REV: review?
        REV-->>CTRL: review?
        CTRL->>POLICY: derive(listing, review)
        POLICY-->>CTRL: CertificateFacts?
    end
    CTRL-->>TOOL: AgentListingDetail
    TOOL->>MAP: 6. toDetail(detail)
    MAP-->>TOOL: StructuredDetail
    TOOL-->>EP: CallToolResult
    EP-->>AG: 6. JSON-RPC result
```

**Authorization:** none; detail is public. Certificate bytes stay behind the
existing `/v1/listings/{id}/certificate` verified/approved/archived rule.
**State transitions:** none.

## Delta notes

| Flow | Concrete cause | Result |
|---|---|---|
| E1 | `findById` null → `AgentListingNotFoundException` | `isError=true`, "restaurant not found" |
| E2 | `parseListingId` → `InvalidSearchArgumentsException` | `isError=true`, "id must be valid" |
| E3 | `AgentRateLimiter.tryAcquire` false | HTTP 429 + `Retry-After` |
| E4 | SDK JSON-RPC failure | JSON-RPC error |
| E5 | Origin not in allow-list | HTTP 403 |
| E6 | Persistence failure | `isError=true`, generic message |

**A1** — ineligible certificate: `CertificateFactsPolicy` returns null; no
`certificateUrl` is advertised (never a 404 link).
**A2** — partial halal: scope and the declared per-item status are emitted independently of verification; absence = unknown.

**Error hygiene:** no exception text, SQL, stack trace or server path reaches the agent.
