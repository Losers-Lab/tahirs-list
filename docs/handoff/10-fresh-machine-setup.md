---
type: handoff-document
title: "10 — Fresh Machine Setup (Docker, from zero)"
description: "A complete, machine-independent procedure to go from a brand-new computer with nothing installed to a running Tahir's List stack: Docker setup per OS, backing services, backend, frontend, smoke tests, teardown, and troubleshooting."
tags: [tahirs-list, setup, onboarding, docker, docker-compose, wsl2, macos, linux, postgis, minio, ktor, sveltekit]
status: active
generated: 2026-11-02
generated_by: adnan
verified: false
audience: "A developer on a computer that has never run this project."
sources:
  - resource: backend/docker-compose.yml
    title: "PostGIS + MinIO service definitions"
  - resource: scripts/backend-test.sh
    title: "Containerized backend build/test"
  - resource: scripts/frontend-test.sh
    title: "Containerized frontend build/test"
  - resource: scripts/backend-live.sh
    title: "Live-stack backend launcher"
  - resource: backend/bootstrap/src/main/kotlin/com/tahirslist/bootstrap/config/AppConfig.kt
    title: "Environment-variable configuration (APP_*)"
  - resource: scripts/frontend-dev.sh
    title: "SvelteKit dev server and API proxy target"
  - resource: AGENTS.md
    title: "Containerized-execution mandate"
---

# 10 — Fresh Machine Setup

This document replaces the assumption that you are working on the machine the project
was built on. Nothing here depends on a particular user account, home directory, hostname,
or Hermes installation. Every path is relative to the repository root unless it starts
with `~/`.

Read [05 — Operations and Infrastructure](05-operations-and-infrastructure.md) **after**
this document if you want to understand how the original machine was wired. That document
describes the old host, including automation that does not travel with the repo. This one
gets you running.

## What you are setting up

Five things run. Four are containers; the fifth is a container that runs your build.

```
                          your browser
                               │
                               ▼
                    ┌──────────────────────┐
                    │ tahirs-list-frontend │  Next.js 16 on node:22, port 3000
                    │ (container)          │  proxies /v1/* to the backend
                    └──────────┬───────────┘
                               │ http://tahirs-list-backend:8080
                               ▼
                    ┌──────────────────────┐
                    │ tahirs-list-backend  │  Kotlin / Ktor 3, port 8080
                    │ (container)          │  runs ./gradlew :bootstrap:run
                    └─────┬──────────┬─────┘
                          │          │
              jdbc ───────┘          └─────── s3
                          ▼                     ▼
              ┌────────────────────┐  ┌───────────────────┐
              │ tahirs-list-postgis│  │ tahirs-list-minio │
              │ PG17 + PostGIS     │  │ S3-compatible     │
              │ port 5432          │  │ ports 9000/9001   │
              └────────────────────┘  └───────────────────┘
```

All five join one Docker bridge network, `tahirs-list_default`. Containers address
each other by **service name** (`postgis`, `minio`), not by `localhost`. The browser only
ever talks to port 3000; Next.js rewrites `/v1/*` to the backend so the browser stays
same-origin and the backend needs no CORS configuration.

## What you need before you start

### Hardware

| Resource | Minimum | Comfortable |
|---|---|---|
| RAM | 8 GB | 16 GB |
| Free disk | 15 GB | 30 GB |
| CPU | 2 cores | 4 cores |

The first build is the expensive part: the Gradle dependency cache and the npm cache are
several GB. After that, incremental runs are cheap.

### Software

| Tool | Why | Notes |
|---|---|---|
| **Docker Engine 25+** (Docker Desktop 4.28+) | Everything runs in containers | **Inferred** minimum from the Testcontainers API pin (`api.version=1.44`) noted in `backend/README.md`. Newer is better. |
| **Docker Compose v2** | Backing services | Ships with Docker Desktop; on Linux install the plugin. |
| **Git** | Clone and branch | Any recent version. |
| **A terminal** | All commands below | On Windows, use the **WSL2** shell, not PowerShell. |
| `gh` (optional) | Pull requests, issues | `https://cli.github.com` |
| `cloudflared` (optional) | Public preview URL | Only if you want a public hostname. |

