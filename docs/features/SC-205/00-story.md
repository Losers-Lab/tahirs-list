# SC-205 — Add Image: the owner's frontend upload UI

> **Purpose:** lock the requirement and its verbatim acceptance line · **Derived from:** Shortcut SC-205 (split from SC-53) · **Delta:** frontend feature; two small backend enablers (D-1, D-2)

| Field | Value |
|---|---|
| Shortcut | https://app.shortcut.com/halal-food-finder/story/205 |
| Parent | SC-53 *Add Image* (Completed — backend `PUT`/`DELETE /v1/listings/{id}/image`) |
| Sibling | SC-206 *Remove Image — frontend delete UI* (not this story) |
| Epic | M1 Accounts & User Features |
| Iteration | none assigned — backlog position after SC-53/54 |
| Type · state | feature · In Development |
| Personas | **RO**, **VRO** (acts); **U** (sees the result); no new persona |
| Workflow | `@tahirs-feature-workflow`; founder approval before implementation |

## Story (verbatim from Shortcut)

> Remaining work split from SC-53 (Add Image).
>
> Still to do:
>
> - Frontend Add button in the restaurant images section.
> - Upload an existing image or take a new one.
> - Show the new image after upload.
>
> Acceptance: a signed-in owner can add/replace the listing image from the restaurant page.

The parent story's flow, still authoritative as context:

> Precondition: The user is viewing the restaurant page.
> A user clicks on the images section of a restaurant listing. Then, the click can click the Add button to add an image. The user is prompted to either upload an existing image or take a new one. After attaching the image, the image is then added to the images of the restaurant.

## What already exists (verified in the tree)

| Layer | Present today | Gap |
|---|---|---|
| Backend write | `PUT /v1/listings/{id}/image` — multipart part `image`, owner-only, 8 MiB cap, media type derived from bytes, FULL + 400/768/1280/1920 variants, 204 | none for the write itself |
| Backend read | `GET /v1/listings/{id}/image?variant=…`, `public, max-age=86400`; detail payload carries `imageUrl` + `imageSrcset` | no version in the URL → a replaced photo is invisible until the cache expires |
| Frontend | Detail page renders one hero `RestaurantPhoto` (or an empty frame) | no images section, no Add/Replace control, no upload client, no owner signal |
| Frontend API client | `getListing`, `updateListing` (JSON only) | no multipart upload; `ApiErrorCode` lacks `payload_too_large` |

## Acceptance criteria (verbatim, split into provable parts)

| # | Criterion | Provable by |
|---|---|---|
| AC-1 | A signed-in owner sees an Add control in the listing's images section | component test: owner session + manage signal → control rendered |
| AC-2 | The control offers **uploading an existing image** and **taking a new one** | component test: both actions open their input (`accept="image/*"`, camera input adds `capture="environment"`) |
| AC-3 | A signed-in owner can add **or replace** the listing image from the restaurant page | page test with a stubbed API: PUT called with the file, 204 → success state |
| AC-4 | The new image is shown after upload (and stays shown after a reload) | test: success triggers a re-read whose URLs carry the new photo version |
| AC-5 | A non-owner cannot change the photo | test: 403 `not_listing_owner` → explicit message, photo unchanged |

AC-1…AC-3 are the story's own text. AC-4 makes "show the new image" durable rather than session-only (D-1). AC-5 is the server rule the UI must not hide.

## Edge cases

| # | Case | Required behaviour |
|---|---|---|
| E-1 | Anonymous viewer | No Add control; a direct PUT is 401 and the UI offers sign-in and returns to the page |
| E-2 | Signed-in non-owner | Control not offered when the manage signal says no; a forced PUT is 403 with an explicit message |
| E-3 | Listing has no photo yet | The frame shows a labelled empty state, not a broken image |
| E-4 | File larger than 8 MiB | Rejected before upload with the limit in the message; a bypassing client still gets 413 |
| E-5 | File is not a decodable image (incl. **HEIC** from iPhone) | Rejected with "choose a JPEG or PNG"; storage untouched |
| E-6 | Session expires mid-upload | Refresh attempt, then 401 → sign-in prompt; the chosen file is not silently lost |
| E-7 | Object storage or DB fails after some variants are written | The operation reports failure; the previous photo remains the one the page serves (known partial-write limitation, see Q-2) |
| E-8 | Repeat upload | Last write wins; exactly one hero photo, version strictly increases |
| E-9 | Listing deleted between page load and upload | 404 → "this listing was removed"; control disappears |

## Open questions

| # | Question | Owner | Resolution path |
|---|---|---|---|
| Q-1 | Does the Add control depend on a server ownership signal, or is it shown to every signed-in user? | founder | **D-2** in `08` |
| Q-2 | A storage failure can leave some variants new and some old (object stores have no transaction). Accepted for now, or a cleanup/revert WI? | founder + Hamza | design notes it; not a blocker |
| Q-3 | Is a photo a single hero (current) or a gallery? | — | **resolved** by SC-53 delivered scope: one hero, last-write-wins; gallery deferred |
| Q-4 | May a non-owner submit a photo (the folded SC-33 wording says "a user")? | — | **resolved** by the delivered backend: owner only (SC-53) |
| Q-5 | Do we transcode HEIC client-side? | founder | not in this story; explicit rejection copy instead |
