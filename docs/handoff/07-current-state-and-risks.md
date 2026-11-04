---
type: handoff-document
title: "07 — Current State and Risks"
description: "The honest audit: what is broken, stale, unverified, or risky in Tahir's List as of 2026-11-02, with severity and next actions."
tags: [tahirs-list, risks, audit, technical-debt, state-of-the-system, security]
status: active
generated: 2026-11-02
generated_by: adnan
verified: false
sources:
  - resource: live system checks (docker, curl, git, sqlite) on 2026-11-02
    title: "Observed state"
  - resource: ~/.hermes/kanban/boards/halal-food-finder/kanban.db
    title: "Blocked card and completion timestamps"
  - resource: app.shortcut.com/halal-food-finder
    title: "Workflow state counts and open stories"
  - resource: ~/.hermes/profiles/omar/SOUL.md
    title: "Generic persona (debt)"
---

# 07 — Current State and Risks

This is the document I would want if I were taking over. It is deliberately blunt.
Nothing here is exaggerated, and where I am unsure I say so.

## The state in one paragraph

The codebase is substantially built and the backend is running. The preview frontend
is down. There is no production deployment. The work queue has been empty since
2026-09-09, so the automation has been running in circles for roughly eight weeks.
Shortcut and the Kanban disagree about what is in progress. There are no backups and
no CI. The biggest risk is not the code; it is that the operating state is illegible
and fragile.

## Risk register

Severity is about impact on users or data. Likelihood is about whether it bites you
without intervention.

| # | Risk | Severity | Likelihood | Evidence |
|---|------|----------|------------|----------|
| R1 | **No database backup or restore procedure** | Critical | High | No backup script/job found; data lives in Docker volumes `pgdata` and `miniodata` |
| R2 | **Preview frontend is down** | High | Certain (now) | `hff-frontend` restarting, exit 254; port 3000 refused; tunnel 502 |
| R3 | **Watchdog cannot detect a crash loop** | High | High | Liveness check reads `.State.Running`, true during restart cycles |
| R4 | **No CI pipeline** | High | Medium | No `.github/workflows/`; "green build" enforced manually |
| R5 | **No production host provisioned** | High | Certain | No SSH config, no server, no deployed artifact; only local Docker + dev tunnel |
| R6 | **Kanban/Shortcut drift** | Medium | Certain | 2 stories "In Development" with no active work; expired Sprint 6 |
| R7 | **Dirty working tree on a feature branch** | Medium | Certain | `git status` shows modified + untracked; branch is `wt/sc-187-detail-address-map` |
| R8 | **Seed image 403s** | Medium | Certain for affected listings | Backend logs show `FETCH_FAILED ... HTTP 403` from `lh3.googleusercontent.com` |
| R9 | **Uncommitted handoff bundle** | Medium | Certain | Files written to working tree only |
| R10 | **42 stale git worktrees** | Low | Certain | `.worktrees/` |
| R11 | **Omar's persona is the generic default** | Medium | Certain | `SOUL.md` is the Hermes default, not a security reviewer |
| R12 | **Secrets on disk in the working tree** | Medium | Low | `.cloudflared-token.txt` at repo root; gitignored and untracked, but present |
| R13 | **Dev credentials are defaults** | Medium | Certain | `halal/halal`, `minioadmin/minioadmin` in compose and launch script |
| R14 | **Image endpoints may not be on `main`** | Medium | Unknown | Last card completed 09-09, five days after last commit; verify before assuming |
| R15 | **Duplicate and mis-assigned Kanban cards** | Low | Certain | `Review PR #64` twice; backend cards assigned to Omar |
| R16 | **Open dependency vulnerabilities** | High | Certain | GitHub reports **47 Dependabot alerts** on the default branch: 6 critical, 13 high, 21 moderate, 7 low (observed 2026-11-02) |

### R1 — No backups (fix first)