### Explicitly NOT needed on the host

**Do not install a JDK, Gradle, Node, or npm.** `AGENTS.md` makes containerized execution
mandatory: no language toolchains on the host, and no `apt install` / `sdkman` / `brew install`
for a runtime. Every build and test runs inside a container. If a toolchain seems missing,
that is a signal to containerize, not to install.

### Accounts and access

- Read/write access to `https://github.com/Losers-Lab/tahirs-list`.
- The **Cloudflare tunnel token** for `dev.tahirslist.com` — only if you want the public
  preview. It is **not** in the repository and is not recoverable from the code. Ask the
  founder.
- Shortcut access (`app.shortcut.com/halal-food-finder`) for product requirements.

---

## Step 0 — Install Docker for your operating system

### Windows 10/11 (recommended path: WSL2 + Docker Desktop)

1. Install **WSL2** and a distribution:
   ```powershell
   wsl --install -d Ubuntu
   ```
   Reboot if prompted, then open Ubuntu and create your Linux user.

2. Install **Docker Desktop for Windows**. In **Settings → Resources → WSL Integration**,
   enable integration for your Ubuntu distribution. Docker Desktop must be running for any
   of the commands below to work.

3. **Clone the repository inside the WSL filesystem**, not on the Windows drive:
   ```bash
   mkdir -p ~/programs && cd ~/programs
   git clone https://github.com/Losers-Lab/tahirs-list.git
   cd tahirs-list
   ```
   A clone under `/mnt/c/...` works but is markedly slower for npm/Gradle builds and can
   corrupt the executable bit on `gradlew`. Use the Linux home directory.

4. Turn off CRLF translation **before** building, or `gradlew` will fail with
   `bad interpreter: /bin/sh^M`:
   ```bash
   git config --global core.autocrlf false
   git rm --cached -r . >/dev/null 2>&1 || true
   git reset --hard
   ```
   Run the `git config` line *before* the clone if you can; it is simpler.

### macOS

1. Install **Docker Desktop for Mac** (Apple Silicon or Intel build as appropriate) and start it.
2. If the PostGIS image has no native build for your architecture, Docker Desktop's
   **Settings → General → "Use Rosetta for x86_64/amd64 emulation"** lets it run the amd64
   image. Alternatively export `DOCKER_DEFAULT_PLATFORM=linux/amd64` in the shell you build
   from. Expect slower builds under emulation.
3. Clone anywhere in your home directory:
   ```bash
   mkdir -p ~/programs && cd ~/programs
   git clone https://github.com/Losers-Lab/tahirs-list.git
   cd tahirs-list
   ```

### Linux (Ubuntu/Debian)

1. Install Docker Engine and the Compose plugin:
   ```bash
   curl -fsSL https://get.docker.com | sudo sh
   sudo usermod -aG docker "$USER"
   ```
   Log out and back in so the group change applies.

2. Verify:
   ```bash
   docker version
   docker compose version
   ```

3. Clone:
   ```bash
   mkdir -p ~/programs && cd ~/programs
   git clone https://github.com/Losers-Lab/tahirs-list.git
   cd tahirs-list
   ```

> **Linux note:** `scripts/backend-test.sh` mounts `/var/run/docker.sock`. That exists
> natively on Linux and inside WSL2 with Docker Desktop integration. It does **not** exist
> in a plain container or on macOS at that exact path — see Troubleshooting.

---

## Step 1 — Sanity-check Docker before building anything

Run these from the repository root. Fix failures here rather than debugging them later.

```bash
docker version                 # client AND server must both print
docker compose version         # v2.x
docker info --format '{{.ServerVersion}}'
docker run --rm hello-world    # proves the daemon can pull and run
```

