# 00 — Story lock: SC-209

- **Shortcut:** [SC-209 — Search for halal restaurants from AI agents (MCP server)](https://app.shortcut.com/halal-food-finder/story/209)
- **Type:** Feature
- **Epic:** [M2 Agent & API Surface (207)](https://app.shortcut.com/halal-food-finder/epic/207)
- **Iteration:** [M2 Sprint 1 — Agent API (MCP) (208)](https://app.shortcut.com/halal-food-finder/iteration/208)
- **Workflow state:** Ready for Development
- **Owner:** Arham
- **Priority:** Proposed highest in the iteration — the story was placed first in
  "Ready for Development". The Shortcut *User Story Prioritization* and
  *Use Case Prioritization* docs were **not discoverable** through the workspace
  docs search, so priority is a proposal pending confirmation (U-1).
- **Personas:** **U (User)** searches through their AI agent. The **AI agent
  client** is a supporting external actor, not yet a README persona (U-2).

> This story and its acceptance criteria were created in Shortcut by the acting
> agent (Hamza) from the founder-approved 2026-11-07 planning session, under the
> founder's explicit delegation ("you create the story in Shortcut and manage
> it"). They are reproduced **verbatim** below and are the requirement baseline
> for this design package.

## Context (from the story)

Tahir's List already has a public location search (`GET /v1/listings/search`) and
public listing detail (`GET /v1/listings/{id}`). AI agents cannot use it well: it
needs a bespoke HTTP integration and precise lat/lng. MCP (Model Context
Protocol) is the standard way to expose tools to AI agents, so a remote MCP
server lets a user add Tahir's List to Claude, Cursor, ChatGPT connectors, etc.,
and search in natural language.

This story delivers the MCP server with **search capability only** (plus listing
detail as the follow-up half of a search).

## Scope (approved in the 2026-11-07 planning session)

- **Embedded** in the existing Ktor backend: new `adapters/mcp` module (hexagonal
  driving adapter), mounted at `POST /mcp`, **Streamable HTTP, stateless**.
- **Two tools:** `search_restaurants` and `get_restaurant`.
- **Location input:** free-text `place` (geocoded server-side via the existing
  `GeocoderPort`/Photon) **or** precise `center` lat/lng + radius in miles.
- **Public anonymous** access (parity with the public REST search), with
  **per-client rate limiting** and MCP-spec Origin/CORS validation.
- **Read-only:** no listing creation/editing, no favorites, no verification
  actions.

## Out of scope (explicit)

- A public "connect your AI agent" docs page (separate story).
- API keys / usage analytics / billing.
- Write tools, resources, prompts, or completions.
- Geocode-result caching (future optimisation).

## Acceptance criteria (verbatim from SC-209)

- **AC-1 — Transport & discovery.** A remote MCP client completes an MCP session
  over Streamable HTTP against `POST /mcp` without authentication, and
  `tools/list` returns exactly `search_restaurants` and `get_restaurant`. Each
  tool declares a JSON Schema object `inputSchema` and an `outputSchema`, and a
  description that explains halal-domain terms (hand-cut, verification status,
  halal scope) for a model with no domain context.
- **AC-2 — Search by coordinates.** `search_restaurants` accepts `center`
  (lat/lng) and `radiusMiles` (> 0) and returns the listings within the
  straight-line radius, ordered by distance ascending. Each result exposes id,
  name, address parts (address/city/province/postal/country), lat, lng,
  distanceMiles, cuisine, isHandCut (`true|false|null`), halalScope
  (`FULLY_HALAL|PARTIALLY_HALAL|NOT_DISCLOSED`), isDelivery (`true|false|null`),
  verificationStatus (`VERIFIED|UNVERIFIED`), rating (number|null) and
  websiteUrl.
- **AC-3 — Search by place.** When `place` is supplied instead of `center`, the
  server geocodes it server-side via the existing geocoder port and searches
  around the resolved point. An unresolvable place returns a tool error
  (`isError=true`) telling the caller to try a nearby city or provide lat/lng. A
  geocoder outage returns a distinct, actionable error recommending lat/lng.
- **AC-4 — Filter parity.** The tool supports `handCutOnly` (extra on/off
  filter; `true` excludes unknown and not-hand-cut), `deliveryOnly`, `cuisines` +
  `cuisineLogic` (AND/OR, default OR), `minPrice`/`maxPrice` (0..Price.MAX,
  min <= max), `minRating` (0..5), `limit` (1..100, default 20), `offset`
  (>= 0) with the **same semantics as the public REST search**. Every invalid
  value returns an actionable tool error — never a protocol-level failure or 500.
- **AC-5 — Detail.** `get_restaurant` takes a listing id and returns the full
  listing detail including `crossContamination` and, for a VERIFIED listing with
  an approved certificate, the certificate facts (certifier, reviewedOn,
  expiresOn, certificateUrl) plus imageUrl. An unknown or malformed id returns a
  tool error stating the restaurant was not found, without leaking internal
  detail.
- **AC-6 — Public access & rate limiting.** The endpoint requires no JWT or API
  key. Requests beyond the configured per-client budget are rejected with HTTP
  429 **before tool execution** (no database or geocoder work for rejected
  calls). The budget is configuration-driven.
- **AC-7 — Origin/CORS conformance.** The MCP endpoint validates the `Origin`
  header as required by the MCP Streamable HTTP specification and supports
  browser-based MCP clients (e.g. MCP Inspector), while non-browser agents that
  send no `Origin` are unaffected.
- **AC-8 — No REST regression.** `GET /v1/listings/search` and
  `GET /v1/listings/{id}` behavior (status codes, bodies, ordering, validation)
  is unchanged; their existing tests pass unmodified.
- **AC-9 — Protocol robustness.** Malformed JSON-RPC, unknown methods/tools,
  missing or invalid tool arguments, and oversized request bodies produce
  spec-conformant JSON-RPC errors or `isError` tool results, with no internal
  exception text, stack traces, or server paths.
- **AC-10 — Operability.** The server advertises the correct implementation
  name/version; every tool call is logged with tool name, outcome and duration
  (no PII); the stateless endpoint requires no session state and survives
  concurrent calls.

## Edge cases (from the story)

- `place` and `center` both supplied / neither supplied.
- Ambiguous or out-of-region place; geocoder returns no match; geocoder throws.
- `radiusMiles` zero/negative/NaN; `limit` 0 or 101; `offset` negative;
  `minPrice` > `maxPrice`; `minRating` outside 0..5; blank or over-long cuisine.
- Unknown cuisine value → empty result list, not an error.
- Listings with unknown hand-cut / delivery / rating (null) — filter semantics
  match REST; results show null.
- PARTIALLY_HALAL + VERIFIED combination.
- VERIFIED listing without an approved/archived certificate → detail returns no
  certificate block (no broken URL).
- Empty result set → empty array with count 0, not an error.
- Malformed or non-existent UUID for `get_restaurant`.
- Requests with no `Origin`; with a disallowed `Origin`; CORS preflight if
  applicable.
- Concurrent stateless requests; no `Mcp-Session-Id`.
- Listing text containing prompt-injection attempts (name/address): treated as
  data; the tool description warns the model.
- Rate-limit boundary: nth allowed, n+1 rejected with 429.

## Definition of done (from the story)

- Full design package in `docs/features/` (domain model → complete class
  diagram), walked through and **approved by the founder** before any
  implementation.
- Backend TDD, containerized suites green (`./scripts/backend-test.sh`).
- PR review to convergence: Omar (security), Yusuf (QA); recorded
  `Verdict: APPROVE`.
- Merged and verified on dev.tahirslist.com; then this story is moved to Done.

## Known unknowns

| ID | Unknown | Owner | Needed by |
|----|---------|-------|-----------|
| U-1 | Which prioritization document applies, and where this story sits against it. Shortcut docs search returned no prioritization docs. | Adnan | Before implementation |
| U-2 | The README persona table has no "AI agent" actor. Confirm the agent is modelled as a supporting external actor only (not a persona). | Founder / Adnan | Design approval |
| U-3 | Final per-client rate-limit budget and Origin/CORS allow-list. | Omar | Design approval |
| U-4 | SDK protocol revision support and ContentNegotiation coexistence (spike in WI-01). | Hamza | Implementation start |
| U-5 | Production values for the public web base URL (deep links) and public API base URL (certificate/image URLs). | Founder / Adnan | Deploy |

## Story-lock checks

- [x] Story exists in Shortcut, in an epic (207) and an iteration (208).
- [x] Acceptance criteria are testable and numbered (AC-1 … AC-10).
- [x] Edge cases are listed.
- [x] Every unknown has an owner and a "needed by".
- [x] Nothing about the requirement is invented beyond the approved planning
      session; the story records where it came from.
