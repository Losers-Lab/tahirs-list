---
type: knowledge-bundle-index
title: "Tahir's List — Engineering Handoff Bundle"
description: "Entry point and reading order for the complete handoff of Tahir's List (Halal Food Finder): product vision, architecture, development process, team, operations, work ledger, current state, roadmap, and references."
tags: [tahirs-list, halal-food-finder, handoff, index, knowledge-bundle]
status: active
generated: 2026-11-02
generated_by: adnan
verified: false
repo: .
remote: https://github.com/Losers-Lab/tahirs-list
sources:
  - resource: README.md
    title: "README — product overview"
  - resource: ARCHITECTURE.md
    title: "ARCHITECTURE — agreed architecture and open decisions"
  - resource: AGENTS.md
    title: "AGENTS — engineering rules and context"
  - resource: app.shortcut.com/halal-food-finder
    title: "Shortcut workspace — product source of truth"
  - resource: ~/.hermes/kanban/boards/halal-food-finder/kanban.db
    title: "Hermes Kanban — execution queue and work history"
---

# Tahir's List — Engineering Handoff Bundle

This bundle is the handoff from Adnan (Product & Engineering Manager) to whoever
takes over Tahir's List. It is written to be read **synchronously**: start at the
top, follow the order, and stop when you have what you need. Every document is
plain markdown with YAML frontmatter so it diffs, reviews, and versions like code.

It is deliberately **not** a diary. It records what is *true now*, what was
*decided*, what was *built*, and what is *still unknown* — separated, as the
project's own rules require.

## Reading order

| # | Document | Read this when |
|---|----------|----------------|
| 00 | [Executive handoff](00-executive-handoff.md) | **Start here.** The honest state and the first five things to do. |
| 01 | [Product vision](01-product-vision.md) | You need to know what we are building and for whom. |
| 02 | [Architecture](02-architecture.md) | You need to know how the system is put together. |
| 03 | [Development process](03-development-process.md) | You are about to write, review, or merge code. |
| 04 | [Team and roles](04-team-and-roles.md) | You need to know who owns what (and what to stop doing). |
| 05 | [Operations and infrastructure](05-operations-and-infrastructure.md) | You are about to run, deploy, or debug the stack. |
| 06 | [Work ledger](06-work-ledger.md) | You need the factual record of what was built and what is left. |
| 07 | [Current state and risks](07-current-state-and-risks.md) | You need the audit: what is broken, stale, or unverified. |
| 08 | [Roadmap and open questions](08-roadmap-and-open-questions.md) | You are planning the next sprint or need founder decisions. |
| 09 | [References and glossary](09-references-and-glossary.md) | You need IDs, commands, file pointers, or terminology. |
| 10 | [Fresh machine setup](10-fresh-machine-setup.md) | **You are on a new computer.** Docker from zero, services, backend, frontend, smoke tests, troubleshooting. |

## If you read only one thing

Read [00-executive-handoff.md](00-executive-handoff.md). It contains the state of
the system as of 2026-11-02, including the fact that the preview frontend was down
and the pipeline had been idle since 2026-09-09.

**On a new computer, read [10-fresh-machine-setup.md](10-fresh-machine-setup.md)
first.** It is the only document that assumes nothing about your machine. Docs 05
and 06 describe the original host and its local automation; they are context, not
setup instructions.

## The three-sentence version

Tahir's List is a listing-first directory that helps Muslims find halal food,
differentiated by a hand-cut filter and a formal verification process for
restaurant halal certification. The backend (Kotlin/Ktor 3 + PostgreSQL/PostGIS,
hexagonal modules, TDD) and frontend (Next.js/TypeScript) are substantially built,
with 115 completed work items across auth, listings, search/filters, verification,
images, favorites, and owner tools. The project went dormant around 2026-09-09
with one blocked frontend card and a broken preview deployment; everything after
that date in this bundle is diagnosis, not development.

## Source-of-truth hierarchy

When documents disagree, this order wins:

1. **Shortcut** (`app.shortcut.com/halal-food-finder`) — product requirements,
   PRD, use cases, epics, stories, iterations, priorities. Product truth.
2. **This repository** — implementation truth. `ARCHITECTURE.md` and `AGENTS.md`
   are the governing technical documents; this handoff bundle is subordinate to
   them and should be corrected if it drifts.
3. **Hermes Kanban** (`~/.hermes/kanban/boards/halal-food-finder/`) — execution
   truth on the original host: what was dispatched, by whom, and what actually
   happened. **Does not travel with the repository.**
4. **This bundle** — synthesis and pointers. Useful, but not authoritative on
   product or code.

## Provenance and confidence

Every factual claim here is tagged in prose as one of:

- **Established** — read directly from code, config, database, or a primary doc.
- **Observed** — seen in a live system check at the time of writing.
- **Inferred** — my reasoning from evidence; may be wrong.
- **Unresolved** — genuinely unknown; needs a founder or specialist decision.

I have not marked every sentence, but the risky claims are marked explicitly.
Where I am unsure, I say so. That is the point of the exercise.

## Status of this bundle

Written 2026-11-02 while the system was in the state described in
[07](07-current-state-and-risks.md). It is **committed on `main`** in the
repository. The "working tree" and "current branch" details in doc 05 describe
the original host at handoff time, not your checkout.

## Portability

This bundle travels with the repository, and the product, architecture, process,
and roadmap documents apply anywhere. Two things do **not** travel: the Hermes
runtime (Kanban database, cron jobs, agent personas) and the Cloudflare tunnel
token. [10-fresh-machine-setup.md](10-fresh-machine-setup.md) lists exactly what
is missing and how to replace it.
