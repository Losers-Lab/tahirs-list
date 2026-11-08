---
name: Tahir's List Feature Workflow
description: "Diagram-first feature delivery for Tahir's List: domain model, use cases, use case diagram, black-box and white-box sequences, system and complete class diagrams, founder walkthrough for approval, then TDD, PR review to convergence, merge, and deploy to dev.tahirslist.com. Use only when explicitly asked. Never for typo, copy, colour, dependency, or chore changes."
metadata:
  opencode/autoinvoke: false
---

# Tahir's List Feature Workflow

Canonical: `docs/process/feature-workflow.md` (ratified, advisory). Read it for
templates and rationale. This file is the checklist.

## When

Run on `/feature <STORY>`, `@tahirs-feature-workflow`, or an explicit request for
the feature workflow. Do **not** run for typo, copy, colour, dependency, or chore
changes — say so in one line and offer the lightweight path (fix + regression test
+ review + deploy check).

## The one rule that matters

**The design is the diagrams.** Prose explains only what a diagram cannot show:
the use case narrative, invariants, deltas, open questions. Everything else is a
caption. Outside diagrams, tables, and use cases, an artifact must read in under
a minute.

## Diagram conventions

- **Actors left.** Everything outside the system (user, agent client, Google
  Places, Photon, Yelp, Gemini) is an actor on the left; a call to an actor points
  left. Black-box keeps one "Tahir's List" lifeline and puts external services to
  its left too.
- **Use the symbols.** `actor X as …` for actors (stick figure), not
  `participant`, which draws a rectangle and hides that the box is external.
  Owned stores get `participant DB@{ "type": "database" } as …` (cylinder);
  flowcharts use `[(...)]`.
- **Owned data stores right.** PostgreSQL/PostGIS, MinIO/S3 after the internal
  lifelines. White-box shows the actual SQL/object read/write — never stop at the
  repository interface.
- **No magic data.** For each external dependency, show adapter → external system
  → adapter and the failure branch (no match vs outage).

## Agreements

- No invented requirements — ACs come from Shortcut verbatim.
- No implementation code before the founder approves the design.
- Artifacts are real files in `docs/features/<STORY-ID>/`, committed and PR'd.
- Mermaid inside Markdown. One screen per diagram; split rather than shrink.
- Containerized tooling only: `./scripts/backend-test.sh`, `./scripts/frontend-test.sh`.
- Commits `[SC-###] ≤50 chars`, formatted, green only. PR body: `Author: <agent>`, `[agent]` comments.

## Step 0 — Story

`00-story.md`: link, story/epic/iteration, priority, personas, **verbatim** ACs,
edge cases, open questions with owners.

## Steps 1–7 — Design package

Every artifact starts with:
`> **Purpose:** … · **Derived from:** … · **Delta:** …`

1. **`01-domain-model.md`** — one `classDiagram` + three tables: invariants (each
   row a future test), states (`from/event/to/guard`), glossary. One line on
   `ARCHITECTURE.md` impact. No tables/columns/HTTP/framework.
2. **`02-use-cases.md`** — text allowed: actors, goal, trigger, numbered main
   scenario, alternates, exceptions → resulting state, postconditions, ACs, open
   questions. One UC per actor goal.
3. **`03-use-case-diagram.md`** — system boundary, personas, `«include»`/`«extend»`,
   new vs existing, 1:1 with the UCs.
4. **`04-blackbox/UC-xx.md`** — one per UC: actor + one "Tahir's List" lifeline +
   opaque externals. Main flow + a fragment per alternate/exception. **No
   internal names.** Number messages to match UC steps.
5. **`05-class-diagram-system.md`** — domain / application / boundary sub-diagrams
   + overview. One control class per UC, ports as interfaces, adapter stubs. No
   tables or JSON.
6. **`06-whitebox/UC-xx.md`** — 1:1 with Phase 4, only Phase 5 classes, each
   message annotated with its operation. Show transaction boundary, state
   transition, authz point, and the concrete error per exception. Missing class →
   fix Phase 5 + record delta first.
7. **`07-class-diagram-complete.md`** — layer map + three delta tables (persistence,
   API/OpenAPI, frontend), rows marked new/modified/unchanged. Names are final.

## Step 8 — Walkthrough (STOP)

Write `08-design-walkthrough.md`: one section per diagram, **three bullets** each —
**Shows / Locks in / Need**. Then the decision table, then:

> **Design approval requested.** Reply `approved`, or name the diagram and the change.

Do not write implementation code until the founder replies `approved`. Record
approval with date, design commit SHA, and resolved decision items.

## Step 9 — Work items

`09-work-items.md` table: `WI | layer | covers UC steps | tests first | depends on
| PR`. Backend first (migration → domain → application → persistence →
controller/OpenAPI → security), frontend after the contract lands. Two-way
coverage: every UC step has a WI, every Phase 7 class belongs to one.

## Step 10 — TDD

Branch `feat/<STORY>-<slug>`. Failing test → prove red → implement → green →
refactor. Formatter, `[SC-###]` commit, PR body with evidence. Update
`10-traceability.md` with the PR number immediately.

## Step 11 — Review to convergence

**Run the specialist rounds yourself.** Omar (security, static) and Yusuf (QA,
dynamic) review the PR diff and post `[omar]` / `[yusuf]` findings on the PR.
Do not ask the owner to review until both have converged — owner review is the
final gate, not the first one. The verdicts are the reviewers' own; write the
fixes, not the verdicts.

Self-review → security (static) → QA (dynamic). Blocking vs `nit:`/`optional:`.
Fix, reply per thread, re-request.

Converged when: no blocking threads; every AC proven; traceability complete;
suites green on head; `Verdict: APPROVE` from **both** Omar and Yusuf recorded on
the PR; **two consecutive clean rounds**; diagrams updated; deferrals are
Shortcut stories.

Three rounds without convergence → escalate with a one-paragraph summary. That
asks the owner for a decision, not a code review; merge still needs both
`Verdict: APPROVE` verdicts.

## Step 12 — Owner review, merge, deploy

Only a converged PR goes to the owner. The owner reviews the diff plus the
recorded Omar/Yusuf verdicts and approves or requests changes. Merge only after
that approval, then push to `main` triggers the webhook → dev stack (there is no
CI; record test output as evidence). Verify on
`https://dev.tahirslist.com`: 200 + health UP, every in-scope UC main flow, one
exception path, no new log errors, migration applied, uploads/auth if touched.
Record evidence in `10-traceability.md`. Roll back with a revert PR, never a
force-push.

## Report honestly

Say what was verified and what was not. Name any skipped phase and why. Unresolved
stays unresolved and is escalated.
