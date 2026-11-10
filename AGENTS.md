# AGENTS.md — Halal Food Finder Engineering Rules & Context

This file provides project-wide engineering rules and context. It is intended to
be read by both human engineers and AI agents working in this repository (it is
portable across Hermes, Claude Code, Codex, etc.).

> Bismillah. This is a Muslim software project. Treat the accuracy, integrity,
> and verification of halal information as a communal trust (amanah). Report
> status, progress, and defects honestly — never exaggerate or hide known
> issues.

## Sources of truth

- **Shortcut** (`halal-food-finder` workspace) is the source of truth for
  **product requirements, roadmap, epics, stories, iterations, and project
  management.** The most relevant Shortcut documents:
  - *Product Requirements Doc* (PRD) — the master requirements document
  - *Use Case Brief Descriptions* — persona + use-case detail per story
  - *Use Case Diagram* — actor/use-case model
  - *User Story Prioritization* — **priority = Backlog order filtered by the
    "Product" skill set**
  - *Use Case Prioritization* — **priority = Backlog order filtered by the
    "Development Process" skill set**
  - *Development Process* — the team's workflow (see below)
- **This Git repository** is the source of truth for **implementation**.

**Do not invent product requirements.** When a requirement is not known, consult
Shortcut first. When it is genuinely unresolved, mark it as unresolved rather
than inventing an answer, and flag it to the product/engineering manager.

## Development process (from Shortcut doc "Development Process")

The team's agreed workflow, per feature, in order:

1. **Develop User Story**
2. **Develop Use Case**
3. **Update Use Case Diagram**
4. **TDD for the backend server** (test-driven development; tests first)
5. **Front-end development**
6. **Close the loop in Shortcut** — after the merge and the dev verification,
   move the story to **Completed** and record the evidence on the story itself
   (merge commit, PR, dev checks, suite counts). Merging the PR is not the end
   of the story; a story still sitting in "In Development" after merge is an
   open process defect.

Follow this order. Backend work is test-driven; do not write backend
implementation before its failing tests unless explicitly directed otherwise.

## Product at a glance

Halal Food Finder helps Muslims find halal food, with a **hand-cut filter**
(hand-cut, not hand-cut, or unknown — there is no counter-category) and a
**formal verification process** for restaurants. Core pillars (from the PRD):

- Searchable restaurant listings by location; filters for cutting method,
  price, cuisine (with AND/OR chaining), rating, and distance.
- Listing-first model: anyone can add a restaurant (starts **unverified**);
  owners claim it by submitting proof of ownership + a photo of their halal
  certification.
- Verification assisted by an **image-recognition model**, reviewed by a human
  **Verification Committee** in early stages; target ~1 week turnaround.
- **Verified certification display** on restaurant pages.
- **Browser extensions** for Google and Yelp surfacing our listing/verification
  status.

### Personas

U = User, RO = Restaurant Owner, VRO = Verified Restaurant Owner,
VC = Verification Committee, IC = Issues Committee, ET = Extension Trigger.
See README.md for the full table.

### Edge cases (documented in PRD — requirements may depend on these)

- Multi-cuisine filtering needs AND/OR chaining (default logic: **OR**).
- Partial-halal restaurants/handling of partially halal menus is unresolved.
- Certification expiry/revocation handling is unresolved.
- Menu items and analytics date-range customizability are deferred features.

## Architecture

See [ARCHITECTURE.md](./ARCHITECTURE.md). The domain model (User /
RestaurantOwner / VerifiedRestaurantOwner, Restaurant ↔ HalalCertification,
RestaurantListing → Verified/Unverified, committee review) is agreed. The
**technical stack is NOT yet decided** — see the "Unresolved decisions" section
of ARCHITECTURE.md. Do not assume a stack; propose/confirm it before
introducing frameworks, languages, or databases.

## Containerized execution (MANDATORY — founder directive)

**The host machine has NO language toolchains — do not install or assume any.**
No JDK, no Gradle, no Node, no npm on the host. **Docker is the only build
runtime.** All builds, tests, and tool runs happen inside containers. Never run
`apt install`/`sdkman`/`brew install` for a language runtime; if a needed
toolchain is missing, that's a signal to containerize, not to install.

### Build & test (the only supported paths)

