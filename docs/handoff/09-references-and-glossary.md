---
type: handoff-document
title: "09 — References and Glossary"
description: "Lookup tables: glossary, Shortcut story IDs, file pointers, commands, cron job IDs, URLs, and where to find the detailed documentation."
tags: [tahirs-list, glossary, references, story-ids, commands, index]
status: active
generated: 2026-11-02
generated_by: adnan
verified: false
sources:
  - resource: .
    title: "Repository root"
  - resource: app.shortcut.com/halal-food-finder
    title: "Shortcut workspace"
  - resource: ~/.hermes/kanban/boards/halal-food-finder/
    title: "Kanban board data"
---

# 09 — References and Glossary

## Glossary

| Term | Meaning |
|------|---------|
| **Tahir's List** | The product name. Pivot from "Halal Food Finder" (HFF). Domain `tahirslist.com`. |
| **HFF** | Halal Food Finder. The original project name, still used in paths, cron job names, and some code. Same product. |
| **Amanah** | Trust / communal responsibility. Used in AGENTS.md to mean: report status, progress, and defects honestly; never exaggerate or hide known issues. |
| **Hand-cut** | Boolean listing attribute. Whether meat is hand-cut (Zabiha). The product has **no machine-cut concept** after `sc-42`. |
| **Partial-halal** | A restaurant that is not wholly halal (e.g. some menu items or shared kitchen). In MVP scope; exact semantics unresolved (U-14). |
| **Alcohol-served** | Listing attribute: whether the establishment serves alcohol. In MVP scope; semantics partly open (U-15). |
| **Verification** | The formal process by which an owner claims a listing, consents to AI review, uploads proof + certificate, and a human committee approves or denies. |
| **VC** | Verification Committee. A role on an existing account that reviews verification requests. |
| **IC** | Issues Committee. A role that reviews reported issues. |
| **VRO** | Verified Restaurant Owner. An owner verified as the owner of a listing. |
| **ET** | Extension Trigger. The event the browser extensions listen for. |
| **Verification seam** | The `VerificationProvider` port plus adapters. Lets the hosted-AI provider be swapped without changing domain code. |
| **Hosted AI** | The vision model used to suggest a verdict on a certificate image. Default: Gemini 2.5 Flash, paid tier. |
| **Conservative verdict policy** | The rule that the AI suggests a verdict cautiously; uncertain cases go to human review rather than auto-approving. |
| **Human-in-the-loop** | Mandatory human review of AI verification suggestions. Required by provider terms and by founder decision. |
| **Hexagonal architecture** | Ports-and-adapters layout: `:domain → :application → adapters → :bootstrap`. Domain must not depend on frameworks. |
| **Seam** | An interface boundary where an implementation can be swapped (e.g. image storage, geocoder, verification provider). |
| **M1** | Milestone 1. The MVP scope. Epics 111–115 and 158, plus cost-hardening epic 196. |
| **SC-###** | A Shortcut story identifier, e.g. `SC-131`. Used as the commit prefix. |
| **t_xxxxxxxx** | A Hermes Kanban task ID, e.g. `t_63bc37f1`. |
| **Kanban** | Hermes Kanban. The execution queue (what to do), distinct from Shortcut (what the product needs). |
| **Worktree** | A separate checkout of the same git repo, used so multiple tasks can work in parallel without branch conflicts. `.worktrees/` holds 42 historical ones. |
| **Coordination gate** | The 30-minute cron job that merges PRs, dispatches work, and unblocks cards when the action fingerprint changes. |
| **Watchdog** | The 15-minute script-only cron job that redeploys the preview when `main` moves or a container dies. |
| **Preview** | The local Docker stack exposed at `https://dev.tahirslist.com` via a stable Cloudflare tunnel. Not production. |
| **Live config** | `APP_S3_ENDPOINT` set and `APP_SEED_INGEST_ENABLED=true`. Enables the MinIO-backed `ImagePort` and the seed-photo ingest runner. |
| **Seed ingest** | Boot-time runner that downloads seed restaurant photos into MinIO. Currently failing with HTTP 403 on some URLs. |
| **OKF** | Open Knowledge Format. Markdown + YAML frontmatter knowledge bundles. This handoff uses its conventions. |

## Shortcut story ID index

**Established** from the Kanban ledger titles and Shortcut. This is the practical
cross-reference for the IDs you will see in commits and cards.

