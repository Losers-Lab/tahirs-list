---
name: Tahir's List Feature Workflow
description: "Run the ratified Tahir's List feature workflow for a feature or substantial task: domain model, use cases, use case diagram, black-box and white-box sequence diagrams, system and complete class diagrams, a founder walkthrough for design approval, then TDD implementation, PR review cycles to convergence, merge, and deploy to dev.tahirslist.com. Use only when the user explicitly asks for the feature workflow. Never for typo, copy, colour, dependency, or chore changes."
metadata:
  opencode/autoinvoke: false
---

# Tahir's List Feature Workflow

Canonical document: `docs/process/feature-workflow.md` (ratified by the founder,
advisory). Read it when you need the detail, the templates, or the rationale.
This file is the operative checklist.

## When to run this

**Run it** when the user invokes `@tahirs-feature-workflow`, `/feature`, or asks
for the feature workflow on a feature or a substantial behavior change.

**Do not run it** for typo fixes, copy edits, colour tweaks, dependency bumps,
chores, or small defect fixes. If the user invoked it on something that is
obviously one of those, say so in one sentence and ask whether they want the
lightweight path instead (fix + regression test + PR review + deploy).

If the user asks for a design change mid-flight after approval, re-run only the
affected phase and redraw downstream diagrams. A *new requirement* stops the
workflow and goes to Shortcut first.

## Working agreements

- **No invented requirements.** The story and its acceptance criteria come from
  Shortcut. If Shortcut is unavailable, ask for the story text. Mark unknowns as
  unknowns with an owner.
- **Design before code.** No implementation file is touched before the founder
  approves the design.
- **Artifacts are real files** under `docs/features/<STORY-ID>/`, committed on a
  branch and opened as a docs PR. A diagram in chat only does not exist.
- **Mermaid inside Markdown** so GitHub renders it in PR review. PlantUML only
  by exception, rendered in a container.
- **Containerized tooling only.** `./scripts/backend-test.sh`,
  `./scripts/frontend-test.sh`. No host toolchains.
- **Commits:** `[SC-###] Imperative sentence`, ≤50 chars, formatted, green build
  only. PR attribution: `Author: <agent>` in the body, `[agent]` on comments.

## Step 0 — Confirm the story

Create `00-story.md`: Shortcut link, story ID, epic, iteration, priority,
personas, **verbatim** acceptance criteria, edge cases, open unknowns with
owners. If the story is not in an epic and an iteration, flag it before designing.

## Steps 1–7 — Build the design package

Produce in this order. Each file self-checks against the checklist in
`docs/process/feature-workflow.md` Appendix D before moving on.

1. **`01-domain-model.md`** — aggregates, entities, value objects, invariants,
   state machines, glossary, and an explicit statement of whether
   `ARCHITECTURE.md` §1.2 changes. Technology-free: no tables, no HTTP, no
   framework names.
2. **`02-use-cases.md`** — one section per `UC-xx`: actors, preconditions,
   trigger, numbered main scenario, alternate flows, exception flows with the
   resulting state, postconditions, business rules, linked ACs, open questions.
   One use case per actor goal, not per screen or endpoint.
3. **`03-use-case-diagram.md`** — system boundary, personas, `«include»` /
   `«extend»`, new vs existing, 1:1 with the use cases.
4. **`04-blackbox/UC-xx-<slug>.md`** — one per use case: actor(s) + a single
   "Tahir's List" lifeline + opaque external services. **No internal names.**
   Number messages to match the use case steps. Cover every alternate and
   exception flow.
5. **`05-class-diagram-system.md`** — entity classes, exactly one control class
   per use case, boundary classes, ports as interfaces, adapter stubs. No tables
   or framework detail. Every use case step must resolve to an operation.
6. **`06-whitebox/UC-xx-<slug>.md`** — 1:1 with the black-box files, using only
   classes/interfaces from step 5. Show transaction boundaries, aggregate state
   transitions, authorization placement, persistence ports, and the concrete
   error for each exception flow. If you need a missing class, update step 5
   first and record the delta.
7. **`07-class-diagram-complete.md`** — the ultimate design: backend modules
   (`:domain`, `:application`, `:persistence`, `:bootstrap`, adapters), migrations
   by name, API/OpenAPI delta, frontend routes/components/stores/client, UI
   states, config/env. Mark new / modified / unchanged. Naming is final.

## Step 8 — Walkthrough and approval (STOP HERE)

Write `08-design-walkthrough.md` and present the diagrams to the founder in
order. For each diagram give four short blocks:

- **What it shows**
- **What it locks in**
- **What I need from you** — approve / choose X vs Y (with a recommendation) /
  correct
- **Known gaps**

Then list the decision items in a numbered table, and end with:

> **Design approval requested.** Reply `approved` to start implementation, or
> name the diagram and the change.

**Do not write implementation code until the founder replies `approved`.** Record
the approval in `08-design-walkthrough.md` with date, design commit SHA, and the
resolved decision items.

## Step 9 — Work items

Write `09-work-items.md`: backend first (migration → domain → application →
persistence → controller/OpenAPI → security wiring), then frontend after the
contract lands. Each `WI-xx` carries its use case steps, the **names of the
failing tests to write first**, acceptance criteria, dependencies, PR scope, risk,
and review focus. Two-way coverage: every use case step has a work item, every
step-7 class belongs to exactly one.

## Step 10 — Implement with TDD

- Branch `feat/<STORY>-<slug>`, one branch per work item or small group.
- Failing test first, prove it fails for the right reason, implement, go green,
  refactor.
- Run the formatter, commit green only, `[SC-###]` prefix.
- Open the PR with story, work item, design links, acceptance criteria, evidence,
  attribution, and security/QA notes.
- Update `10-traceability.md` with the PR number immediately.

## Step 11 — Review cycles to convergence

Self-review first. Then security review (static, attack surfaces) and QA review
(dynamic, acceptance criteria, edge cases). Classify findings **blocking** or
`nit:`/`optional:`. Fix, reply to each thread, re-request review.

Converged when: no unresolved blocking thread; every AC proven by a test or
recorded manual evidence; traceability complete; full containerized suites green
on the PR head; an attributed `Verdict: APPROVE` recorded; **two consecutive
rounds with zero new blocking findings**; no behavior change without updated
diagrams; deferrals tracked as Shortcut stories.

Not converged after 3 rounds → escalate with a written summary; do not loop.

## Step 12 — Merge, deploy, verify

Merge only after convergence. Then deploy via the documented path (push to `main`
→ webhook → dev stack; no CI exists, so record the test output as evidence) and
verify **on `https://dev.tahirslist.com`**:

- [ ] dev returns 200, backend health UP
- [ ] every in-scope use case main flow exercised on dev
- [ ] one exception path exercised
- [ ] no new log errors; migration applied; uploads/auth re-checked if touched
- [ ] evidence recorded in `10-traceability.md`
- [ ] Shortcut story Done; follow-ups are stories, not TODOs

If deploy fails, follow the documented fix procedure, record the cause, and route
code fixes back through the PR loop. Roll back by reverting the merge commit via
a PR — never force-push.

## Report honestly

State what was verified and what was not. If a phase was skipped, say which and
why. If something is unresolved, it stays unresolved and is escalated.
