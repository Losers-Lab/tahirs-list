# SC-205 — Phase 10: Traceability and evidence

> **Purpose:** prove each acceptance criterion and record what actually ran · **Derived from:** 00, 09 · **Delta:** implementation PR (same story)

## Acceptance criteria → test → evidence

| AC | Test | Result |
|---|---|---|
| **AC-1** signed-in owner sees the control | `ListingPhotoPanel.test.ts` "shows the empty state and an Add control…" · "never offers the control to a viewer the server says may not manage" · `ListingManageEndpointTest` owner → `canManage:true` | PASS |
| **AC-2** upload an existing image or take a new one | `PhotoUploadDialog.test.ts` "offers both ways to supply a photo" (two inputs, `capture="environment"` on the camera one) | PASS |
| **AC-3** add or replace from the restaurant page | `ListingPhotoPanel.test.ts` "uploads the chosen file and tells the page to re-read on success" · `ListingImageEndpointTest` happy upload/replace (existing) | PASS |
| **AC-4** the new image is shown, and stays shown | `Sc205PhotoVersionEndpointTest` (version appears, all srcset entries agree, replace changes it, versioned URL still serves) · `ListingPhotoPanel.test.ts` success → `onUploaded` → page re-reads | PASS |
| **AC-5** a non-owner cannot change the photo | `AddListingImageTest` "a non-owner who is not an admin is unauthorized" · `ListingImageEndpointTest` 403 (existing) | PASS |
| **AC-6** an admin can change any listing's photo | `PhotoWritePolicyTest` (5 cases) · `AddListingImageTest` admin override + ownerless listing · `ListingManageEndpointTest` admin token on a foreign listing | PASS |

## Decisions → where they live

| # | Decision | Implementation |
|---|---|---|
| D-1 | Durable photo version | `V25` (`restaurant_listings.image_version`, `listing_search.image_version`), `bumpImageVersion`, `&v=` in `imageUrl`/`imageSrcset` |
| D-2 | `/manage` signal | `GetListingManage`, `GET /v1/listings/{id}/manage`, `api.getManage` |
| D-3 | Two inputs, camera via capture attr | `PhotoUploadDialog.svelte` |
| D-4 | Native `<dialog>` | `PhotoUploadDialog.svelte` (`showModal`, jsdom fallback) |
| D-5 | One UC, two named flows | `02-use-cases.md` Flow A/B; the dialog discloses the loss at 3b |
| D-6 | Partial-variant window accepted | `AddListingImage` stamps last; no revert path added |
| D-7 | `PUT` 204 + client re-read | `client.ts` `uploadListingImage`, page `refreshListing` |
| D-8 | HEIC rejected, no transcoding | `photo-upload.svelte.ts` `notImage` copy |
| D-9 | `svelte` MCP before frontend WIs | **not available** in this worktree's session catalog; repo conventions + `svelte-check` used instead (see Deviations) |
| D-10 | `ADMIN` via one-line operator SQL | `V26` widens `ck_users_role`; `V26` promotes nobody; runbook below |
| D-11 | Log line, not a table | `AuditRecorder` + `LoggingAuditRecorder`; called only on an override |
| D-12 | Photos only | content edit untouched; follow-up story still needed |
| D-13 | JWT claim is the authority | `requireActor()` from the verified principal; no DB role read on the write path |

## Operator runbook (D-10)

```sql
-- Promote the operator account. There is deliberately no endpoint for this.
UPDATE users SET role = 'ADMIN' WHERE email = '<operator email>';
```

The change applies to the **next access token** the account receives (refresh or sign-in), because the role comes from the JWT (D-13). Until then the account keeps its old role and the photo control stays hidden.

## Evidence — what actually ran