| ID | What it is |
|----|------------|
| SC-39 | Create Account |
| SC-40 | Log In |
| SC-42 | Filter by food cutting method → collapsed to boolean hand-cut |
| SC-43/44 | Price and cuisine filters (AND/OR chaining) |
| SC-45/72 | Rating and distance filters |
| SC-46 | Owner claim a listing |
| SC-47 | Edit Unverified Restaurant |
| SC-48 | Edit Verified Restaurant |
| SC-49 | Verified certification display — wire to verification |
| SC-50/51/52 | Favorites (backend endpoints, frontend provider, saved page) |
| SC-53 | Add Image (owner listing images) |
| SC-54 | Remove Image (owner listing images) |
| SC-73 | Verification Committee approve/deny + promotion + RBAC |
| SC-74 | Approve or Deny Restaurant Edits |
| SC-116 | Scaffold project repo (backend + frontend skeletons) |
| SC-117 | Verification provider seam (hosted AI + human review) |
| SC-118 | Alcohol-served attribute |
| SC-119 | Partial-halal modeling + cross-contamination index gate |
| SC-120 | AI consent on the verification claim |
| SC-131 | JWT verification filter |
| SC-132 | Server-side logout |
| SC-133 | Refresh-token HttpOnly cookie + session lifecycle |
| SC-134 | Fail-fast config, keys, error handling, security headers |
| SC-135 | Auth test-gap closure + QA conventions |
| SC-138 | Add-listing vertical — external services gate review (geocoding, POI/listing data sources) |
| SC-155 | Seed ingest |
| SC-157 | Responsive restaurant imagery: MinIO variants + frontend loading |
| SC-159 | Tahir mascot + logo identity |
| SC-164 | Rebrand codebase package names to Tahir's List |
| SC-171 | Live listing read |
| SC-178 | Seed photo follow-up: resolve UNRESOLVED + replace 403 URLs |
| SC-180 | next/image optimizer allow `/v1` image URLs |
| SC-183 | Responsive multi-width `srcset` for card thumbs |
| SC-184 | Pickup/delivery service mode + filter |
| SC-185 | QA fix: duplicate Unverified badge + desktop bottom nav |
| SC-187 | Structured address fields + map preview |
| SC-196–200 | Cost hardening epic and its four open stories |

> IDs are from commit messages, Kanban titles, and Shortcut search results. If a number
> is not in this table, look it up in Shortcut rather than guessing.

## Where things live

| What you need | Where it is |
|---------------|-------------|
| Product overview and personas | `README.md` |
| Agreed architecture + open decisions | `ARCHITECTURE.md` |
| Engineering rules (read before coding) | `AGENTS.md` |
| MVP vertical slice plan | `.hermes/plans/2026-08-29_mvp-trust-verification-vertical-slice.md` |
| Design specs and tokens | `docs/design/` (14 files) |
| QA assessment and conventions | `docs/qa/` (2 files) |
| External services and seed research | `docs/research/` (3 files) |
| External services review | `docs/reviews/` |
| Auth security review | `docs/security/auth-security-review-2026-08-29.md` |
| Brand decisions and character bible | `brand/tahir/BRAND-DECISIONS.md`, `brand/tahir/CHARACTER.md` |
| Mascot art and lockups | `brand/tahir/` |
| Backend source | `backend/` (hexagonal modules) |
| Database migrations | `backend/adapters/persistence/src/main/resources/db/migration/` |
| OpenAPI spec | `backend/openapi/` |
| Frontend source | `frontend/src/` |
| Build and run scripts | `scripts/` |
| Kanban board data | `~/.hermes/kanban/boards/halal-food-finder/kanban.db` |
| Cron job definitions | `~/.hermes/profiles/adnan/cron/jobs.json` |
| Operations scripts | `~/.hermes/profiles/adnan/scripts/` |
| This handoff bundle | `docs/handoff/` |

## Commands cheat sheet

### Build and test (Docker only; no host toolchains)

```bash
cd /path/to/tahirs-list

# Backend: full build + tests
./scripts/backend-test.sh

# Backend: tests only (faster)
./scripts/backend-test.sh test-only

# Frontend: clean install + build
./scripts/frontend-test.sh

# Frontend: reuse existing install
./scripts/frontend-test.sh dev-install
```

### Run the stack

```bash
# Backing services (PostGIS + object store)
docker compose -f backend/docker-compose.yml up -d

# Backend with the live profile (S3 + seed ingest)
bash scripts/backend-live.sh

# Frontend (inline command from the watchdog)
docker rm -f tahirs-list-frontend
docker run -d --name tahirs-list-frontend --restart unless-stopped -p 3000:3000 \
  --network tahirs-list_default \
  -v "$PWD/frontend":/app -w /app -v /app/node_modules \
  -v tahirs-list-npm-cache:/npm-cache -e npm_config_cache=/npm-cache \
  -e API_PROXY_TARGET='http://tahirs-list-backend:8080' \
  node:22 bash -lc "npm install --no-audit --no-fund && npm run build && npm run start"
```

