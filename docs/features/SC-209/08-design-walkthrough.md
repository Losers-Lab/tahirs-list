# 08 — Design walkthrough: SC-209

Design branch: `docs/SC-209-mcp-design` · Design PR: [#73](https://github.com/Losers-Lab/tahirs-list/pull/73) · Design commit: recorded with the approval · Walkthrough date: 2026-11-07

The walkthrough below follows the ratifying order: story and domain model, use
cases, use case diagram, black-box sequences, system class diagram, white-box
sequences, complete class diagram. The complete class diagram is the design being
approved.

## 1. Story and domain model

**What it shows.** The problem is distribution: our public search exists but AI
agents cannot use it. The model itself does not change — no new aggregate,
entity or persisted value object. The feature reads `RestaurantListing` and its
certification history through the same ports the REST surface uses. The new
vocabulary is read-side: search centre (place text or coordinates), place
resolution, radius, straight-line distance, certificate facts, and the agent
tool call. Fifteen invariants turn the story's acceptance criteria into testable
rules (index gate, distance order, page bounds, tri-state filter semantics,
certificate visibility, read-only, no work when refused).

**What it locks in.** The feature stays inside the agreed domain. Verification
status and halal scope remain orthogonal (a partially halal place may be
verified). Only no-cross-contamination listings are searchable. "Unknown" stays
visible as `null`, never coerced to `false`/`0`.

**What I need from you.** Approve the model as unchanged, and confirm that the
AI agent is a supporting external actor rather than a new README persona
(D-9), plus ratify the architecture row this delivery needs (D-1).

**Known gaps.** The persona question is open (D-9). `ARCHITECTURE.md` §1.0 is not
edited yet; it will be updated in the implementation PR once ratified (D-1).

## 2. Use cases

**What it shows.** Two use cases: `UC-01 Search for halal restaurants (via an AI
agent)` and `UC-02 View a restaurant's details (via an AI agent)`. UC-01 covers
place-vs-coordinate input, empty results, paging, and every validation/geocoding/
rate-limit/origin/protocol failure. UC-02 covers detail, certificate visibility,
partial-halal, malformed and unknown ids. Both are read-only; both are
preceded by MCP initialization/tool discovery, which is modelled as a
precondition rather than a use case.

**What it locks in.** The tool surface is exactly two read operations. Every
acceptance criterion maps to at least one step or flow (matrix in
`02-use-cases.md`). Error behavior is part of the contract: specific,
actionable tool errors — never a generic 500.

**What I need from you.**
- Approve the two-use-case scope (no third "connect/discover" use case).
- D-1 (founder): ratify the architecture row.
- D-2 (Adnan): confirm story placement — epic **M2 Agent & API Surface (207)**,
  iteration **M2 Sprint 1 — Agent API (MCP) (208)**, first in
  "Ready for Development". The prioritization docs were not discoverable.
- D-3 (Omar): rate-limit budget.
- D-9 (founder/Adnan): agent-actor modelling.

**Known gaps.** Protocol discovery is intentionally not a use case; the story's
priority is a proposal until Adnan confirms it.

## 3. Use case diagram

**What it shows.** The two new use cases inside the Tahir's List boundary,
associated with U (the user's goal) and the AI agent client (the executor), with
the geocoding provider included only for place text. The system-wide view places
them among the existing catalog without changing any existing use case.

**What it locks in.** The agent surface does not include or reuse the existing
UI search use case as an HTTP hop; it reaches the same read model directly. No
existing use case changes. The only added actor is the agent client, flagged.

**What I need from you.** Approve the new/existing split and D-9. If you want
the agent added to the README persona table, say so and it becomes part of the
design PR rather than a supporting actor only.

**Known gaps.** The authoritative system-wide diagram lives in Shortcut; the
existing set here is derived from the workspace stories for placement.

## 4. Black-box sequences

**What it shows.** Actor-visible behavior, flow by flow, with no internal names:
the user's request, the agent's tool call, validity checks, optional external
place resolution, distance-ordered results with halal/verification facts and
links, and for detail: loading, certificate visibility, and links. Alternates
cover coordinates, empty results, paging and the hand-off to detail. Exceptions
cover invalid input, no place match, provider outage, rate limit, malformed
protocol, disallowed origin and store failure — each with the externally visible
outcome only.

**What it locks in.** What an agent (and therefore a user) sees in every flow:
an ordered page with tri-state facts and links; actionable errors for input and
geocoding problems; HTTP 429/403 for transport refusals; no state changes
anywhere.

**What I need from you.** Approve the flows, especially the error wording
intent ("try a nearby city or supply coordinates") and the decision that an
empty result is success, not an error. Wording of the descriptions themselves is
D-5 (Fatima).

**Known gaps.** E4–E7 are collapsed into one table because they are transport
refusals with no domain-level branch; each still has a defined outcome.

## 5. System class diagram

**What it shows.** The shape: existing entities/value objects; two new control
classes — `SearchListingsForAgent` and `GetListingForAgent` (one per use case);
boundary classes (`McpHttpEndpoint`, `AgentRequestGate`, `SearchRestaurantsTool`,
`GetRestaurantTool`, `AgentResultMapper`); shared pure helpers
(`SearchRequestParser`, `CertificateFactsPolicy`); five ports (search query,
listing repository, review repository, geocoder, rate limiter) and their adapter
stubs. The operation inventory maps every use-case step to an operation.

**What it locks in.** The MCP adapter is a driving adapter like `web-api`: it
calls application services, never persistence or HTTP directly. Geocoding stays
behind `GeocoderPort`; rate limiting is a boundary concern behind a port. The
existing out-ports are reused unchanged. No "for the future" classes.

**What I need from you.** Approve the class shape and D-8: the rate limiter is a
new small `:mcp` class duplicating ~60 lines of the auth gate, rather than a new
shared module. Recommendation: duplicate now; extract when a third consumer
appears.

**Known gaps.** Configuration (base URLs) and the SDK's own server class are
Phase 7 concerns; they are named there.

## 6. White-box sequences

**What it shows.** How each use case is served, 1:1 with the black-box files,
using only Phase 5 classes: the endpoint → gate → tool → control → port →
adapter chain; the read-only transaction boundaries in
`ExposedListingSearchQuery.searchNearby` and the two detail reads; exactly where
validation and mapping happen; and the concrete exception → tool-result/HTTP
mapping for every exception flow. Authorization is shown as *absent by design*
on a public read-only surface, with the gate marked as a budget, not identity.

**What it locks in.** No write path exists. No external call happens inside a
database transaction. Every error path has a concrete, non-internal outcome.
Certificate URLs are only advertised when the existing public proxy's
verified/approved/archived rule holds.

**What I need from you.** Approve the error mapping and the no-authz posture.
The one technical unknown — the SDK transport helper's exact mount/method
behavior alongside our existing `ContentNegotiation` — is resolved by WI-01's
spike, with the fallback documented (D-11).

**Known gaps.** SDK helper specifics (method names, whether it installs a second
`ContentNegotiation`, exact admitted methods) are pinned by WI-01 before any
other implementation work.

## 7. Complete class diagram (the design being approved)

**What it shows.** The full implementation plan: the new `:mcp` module and its
classes; the two new application services and shared parser/certificate policy;
the behaviour-preserving `web-api` changes; bootstrap config/wiring; **no
migration**; **no OpenAPI change**; **no frontend change**; the MCP endpoint
contract with both tool schemas and LLM-facing descriptions; the config keys and
prod validation; the security wiring; and the full test inventory with names.

**What it locks in.** Module boundaries, final class/file names, the two tool
names and schemas, the stateless Streamable HTTP choice, the additive
`halalItems` field on MCP detail only, the config keys, and the rate-limit/origin
posture. This is what work items and code will be cut from.

**What I need from you.** Approve the complete design — or name the diagram and
the change. The remaining decisions are listed below.

**Known gaps.**
- WI-01 spike outcome (ContentNegotiation coexistence / SDK method specifics).
- Prod base URLs and their dev values (D-10).
- Omar's final rate-limit and origin policies (D-3, D-4).
- Fatima's review of the LLM-facing descriptions (D-5).
- Website docs page is out of scope (separate story, per the story lock).

## Decision items

| # | Question | Recommendation | Your decision |
|---|----------|----------------|---------------|
| D-1 | Ratify the MCP delivery decision in `ARCHITECTURE.md` §1.0: official Kotlin SDK, embedded `adapters/mcp` driving adapter, **stateless** Streamable HTTP at `/mcp`, public read-only, rate-limited. | Approve | pending |
| D-2 | Confirm story placement: epic M2 Agent & API Surface (207), iteration M2 Sprint 1 — Agent API (MCP) (208), first in "Ready for Development". Prioritization docs were not discoverable. | Keep as created; Adnan confirms | pending |
| D-3 | Per-client rate-limit budget: default 60 requests/minute per client IP, bounded bucket map (100k keys), proxy trust via the existing `APP_RATELIMIT_TRUST_PROXY`. | Approve defaults; Omar may tune | pending |
| D-4 | Origin policy: allow-list (`APP_MCP_ALLOWED_ORIGINS`), default empty so no browser origin is accepted in prod; dev adds the Inspector origin `http://localhost:6274`. Alternative: allow any origin (public read-only data). | Allow-list; deny-by-default | pending |
| D-5 | Tool names, schemas and LLM-facing descriptions as drafted in `07` §7.3 (halal terms, trust language, data-not-instructions warning). | Approve; Fatima rewords | pending |
| D-6 | Extract shared validation (`ListingSearchRequestParser`) and the certificate-visibility rule (`CertificateFacts`) into `:application`, with a behaviour-preserving `web-api` refactor, so REST and MCP cannot drift. | Approve | pending |
| D-7 | Include `halalItems` in MCP detail only (REST detail unchanged) so agents can answer "which items are halal?". | Approve | pending |
| D-8 | Rate limiter placement: new `Bucket4jMcpRateLimiter` in `:mcp` (~60 lines, same Bucket4j pattern) rather than a new shared module. | Approve; extract later if a third consumer appears | pending |
| D-9 | Model the AI agent client as a **supporting external actor** only (do not add a README persona). | Approve; add a persona later if the product wants one | pending |
| D-10 | Public base URLs: `APP_MCP_PUBLIC_WEB_BASE_URL` for listing links and `APP_MCP_PUBLIC_API_BASE_URL` for image/certificate links; dev defaults `http://localhost:5173` / `http://localhost:8080`; prod requires https and rejects defaults. | Approve key names and guard; founder confirms prod values before deploy | pending |
| D-11 | If the SDK helper cannot coexist with the app's `ContentNegotiation`, fall back to a dedicated localhost embedded server behind the tunnel (documented last resort), keeping the same application services and ports. | Accept fallback only if the spike proves coexistence impossible | pending |

## Approval

Approved by: `<pending>`
Date:
Design commit:
Scope: phases 1–7 for SC-209
Decision items resolved: `<pending>`

> **Design approval requested.** Reply `approved` to start implementation, or
> name the diagram and the change.
