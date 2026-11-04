---
type: handoff-document
title: "03 — Development Process and Engineering Rules"
description: "The mandated workflow, TDD requirement, commit and PR conventions, review division of labor, and how the Kanban execution loop actually worked."
tags: [tahirs-list, process, tdd, git, pr, code-review, kanban, workflow]
status: active
generated: 2026-11-02
generated_by: adnan
verified: false
sources:
  - resource: AGENTS.md
    title: "AGENTS — engineering rules and context"
  - resource: ARCHITECTURE.md
    title: "ARCHITECTURE §1.4 — engineering process"
  - resource: ~/.hermes/profiles/adnan/scripts/hff_check.sh
    title: "Coordination gate script (action flag logic)"
  - resource: ~/.hermes/kanban/boards/halal-food-finder/kanban.db
    title: "Kanban task history and block reasons"
---

# 03 — Development Process and Engineering Rules

This document describes how work was supposed to flow. It is the most important
document to follow if you want the next change to be reviewable and safe. It is
also where most of the async-model friction came from, so read the "what to keep
and what to drop" section at the end.

## Source-of-truth split

**Established** from AGENTS.md and the founder's instructions:

- **Shortcut** (`app.shortcut.com/halal-food-finder`) owns product requirements,
  roadmap, epics, stories, iterations, priorities, product decisions, status, and
  product discussion. The authoritative documents there are the **Product
  Requirements Doc**, **Use Case Brief Descriptions**, **Use Case Diagram**,
  **User Story Prioritization** (priority = backlog order filtered by the
  "Product" skill set), **Use Case Prioritization** (filtered by "Development
  Process"), and the **Development Process** doc.
- **Git** owns source code, tests, migrations, configuration, implementation
  history, and pull requests.
- **Hermes Kanban** owns execution: what was dispatched, to whom, and what
  happened. It is the queue, not the product truth.

Keeping these aligned was my job. The alignment drifted when the queue emptied and
nobody reconciled Shortcut. That is the first thing to fix (see
[00](00-executive-handoff.md)).

## The mandated development order

**Established** from the Shortcut "Development Process" doc. Per feature, in order:

1. **Develop User Story**
2. **Develop Use Case**
3. **Update Use Case Diagram**
4. **TDD for the backend server** (tests first)
5. **Front-end development**

Implications:

- **Backend is test-driven.** Write the failing test first. Do not write backend
  implementation before its failing tests unless explicitly directed otherwise.
- Requirements and use cases are authored in Shortcut **before** implementation of
  a substantial feature. A story that exists only in a Kanban card is not a
  requirement.

This order is not decoration. The verification vertical slice plan
(`.hermes/plans/2026-08-29_mvp-trust-verification-vertical-slice.md`) followed it
explicitly: tasks 1–4 backend TDD, task 5 frontend. If you find yourself writing
frontend first, you have skipped a step and will pay for it in rework.

## Engineering ground rules

**Established** from AGENTS.md. These are founder directives, not suggestions.

### Work in small, reviewable increments

Prefer clear tasks with explicit acceptance criteria tied to Shortcut stories. One
well-defined task beats several speculative ones. This was the standing execution
policy: **WIP = 1**.

### No invented requirements or architecture

Derive from Shortcut or agreed docs. Mark unknowns as unknown. If a requirement is
missing, ask; do not fill the gap with a plausible guess and ship it.

### Verification before claiming done

A change is "done" only when its acceptance criteria are verified — tests pass and
behavior is confirmed. Code that compiles is not done. A demo on localhost is not
done. A PR that "should work" is not done.

### Commit hygiene

- **Incremental commits if and only if the build is passing.** Never commit on a
  red build. Fix or isolate first.
- **Run the formatter before committing** if the project has one configured (run it
  in the containerized toolchain).
- **Prefix every incremental commit with `[<STORY>]`**, e.g. `[SC-131]`. The story
  is the Shortcut story the work belongs to.
- **Message standards:** max 50 characters excluding the prefix, capitalize the
  first word, no trailing period, imperative mood. Example:
  `[SC-131] Add JWT verification filter`.
- Keep commits focused and logically separated.

### PR-based review flow

- **Every** feature or fix goes branch → push → **open a GitHub PR** → merge to
  `main` only after review. Direct pushes or merges to `main` from feature branches
  without a PR are not allowed.
- **CI/build status must be green on the PR head** before merging.
- **No merge without an approving review recorded on the PR.**
- Security-sensitive work (auth, trust, integrations) additionally requires
  **Omar's explicit approval**.

### PR and comment attribution

This is unusual and important. Read it carefully.

**Established.** Commits intentionally attribute to the shared GitHub account
(**Arham4**). Do **not** change that. Keep the PR title convention
(`[SC-###] ...`, no agent prefix). Instead, the acting specialist must be
identified **inside the PR**:

- State the acting agent's profile name in the **PR body** (e.g. an `Author: hamza`
  line or `<!-- attribution: hamza -->` marker).