### Health checks

```bash
curl -s http://localhost:8080/v1/health        # {"status":"ok"}
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:3000/
curl -s -o /dev/null -w '%{http_code}\n' https://dev.tahirslist.com
docker ps --filter name=tahirs-list-
docker logs tahirs-list-backend --tail 50
docker logs tahirs-list-frontend --tail 50
```

### Kanban

```bash
export -n HERMES_DELEGATED_CHILD_CONTEXT   # required in agent shells
hermes kanban list
hermes kanban boards list
hermes kanban dispatch
```

### Git

```bash
git log --oneline -20
git status --porcelain
git branch -a --sort=-committerdate | head
gh pr list --repo Losers-Lab/tahirs-list --state open
gh pr view <n> --repo Losers-Lab/tahirs-list --json reviewDecision,reviews
```

### Shortcut (via MCP tools)

Available tools include `get_whoami`, `list_epics`, `list_iterations`,
`get_current_iterations`, `get_iteration`, `search_stories`, `get_story`,
`create_story`, `update_story`, `create_story_comment`, `list_story_comments`,
`list_workflow_states`, `list_labels`, `list_members`, and more.

The search query language supports operators such as `state:"In Development"`,
`iteration:191`, `epic:112`, `is:done`, `is:started`, `is:blocked`, `has:pr`,
`created:2026-01-01..2026-02-01`, `updated:>2026-01-01`, and `!is:done`.

## Cron job IDs and schedules

| ID | Name | Schedule |
|----|------|----------|
| `ee7b25152cb2` | HFF coordination gate (daytime) | `0,30 0,9-23 * * *` |
| `f37d47352a20` | HFF coordination gate (overnight) | `0 3,6 * * *` |
| `827f0630945f` | HFF sprint plan + execute | `0 9,17 * * *` |
| `d91459805654` | HFF deploy watchdog | `*/15 * * * *` |

Jobs are defined in `~/.hermes/profiles/adnan/cron/jobs.json`. The watchdog is
`no_agent: true` (script only). The others are LLM jobs gated by a monitor script
(`hff_check.sh` or `hff_sprint_gate.sh`).

**Note:** all jobs reported `last_status: ok` while the stack was down. "Ok" means the job
ran, not that the system is healthy. Do not treat it as a health signal.

## URLs

| What | URL |
|------|-----|
| Public preview | `https://dev.tahirslist.com` |
| Production (not provisioned) | `tahirslist.com` |
| GitHub repo | `https://github.com/Losers-Lab/tahirs-list` |
| Shortcut workspace | `https://app.shortcut.com/halal-food-finder` |
| Backend API (local) | `http://localhost:8080` |
| Frontend (local) | `http://localhost:3000` |
| MinIO console (local) | `http://localhost:9001` |

## Key documents to read in the repo

In this order, if you are new:

1. `README.md` — product overview and personas.
2. `AGENTS.md` — engineering rules and context.
3. `ARCHITECTURE.md` — agreed architecture and open decisions.
4. `.hermes/plans/2026-08-29_mvp-trust-verification-vertical-slice.md` — the slice plan.
5. `docs/design/tokens.md` — design system.
6. `docs/security/auth-security-review-2026-08-29.md` — security posture.
7. `docs/qa/auth-qa-assessment-and-testing-conventions.md` — QA conventions.
8. `docs/research/external-services-brief-2026-08-29.md` — service choices and costs.
9. `brand/tahir/BRAND-DECISIONS.md` — brand decisions.
10. This bundle — `docs/handoff/index.md`.

## People and escalation

| Topic | Who |
|-------|-----|
| Product, scope, brand, money, open questions | The founder (Arham) |
| Backend, schema, migrations, PostGIS | Hamza's prior work; read the code and AGENTS.md |
| Frontend and design intent | Maryam (frontend), Fatima (design) |
| Security posture | Omar's reviews in `docs/security/` and on PRs |
| QA conventions | Yusuf's work in `docs/qa/` |
| External services and data provenance | Aisha's briefs in `docs/research/` |

**Reminder:** the specialist agents may or may not be retained in the synchronous model.
If they are not, their rules still apply — read their personas and carry the rules forward
explicitly rather than assuming a profile will enforce them.