The Postgres data volume (`halal-food-finder_pgdata`) and MinIO volume
(`halal-food-finder_miniodata`) hold the seeded listings, users, and any uploaded
certificates. I found no backup job, no snapshot script, and no documented restore
procedure. If the volumes are pruned, or the machine is rebuilt, that data is gone.

Minimum viable fix: a nightly `pg_dump` plus a `mc mirror` of the MinIO bucket to a
second location, and a restore runbook you have actually tested once. Do this before
you change anything else about the deployment.

### R2 — Preview frontend down

Symptoms and the full fix procedure are in
[05-operations-and-infrastructure.md](05-operations-and-infrastructure.md). Short
version: recreate the containers from a clean `main`, confirm backend health first,
then rebuild the frontend. Read the frontend logs if it still exits 254.

### R3 — Watchdog blind spot

The 15-minute watchdog has run ~3,925 times and reported `ok` while the stack was
broken. Its liveness check is fooled by a crash-looping container. It also swallows
`git checkout main` failures (`2>/dev/null`) and has no `set -e`, so it can redeploy
whatever branch happens to be checked out. Fix both: check HTTP health or restart
counts, and make failures loud.

### R4 — No CI

Every "build green" claim in the PR history rests on someone running the scripts and
saying so. Add a minimal GitHub Actions workflow running `backend-test.sh` and
`frontend-test.sh` on PRs and on `main`. This is the highest-leverage process fix
available and it takes very little effort.

### R5 — No production host

Hetzner was ratified but never provisioned. There is no server, no Jib image pushed to
a registry, no DNS pointing at a production origin. The product has never been publicly
deployed. Do not describe it as live. The migration path is documented and cheap
(Jib images + env-var connection strings; `pg_dump`/restore for the database), but it
has not been executed.

### R6 — Kanban/Shortcut drift

Shortcut says #53 and #23 are "In Development". The Kanban has no running cards. Sprint
6's end date (2026-09-10) has passed. Reconcile before planning: either resume the work
or move the stories back to Prioritization and close the iteration. A board that lies
is worse than an empty board.

### R7 — Dirty tree on a feature branch

The repo is on `wt/sc-187-detail-address-map`, not `main`, with modified and untracked
files. Both deploy scripts assume `main`. The watchdog's `git checkout main` fails
silently against a dirty tree. Get clean and stay clean; this is a recurring footgun.

### R8 — Seed images returning 403

The backend's seed ingest logs repeated HTTP 403s from Google's image CDN for several
seeded restaurants (Caspian Grill, Halal Bros, Halal Wings). Those URLs are dead or now
require auth. Listings still render, but without photos. Re-host the images in MinIO or
replace the URLs. This is content debt, not a code bug — and it is a visible quality
problem on a product whose whole value proposition is trust.

### R9 — Uncommitted handoff

This bundle is in the working tree only. If the machine is rebuilt or the branch is
discarded, it is lost. Commit it on a clean branch first.

### R10 — Worktree sprawl

42 directories under `.worktrees/`. They consume disk and make `git` operations slower.
Some may contain unmerged work. Check for unmerged commits (`git log main..<branch>`)
before deleting, then prune.

### R11 — Omar's persona debt

`~/.hermes/profiles/omar/SOUL.md` is 876 bytes of the generic Hermes Agent
prompt plus a note to review diffs rather than whole files. The security-reviewer role
lived in AGENTS.md and in task assignment prompts, not in the persona. If you keep Omar,
rewrite the persona. If you collapse to one agent, carry the security rules explicitly
so they do not evaporate with the profile.

### R12 — Secrets on disk

`.cloudflared-token.txt` sits at the repo root. It is gitignored and **not tracked**
(verified with `git ls-files --error-unmatch`), so it has not leaked to GitHub. But it is
a live credential in a working tree that also contains untracked logs and artifacts. Move
it to a secret store or at minimum keep it out of any archive or bundle you share.

### R13 — Default dev credentials

