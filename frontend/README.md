# Tahir's List — frontend (SvelteKit 3)

The web app for Tahir's List: a searchable, verification-first directory of
halal restaurants. SvelteKit 3 (Svelte 5 runes) + Vite 8 (Rolldown) + Tailwind
CSS v4, served by the Node adapter with SSR for crawlable public listing pages.

> Design tokens live in `src/app.css` (`@theme`), derived from the
> founder-ratified "Stamps & Search" v2 system (`docs/design/tokens.md`).
> Never hardcode hex values in components.

## Stack

| Layer | Choice |
|---|---|
| Framework | SvelteKit 3 / Svelte 5 (runes), TypeScript |
| Build | Vite 8 (Rolldown) |
| Styling | Tailwind CSS v4 (`@theme` tokens), self-hosted Fontsource fonts |
| Tests | Vitest 4 + `@testing-library/svelte` (jsdom) |
| API | same-origin `/v1` proxy in `src/hooks.server.ts`, typed via `openapi-typescript` |
| Deploy | `@sveltejs/adapter-node` (SSR); `adapter-static` later for the Capacitor app |

## Commands (Docker only — no host Node)

From the repo root:

```bash
./scripts/frontend-test.sh              # install + svelte-check + vitest + vite build
./scripts/frontend-test.sh test-only    # check + tests only
./scripts/frontend-test.sh dev-install  # allow lockfile drift while developing
./scripts/frontend-dev.sh               # dev server on http://localhost:5173
```

Both scripts cap container memory/CPU and Rolldown worker threads; Playwright
is intentionally not part of the default run (it is the biggest memory
consumer). E2E runs are a separate, opt-in follow-up.

## Environment

- `API_PROXY_TARGET` (private, server-only) — backend origin for the `/v1`
  proxy. Defaults to `http://127.0.0.1:8080`; the dev script defaults to
  `http://host.docker.internal:8080`.

## Security-relevant behavior

- Access token: memory only. Refresh token: HttpOnly cookie, never in JS.
- Only `{accountId, role, email}` is persisted (`hff.session.v1`) as a
  non-secret identity hint.
- Security headers (CSP/HSTS/nosniff/frame denial) are applied in
  `src/hooks.server.ts`, including to proxied API responses.
