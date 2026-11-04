---
type: handoff-document
title: "06 — Work Ledger: What Was Actually Built"
description: "The factual record of completed work: Kanban statistics, timeline by date and owner, what shipped, and what remains unfinished."
tags: [tahirs-list, ledger, kanban, history, delivery, milestones]
status: active
generated: 2026-11-02
generated_by: adnan
verified: false
sources:
  - resource: ~/.hermes/kanban/boards/halal-food-finder/kanban.db
    title: "Kanban tasks (115 done, 1 blocked, 4 archived)"
  - resource: git log (223 commits, 2026-08-28 → 2026-09-04)
    title: "Commit history"
  - resource: app.shortcut.com/halal-food-finder
    title: "Epics, stories, iterations, workflow states"
---

# 06 — Work Ledger

This is the factual record, not the narrative. If you need to know whether something
was built, check here and then check the code. If the two disagree, the code wins.

## Numbers

**Established.**

| Metric | Value |
|--------|-------|
| Commits | 223 |
| Commit window | 2026-08-28 → 2026-09-04 |
| Kanban tasks total | 120 |
| — done | 115 |
| — archived | 4 |
| — blocked | 1 |
| — ready / running / todo | 0 |
| Backend test files | 68 |
| Frontend test files | 22 |
| Flyway migrations | 20 (V1–V20) |
| Frontend routes | 9 |
| Backend controllers | 11 |
| Docs: design | 14 files |
| Docs: qa | 2 files |
| Docs: research | 3 files |
| Docs: reviews | 1 file |
| Docs: security | 1 file |
| Shortcut epics | 7 |
| Shortcut workflow states | 6 |
| Stories completed (Shortcut) | 38 |
| Stories in Prioritization | 35 |
| Stories in Development | 2 |
| Stories in Ready for Development | 0 |

**Note the discrepancy:** Shortcut shows 38 completed stories while Kanban shows 115
done cards. Kanban cards include review tasks, QA tasks, fixes, and sub-work that do
not map one-to-one to stories. Do not treat the Kanban count as a story count.

## Commit activity by date

**Established** from `git log --date=short`.

| Date | Commits |
|------|---------|
| 2026-08-28 | 8 |
| 2026-08-29 | 41 |
| 2026-08-30 | 78 |
| 2026-08-31 | 50 |
| 2026-09-01 | 28 |
| 2026-09-02 | 12 |
| 2026-09-03 | 5 |
| 2026-09-04 | 1 |

The velocity curve is steep up and steep down. Nearly 80% of commits landed in the
first four days. This was a concentrated build sprint followed by a taper into
review-and-fix work. After 2026-09-04 there were no code commits at all; the last
Kanban card completed on 2026-09-09.

## What shipped, by domain

**Established** from the Kanban ledger and git history. Grouped for readability; the
full card list is in the Kanban database if you need per-task detail.

### Foundations and scaffolding

- `sc-116` — Backend skeleton + TDD harness (Hamza); frontend skeleton + design-token
  base (Maryam); M1 design foundation: tokens, trust components, auth screen specs
  (Fatima).
- Containerized build scripts for backend and frontend; Docker Compose for PostGIS +
  MinIO.

### Authentication and security

- `sc-39` — Create Account backend, TDD (Hamza).
- `sc-40` — Log In backend, TDD (Hamza); Sign Up + Log In UI (Maryam).
- `sc-131` — JWT verification filter, deny-by-default resource server (Hamza);
  security review (Omar).
- `sc-132` — Server-side logout, TDD (Hamza); approving review (Omar).
- `sc-133` — Refresh-token HttpOnly cookie + session lifecycle (Hamza backend,
  Maryam frontend); attack cases prepared by Omar; break-it pass by Yusuf.
- `sc-134` — Fail-fast config, key handling, error handling, security headers
  (Hamza); frontend security headers via `next.config` (Maryam); actuator lockdown;
  production key guard.
- Auth rate limiting (Bucket4j), Argon2id password hashing, JWT RSA key pair loader.
- `sc-135` — Auth test-gap closure and QA conventions (Yusuf); frontend test gaps.

### Listings and search

- Listing create/read (`POST /v1/restaurants` equivalent), unverified-by-default.
- `sc-43/44` — Price and cuisine filters, including AND/OR chaining.
- `sc-45/72` — Rating and distance filters with straight-line miles.
- `sc-42` — Cutting filter refactor: boolean hand-cut, machine-cut dropped (backend
  Hamza, frontend Maryam).
