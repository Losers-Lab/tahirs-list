# 02 — Use cases: SC-209 (MCP search for AI agents)

Two use cases, one per actor goal. Both are **new**; the existing UI search and
view use cases are unchanged (the MCP surface reads the same model through the
same ports).

> Protocol precondition shared by both: before any use case, the agent client
> completes the MCP initialization handshake and discovers the tool catalog. That
> is protocol machinery, not a user goal, so it is a precondition rather than a
> use case; it is still exercised by AC-1 and covered by a dedicated test.

## Use-case map

| ID | Name | Primary actor | Supporting actors |
|----|------|---------------|-------------------|
| UC-01 | Search for halal restaurants (via an AI agent) | U (User) | AI agent client; Geocoding provider |
| UC-02 | View a restaurant's details (via an AI agent) | U (User) | AI agent client |

---

### UC-01 — Search for halal restaurants (via an AI agent)

- **Primary actor:** U (User)
- **Supporting actors:** AI agent client (external software acting for the user);
  Geocoding provider (external service, only when place text is used)
- **Goal:** The user, through their AI agent, finds halal restaurants near a place
  or coordinate, narrowed by halal/food filters.
- **Preconditions:**
  - The MCP endpoint is reachable and the server is healthy.
  - The agent client has completed MCP initialization and tool discovery.
  - The user has expressed what they want and where (a place, or coordinates).
- **Trigger:** The user asks their AI agent to find halal restaurants nearby.
- **Business rules:**
  - **BR-1** Radius is positive; zero/negative/non-numeric is a caller error.
  - **BR-2** Page size is 1..100 (default 20); offset is >= 0.
  - **BR-3** Results are ordered by straight-line distance ascending.
  - **BR-4** Only index-qualified listings (no cross-contamination) are returned.
  - **BR-5** Hand-cut is an extra filter: true excludes unknown and false;
    absent/false means any. There is no machine-cut concept.
  - **BR-6** Delivery follows the same tri-state pattern.
  - **BR-7** Cuisines combine with OR (default) or AND; a listing with no cuisine
    never matches a cuisine filter.
  - **BR-8** Price bounds are 0..100000 with min <= max; unknown-price listings
    never match a price filter.
  - **BR-9** Minimum rating is on the closed 0..5 scale; unknown-rating listings
    never match a rating filter.
  - **BR-10** Place text is resolved server-side; "no match" and "provider
    unavailable" are distinct outcomes.
  - **BR-11** The surface is read-only: this use case mutates nothing.
  - **BR-12** Requests are rate-limited per client before any work is done.

**Main success scenario**

1. The user asks their AI agent for halal restaurants near a place, optionally
   naming filters (hand-cut, delivery, cuisine, price, rating).
2. The AI agent client calls the search tool with place text **or** coordinates,
   a radius in miles, and the filters.
3. Tahir's List validates the radius, page and every filter value.
4. When place text was supplied, Tahir's List resolves it to a search centre.
5. Tahir's List finds index-qualified listings within the radius that match the
   filters.
6. Tahir's List orders them by straight-line distance, applies the page, and
   returns each listing with its halal-scope, hand-cut, delivery, verification
   and rating facts plus a link to the listing page.
7. The AI agent client presents the results to the user.

**Alternate flows**

- **A1 (from step 2) — coordinates supplied instead of place text:** steps 3 and
  5–7 are performed; step 4 is skipped. Postcondition: same as success.
- **A2 (from step 5) — no listing matches:** Tahir's List returns an empty page
  with count 0. Postcondition: success with an empty result; no error.
- **A3 (from step 6) — more matches than the page size:** Tahir's List returns
  the page and a next-offset hint. Postcondition: success; the agent can page.
- **A4 (from step 6) — the agent needs more detail on one result:** continue in
  UC-02 with that listing's id. Postcondition: UC-02's postcondition.

**Exception flows**

- **E1 (from step 3) — invalid argument** (radius <= 0 or not a number; page out
  of range; min price > max price; rating outside 0..5; blank or over-long
  cuisine; both or neither of place and coordinates): the returned tool result
  is an error carrying the specific reason and a correction hint. No search is
  performed; no state changes.
- **E2 (from step 4) — place has no match:** the tool result is an error telling
  the caller to try a nearby city or supply coordinates. No search is performed.
- **E3 (from step 4) — geocoding provider unavailable:** a distinct error telling
  the caller to supply coordinates instead. No search is performed.
- **E4 (from step 2) — per-client budget exhausted:** the request is refused with
  HTTP 429 before any tool work; the agent may retry after the window. No search
  is performed; no state changes.
- **E5 (from step 2) — malformed protocol request:** the agent receives a
  protocol error; no tool executes.
- **E6 (from step 2) — browser origin not allowed:** the request is refused with
  HTTP 403; no tool executes.
- **E7 (from step 5) — data store unavailable:** the agent receives a generic,
  non-internal error. Nothing is mutated.

**Postconditions**

- **Success:** the agent holds an ordered page of matching restaurants with
  halal/verification facts and listing links; **no state changed**.
