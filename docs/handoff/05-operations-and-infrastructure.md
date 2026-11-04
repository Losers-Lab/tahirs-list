---
type: handoff-document
title: "05 — Operations and Infrastructure"
description: "How the Tahir's List stack actually runs: Docker services, build scripts, deployment paths, cron automation, the current outage, and the production plan that was never executed."
tags: [tahirs-list, operations, infrastructure, docker, deployment, cloudflare, cron, watchdog]
status: active
generated: 2026-11-02
generated_by: adnan
verified: false
sources:
  - resource: scripts/backend-test.sh
    title: "Containerized backend build/test"
  - resource: scripts/frontend-test.sh
    title: "Containerized frontend build/test"
  - resource: scripts/backend-live.sh
    title: "Live profile backend launcher"
  - resource: ~/.hermes/profiles/adnan/scripts/hff_watchdog.sh
    title: "Deploy watchdog backstop"
  - resource: ~/.hermes/profiles/adnan/scripts/hff_redeploy.sh
    title: "GitHub webhook redeploy trigger"
  - resource: ~/.hermes/profiles/adnan/cron/jobs.json
    title: "Scheduled jobs"
  - resource: backend/docker-compose.yml
    title: "Backing services definition"
---

# 05 — Operations and Infrastructure

> **On a new machine?** This document describes the host the project was built on,
> including automation that lives outside the repository. For a clean, machine-independent
> setup procedure, read [10 — Fresh Machine Setup](10-fresh-machine-setup.md) first. Come
> back here for the architecture of the running stack, the deploy paths, and the diagnosis
> of the original outage.
>
> **Name changes (2026-11-03):** the project was renamed from Halal Food Finder to
> Tahir's List. Container, network and cache-volume names in the observations below are
> the ones seen on the old host: `hff-postgis`/`hff-minio`/`hff-backend`/`hff-frontend` are
> now `tahirs-list-*`, the compose project/network `halal-food-finder_default` is now
> `tahirs-list_default`, data volumes are prefixed `tahirs-list_`, and the Gradle/npm cache
> volumes are `tahirs-list-gradle-cache` / `tahirs-list-npm-cache` /
> `tahirs-list-gradle-test-cache`. The Shortcut workspace slug and the Hermes Kanban path
> keep their original names — they are external identifiers, not product branding.

## The short version

Everything runs as **local Docker on the development machine**, exposed through a **stable
Cloudflare named tunnel** at `https://dev.tahirslist.com`. There is **no CI
pipeline** and **no provisioned production server**. The deploy path is a GitHub
push webhook that fires a shell script; a 15-minute watchdog is the backstop.

**As of 2026-11-02 the preview is partially down:** backend healthy, frontend in a
restart loop, tunnel returning 502. Fix procedure is at the end of this document.

## Repository and working state

**Observed.**

| Item | Value |
|------|-------|
| Path | wherever you cloned the repo (see [10](10-fresh-machine-setup.md)) |
| Remote | `https://github.com/Losers-Lab/tahirs-list.git` |
| Current branch | `wt/sc-187-detail-address-map` (**not** `main`) |
| Working tree | **Dirty**: modified `frontend/package-lock.json`, plus untracked logs, brand PNGs, `.gitignore`, `backend-repomix.xml`, `gphoto_method.md`, and `backend/.gradle.stale.*` |
| Last commit | 2026-09-04 `[SC-53] Add owner listing image endpoints (#65)` |
| Commits | 223 total, 2026-08-28 → 2026-09-04 |
| Open PRs | none |
| Historical worktrees | 42 directories under `.worktrees/` |

**First operational action:** get off the feature branch. Stash or discard the local
changes, check out `main`, and confirm the tree is clean before running anything.
The dirty tree is why the watchdog's `git checkout main` could not do its job.

## Docker services

**Observed** at time of writing. Network: `halal-food-finder_default` (bridge).

