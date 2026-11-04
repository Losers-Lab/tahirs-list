# Handoff — Tahir's List

This repository is handed over as of **2026-11-02**.

**Start here:** [`docs/handoff/index.md`](docs/handoff/index.md)

**On a brand-new computer:** [`docs/handoff/10-fresh-machine-setup.md`](docs/handoff/10-fresh-machine-setup.md)
— Docker from zero, backing services, backend, frontend, smoke tests, troubleshooting.
It assumes nothing about your machine, user account, or home directory.

The handoff bundle is a set of plain markdown documents with YAML frontmatter,
following Open Knowledge Format (OKF) conventions. It covers:

- the honest state of the system and the first actions to take
- **setup from zero on a new machine**
- the product vision, personas, brand, and open product questions
- the ratified architecture, module layout, and data model
- the mandated development process and engineering rules
- the specialist team and what each actually delivered
- operations, deployment, cron automation, and the original outage
- the work ledger: what shipped and what remains
- the risk register and prioritized fixes
- the roadmap and decisions that still need the founder

## The three things you need to know right now

1. **Nothing is running on your machine yet.** Follow
   [`docs/handoff/10-fresh-machine-setup.md`](docs/handoff/10-fresh-machine-setup.md).
   The stack is five Docker containers: PostGIS, MinIO, a one-shot bucket initializer,
   the Spring Boot backend, and the Next.js frontend.
2. **There is no production deployment and no database backup.** Hetzner was chosen
   but never provisioned, and the data lives in Docker volumes with no backup job.
   Backups are the highest-priority fix.
3. **The work queue has been empty since 2026-09-09.** Shortcut and the Kanban
   disagree about what is in progress. Reconcile before planning.

## Status of this handoff

Committed on `main`. Docs 05 and 06 describe the original development host and its
local Hermes automation; they are context, not setup instructions. What travels
with the repo is everything in `docs/handoff/`; what does not is the Hermes runtime
(Kanban database, cron jobs, agent personas) and the Cloudflare tunnel token.

Existing governing documents remain authoritative over this bundle:

- `README.md` — product overview
- `ARCHITECTURE.md` — agreed architecture and open decisions
- `AGENTS.md` — engineering rules and context
- Shortcut — product requirements and project management

If this bundle conflicts with any of those, they win. Correct the bundle.