- **Backend:** `./scripts/backend-test.sh` — runs Gradle build + tests in
  `eclipse-temurin:21-jdk`. `./scripts/backend-test.sh test-only` for tests only.
  No JDK/Gradle needed on the machine.
- **Frontend (SvelteKit 3 + Vite 8):** `./scripts/frontend-test.sh` — frozen
  `pnpm install` + `svelte-check` + `vitest run` + `vite build` in a
  `node:24-slim` container. `./scripts/frontend-test.sh dev-install` allows
  lockfile drift; `test-only` runs check + tests. `./scripts/frontend-dev.sh`
  serves the dev app on `:5173`. The script caps container memory/CPU and the
  Node heap; Playwright is deliberately not in the default run.
- Dependencies are cached in **named volumes** (`tahirs-list-gradle-cache`,
  `tahirs-list-pnpm-store`, `tahirs-list-frontend-node_modules`) — never inside
  the repo tree, never on the host.

### Testcontainers-in-Docker notes (backend)

The build container mounts `/var/run/docker.sock` so Testcontainers can manage
**sibling** containers. This requires (baked into the script):

- `TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal` +
  `--add-host host.docker.internal:host-gateway` — sibling containers bind on
  the host; the build container reaches them via the gateway alias (else:
  "Connection to 172.17.0.1 refused").
- `TESTCONTAINERS_RYUK_DISABLED=true` — Ryuk's reaper has the same
  cross-container networking problem.

**Security note:** the docker.sock mount is host-root-equivalent. Accepted
deliberately for our own build/test in a trusted repo; do NOT extend this
pattern to running untrusted code.

### Adding tooling

New dev tooling (linters, generators, migration tools) must also run in
containers — either as a one-off `docker run` or as a compose service. If you
find yourself reaching for `apt`/`pip`/`npm install -g` on the host, stop.

## MCP tool usage (registered in `opencode.json`)

Project-scoped MCP servers are registered in `opencode.json` at the repo root
and can be toggled (without deletion) in OpenChamber **Settings → MCP**. Follow
the rules below; note that `svelte` and `shadcn-svelte` are registered but
start **disabled** — enable them for SvelteKit work and disable them again
afterwards to keep context lean.

- **`context7`** (always on): use for general third-party documentation lookups
  when the version you need matters.
- **`shortcut`** (always on): Shortcut is the source of truth for requirements,
  epics, and stories (see "Sources of truth" above). Consult it before starting
  or estimating any story rather than working from memory.
- **`kotlin-lib-mcp`** (on during Ktor/backend epics): **before writing or
  modifying backend Kotlin code that calls a third-party JVM library** —
  especially adapter/integration code (storage, mail, geocoding, image
  recognition, DB drivers) — look up the library's current API and correct
  usage via `kotlin-lib-mcp`. Do not write adapter code against APIs recalled
  from memory.
- **`svelte`** (on during SvelteKit phases): **before creating or rewriting
  frontend components, routes, load functions, or stores**, consult the live
  Svelte/SvelteKit documentation MCP to confirm the version-correct idiom
  (runes, `$app/state`, etc.) instead of patterns that may be outdated.
- **`basic-memory`** (always on, project default `tahirs-list-kb`): the shared
  knowledge base lives in [`Losers-Lab/tahirs-list-kb`](https://github.com/Losers-Lab/tahirs-list-kb)
  as an OKF v0.2 bundle. **Division of labor:** read and search cross-cutting
  knowledge (product, process, architecture goals, operations, QA-conventions,
  security, design decisions, research, roadmap, work ledger) through the
  Basic Memory tools instead of guessing; write *new* cross-cutting knowledge
  there as structured notes (typed observations + relations), never into this
  repo's `docs/`; keep per-feature workflow artifacts in `docs/features/SC-*`
  here. Validate bundle conformance with `basic-memory okf check` before
  claiming a KB change is complete. KB edits are written in place with the
  Basic Memory tools (`bm_write` / `bm_edit`) — no branch, PR, or review round
  for a KB note; the PR flow governs this repo's code and docs, not the KB.
- **`shadcn-svelte`** (on while implementing SvelteKit UI slices): use it to
  add/verify shadcn-svelte components rather than hand-rolling equivalents. The
  legacy Next.js app was removed in the SvelteKit migration (recover it from
  git history if you need to compare behavior). Turn the server off when the UI
  slice is done.

If a task requires touching files in a disabled server's domain (e.g. frontend
components while `svelte` is off), state the mismatch and ask to enable it
rather than proceeding with unverified assumptions.