- `sc-184` — Pickup/delivery service mode on listings plus search filter (backend
  Hamza, frontend Maryam).
- `sc-187` — Structured address fields (city/province/postal/country) plus full
  address rendering and map preview with pin (backend, frontend, and QA fix).
- `sc-118` — Alcohol-served attribute on listings.
- `sc-119` — Partial-halal modeling and cross-contamination index gate; hand-cut
  search. Included a review-blocker fix restoring the trust doc and reconciling the
  `sc-43` test.
- `sc-185` — QA fix: duplicate Unverified badge and bottom nav on desktop.

### Trust and verification

- `sc-117` — Verification provider seam with hosted-AI adapter and human review
  (Hamza); code review (Omar).
- Conservative verdict policy (`ConservativeVerdictPolicyTest`).
- `sc-73` — Verification Committee decision use case, persisted VC reviews and listing
  promotion, VC review endpoints and RBAC, VC review API client and queue state, VC
  approve/deny screen. Certification metadata exposed on the detail read surface.
- `sc-120` — AI consent required on the claim endpoint, consent timestamp persisted on
  reviews; design doc `docs/design/sc-120-ai-consent.md`.
- `sc-46` — Owner claim design and flow.

### Images and media

- `sc-157` — MinIO-backed image variants behind an `ImagePort` (Hamza, TDD); responsive
  restaurant images via `next/image` (Maryam); seed hero photos manifest (Aisha); seed
  photo ingest runner; live profile wiring.
- `sc-183` — Multi-width thumbnail variants for responsive `srcset` (backend Hamza,
  frontend Maryam).
- `sc-180` — Next/image optimizer configured to allow `/v1` image URLs.
- `sc-53` — Owner listing image add/remove endpoints (backend Hamza), reviewed by
  Omar.

### Owner tools

- `sc-23/47/48` — Owner listing edit: `UpdateListingUC` + `PATCH /v1/listings/{id}`
  (backend), and the manage/edit listing screen (frontend). Reviewed by Hamza and
  Omar.
- `sc-53/54` — Listing image management. Backend merged. **Frontend blocked.**

### Favorites

- `sc-50/51/52` — Favorites backend endpoints and frontend provider plus saved page.
  Required a rebase and conflict resolution on PR #46.

### Brand

- Tahir mascot and logo identity exploration (Fatima).
- Rebrand application to Tahir's List with C2 lockup (Maryam).
- Mascot v2 art swap across screens (Fatima).
- Production favicon/app-icon set generated from `tahir-head.jpg` (Fatima).
- C2 lockup applied to site header and footer (Maryam).

### Seed data and research

- Seed restaurant research for NYC, Dallas, Austin, Toronto (Aisha).
- Seed hero photos manifest (Aisha).
- Photon geocoder adapter and seed ingestion with TDD (Omar/Hamza).
- `scripts/generate_seed_migration.py` to generate seed migrations.

## Timeline of the last week

**Established** from Kanban completion timestamps. This is the highest-resolution view
of what was happening at the end.