- Open each **comment or review comment** with `[hamza] ...`.
- If a human wrote it, use the human's name.

This exists because git author and PR titles are shared, so attribution had to live
somewhere auditable. It works, but it is easy to forget. If you drop the
convention, PR authorship becomes unattributable.

### The shared-account approval workaround

**Established.** Because the reviewer and author are the same GitHub account
(Arham4), GitHub blocks a formal `APPROVED` review on your own PR. The team's
workaround, encoded in the coordination gate prompt:

> An open PR **is** merge-approved when an attributed reviewer comment (starting
> `[omar]` or `[yusuf]`) states **"Verdict: APPROVE"** — treat that as a valid
> approving review even if `reviewDecision` is empty or `COMMENTED`.

Still required: the PR must be **MERGEABLE** (no conflict) and the build must be
green. If a PR is conflicting, rebase or merge `main` in; do not block silently.

If you move to a proper multi-account setup, this workaround can be deleted. Until
then, it is the only way to satisfy "no merge without approval" on a single account.

### Security review division of labor

**Established** (founder-ratified):

- **Omar — Security Reviewer (static, code-level).** Reviews diffs and PRs for
  vulnerability classes; approves or blocks PRs on security grounds; defines
  security requirements, threat models, and attack cases. Does **not** run exploit
  scripts or own test tooling.
