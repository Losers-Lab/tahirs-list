# End-to-end browser checks

Every PR that changes `frontend/` is exercised in a real browser before it is
merged, and the PR carries screenshots of the states it changed. Component tests
(jsdom) are not a substitute: they cannot see hydration, CSS, the native
`<dialog>`, the image pipeline, or the app's CSP/HSTS behaviour.

The founder made this a standing rule on 2026-11-10 (see `AGENTS.md`, "Browser
pass for frontend PRs").

## Requirements

- **Docker only.** No Node, Playwright or browser on the host.
- Run against a **locally built stack** (backend + dev server), never against
  `dev.tahirslist.com` — the point is to test the branch before it ships.
- One script per story, committed under `e2e/`, so the pass is repeatable and
  reviewable rather than a one-off click-through.
- Screenshots go in `docs/features/<STORY>/screenshots/` and are embedded in the
  PR (a comment is fine).
- The script **asserts** the acceptance criteria; a mismatch exits non-zero, so
  the pass is a gate, not a demo.

## Running one (SC-205 as the worked example)

1. **Backing services**

   ```bash
   docker compose -f backend/docker-compose.yml up -d
   ```

2. **Backend on a free port** (8090 here; 8080 is usually the live container)

   ```bash
   docker run -d --name sc205-backend --network tahirs-list_default -p 8090:8080 \
     -v "$PWD/backend:/workspace" -v tahirs-list-gradle-cache-sc205:/root/.gradle \
     -w /workspace \
     -e APP_PROFILE=live \
     -e APP_DB_URL=jdbc:postgresql://postgis:5432/halal \
     -e APP_DB_USER=halal -e APP_DB_PASSWORD=halal \
     -e APP_S3_ENDPOINT=http://minio:9000 \
     -e APP_S3_ACCESS_KEY=minioadmin -e APP_S3_SECRET_KEY=minioadmin \
     -e APP_S3_REGION=us-east-1 -e APP_S3_BUCKET=halal \
     -e APP_SEED_INGEST_ENABLED=false -e APP_COOKIE_SECURE=false \
     eclipse-temurin:21-jdk \
     ./gradlew --no-daemon --project-cache-dir=/tmp/gradle-project-cache :bootstrap:run
   ```

   Poll `http://localhost:8090/v1/health` until it returns 200 (Flyway runs on
   boot, so the first start also applies the branch's migrations).

3. **Dev server** on another free port, proxying to that backend

   ```bash
   docker run -d --name sc205-frontend --add-host host.docker.internal:host-gateway \
     -p 5175:5175 -v "$PWD/frontend:/app" -w /app \
     -v tahirs-list-pnpm-store:/pnpm-store \
     -v tahirs-list-frontend-node_modules:/app/node_modules \
     -e API_PROXY_TARGET=http://host.docker.internal:8090 \
     -e NODE_OPTIONS=--max-old-space-size=768 \
     node:24-slim bash -lc "corepack enable >/dev/null 2>&1; \
       pnpm config set store-dir /pnpm-store >/dev/null 2>&1; \
       pnpm dev --host 0.0.0.0 --port 5175"
   ```

4. **Promote the test admin** (D-10 has no endpoint, by design)

   ```bash
   docker exec tahirs-list-postgis psql -U halal -d halal \
     -c "UPDATE users SET role='ADMIN' WHERE email='<admin email>'"
   ```

5. **Run the browser script**

   ```bash
   IP=$(docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' sc205-frontend)
   docker run --rm --network tahirs-list_default -v "$PWD:/work" -w /tmp/runner \
     -e PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1 \
     -e BASE_URL=http://localhost:5175 \
     -e API_URL=http://$IP:5175 \
     -e HOST_MAP=$IP \
     -e OWNER_EMAIL=... -e ADMIN_EMAIL=... \
     -e FIXTURE=/work/e2e/fixtures/storefront.jpg \
     -e SHOT_DIR=/work/docs/features/SC-205/screenshots \
     mcr.microsoft.com/playwright:v1.65.0-noble \
     bash -lc "npm i playwright@1.65.0 >/dev/null 2>&1 && \
       cp /work/e2e/sc205-photo-upload.cjs . && node sc205-photo-upload.cjs"
   ```

## Why the two URLs and the host mapping

The dev app sends HSTS and a CSP with `upgrade-insecure-requests`. Over a bare
container IP Chromium upgrades subresources to `https`, hydration fails and the
page silently does nothing. Mapping `localhost` to the container IP
(`--host-resolver-rules`) keeps the origin *trustworthy*, so the app hydrates.
Node's `request` does not share the browser's resolver, hence `API_URL` for the
setup calls and `BASE_URL` for the browser.

Also note Vite rejects unknown `Host` headers, so `host.docker.internal` is not
accepted; use the mapped `localhost` or the container IP.

## What SC-205's pass proves

`e2e/sc205-photo-upload.cjs` drives the real UI and asserts:

| Check | Evidence |
|---|---|
| Empty listing shows a labelled frame and *Add a photo* | `01-owner-empty-state.png` |
| The dialog offers both sources | `02-upload-dialog.png` |
| Upload succeeds and the URL gains a version | `03-owner-after-upload.png` |
| The same versioned URL survives a reload | `04-after-reload.png` |
| An admin sees *Replace photo* on a foreign listing | `05-admin-foreign-listing.png` |
| Replace discloses the loss first | `06-admin-replace-disclosure.png` |
| A signed-in non-owner is offered no control | `07-non-owner-no-control.png` |
