# SC-205 — Phase 9: Work items

> **Purpose:** the build order, tests first · **Derived from:** 00–08 (approved 2026-11-09) · **Delta:** 14 work items, all in **one implementation PR**

Backend first (migration → domain → application → persistence → controller/OpenAPI), frontend after the contract lands. Every item names the failing test that must exist before its code.

| WI | Layer | Covers | Tests first | Depends on | PR |
|---|---|---|---|---|---|
| **WI-01** | persistence | enabler: `V25__sc_205_image_version.sql` (column + backfill 1), `V26__add_admin_role.sql` (constraint + `ADMIN`) | `IngestionSchemaMigrationTest` (V25 column, default 0, backfill) · `UsersRoleMigrationTest` (six values kept, `ADMIN` admitted, nobody promoted) | — | SC-205 impl |
| **WI-02** | domain | `Role.ADMIN`; `RestaurantListing.imageVersion` + `fromStorage(imageVersion)` | `RoleTest.adminIsAValue` · `RestaurantListingTest.imageVersionDefaultsToZero` | WI-01 | SC-205 impl |
| **WI-03** | application | `Actor(accountId, role)` + `PhotoWritePolicy.mayManage` (AC-6) | `PhotoWritePolicyTest`: admin/foreign, user/foreign, owner, admin/ownerless, `null` owner | WI-02 | SC-205 impl |
| **WI-04** | application | `AuditRecorder` port + `LoggingAuditRecorder` (D-11) | `LoggingAuditRecorderTest.recordsActorListingAndVersions` | — | SC-205 impl |
| **WI-05** | application | `AddListingImage.execute(listingId, actor, original)`: policy, bump, audit on override | `AddListingImageTest`: owner OK · non-owner 403 · admin override OK · override audited · version bumps only on success · unknown listing 404 | WI-03, WI-04, WI-07 | SC-205 impl |
| **WI-06** | application | `GetListingManage.execute(listingId, actor)` | `GetListingManageTest`: owner true · admin true · user false · missing listing null | WI-03 | SC-205 impl |
| **WI-07** | persistence | `image_version` in `LISTING_COLUMNS`/`toListing`; `bumpImageVersion` | `ExposedRestaurantListingRepositoryTest.bumpImageVersionIncreases` · `…ReturnsNullForUnknownId` · `…RoundTripsThroughFindById` | WI-01, WI-02 | SC-205 impl |
| **WI-08** | web-api | `GET /v1/listings/{id}/manage`; `&v=` on `imageUrl`/`imageSrcset` | `ListingManageEndpointTest`: 200 owner · 200 admin · 200 user false · 401 · 404 · `ListingReadEndpointTest.srcsetSharesOneVersion` | WI-06, WI-07 | SC-205 impl |
| **WI-09** | web-api / OpenAPI | document `/manage` + the `v` param; regenerate `frontend/src/lib/api/{openapi.json,schema.d.ts}` | openapi schema test (existing contract suite) | WI-08 | SC-205 impl |
| **WI-10** | frontend | `ApiClient.uploadListingImage` (FormData), `getManage`, `ApiErrorCode += payload_too_large/unauthorized` | `client.test.ts`: multipart body has no JSON content-type · bearer attached · 413 → `payload_too_large` | WI-08, WI-09 | SC-205 impl |
| **WI-11** | frontend | `PhotoUploadController` (states, 8 MiB pre-check, error mapping) | `photo-upload.test.ts`: idle→uploading→success · oversize blocks the call · each error kind · cancel resets | WI-10 | SC-205 impl |
| **WI-12** | frontend | `PhotoUploadDialog` (native `<dialog>`, two inputs, 3b confirmation, retry) | `PhotoUploadDialog.test.ts`: both inputs · `capture="environment"` · replace confirmation · focus return | WI-11 | SC-205 impl |
| **WI-13** | frontend | `ListingPhotoPanel` + detail-page integration | `ListingPhotoPanel.test.ts`: empty state · control gated by `canManage` · success announcement · `restaurants/[id]/page.test.ts`: owner flow, admin flow, non-owner 403 | WI-11, WI-12 | SC-205 impl |
| **WI-14** | docs | `10-traceability.md`: AC → test → evidence, dev checks | — | all | SC-205 impl |

## Two-way coverage

**Every UC step has a WI**

| UC-01 | WI |
|---|---|
| 1–3 (render + manage signal) | WI-06, WI-08, WI-13 |
| 3b (replace confirmation) | WI-12 |
| 4–7 (dialog, both sources) | WI-12, WI-13 |
| 8 (client pre-check) | WI-11 |
| 9 (upload) | WI-10, WI-11 |
| 10 (authn + policy) | WI-03, WI-05, WI-08 |
| 11 (decode, resize, store) | unchanged `IngestHeroImage` |
| 12 (version + attribution) | WI-04, WI-05, WI-07 |
| 13–14 (re-read, show, focus) | WI-08, WI-10, WI-13 |
| A1 (replace) / A2 (camera) / A3 (cancel) | WI-12, WI-13 |
| A4 / A5 (admin, ownerless) | WI-03, WI-05, WI-13 |
| E1 (401) / E2 (403) / E9 (stale role) | WI-03, WI-05, WI-11, WI-13 |
| E3 (413) / E4 (400) / E5 (404) / E6–E7 (retry) | WI-10, WI-11 |

**Every Phase 7 class has a WI**

| Class | WI |
|---|---|
| `V25`, `V26` | WI-01 |
| `Role`, `RestaurantListing.imageVersion` | WI-02 |
| `Actor`, `PhotoWritePolicy` | WI-03 |
| `AuditRecorder`, `LoggingAuditRecorder` | WI-04 |
| `AddListingImage` | WI-05 |
| `GetListingManage` | WI-06 |
| `ExposedRestaurantListingRepository` | WI-07 |
| `ListingManageEndpoint`, DTO `v` | WI-08 |
| `ApiClient` | WI-10 |
| `PhotoUploadController` | WI-11 |
| `PhotoUploadDialog` | WI-12 |
| `ListingPhotoPanel`, `RestaurantDetailPage` | WI-13 |

## Notes

- **No separate frontend ticket.** The contract (`/manage`, `&v=`) lands in WI-08/09 before WI-10 starts; the frontend never needs a mock-only phase.
- **`V26` ships with the frontend slice**, because AC-6 is unreachable without the role; the promotion itself is the operator runbook (D-10), not code.
- **Omar's attention points:** role authority (D-13), the single policy point (WI-03), attribution (WI-04/05), and the absence of any role-granting route (INV-14).
- **Yusuf's attention points:** the admin override against a real dev listing, the stale-token refusal (E9), and the cache behaviour after a replace (AC-4 after reload).