**Expected:** `hello-world` prints its banner. If it does not, the Docker daemon is not
running or your user cannot reach the socket. On Windows, confirm Docker Desktop is started
and WSL integration is on. On Linux, confirm you are in the `docker` group.

---

## Step 2 — Start the backing services

The database and object store are defined in `backend/docker-compose.yml` and are the only
services you start with Compose. The application containers are launched by scripts.

```bash
cd backend
docker compose up -d
cd ..
```

This starts three containers:

| Container | Image | Purpose | Ports |
|---|---|---|---|
| `tahirs-list-postgis` | `postgis/postgis:17-3.4` | PostgreSQL 17 + PostGIS 3.4; Flyway migrates on app boot | `5432` |
| `tahirs-list-minio` | `pgsty/silo:latest` | S3-compatible object store for restaurant photos | `9000` (API), `9001` (console) |
| `tahirs-list-minio-init` | `pgsty/silo:latest` | One-shot: waits for the object store, creates the `halal` bucket, exits | none |

> **Why not `minio/minio`?** MinIO deleted `minio/minio` and `minio/mc` from Docker Hub in
> September 2026, and `quay.io/minio` now rejects anonymous pulls, so the previously pinned
> tags no longer resolve on a fresh machine. `pgsty/silo` is a maintained, drop-in
> MinIO-compatible fork; one image serves both the server and the `mc` client used by the
> bucket initializer.

The compose file sets the project name to `tahirs-list`, so the network is always
`tahirs-list_default` and the volumes are prefixed `tahirs-list_`.

### Verify the services are healthy

```bash
docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
docker compose -f backend/docker-compose.yml logs minio-init
```

**Expected:**

- `tahirs-list-postgis` — `Up ... (healthy)`
- `tahirs-list-minio` — `Up ... (healthy)`
- `tahirs-list-minio-init` — `Exited (0)` and its log ends with `MinIO bucket halal ready`

If `minio-init` failed, the bucket does not exist and every image upload will fail later.
Re-run it:

```bash
docker compose -f backend/docker-compose.yml up minio-init --force-recreate
```

---

## Step 3 — Build and run the backend

The backend runs from source inside an `eclipse-temurin:21-jdk` container, with the repo's
`backend/` directory bind-mounted to `/workspace`. The script also ensures the backing
services are up, so it is safe to run even if you skipped Step 2.

```bash
./scripts/backend-live.sh
```

What this does, in order:

1. `docker compose up -d` on the backing services.
2. Removes any existing `tahirs-list-backend` container.
3. Starts a new one with the PostGIS JDBC URL and the
   `APP_S3_*` variables pointing at `http://minio:9000`.
4. Runs `./gradlew --no-daemon :bootstrap:run` inside the container.

**The first run downloads the Gradle wrapper and all dependencies. Budget 5–15 minutes**
depending on network. Subsequent runs reuse the `tahirs-list-gradle-cache` volume and are much
faster. The command returns immediately (the container is detached); the build continues
in the background.

### Watch the build and wait for health

```bash
docker logs -f tahirs-list-backend
```

Look for Ktor's `Application started` / `Responding at http://0.0.0.0:8080` line.
In a second terminal, poll:

```bash
curl -s http://localhost:8080/v1/health
```

Repeat until it returns `200`. This can take several minutes on the first boot because
Flyway applies all 20 migrations and the seed-photo ingest runs.

### What the live stack changes

The launcher sets two environment variables, and both matter:

- **Real S3 storage** (`APP_S3_ENDPOINT`). Without it the app uses an in-memory image stub
  and every `GET /v1/listings/{id}/image` returns 404. With it, images go to MinIO.
- **Seed-photo ingest on boot** (`APP_SEED_INGEST_ENABLED=true`). A runner reads
  `seed-photos.json` and stores full + thumbnail variants for seeded listings.
  **It fetches the photo URLs from the internet.** Some of those URLs returned 403
  during development; the runner logs the failure and continues. A handful of seed
  photos may therefore be missing. That is a known defect, not a setup error.