- **Failure:** the agent holds a specific, actionable error or a protocol
  refusal; **no state changed**.

- **Data:** read — search index (and geocoding provider when place text is used);
  created/updated — none.
- **Acceptance criteria:** AC-1, AC-2, AC-3, AC-4, AC-6, AC-7, AC-8, AC-9, AC-10.
- **Open questions:** see the shared table below (Q-1, Q-3, Q-6, Q-7).

---

### UC-02 — View a restaurant's details (via an AI agent)

- **Primary actor:** U (User)
- **Supporting actors:** AI agent client
- **Goal:** The user, through their AI agent, gets the full trust-relevant detail
  of one restaurant found by a search.
- **Preconditions:**
  - The MCP endpoint is reachable and the server is healthy.
  - The agent client has completed MCP initialization and tool discovery.
  - The caller holds a listing id (from a search result or a listing link).
- **Trigger:** The user asks for more about a restaurant the agent found.
- **Business rules:**
  - **BR-13** The id must be a valid identifier; a malformed id is a caller error.
  - **BR-14** An unknown id is reported as "restaurant not found".
  - **BR-15** Certificate facts appear only for a verified listing whose latest
    approved review carries an archived certificate reference; otherwise the
    detail is returned without them (never a broken link).
  - **BR-16** Halal scope and verification status are reported independently.
  - **BR-17** The surface is read-only.
  - **BR-18** The request is rate-limited per client before any work.

**Main success scenario**

1. The user asks for details about a restaurant returned by a search.
2. The AI agent client calls the detail tool with the listing id.
3. Tahir's List validates the id format.
4. Tahir's List loads the listing.
5. Tahir's List derives certificate facts when the listing is verified and has
   an approved, archived certificate.
6. Tahir's List returns the full detail: address and coordinates, cuisine,
   hand-cut, delivery, halal scope and items, cross-contamination state,
   verification status, image, certificate facts when eligible, and a link to the
   listing page.
7. The AI agent client presents the detail to the user.

**Alternate flows**

- **A1 (from step 5) — unverified, or approved without an archived
  certificate:** the detail is returned without certificate facts. Postcondition:
  success; no certificate link is advertised.
- **A2 (from step 5) — partially halal listing:** the detail states the partial
  scope and the halal items; verification status is reported independently.
  Postcondition: success.

**Exception flows**

- **E1 (from step 4) — no listing with that id:** the tool result is an error
  saying the restaurant was not found. No state changes.
- **E2 (from step 3) — id is not a valid identifier:** the tool result is an
  error saying the id must be valid. No state changes.
- **E3 (from step 2) — per-client budget exhausted:** HTTP 429 before any tool
  work. No state changes.
- **E4 (from step 2) — malformed protocol request:** protocol error; no tool
  executes.
- **E5 (from step 2) — browser origin not allowed:** HTTP 403; no tool executes.
- **E6 (from step 4) — data store unavailable:** a generic, non-internal error.
  No state changes.

**Postconditions**

- **Success:** the agent holds the full trust-relevant detail; **no state
  changed**.
- **Failure:** the agent holds a specific, actionable error or a protocol
  refusal; **no state changed**.

- **Data:** read — listing, latest approved certification review; created/updated
  — none.
- **Acceptance criteria:** AC-1, AC-5, AC-6, AC-7, AC-8, AC-9, AC-10.
- **Open questions:** see the shared table below (Q-1, Q-3, Q-6, Q-7).

---

## Acceptance-criteria → use-case coverage

| AC | Covered by |
|----|-----------|
| AC-1 | Precondition + UC-01 steps 2/3; UC-02 steps 2/3 |
| AC-2 | UC-01 steps 3, 5, 6; A1 |
| AC-3 | UC-01 step 4; E2, E3 |
| AC-4 | UC-01 step 3, step 5; E1 |
| AC-5 | UC-02 steps 3–6; A1, A2; E1, E2 |
| AC-6 | UC-01 E4; UC-02 E3 |
| AC-7 | UC-01 E6; UC-02 E5 |
| AC-8 | Both use cases (no REST behavior change) |
| AC-9 | UC-01 E5; UC-02 E4; E1s |
| AC-10 | Both use cases (stateless, logged, no session) |

## Open questions raised by the use cases

| ID | Question | Owner | Needed by |
|----|----------|-------|-----------|
| Q-1 | Final per-client budget and Origin/CORS allow-list. | Omar | Design approval |
| Q-2 | Epic/iteration/priority confirmation (M2 207 / 208, first in state). | Adnan | Design approval |
| Q-3 | LLM-facing tool-description wording (halal terms, trust language). | Fatima | Before implementation |
| Q-4 | `ARCHITECTURE.md` §1.0 ratification of the MCP decision. | Founder | Design approval |
| Q-5 | SDK mounting/protocol spike outcome (see WI-01). | Hamza | Implementation start |
| Q-6 | Production base URLs for listing links and certificate/image URLs. | Founder / Adnan | Deploy |
| Q-7 | Is the AI agent a supporting actor only, or a new README persona? | Founder / Adnan | Design approval |