- **Yusuf — QA / Adversarial Testing (dynamic, runtime).** Verifies acceptance
  criteria against real behavior; owns the "break it" pass (running the app in
  containers, executing Omar's attack cases); owns QA conventions and regression
  checks. Reports findings; does **not** adjudicate security risk — Omar does.
- **Handshake:** Omar defines what to attack and why → Yusuf executes and reports →
  Omar adjudicates (real risk vs. accepted).

This split is genuinely good and worth preserving. It prevents the common failure
where the person who wrote the code also declares it secure.

### Honesty in status reporting (amanah)

Report real results. Surface blockers and unknowns rather than papering over them.
Do not claim success without verifying it. This was stated as a religious and
professional obligation, and it is the rule most worth carrying forward.

## How the execution loop actually worked

**Established** from the cron job definitions and `hff_check.sh`.

There were four scheduled jobs:

| Job | Schedule | Role |
|-----|----------|------|
| HFF coordination gate | Every 30 min, 00:00 and 09:00–23:00 | Merge review, dispatch, unblock |
| HFF sprint plan + execute | Daily 09:00 and 17:00 | Sprint lifecycle, planning, opening/closing |
| HFF coordination gate (overnight) | 03:00 and 06:00 | Same as gate, quiet hours |
| HFF deploy watchdog | Every 15 min | Self-heal the preview stack |

### The action-gated design

The gate is deliberately **deterministic and cheap**. `hff_check.sh` prints a
fingerprint containing:

- `action=0|1` — whether a coordination action is needed
- `prs=<sorted open PR numbers>` — so the fingerprint changes when a PR opens,
  merges, or closes
- `reviews=<per-PR reviewDecision:states:last-review-timestamp>` — so the
  fingerprint changes when a review lands on an **already-open** PR

The Hermes cron `monitor` gate compares the output to the previous tick and wakes
the LLM **only when the fingerprint changes**. This prevents the agent from waking
every 30 minutes to say "nothing to do."

The action logic, in priority order:

1. If any PR is open → `action=1` (merge review).
2. Else if ready cards exist and no worker is running → `action=1` (dispatch).
3. Else if blocked cards exist → `action=1` (unblock).
4. Else `action=0`.

Two bugs were fixed during development and are documented in the script's comments;
both are worth knowing because they illustrate the failure mode:

- **Count-vs-list bug:** emitting only a count meant a *new* PR arriving while
  another was already open did not change the hash, so the gate never woke. Fixed
  by emitting the sorted PR list.
- **Review-on-open-PR bug:** the PR list does not change when a review lands on an
  already-open PR, so the gate never re-evaluated. Fixed by emitting per-PR review
  state and timestamps.

### The agent's job in a tick

The gate prompt told the agent to:

1. Read the injected diff; if it is only cosmetic, treat as nothing to do.
2. Run `hermes kanban list`.
3. **merge_review:** verify and merge open PRs (with the approval workaround above).
4. **dispatch:** dispatch the next ready card (`hermes kanban dispatch`), WIP=1.
5. **unblock:** clear resolved blockers.
6. If nothing was done or nothing new, respond with **exactly** `[SILENT]`.
7. Only report if something actually happened (merged/dispatched/unblocked/synced).

The `[SILENT]` convention is important: it kept the Discord channel quiet so that a
message meant something. If you keep any automation, keep that property.

### The sprint job

The daily 09:00/17:00 job owned the sprint lifecycle end-to-end with full autonomy:

- Diagnose sprint state **from work, not the calendar**. An iteration end date alone
  is not evidence the sprint is alive.
- A sprint is **exhausted** when no card is running and no ready/todo card is
  actually executable (founder-gated cards do not count).
- A sprint is **healthy** when there is running work or executable ready cards.
- If healthy: do nothing, report honestly, stop.
- If exhausted or absent: close the old iteration, create a new one named
  `M1 Sprint N — <Theme>` with a real ~7-day window, and plan a coherent milestone
  across backend, frontend, edge cases, and review gates.
- Source scope **only** from real Shortcut stories in backlog epics that are not
  done. Never invent features. Own-initiated tickets are bug fixes only.
- Reconcile against the repo before queueing: inspect what is already built so you
  do not re-card done work. Verify the foundation is green before dispatching on
  top of it.
- Every story goes in its epic **and** its iteration (founder rule).

### Iteration budget behaviour

**Observed.** Kanban tasks carry a max-iteration budget. When it is exhausted the
card is blocked with a message like *"Iteration budget exhausted (40/40) — task
could not complete within the allowed iterations."* This is a worker-resource
failure, not a product blocker. The blocked card `t_63bc37f1` failed this way.

If you see this, the fix is to increase the budget or split the card — not to
investigate the product.

## What to keep and what to drop

You are moving to a synchronous model. My recommendation, stated as a
recommendation and not a requirement:

**Keep:**

- Shortcut as product truth; the repo as implementation truth. The split is sound.
- TDD for backend. It is the reason the backend is in good shape.
- PR-based review with green build and a recorded approval. This caught real issues.
- The attribution convention while the GitHub account is shared. Cheap and useful.
- The security/QA split between static review and dynamic break-it testing.
- The honesty rule. It is the cheapest quality mechanism you have.

**Drop or simplify:**

- The seven-persona structure, unless the founder explicitly wants to keep it. It
  produced good work but the coordination overhead is what made the project hard to
  follow. A single agent with clear domain checkpoints would be easier to manage.
- The 30-minute action gate. In a synchronous model you will know what is happening;
  a 30-minute autonomous loop is more machinery than you need. Keep the deploy
  watchdog only if you fix its liveness check (see [05](05-operations-and-infrastructure.md)).
- The 42 historical worktrees under `.worktrees/`. They are noise now. Prune them
  after confirming nothing unmerged is needed.
- The `[SILENT]` convention only if you no longer use Discord as the status channel.
  If you do, keep it — a silent channel is a feature.