### Config you may need to override

The defaults assume the compose service names and the development credentials. Override by
exporting variables before running the script:

| Variable | Default | When to change |
|---|---|---|
| `APP_S3_ENDPOINT` | `http://minio:9000` | You point at real S3/R2 instead of MinIO |
| `APP_S3_ACCESS_KEY` | `minioadmin` | You change the MinIO credentials |
| `APP_S3_SECRET_KEY` | `minioadmin` | Same |
| `APP_S3_REGION` | `us-east-1` | Your object store requires a real region |
| `APP_S3_BUCKET` | `halal` | You use a different bucket name |

Database credentials are hardcoded in `scripts/backend-live.sh` (`halal`/`halal` against
`postgis:5432`). For a non-local environment, edit the script or set
`APP_DB_*` — but do not commit real credentials.

### About the JWT signing key

No key is configured by default. The app generates an **ephemeral RSA key pair on every
boot**, which means **every backend restart invalidates all issued access tokens** and users
must log in again. That is acceptable for local development and unacceptable in production.

For production, generate a fresh key and supply it base64-encoded:

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out /tmp/jwt.pem
openssl pkcs8 -topk8 -nocrypt -in /tmp/jwt.pem -outform DER | base64 -w0 > /tmp/jwt.b64
```

Pass the contents of `/tmp/jwt.b64` as `APP_JWT_RSA_PRIVATE_KEY_BASE64`. The loader
**refuses to boot** under a `prod` profile if the key is blank or matches the committed
test fixture. Do not use the test key.

---

## Step 4 — Build and run the frontend

Run this **from the repository root**, not from `frontend/`, because the mount path is
relative to the current directory.

```bash
docker rm -f tahirs-list-frontend 2>/dev/null

docker run -d \
  --name tahirs-list-frontend \
  --restart unless-stopped \
  -p 3000:3000 \
  --network tahirs-list_default \
  -v "$PWD/frontend":/app \
  -w /app \
  -v /app/node_modules \
  -v tahirs-list-npm-cache:/npm-cache \
  -e npm_config_cache=/npm-cache \
  -e API_PROXY_TARGET='http://tahirs-list-backend:8080' \
  node:22 bash -lc "npm install --no-audit --no-fund && npm run build && npm run start"
```

Notes:

- `API_PROXY_TARGET` **must** be the container name, not `localhost`. Inside the frontend
  container, `localhost` is itself.
- The container performs `npm install`, `next build`, then `next start`. The first build
  takes several minutes. Watch it:
  ```bash
  docker logs -f tahirs-list-frontend
  ```
- The bind mount means source edits are visible immediately, but Next.js production mode
  does **not** hot reload. Rebuild and restart the container to see changes.
- `/app/node_modules` is an anonymous volume so a host `node_modules` cannot poison the
  container build. It is discarded when the container is removed.

### For active frontend development

Production mode is slow to iterate on. Run the dev server in a container instead:

```bash
docker rm -f tahirs-list-frontend-dev 2>/dev/null

docker run -it --rm \
  --name tahirs-list-frontend-dev \
  -p 3000:3000 \
  --network tahirs-list_default \
  -v "$PWD/frontend":/app \
  -w /app \
  -v /app/node_modules \
  -v tahirs-list-npm-cache:/npm-cache \
  -e npm_config_cache=/npm-cache \
  -e API_PROXY_TARGET='http://tahirs-list-backend:8080' \
  node:22 bash -lc "npm install --no-audit --no-fund && npm run dev"
```

---

## Step 5 — Verify end to end

Open `http://localhost:3000`. Then walk this checklist. It exercises every layer, so a
failure tells you which layer broke.