| Container | Image | Ports | State |
|-----------|-------|-------|-------|
| `hff-postgis` | `postgis/postgis:17-3.4` | 5432 | **Up 21h (healthy)** |
| `hff-minio` | `minio/minio:RELEASE.2025-04-22T22-12-26Z` | 9000 (S3), 9001 (console) | **Up 21h (healthy)** |
| `hff-backend` | `eclipse-temurin:21-jdk` | 8080 | **Up; `/actuator/health` = UP** |
| `hff-frontend` | `node:22` | 3000 | **Restarting loop, exit 254** |
| `hff-tunnel-dev` | `cloudflare/cloudflared` | — | Up 21h |
| `hff-webhook-tunnel` | `cloudflare/cloudflared` | — | Up 21h |

> **2026-11-04 update (Ktor migration):** the app containers are now named
> `tahirs-list-backend` / `tahirs-list-frontend` and the backend has no actuator:
> the health probe is `GET http://localhost:8080/v1/health`. The `hff-*` rows
> above describe the pre-migration host and are kept for history.

### Backing services (`backend/docker-compose.yml`)

Project name `halal-food-finder`. Three services:

- **`postgis`** — Postgres 17 + PostGIS 3.4. DB `halal`, user `halal`, password
  `halal` (dev only). Volume `pgdata`. Healthcheck `pg_isready`.
- **`minio`** — S3-compatible object store. Root user/password `minioadmin`
  (dev only). Volume `miniodata`. Healthcheck on `/minio/health/live`.
- **`minio-init`** — one-shot `mc` container that waits for MinIO and creates the
  private bucket `halal`.

**Data lives in Docker named volumes `halal-food-finder_pgdata` and
`halal-food-finder_miniodata`.** I found **no backup job, no snapshot script, and no
documented restore procedure**. That is the single largest data risk in the system.
If the volumes are pruned or the machine is rebuilt, the seeded listings and any
uploaded certificates are gone. Address this before anything else production-shaped.

### Backend launch (`scripts/backend-live.sh`)

Launches the Ktor backend and the two infra switches are **environment
variables**, not a Spring profile:

- `APP_S3_ENDPOINT` (plus `APP_S3_*` credentials/bucket) → the real
  MinIO-backed `ImagePort` instead of the in-memory stub.
- `APP_SEED_INGEST_ENABLED=true` → the seed-photo ingest runner runs on boot.

**Without `APP_S3_ENDPOINT`** the app boots with `InMemoryImagePort` and seed
ingest off — which is why `GET /v1/listings/{id}/image` 404s in that
configuration. If images are missing, check the env vars first.

`APP_PROFILE` only selects strict production behaviour for the JWT key
(`isProduction`); `live` is descriptive and does not turn features on. It is
**not** a production profile: `live` deliberately skips the production
credential, object-storage and JWT-key guards (the app logs a warning at boot),
so this preview stack must never front real data.

The script does `docker compose up -d` for backing services, then `docker rm -f
tahirs-list-backend` and `docker run` with:

- `-v "$repo_root/backend":/workspace`
- `-v tahirs-list-gradle-cache:/root/.gradle`
- `-w /workspace`
- `--network tahirs-list_default`
- datasource `jdbc:postgresql://postgis:5432/halal`, user/pass `halal`
- `APP_S3_*` env vars defaulting to `http://minio:9000`, `minioadmin/minioadmin`,
  region `us-east-1`, bucket `halal`
- `APP_SEED_INGEST_ENABLED` defaulting to `true`
- command `./gradlew --no-daemon --project-cache-dir=/tmp/gradle-project-cache :bootstrap:run`

It runs the app from source via Gradle, not from a prebuilt image. That is fine
for preview, but it means startup is slow and depends on the Gradle cache
volume.

### Frontend launch

`./scripts/frontend-dev.sh` serves the SvelteKit dev app on `:5173` inside a
`node:24-slim` container (see AGENTS.md and the script header for the mounts and
cache volume). The SvelteKit server proxies `/v1` API calls to the backend via
`API_PROXY_TARGET`, so the backend must be running too.

### Build and test scripts (the only supported paths)

**Established** from AGENTS.md and the scripts themselves. The host has no language
toolchains and must not get any.

