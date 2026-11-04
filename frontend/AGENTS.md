<!-- SvelteKit agent rules for `frontend/` -->

# This is SvelteKit 3 + Svelte 5 (runes)

This app is **not** React, Next.js, or SvelteKit 2. Before writing components,
routes, load functions, or stores, read the version-matched docs (the `svelte`
MCP server is registered in `opencode.json`; enable it for SvelteKit work).

- **Runes only**: `$state`, `$derived`, `$props`, `$bindable`, `$effect`,
  `$props.id()`. No `export let`, no `$:` reactive statements, no legacy stores
  in new code. `$effect` is a last resort — prefer `$derived` and events.
- **Module aliases**: `#lib/...` (SvelteKit 3). `$lib` was **removed** — do not
  use it. `$app/env` exposes `browser`/`dev`/`building`; private env comes from
  `src/env.ts` via `$app/env/private` (never import private env in browser code).
- **Routing**: file-based under `src/routes`. Import page types as
  `./$types`. Pages import the component as `import Page from './+page.svelte'`.
- **State stores**: runes singletons live in `*.svelte.ts` modules
  (`src/lib/auth/session.svelte.ts`, `src/lib/favorites/store.svelte.ts`).
  Do not create per-request module state that is mutated during SSR — the
  server process is shared across requests. Mutate only from browser paths.
- **API access**: always same-origin through the `/v1` proxy in
  `src/hooks.server.ts`. Never call the backend cross-origin: the HttpOnly
  refresh cookie, CSP (`connect-src 'self'`, `img-src 'self'`) and relative
  image URLs all depend on it.
- **Security contract (do not regress)**: access token in memory only; the
  refresh token never exists in JS; `attachAuth: false` on refresh/logout;
  only the non-secret identity hint `{accountId, role, email}` persists in
  `localStorage["hff.session.v1"]`.
- **Generated / do-not-edit**: `.svelte-kit/`, `build/`, `src/lib/api/schema.d.ts`
  (regenerate with `pnpm codegen:api`), `pnpm-lock.yaml` (change deps with pnpm
  in the container, never by hand).
- **Commands** (all containerized from the repo root):
  `./scripts/frontend-test.sh` (install + check + test + build),
  `./scripts/frontend-test.sh test-only`, `./scripts/frontend-dev.sh`.
- **Done means**: `pnpm check` 0 errors, `pnpm test` green, `pnpm build` green,
  and `pnpm format` applied before committing.