| # | Check | Expected | Layer tested |
|---|---|---|---|
| 1 | `http://localhost:3000` | Home page renders, no error overlay | Next.js |
| 2 | `curl -s localhost:8080/v1/health` | `200`, health JSON | Backend + datasource |
| 3 | `curl -s localhost:8080/v1/api-docs \| head -c 200` | OpenAPI 3.1 JSON begins | Backend routes |
| 4 | Search or browse listings on the home page | Listings appear | Backend → PostGIS → frontend proxy |
| 5 | Open a listing with a photo | Image renders | MinIO → S3 adapter → `/v1/.../image` |
| 6 | Sign up with a fresh email, then log in | Account created, session established | Auth, JWT, Flyway tables |
| 7 | Reload the page while logged in | Session persists | Refresh-token cookie |
| 8 | Restart the backend container and reload | You are **logged out** | Ephemeral JWT key (expected) |

Check 8 is not a bug in your setup — it is the ephemeral-key behavior described in Step 3.
It is the clearest proof that you understand the auth configuration.

### Verify the database and bucket directly (optional)

```bash
docker exec tahirs-list-postgis psql -U halal -d halal -c '\dt'
docker exec tahirs-list-postgis psql -U halal -d halal -c 'select count(*) from users;'
docker exec tahirs-list-minio mc ls local/halal
```

The first should list tables including `users`, `refresh_tokens`, `listings`, and the
verification tables. The third should list stored image objects if seed ingest succeeded.

---

## Step 6 — Run the test suites

Both scripts are self-contained and require only Docker. Run them before you trust the
checkout.

```bash
./scripts/backend-test.sh          # full build + all tests (slow, first run very slow)
./scripts/backend-test.sh test-only  # :persistence:test only (faster smoke check)
./scripts/frontend-test.sh         # npm ci + next build
```

The backend suite uses **Testcontainers**, so it starts real PostGIS containers as siblings.
`scripts/backend-test.sh` handles the two networking requirements for you:

- `TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal` plus
  `--add-host host.docker.internal:host-gateway`, so the build container can reach sibling
  containers bound on the host.
- `TESTCONTAINERS_RYUK_DISABLED=true`, because Ryuk's reaper has the same cross-container
  networking problem. Cleanup falls to `--rm`.

It also mounts `/var/run/docker.sock`. That mount is **root-equivalent on the host** and is
accepted deliberately so tests can manage sibling containers. Do not extend the pattern to
untrusted code.

The test suite uses its **own** Gradle cache volume (`tahirs-list-gradle-test-cache`), separate
from the live backend's (`tahirs-list-gradle-cache`). This is deliberate: Gradle takes a lock on
its user home, so two builds sharing one volume cannot run at the same time. With separate
volumes you can run tests while `tahirs-list-backend` is up. The first test run downloads the Gradle
distribution and dependencies into the new volume and takes a few minutes; later runs are
fast. If you see `Timeout waiting to lock journal cache`, something is sharing a Gradle user
home — see Troubleshooting.

**Expected:** both scripts exit `0`. If the backend suite cannot reach Docker, see
Troubleshooting.

---

## Step 7 — Optional: expose a public preview URL

The original setup used a **named** Cloudflare tunnel so the hostname stayed stable across
restarts. Do not use an ephemeral quick tunnel; it rotates the URL on every restart and
breaks the webhook configuration.

```bash
# One-time, on the machine:
cloudflared tunnel login
cloudflared tunnel create tahirs-list-dev
cloudflared tunnel route dns tahirs-list-dev dev.tahirslist.com
```

Run the tunnel as a container on the same network:

```bash
docker rm -f tahirs-list-tunnel-dev 2>/dev/null

docker run -d \
  --name tahirs-list-tunnel-dev \
  --restart unless-stopped \
  --network tahirs-list_default \
  cloudflare/cloudflared:latest \
  tunnel --no-autoupdate run --token "$CLOUDFLARE_TUNNEL_TOKEN"
```

The tunnel's ingress rule must point `dev.tahirslist.com` at `http://tahirs-list-frontend:3000`
(service name, not `localhost:3000` — the original configuration pointed at
`http://localhost:3000`, which only works when the tunnel shares the host's network
namespace).