| Command | What it does |
|---------|--------------|
| `./scripts/backend-test.sh` | Full `build` (compile + every module's tests + packaging) in `eclipse-temurin:21-jdk`, capped at 3 GB / 2 CPUs. Mounts repo to `/app`, `tahirs-list-gradle-test-cache` to `/gradle-home`, and `/var/run/docker.sock` for Testcontainers. |
| `./scripts/backend-test.sh test-only` | Same container, runs all `test` tasks without packaging. |
| `./scripts/frontend-test.sh` | Frozen `pnpm install` + `svelte-check` + `vitest run` + `vite build` in `node:24-slim`. |
| `./scripts/frontend-test.sh dev-install` | Allows lockfile drift instead of the frozen install. |
| `./scripts/frontend-test.sh test-only` | Skips install; runs check + tests only. |
| `./scripts/frontend-dev.sh` | SvelteKit dev server on `:5173`, proxying `/v1` via `API_PROXY_TARGET`. |

Both scripts find the nearest `backend/` or `frontend/` directory relative to their
own location, so they work from the repo root **and** from a worktree under
`.worktrees/`. That is deliberate and worth preserving.

**Testcontainers settings are mandatory** and baked into `backend-test.sh`:
`TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal`,
`--add-host host.docker.internal:host-gateway`, and
`TESTCONTAINERS_RYUK_DISABLED=true`. Remove them and sibling-container tests fail
with connection-refused errors.

## Deployment paths

There are **two** paths, by design.

### Primary: GitHub push webhook

**Established** from `hff_redeploy.sh` and `hff_redeploy_worker.sh`.

1. A push lands on `refs/heads/main` (including PR merges).
2. GitHub posts to the Hermes webhook platform on port 8644
   (`hff_restart_gateway.sh` exists to restart that gateway).
3. `hff_redeploy.sh` reads the payload. If the ref is not `refs/heads/main`, it exits
   silently. Otherwise it extracts the commit SHA and a clean commit subject.
4. It fires `hff_redeploy_worker.sh` asynchronously with `nohup ... &` so the webhook
   returns in under a second (GitHub retries on timeout).
5. It prints a short confirmation referencing the stable preview URL
   `https://dev.tahirslist.com`.

### Backstop: 15-minute watchdog

**Established** from `hff_watchdog.sh`. Polls every 15 minutes. It redeploys when:

- `git fetch origin main` shows `origin/main` moved past local `HEAD`, **or**
- either `hff-backend` or `hff-frontend` is not running.

It stays silent when healthy (empty stdout = no message, because the cron job is
`no_agent: true`).

**It then:** `git checkout main`, `git pull origin main`, run `backend-live.sh`, and
`docker rm -f hff-frontend` followed by the `docker run` above. It polls
`localhost:8080/v1/health` for up to 24 × 5s.

### ⚠ The watchdog bug you inherit

**Observed and inferred.** Docker reports `.State.Running = true` during the brief
up-phase of a crash-looping container. The watchdog's liveness check is:

```bash
[ "$(docker inspect -f '{{.State.Running}}' $c)" = "true" ] || NEED_DEPLOY=1
```

So a container that is restarting every 60 seconds can read as "alive" at the moment
of the check. If `main` has not moved, `NEED_DEPLOY` stays `0`, and the watchdog
stays silent while the stack is broken. This is the most likely reason the preview
sat down without an alert. The fix is to check health endpoints or restart counts,
not `.State.Running`.

A second, related fragility: the watchdog's `git checkout main` will fail silently on
a dirty working tree (`2>/dev/null`), and the script has no `set -e`, so it proceeds
to redeploy whatever is checked out. That is how a feature branch can end up running
the "preview".

### Tunnel

**Observed.** Two `cloudflared` containers run continuously:

- `hff-tunnel-dev` serves the stable named tunnel for the preview.
- `hff-webhook-tunnel` serves the GitHub webhook endpoint.

The tunnel config lives in the container token, not in a mounted `config.yml`
(`~/.cloudflared/` is empty on the original host). The token is in `.cloudflared-token.txt` at
the repo root — **gitignored and not tracked** (verified), but still a credential
sitting in the working tree. Move it to a proper secret store when you can.

Tunnel logs at the time of writing show the failure clearly:

```
ERR Unable to reach the origin service ... dial tcp [::1]:3000: connect: connection refused
dest=https://dev.tahirslist.com/ ingressRule=1 originService=http://localhost:3000
```

The tunnel is fine. The frontend origin is not.

## Scheduled automation

**Established** from `~/.hermes/profiles/adnan/cron/jobs.json`. Four jobs
were running. Three delivered to Discord channel `1543111845106286653`; the watchdog
delivered to `local`.

| ID | Name | Schedule | Runs | Monitor |
|----|------|----------|------|---------|
| `ee7b25152cb2` | HFF coordination gate (daytime) | `0,30 0,9-23 * * *` | ~1,340 | `hff_check.sh` |
| `f37d47352a20` | HFF coordination gate (overnight) | `0 3,6 * * *` | ~92 | `hff_check.sh` |
| `827f0630945f` | HFF sprint plan + execute | `0 9,17 * * *` | ~88 | `hff_sprint_gate.sh` |
| `d91459805654` | HFF deploy watchdog | `*/15 * * * *` | ~3,925 | none (script only) |

All four report `last_status: ok`, which is misleading: "ok" means the job executed,
not that the system is healthy. The watchdog has run ~3,925 times and the stack is
still down. See [03-development-process.md](03-development-process.md) for what the
gates do and how their fingerprints work.

**Recommendation:** in a synchronous model, keep at most the watchdog (fixed) and one
daily digest. Disable the rest. The 30-minute gate is solving a coordination problem
you will no longer have.

## Current outage and fix procedure

**Observed** on 2026-11-02.

### Symptoms

- `https://dev.tahirslist.com` → **502**.
- `hff-frontend` → restarting, `exit 254`, port 3000 not listening (`curl` returns
  `000`).
- `hff-backend` → up; `http://localhost:8080/v1/health` returns
  `{"status":"UP"}`; `/` returns **401** (expected — security is on).
- Tunnel logs: `dial tcp [::1]:3000: connect: connection refused`.
- PostGIS and MinIO healthy.
- Backend logs show repeated `FETCH_FAILED ... HTTP 403` for several seed images
  from `lh3.googleusercontent.com` (Caspian Grill, Halal Bros, Halal Wings). Those
  seed photo URLs are dead or now require auth. This is a **content debt**, separate
  from the outage, and it means some listings will render without photos.

### Diagnosis

**Inferred, high confidence.** The long-lived containers lost their bind mounts (a
known Docker Desktop/WSL2 failure after restarts), so `/workspace/gradlew` and
`/app/package.json` were not visible inside them and the entrypoints failed. I
verified the mount configuration is correct by running the same bind mount in a fresh
container — the files were present. Recreating the containers should restore service.

**Why the watchdog did not fix it:** see the bug above. The container reads as
running during its restart cycle.

### Fix procedure

Run these in order. Do not skip the verification steps.

```bash
cd /path/to/tahirs-list

# 0. Get onto a clean main. The dirty feature branch blocks the deploy scripts.
git stash push -u -m "handoff-preserve-$(date -u +%Y%m%dT%H%M%SZ)"   # or discard
git checkout main
git pull origin main

# 1. Confirm backing services.
docker compose -f backend/docker-compose.yml up -d
docker ps --filter name=hff-postgis --filter name=hff-minio

# 2. Recreate the backend from source with the live profile.
bash scripts/backend-live.sh

# 3. Wait for backend health, then recreate the frontend.
for i in $(seq 1 40); do
  code=$(curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/v1/health)
  [ "$code" = "200" ] && break; sleep 5
done
echo "backend health: $code"

docker rm -f hff-frontend
docker run -d --name hff-frontend --restart unless-stopped -p 3000:3000 \
  --network halal-food-finder_default \
  -v "$PWD/frontend":/app -w /app -v /app/node_modules \
  -v halal-npm-cache:/npm-cache -e npm_config_cache=/npm-cache \
  -e API_PROXY_TARGET='http://hff-backend:8080' \
  node:22 bash -lc "npm install --no-audit --no-fund && npm run build && npm run start"

# 4. Verify end to end.
curl -s -o /dev/null -w 'frontend %{http_code}\n' http://localhost:3000/
curl -s -o /dev/null -w 'preview  %{http_code}\n' https://dev.tahirslist.com
docker logs hff-frontend --tail 40
```

If the frontend still exits 254, read its logs before rebuilding. Exit 254 usually
means the container's command failed to start — a missing `package.json`, a broken
`npm` invocation, or a build error. The log will say which.

### After it is up

1. **Fix the watchdog liveness check** so a crash loop cannot look healthy. Check
   `http://localhost:3000/` and `/v1/health`, or use restart counts, not
   `.State.Running`.
2. **Make the watchdog fail loudly.** A silent watchdog that cannot detect failure is
   worse than no watchdog. Send a message when a redeploy is attempted and when it
   fails.
3. **Get the tree clean** and keep it clean. The deploy path assumes `main`.

## Production hosting: planned, not built

**Established** from `ARCHITECTURE.md` §1.0 (U-08) and the external-services brief.

- **Decision:** Hetzner VPS, **CX/CAX line** (not CPX — CPX prices rose in 2026),
  roughly **CX23 2 vCPU / 4 GB / 40 GB at ~US$6–7/month**, self-hosting Postgres 16/17
  + PostGIS, MinIO, the Ktor app image, and the async verification worker on
  one always-on box.
- **Why self-host:** full extension control so a managed tier cannot drop PostGIS.
- **Alternative considered:** Neon (managed Postgres with native PostGIS, free /
  scale-to-zero) as the zero-ops $0 option.
- **Migration is cheap by design:** Jib OCI images plus env-var connection strings;
  moving the DB is `pg_dump`/restore plus one JDBC URL (<1 day).
- **Avoid provider-specific lock-in.** EKS was rejected as an MVP option
  (~$60–300/month).

**Observed:** no SSH config, no server, no deployed production artifact. The only
running environment is the local Docker stack behind the dev tunnel. **Production is
unbuilt.** Do not describe the product as deployed or live.

**Estimated M1 run-rate:** ≈ $8–10/month total (Hetzner ~$7, domain ~$1, maps ~$0,
Gemini <$1, email $0, storage $0, plus a one-time ~$5 Chrome Web Store fee for
extensions).

## CI: there is none

**Observed.** There is no `.github/workflows/` directory. The rule "CI/build status
must be green on the PR head" was enforced by humans and agents running
`./scripts/backend-test.sh` and `./scripts/frontend-test.sh` manually and reporting
the result. That worked, but it is unverifiable after the fact.

**Recommendation:** add a minimal GitHub Actions workflow that runs both scripts on
`pull_request` and on `push` to `main`. It is a few lines and it removes the single
biggest "trust me" from the process. If you do this first, you will prevent more
breakage than any other infrastructure change.

## Ports and URLs cheat sheet

| What | Where |
|------|-------|
| Backend API | `http://localhost:8080` |
| Backend health | `http://localhost:8080/v1/health` |
| Backend API root | `/v1/...` (OpenAPI under `backend/openapi/`) |
| Frontend | `http://localhost:3000` |
| Public preview | `https://dev.tahirslist.com` |
| Postgres/PostGIS | `localhost:5432`, db `halal`, user `halal` |
| MinIO S3 API | `http://localhost:9000`, bucket `halal` |
| MinIO console | `http://localhost:9001`, `minioadmin`/`minioadmin` |
| Hermes webhook platform | `http://localhost:8644` |
| GitHub repo | `github.com/Losers-Lab/tahirs-list` |
| Shortcut workspace | `app.shortcut.com/halal-food-finder` |

## Known operational debt (prioritized)

1. **No database backup or restore procedure.** Highest risk. Fix first.
2. **Watchdog cannot detect a crash loop.** Fix the liveness check and make failures
   loud.
3. **No CI pipeline.** Manual green-build reporting is unverifiable.
4. **Dirty working tree on a feature branch.** Breaks the deploy path's assumptions.
5. **42 stale worktrees.** Noise and disk. Prune after checking for unmerged work.
6. **Seed image 403s.** Several seed photo URLs are dead; those listings render
   without images. Re-host the images in MinIO or replace the URLs.
7. **Secrets in the working tree.** `.cloudflared-token.txt` is gitignored but still a
   credential on disk. Move to a secret store.
8. **Dev credentials are defaults.** `halal/halal` and `minioadmin/minioadmin` are
   fine for localhost, unacceptable on a public host. Change them when you provision.