### Memory discipline (MANDATORY — founder directive)

The `basic-memory` MCP (KB project `tahirs-list-kb`) is the durable memory of
this project. Every session — human-prompted or scheduled, first prompt or
fiftieth — follows this discipline **without being asked**:

**Read first (anything beyond a trivial edit):**

1. Before planning or answering a product/process/architecture/security/QA/
   design question, search the KB (`bm_search`/semantic grep) and follow
   relevant relations. If the KB returns nothing relevant, say so explicitly —
   silence from the KB is information, not permission to guess.
2. Before writing or changing cross-cutting rules, architecture decisions, QA
   conventions, or design tokens: read the current KB note for that area. You
   amend, not duplicate.
3. When the user references a past event/decision ("the tokens review", "the
   certifier spike"): the KB's `work/`, `risks/`, and `research/` notes are the
   record of record. Read before asking the user to repeat it.

**Write back (before ending the turn / session):**

4. Any reusable non-task knowledge produced in a session — a decision and its
   reason, a rejected alternative, a gotcha, a framework/library quirk, test
   or probe evidence, environment facts — must reach the KB as a structured
   note (typed observations + relations) **in the same turn it was learned**,
   not "later".
5. A default-skipped write needs an explicit reason: "nothing worth keeping"
   is stated in the outro, never assumed.
6. Before claiming KB work done: `basic-memory okf check` passes and inbound
   links still resolve.
7. **Push the KB after writing it** (founder, 2026-11-09). The `basic-memory`
   MCP writes files into the local `tahirs-list-kb` checkout and never runs
   git, so an uncommitted note exists on one machine only and is invisible to
   everyone else. After any `bm_write`/`bm_edit`: run
   `uvx --python 3.12 basic-memory okf check .` in that repo, then
   `git add -A && git commit && git push origin main` — **no PR, no branch**.
   Other sessions' notes may be sitting uncommitted there too; include them
   rather than leaving them stranded.

**Enforcement:**

- Sessions cite which KB notes they read (paths) and which they wrote when
  reporting status. A status report without a memory line is incomplete.
- A KB note is delivered when it is on `origin/main` of `tahirs-list-kb`, not
  when the MCP tool returned success.
- Any convergence decision made *without* consulting the KB is a process bug
  to be flagged by reviewers, same as a skipped PR review.

## Engineering ground rules

- **Hand-cut vocabulary rule (product-wide, all layers).** Tahir's List has
  exactly one cutting attribute: **hand-cut**. A certifier-backed row is
  `TRUE` only when the certifier declares hand-cut, and `FALSE` otherwise
  (including when the certifier is silent); `NULL` is reserved for rows with no
  certifier claim. Never introduce a counter-category, antonym, or
  alternative-method label for hand-cut anywhere: UI copy, data vocabularies,
  enum values, column names, test names, docs, commit messages, or PR text.
  Verification and hand-cut are independent: a recognized certifier's tier
  drives `VERIFIED`; that certifier's own declaration drives hand-cut. This
  rule binds every future agent and every surface, not only ingestion.
- **Work in small, reviewable increments.** Prefer clear tasks with explicit
  acceptance criteria tied to Shortcut stories.
- **TDD for the backend** (per the Development Process doc). Keep tests runnable
  and green before declaring work done.
- **No invented requirements or architecture.** Derive from Shortcut / agreed
  docs; mark unknowns as unknown.
- **Verification before claiming done:** a change is only "done" when its
  acceptance criteria are verified (tests pass, behavior confirmed), not when
  code merely compiles.
- **Commit hygiene (MANDATORY — founder directive):**
  - **Incremental commits if and ONLY if the build is passing.** Never commit
    on a red build; fix or isolate first.
  - **Pre-commit action:** always run project formatting before committing, if
    the project has a formatter configured (run it in the containerized toolchain).
  - **Prefix:** every incremental commit uses `[<STORY>]` as its prefix, where
    `<STORY>` is the Shortcut story the work belongs to (e.g. `[SC-131]`).
  - **Message standards:** max 50 characters (excluding the prefix), capitalize
    the first letter, no trailing period, imperative mood (e.g. `Add feature`,
    `Fix bug`). Full: `[SC-131] Add JWT verification filter`.
  - Keep commits focused and logically separated; write clear messages.
- **PR-based review flow (MANDATORY — founder directive, all agents and humans):**
  - **Every** feature/fix goes: branch → push → **open a GitHub PR** → review
    approval → merge to `main`. Direct pushes/merges to `main` from feature
    branches without a PR are not allowed.
  - **📛 PR & comment attribution (MANDATORY — founder directive):** commits
    intentionally attribute to the shared account (Arham4) — **do NOT change
    that**, and keep the existing PR title convention (`[SC-###] ...`, no
    agent prefix). Instead, the **acting specialist must be identified in the
    PR description and in each PR comment / review comment**: state the acting
    agent's profile name (hamza, maryam, fatima, omar, yusuf, aisha, adnan)
    in the PR body (e.g. an "Author: hamza" line or `<!-- attribution:
    hamza -->` marker) and open each comment with `[hamza] ...`. If a human
    wrote it, use the human's name. This makes PR authorship auditable on
    GitHub (who made it / who commented) even though git author and PR titles
    keep their existing style.
  - **Reviews are PR artifacts:** reviewers (Omar for security/code review,
    Yusuf for QA) post their findings **as PR review comments** on the PR itself
    — not only in task-manager comments. The PR diff + review must be auditable
    on GitHub.
  - **Review order is fixed:** the agent runs the Omar/Yusuf rounds itself until
    they converge, then asks the owner to review. Never put an unconverged PR in
    front of the owner, and never treat owner approval as a substitute for the
    specialist rounds. Both `Verdict: APPROVE` verdicts (`[omar]`, `[yusuf]`)
    must be on the PR before the owner is asked. When rounds stall, escalate to
    the owner **for a decision** (see the three-round rule in the process doc);
    that escalation is not an owner code review, so "never show the owner an
    unconverged PR **for review**" still holds.
  - **No merge without an approving review** recorded on the PR. Security-
    sensitive work (auth, trust, integrations) additionally requires Omar's
    explicit approval.
  - CI/build status must be green on the PR head before merging.
- **Browser pass for frontend PRs (MANDATORY — founder directive, 2026-11-10):**
  every PR that touches `frontend/` must be exercised in a real browser before
  merge, and the PR must show screenshots of the changed states. Component/jsdom
  tests do not count: they cannot see hydration, CSS, native `<dialog>`, the
  image pipeline, or the app's CSP/HSTS behaviour. Run Playwright (or Puppeteer)
  in a container against a locally built stack — never against
  `dev.tahirslist.com` — keep the script under `e2e/` so the pass is repeatable,
  and save screenshots under `docs/features/<STORY>/screenshots/` (embedded in a
  PR comment). The script must assert the ACs and exit non-zero on mismatch: a
  demo that always passes is not a gate. `e2e/README.md` has the exact
  invocation (ports, host mapping, admin promotion, known pitfalls).
- **Security review division of labor (founder-ratified):**
  - **Omar (Security Reviewer — static, code-level):** reviews diffs/PRs for
    vulnerability classes; approves/blocks PRs on security grounds; defines
    security requirements, threat models, and attack cases. Does NOT run
    exploit scripts or own test tooling.
  - **Yusuf (QA / Adversarial Testing — dynamic, runtime):** verifies
    acceptance criteria against real behavior; owns the "break it" pass
    (running app in containers, executing Omar's attack cases); owns QA
    conventions and regression checks. Reports findings; does NOT adjudicate
    security risk — Omar adjudicates.
  - **Handshake:** Omar defines what to attack and why → Yusuf executes and
    reports → Omar adjudicates (real risk vs. accepted).
- **Honesty in status reporting (amanah):** report real results; surface
  blockers and unknowns rather than papering over them.

## Contact / coordination

- Product & Engineering Manager: Adnan (coordinates requirements, engineering,
  QA, security review, and escalation to the founder).
- Specialist team: Fatima (design/UX), Maryam (frontend), Hamza (backend/DB),
  Aisha (research/data), Yusuf (QA), Omar (security/code review).

When a decision materially affects architecture or the product, consult the
appropriate specialist and involve the founder rather than deciding silently.