The token is a secret. Keep it out of git; `.gitignore` already excludes
`.cloudflared-token.txt` and `.env`.

---

## Step 8 — Optional: automate redeploys without Hermes

**This is a portability trap worth understanding.** The original redeploy automation lived
in `~/.hermes/profiles/adnan/scripts/` — **outside the repository**. It does not travel with
the code. A fresh clone has no webhook receiver, no worker, and no watchdog.

Three honest options:

1. **Manual redeploy (simplest).** After merging to `main`, run:
   ```bash
   git checkout main && git pull
   ./scripts/backend-live.sh
   docker rm -f tahirs-list-frontend
   # re-run the frontend docker run command from Step 4
   ```
2. **GitHub Actions (recommended for a team).** There is **no CI configured** (no
   `.github/workflows/`). Adding a workflow that runs the two test scripts and, on `main`,
   triggers a deploy is the cleanest replacement. It also closes the "no CI" risk in
   [07](07-current-state-and-risks.md).
3. **Port the scripts into the repo.** Move the webhook/worker/watchdog scripts into
   `scripts/` so they are versioned and reviewable. If you do, fix the watchdog first: it
   reads Docker's `.State.Running`, which stays `true` during a crash loop, so it reports a
   crash-looping container as healthy. Check `.State.Restarting`, `.State.ExitCode`, or an
   HTTP health endpoint instead.

---

## Everyday commands

| Task | Command |
|---|---|
| Start backing services | `docker compose -f backend/docker-compose.yml up -d` |
| Stop backing services (keep data) | `docker compose -f backend/docker-compose.yml down` |
| Start/restart backend | `./scripts/backend-live.sh` |
| Rebuild frontend | `docker rm -f tahirs-list-frontend` then re-run Step 4 |
| Backend logs | `docker logs -f tahirs-list-backend` |
| Frontend logs | `docker logs -f tahirs-list-frontend` |
| Health check | `curl -s localhost:8080/v1/health` |
| Backend tests | `./scripts/backend-test.sh` |
| Frontend tests | `./scripts/frontend-test.sh` |
| List containers | `docker ps -a --format 'table {{.Names}}\t{{.Status}}'` |
| MinIO console | `http://localhost:9001` (`minioadmin` / `minioadmin`) |
| Swagger UI | `http://localhost:8080/v1/swagger-ui/index.html` |
| OpenAPI spec | `http://localhost:8080/v1/api-docs` |

## Teardown and reset

```bash
# Stop and remove containers + the network. DATA IS PRESERVED in named volumes.
docker compose -f backend/docker-compose.yml down
docker rm -f tahirs-list-backend tahirs-list-frontend tahirs-list-tunnel-dev 2>/dev/null

# DESTRUCTIVE: also delete the database and object-store data.
docker compose -f backend/docker-compose.yml down -v
docker volume rm tahirs-list-gradle-cache tahirs-list-npm-cache 2>/dev/null

# See what is using disk
docker system df
```

`down -v` deletes `tahirs-list_pgdata` and `tahirs-list_miniodata`. There is no
backup configured. If the data matters, dump it first:

```bash
docker exec tahirs-list-postgis pg_dump -U halal -d halal > ~/halal-backup-$(date +%F).sql
```

