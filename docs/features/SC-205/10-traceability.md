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
| Domain (role, listing) | `:domain:test --tests RoleTest --tests RestaurantListingTest` | PASS |
| Schema (V25, V26) | `:persistence:test --tests Sc205SchemaMigrationTest` | PASS (3) |
| Repository | `:persistence:test --tests ExposedRestaurantListingRepositoryTest` | PASS (incl. 2 new) |
| Application | `:application:test` (4 SC-205 specs) | PASS (19) |
| HTTP endpoints | `:bootstrap:test --tests ListingManageEndpointTest --tests Sc205PhotoVersionEndpointTest --tests ListingImageEndpointTest` | PASS (5 + 4 + 14) |
| Frontend check | `pnpm check` | **0 errors**, 16 warnings (pre-existing style warnings) |
| Frontend tests | `pnpm test` | **257 passed / 29 files** |
| Frontend build | `pnpm build` | green |
| Formatting | `pnpm format` | applied |

All backend runs used the container directly (`eclipse-temurin:21-jdk`, dedicated Gradle home `tahirs-list-gradle-cache-sc205`), not the shell script.

## Deviations and known gaps

1. **Frontend generated OpenAPI copy was not regenerated.** `frontend/src/lib/api/openapi.json` had drifted far from `backend/openapi/v1.json` (a 3.7k-line diff across unrelated endpoints); regenerating it broke types in files this story does not touch. The backend spec carries the new `/manage` path and `ManageResponse`; the frontend copy is left for a dedicated regen chore. The hand-written client does not depend on it.
2. **`V25` also versions `listing_search`.** The design promised search cards version their URLs too; the search read model is a separate table, so it needed the column and a mirrored stamp in `bumpImageVersion`.
3. **No dev verification yet** — the PR is not merged, so `dev.tahirslist.com` checks and the Shortcut close-out are still pending.
4. **`svelte` MCP unavailable** in this session catalog (D-9); component idioms were checked against the repo's existing runes code and `svelte-check`, not the live docs.
5. **No full backend suite run yet** (targeted specs only). A full `test` run is required before the merge and the count recorded here.