| Check | Command | Result |
|---|---|---|
| **Full backend suite** | `./gradlew test` (all modules, containerized) | **998 tests, 0 failures** |
| Domain (role, listing) | `:domain:test --tests RoleTest --tests RestaurantListingTest` | PASS |
| Schema (V25, V26) | `:persistence:test --tests Sc205SchemaMigrationTest` | PASS (3) |
| Repository | `:persistence:test --tests ExposedRestaurantListingRepositoryTest` | PASS (incl. 2 new) |
| Application | `:application:test` (4 SC-205 specs) | PASS (19) |
| HTTP endpoints | `:bootstrap:test` (manage, version, image, limits) | PASS (5 + 4 + 15 + limits) |
| Frontend check | `pnpm check` | **0 errors**, 16 warnings (pre-existing style warnings) |
| Frontend tests | `pnpm test` | **257 passed / 29 files** |
| Frontend build | `pnpm build` | green |
| Formatting | `pnpm format` | applied |

Total: **1,255 tests green** (998 backend + 257 frontend).

### Regressions the full suite caught (and their fixes)

| Symptom | Cause | Fix |
|---|---|---|
| `ListingReadEndpointTest` 500 on browse | `findAll()` has its own column list (not `LISTING_COLUMNS`), so `toListing` read a column the query did not select | added `image_version` to the `findAll` projection |
| `PostgisSmokeTest` expected 24 migrations | V25 + V26 raise the count to 26 | updated the pinned count |

Both were only visible in the full run — the targeted specs stayed green, which is exactly why the full suite is a gate.

All backend runs used the container directly (`eclipse-temurin:21-jdk`, dedicated Gradle home `tahirs-list-gradle-cache-sc205`), not the shell script.

## Browser pass (Playwright, 2026-11-10)

Run headless Chromium in `mcr.microsoft.com/playwright:v1.65.0-noble` against a
locally built stack (backend :8090 with real PostGIS + MinIO, SvelteKit dev
server :5175). Script: `e2e/sc205-photo-upload.cjs`; screenshots:
`docs/features/SC-205/screenshots/`.

| Check | Result |
|---|---|
| Empty listing → labelled frame + *Add a photo* | PASS |
| Dialog offers *Choose a photo* and *Take a photo* (camera input has `capture`) | PASS |
| Upload succeeds; the image URL gains `&v=` | PASS |
| Reload serves the **same** versioned URL (AC-4 durability) | PASS |
| Admin sees *Replace photo* on a listing they do not own (AC-6) | PASS |
| Replace discloses "the previous one cannot be restored" | PASS |
| Signed-in non-owner is offered no photo control (AC-5) | PASS |
| No uncaught browser errors | PASS |

**Bug the browser found (fixed):** the API always returns an `imageUrl`, so the
panel treated every listing as having a photo and offered *Replace* over a blank
frame. `RestaurantPhoto` now reports when every candidate failed
(`onUnavailable`), the panel falls back to its labelled empty state, and a
component test pins it. jsdom had not caught this because it does not load
images.

This pass is now a standing rule for every frontend PR — see `AGENTS.md` and
`e2e/README.md`.

## Deviations and known gaps

1. **Frontend generated OpenAPI copy was not regenerated.** `frontend/src/lib/api/openapi.json` had drifted far from `backend/openapi/v1.json` (a 3.7k-line diff across unrelated endpoints); regenerating it broke types in files this story does not touch. The backend spec carries the new `/manage` path and `ManageResponse`; the frontend copy is left for a dedicated regen chore. The hand-written client does not depend on it.
2. **`V25` also versions `listing_search`.** The design promised search cards version their URLs too; the search read model is a separate table, so it needed the column and a mirrored stamp in `bumpImageVersion`.
3. **No dev verification yet** — the PR is not merged, so `dev.tahirslist.com` checks and the Shortcut close-out are still pending.
4. **`svelte` MCP unavailable** in this session catalog (D-9); component idioms were checked against the repo's existing runes code and `svelte-check`, not the live docs.
5. ~~No full backend suite run yet~~ — now run: 998 tests, 0 failures (see above).
6. **Authorization now precedes body parsing.** The upload route checks `/manage`-equivalent policy before `receiveForm()`, so an authenticated account cannot make the server buffer and parse 8 MiB for a listing it may not write (Omar round 1). The use case still re-checks; the rule lives in `PhotoWritePolicy`.