---

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `Cannot connect to the Docker daemon` | Docker not running, or WSL integration off | Start Docker Desktop; enable WSL integration; on Linux check `groups` |
| `permission denied ... /var/run/docker.sock` | User not in `docker` group | `sudo usermod -aG docker $USER`, then re-login |
| `bad interpreter: /bin/sh^M` running `gradlew` | CRLF line endings from a Windows clone | `git config --global core.autocrlf false`, re-clone or `git reset --hard` |
| Backend container exits `127` | Mount path wrong / `gradlew` not executable | Confirm you ran the script from the repo root; `chmod +x backend/gradlew`; check `docker logs tahirs-list-backend` |
| Backend container exits with Flyway error | PostGIS not ready yet | Wait for `(healthy)`, then `docker rm -f tahirs-list-backend && ./scripts/backend-live.sh` |
| `Connection to 172.17.0.1 refused` in tests | Testcontainers networking | Use `scripts/backend-test.sh`, which sets the host override and gateway alias. Do not call Gradle directly. |
| `Timeout waiting to lock journal cache (/gradle-home/...)` | Two Gradle builds share one user home — commonly the live backend and the test suite on the same cache volume | Use `scripts/backend-test.sh` (separate `tahirs-list-gradle-test-cache` volume), or stop `tahirs-list-backend` before testing. |
| Backend tests cannot find Docker socket | Running on macOS/plain container | The script expects `/var/run/docker.sock`. On Docker Desktop for Mac, symlink or run tests inside a Linux VM/WSL2. |
| Frontend container loops with `ENOENT ... /app/package.json` | Wrong mount path, or started outside the repo root | Run the `docker run` from the repository root; verify `docker inspect tahirs-list-frontend --format '{{json .Mounts}}'` |
| Frontend up but API calls 502 | `API_PROXY_TARGET` pointing at `localhost` | Recreate with `http://tahirs-list-backend:8080` |
| Frontend up but 500 from `/v1/*` | Backend still booting | Poll `localhost:8080/v1/health` first |
| Listing images 404 | Backend started without the `live` profile, or bucket missing | Use `./scripts/backend-live.sh`; check `docker logs tahirs-list-minio-init` for `MinIO bucket halal ready` |
| First build seems hung | Gradle downloading dependencies | `docker logs -f tahirs-list-backend`; allow 15 min; check network/proxy |
| `port is already allocated` | Another process on 3000/8080/5432/9000 | `docker ps`, or stop the conflicting service; `lsof -i :3000` |
| Out of disk | Old images, worktrees, caches | `docker system prune`, `docker system df`, remove stale `.worktrees/` |
| Seed photos missing from listings | Upstream URLs return 403 | Known defect. Runner logs and continues. See [07](07-current-state-and-risks.md). |

## What is NOT portable from the old machine

Be explicit with yourself about this list. None of it is in the repository:

| Thing | Where it lived | Consequence |
|---|---|---|
| Redeploy webhook + worker + watchdog | `~/.hermes/profiles/adnan/scripts/` | No automatic deploy on a fresh machine. Use Step 8. |
| Cron schedules (4 jobs) | `~/.hermes/profiles/adnan/cron/jobs.json` | No sprint/coordination automation. Decide whether you want it. |
| Kanban execution history | `~/.hermes/kanban/boards/halal-food-finder/kanban.db` | The work ledger's raw data stays behind. The summary is in [06](06-work-ledger.md). |
| Cloudflare tunnel token | `.cloudflared-token.txt` (gitignored) | You cannot recreate the tunnel without the founder. |
| Agent personas (7 profiles) | `~/.hermes/profiles/*/SOUL.md` | Summarized in [04](04-team-and-roles.md). |
| 42 historical worktrees | `.worktrees/` (gitignored) | Unmerged branches may exist only there. Audit before deleting. |
| Database contents | Docker volume `tahirs-list_pgdata` | A fresh machine starts with an empty database and only seed data. |

If you need the actual data (not just the schema), dump it from the old machine **before**
decommissioning it. This is the single most time-sensitive item in this document.

## Before real users

This setup is a **development** stack. It is not production-safe as written. Before exposing
it to real users:

1. Change the database credentials (`halal`/`halal`) and the MinIO credentials
   (`minioadmin`/`minioadmin`).
2. Supply a real, non-committed JWT signing key and run with the `prod` profile.
3. Add TLS termination in front of the stack (the tunnel provides this; a raw port does not).
4. Configure and **test** database backups and object-store backups. Restore once to prove it.
5. Add CI so tests run on every PR.
6. Move secrets out of scripts and into a secret store or environment injection.
7. Pin image tags instead of `latest` for the tunnel and any added service.
