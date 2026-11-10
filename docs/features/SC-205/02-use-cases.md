# SC-205 — Phase 2: Use cases

> **Purpose:** the actor goal in numbered steps, with every way it can end · **Derived from:** 00, 01 · **Delta:** one new use case (UC-01) with **two actors** (owner, admin) and **two named flows** (add, replace); no existing use case changes

## UC-01 — Set the listing photo

| Field | Value |
|---|---|
| Actors | **RO** / **VRO** — the listing's owner · **Admin** — the operator, for any listing (AC-6) · **U** — not a participant |
| Goal | The listing shows the photo that should represent it, and everyone who opens the page sees it |
| Trigger | The actor activates *Add photo* / *Replace photo* in the listing's photos section |
| Frequency | Rare, operator- or owner-initiated |
| Precondition | A listing with a known id exists; the actor is signed in as its owner **or** as an admin |

### Two flows, one goal

Add and replace serve the same goal with the same server operation, but they are **not the same act**: add creates a photo, replace **destroys** the current one (the store is last-write-wins and keeps no history), and the photo being destroyed may not be the actor's own upload. They are therefore written as two named flows rather than one flow with a different button label. They become two *use cases* only if replace later acquires its own rules (history, review, a different actor) — see D-5.

### Main scenario — Flow A: add (the listing has no photo)

1. The owner (or an admin) opens the restaurant page while signed in.
2. The system asks the server whether this actor may manage the listing's photo — owner or admin.
3. The system renders the photos section: a labelled empty frame; and the **Add photo** control because the signal allows it.
4. The actor activates the control.
5. The system opens the photo dialog offering **Choose a photo** and **Take a photo**.
6. The actor picks one; the device's file picker or camera opens.
7. The actor selects or captures an image.
8. The system checks that a file was chosen and that it is at most 8 MiB.
9. The system uploads the file to `PUT /v1/listings/{id}/image` with the session's access token, showing an *Uploading…* state.
10. The backend authenticates the request, loads the listing, and applies the write policy to the verified actor.
11. The backend decodes the bytes, derives the media type from them, resizes every thumbnail width, and stores the FULL image plus each thumbnail.
12. The backend stamps the listing's photo version, records who wrote it, and answers `204 No Content`.
13. The system re-reads the listing; its image URLs carry the new version; the dialog closes and success is announced.
14. Focus returns to the control, and the photo shown on the page is the new one.

### Flow B — replace (the listing already has a photo)

Steps 1–14 of Flow A, with two differences:

| Step | Difference |
|---|---|
| 3 | The control reads **Replace photo**; the current photo is the backdrop of the section |
| 3b | **Confirmation before writing:** the dialog states that the current photo will be replaced and cannot be restored. Proceeding continues at step 4; cancelling is A3 |

Replacing is the administrator's normal flow when correcting a listing, and the owner's flow when the photo is stale. The server behaves identically; only the disclosure differs.

### Alternates

| # | Branch | Difference from the main scenario |
|---|---|---|
| A1 | **Replace** an existing photo | Flow B — the confirmation at step 3b |
| A2 | **Take a photo** | At step 6 the actor chooses the camera; the input carries `capture="environment"` so phones open the rear camera |
| A3 | Actor cancels | At step 3b, 5 or 7 the dialog closes; nothing is uploaded; the listing is unchanged |
| A4 | **Admin** acting on a listing they do not own | Step 2 returns `canManage = true`; steps 3–14 are identical; step 12 attributes the write to the admin (INV-12) |
| A5 | Admin acting on a listing with **no owner** | Same as A4; `ownerId` is null and the policy passes on role alone |

### Exceptions → resulting state

| # | Condition | Response | Resulting state |
|---|---|---|---|
| E1 | No valid session (expired and refresh failed) | `401`; UI replaces the dialog with a sign-in prompt that returns to this page | Signed out; **no write** |
| E2 | Signed in, but **neither the owner nor an admin** (a forced upload) | `403 not_listing_owner`; explicit message | **No write**; photo unchanged |
| E3 | File larger than 8 MiB | Client refuses before sending; a bypassing client gets `413 payload_too_large` | **No write**; photo unchanged |
| E4 | Bytes are not a decodable image (including iPhone **HEIC**) | `400 invalid_input`; "choose a JPEG or PNG" | **No write**; storage untouched |
| E5 | Listing deleted between page load and upload | `404 not_found`; page shows the not-found state | **No write** |
| E6 | Object storage or DB fails during the write | `500 internal_error`; dialog stays open with *Try again* | Previous version remains current; some variants may already be replaced (Q-2) |
| E7 | Network drops mid-upload | No response; dialog shows an error with *Try again* | Unknown-to-client; the next successful upload resolves it (idempotent replace) |
| E8 | Listing has no photo and nobody uploads | — | Empty state stays; cards keep their placeholder |
| E9 | Admin's access token was minted **before** their promotion | `403` — the policy reads the token's role, not the database | **No write** until the next refresh; UI says "sign in again to apply your new access" |

### Postconditions

- On success: the listing has exactly one hero photo, its version is newer than before, every variant belongs to that version, and the acting account id is recorded.
- No other listing, and no field other than the photo, its version and the attribution, is modified.
- On any failure: `imageVersion` and the stored bytes are exactly what they were before the attempt (E6 partial-variant caveat excepted).

### AC coverage

| AC | Steps / branches |
|---|---|
| AC-1 signed-in owner sees the control | 2–3 |
| AC-2 upload an existing image or take a new one | 5–7 (A2) |
| AC-3 add or replace from the restaurant page | 1–12 (Flow B) |
| AC-4 show the new image, durably | 13–14 |
| AC-5 non-owner cannot change it | E2 |
| AC-6 an admin can change any listing's photo | A4, A5, E9 |

## Open questions

| # | Question | Owner | Where decided |
|---|---|---|---|
| Q-1 | Does the control wait for a server manage signal, or show to every signed-in user? | founder | `08` D-2 (the signal now answers *owner or admin*) |
| Q-2 | Accept the partial-variant window of E6, or add a revert/cleanup work item? | founder + Hamza | `08` D-6 |
| Q-5 | Transcode HEIC in the browser? | founder | not this story; E4 copy is explicit |
| Q-6 | How an account becomes `ADMIN` | founder + Omar | `08` D-10 |
| Q-7 | Attribution depth for an admin override | founder + Omar | `08` D-11 |
| Q-8 | Admin edit of listing **content** (not just photos) | founder | separate story — D-12 |