| Date/time | Owner | Card |
|-----------|-------|------|
| 09-01 17:39 | Maryam | Blocked: Verified certification display — wire to verification (sc-49) |
| 09-01 18:02 | Hamza | Expose certification metadata on listing read surface |
| 09-01 19:19 | Hamza | Refactor cutting filter to boolean hand-cut, drop machine-cut (sc-42) |
| 09-01 19:31 | Maryam | Hand-cut boolean UI (sc-42) |
| 09-01 20:52 | Maryam | QA fix: duplicate Unverified badge + desktop bottom nav (sc-185) |
| 09-02 03:26 | Hamza | Fix PR #54 review blockers, restore trust doc, reconcile sc-43 test (sc-119) |
| 09-02 09:18 | Omar | Partial-halal restaurant modeling in MVP (sc-119) |
| 09-02 17:46 | Hamza | Pickup/delivery service mode backend + search filter (sc-184) |
| 09-02 17:57 | Omar | Full address + map preview with pin (sc-187 frontend) |
| 09-02 17:59 | Maryam | Pickup/delivery display + filter UI (sc-184) |
| 09-02 20:03 | Omar | Structured address fields backend, TDD (sc-187) |
| 09-02 20:27 | Maryam | Render full comma-form address on detail (sc-187) |
| 09-02 21:18 | Omar | Code review PR #61 + #62 (sc-187) |
| 09-03 16:56 | Omar | Owner listing edit backend (sc-23/47/48) |
| 09-03 18:57 | Hamza | Rebase + resolve structured-address conflict (PR #61) |
| 09-03 21:10 | Hamza | Review PR #64 (sc-23 owner listing edit frontend) |
| 09-03 21:26 | Hamza | Review PR #64 (duplicate card) |
| 09-03 21:33 | Maryam | Owner listing edit frontend manage/edit screen (sc-23/47/48) |
| 09-03 23:03 | Omar | Review PR #65 (sc-53 listing image endpoints) |
| **09-09 21:31** | **Hamza** | **Listing image add backend — owner add/remove image endpoints (sc-53/54)** |

**Observations:**

- Several cards are assigned to the reviewer rather than the author (`Omar` on
  backend implementation cards). This is either mis-assignment or cards that were
  re-titled as review tasks. Do not read assignee as "who wrote the code" without
  checking git.
- `Review PR #64` appears twice on 09-03 (21:10 and 21:26). Duplicate cards happened.
  Expect some inflation in the ledger.
- The last completed card on 09-09 is five days after the last commit. That suggests
  a final review/merge cycle that did not produce new commits on `main`, or work
  completed on a branch that was never merged. **Check this before assuming the
  image endpoints are on `main`.**

## What remains unfinished

### The one blocked card

**Observed.** `t_63bc37f1` — "Listing image management (frontend) — add/remove image UI
on manage screen [sc-53/sc-54]", assignee Maryam, status blocked.

Body: "Depends on image backend `t_103b4c33`. Owner add/remove listing images in
manage UI, wired to new endpoints. AC: owner can add and remove images."

Block reason: **"Iteration budget exhausted (40/40) — task could not complete within
the allowed iterations."**

This is a worker-resource failure, not a product or dependency failure. The backend
it depends on is merged. Re-dispatch with a larger budget or split it into two cards
(add vs remove).

### Sprint 6 stories still open

**Established** from Shortcut iteration 191, "M1 Sprint 6 — Owner Tools: Manage Your
Listing", 2026-09-03 → 2026-09-10. Six stories:

| Story | Name | State |
|-------|------|-------|
| #53 | Add Image | In Development |
| #23 | Restaurant Information Management | In Development |
| #74 | Approve or Deny Restaurant Edits | Prioritization |
| #54 | Remove Image | Prioritization |
| #47 | Edit Unverified Restaurant | Prioritization |
| #48 | Edit Verified Restaurant | Prioritization |

The iteration end date has passed. It needs to be formally closed or extended. Two
stories say "In Development" but there is no active work — that is the reconciliation
you must do before planning anything new.

### Backlog themes

**Established** from the epics and stories seen in Shortcut. Names are quoted; story
counts are from the epic summaries where visible.

- **Epic 111** — 21 stories, largely search/browse and listing breadth.
- **Epic 112 — M1 Trust & Verification** — verification, edits, partial-halal.
- **Epic 113 — M1 Accounts & User Features** — auth, favorites, images.
- **Epic 114 — M1 Owner Tools** — owner edit, add/remove images.
- **Epic 115** — browser extensions (per README/ARCHITECTURE; not built).
- **Epic 196 — M1 Platform & Cost Hardening** — four open stories (#197–#200):
  downscale cert images before hosted AI, geocode cache table, quantize search coords
  + cache headers, pin JVM footprint. Plus one designed-in item (zero-egress storage
  via the S3 endpoint override).

I did not enumerate all seven epics by name; the Shortcut UI is the authority. The
counts above are what the API returned during this audit.

## How to read the ledger yourself

```bash
# Human-readable board
export -n HERMES_DELEGATED_CHILD_CONTEXT
hermes kanban list

# Full card detail from the database
python3 - <<'PY'
import sqlite3, datetime
db='~/.hermes/kanban/boards/halal-food-finder/kanban.db'
c=sqlite3.connect(db); c.row_factory=sqlite3.Row
for r in c.execute("select title,assignee,status,completed_at,result from tasks order by completed_at"):
    d=datetime.datetime.fromtimestamp(r['completed_at']).strftime('%Y-%m-%d %H:%M') if r['completed_at'] else '—'
    print(f"{d} | {r['assignee'] or '?':<8} | {r['status']:<8} | {r['title']}")
PY
```

The `result` column often contains the worker's final summary. It is the closest thing
to a per-task audit trail. Read it before assuming a card's outcome.