`halal/halal` and `minioadmin/minioadmin` are fine on localhost. They are not fine on a
public host. Change them when you provision production, and do not copy the compose file
to the server unchanged.

### R14 — Unverified: are the image endpoints on `main`?

The last Kanban card completed 2026-09-09, but the last commit is 2026-09-04. That gap
could mean the final work merged without a new commit on `main`, or it completed on a
branch that never merged. Before telling anyone the owner image endpoints exist in
production, verify: `git log main --oneline | head`, `git branch --contains <sha>`, and
check the controller on `main` rather than the working tree.

### R15 — Duplicate and mis-assigned cards

`Review PR #64` appears twice on 2026-09-03. Several backend implementation cards are
assigned to Omar (the reviewer) rather than Hamza (the author). The ledger is directionally
accurate but not clean. Do not use assignee counts as a performance measure without
checking git authorship.

### R16 — Open dependency vulnerabilities

**Observed.** GitHub reported **47 vulnerabilities on the default branch** at push time:
**6 critical, 13 high, 21 moderate, 7 low** (Dependabot). I did **not** triage them, so treat
the individual severities as unverified until reviewed; the count and distribution are what
GitHub reported.

Next action: open the repository's **Security → Dependabot alerts** page and triage by
runtime exposure, not by raw count. Many alerts are likely in development-only dependencies
and will not be reachable in production, but "likely" is not a security decision. Prioritize
anything reachable from the backend HTTP surface or the Next.js server, then the rest.
This is a standing maintenance task, not a one-off patch.

## Things I could not verify

Stated plainly so you do not inherit false confidence:

- **Whether the eight-week dormancy was intentional.** I found no note recording a pause
  decision. The cron jobs kept running, which suggests drift rather than a deliberate
  freeze.
- **Whether `dev.tahirslist.com` is meant to be public.** It is reachable but broken. No
  doc states its intended audience.
- **Whether production data exists anywhere.** If a Hetzner box was ever provisioned and
  later destroyed, I found no trace. Assume no production data exists.
- **Whether the browser extensions are still in M1 scope.** ARCHITECTURE.md says MVP
  scope; no extension code exists in the repo. That is a founder question.
- **Whether all seven Shortcut epics are still current.** I saw seven; I enumerated five
  by name (111, 112, 113, 114, 196) plus 158 referenced in the sprint prompt. The rest
  need a human read of Shortcut.
- **The true state of `main` versus the feature branch.** The working tree is on
  `wt/sc-187-detail-address-map`, so anything I inspected in the tree may not match
  `main`. Re-verify against `main` before acting.

## What is *not* a risk (so you do not over-correct)

- **The backend architecture.** Hexagonal modules, clear dependency direction, 68 test
  files, TDD throughout. It is in good shape. Do not rewrite it.
- **The domain model.** The agreed model in ARCHITECTURE.md is coherent and matches the
  implementation closely enough. Do not redesign it.
- **The design system.** Tokens, trust components, and brand assets are documented and
  consistent. Do not restart the design.
- **The security review trail.** Auth was reviewed statically by Omar and attacked
  dynamically by Yusuf. That is better than most projects at this stage. Preserve the
  practice.
- **The product thinking.** The PRD, use cases, personas, and open questions are thorough.
  The problem was never the thinking.

## Priority order

If you do nothing else, do these in order:

1. Commit this handoff bundle on a clean branch.
2. Back up Postgres and MinIO, and write a restore runbook.
3. Restore the preview (clean `main`, recreate backend then frontend, verify end to end).
4. Fix the watchdog's liveness check and make it fail loudly.
5. Add a minimal CI workflow for both test scripts.
6. Triage the Dependabot alerts (47 open, 6 critical) by runtime exposure.
7. Reconcile Shortcut to reality and close or extend Sprint 6.
8. Clear the blocked image-management card.
9. Decide the operating model and delete the automation you are not using.
