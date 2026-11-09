# SC-205 — Phase 2: Use cases

> **Purpose:** the actor goal in numbered steps, with every way it can end · **Derived from:** 00, 01 · **Delta:** one new use case (UC-01); no existing use case changes

## UC-01 — Add or replace the listing photo

| Field | Value |
|---|---|
| Actors | **RO** / **VRO** (primary, owner of the listing) · **U** (not a participant in this flow) |
| Goal | The listing shows the photo the owner wants, and everyone who opens the page sees it |
| Trigger | The owner activates *Add photo* / *Replace photo* in the listing's photos section |
| Frequency | Rare, owner-initiated; typically once, then when the photo is stale |
| Precondition | A listing with a known id exists; the owner is signed in; the page is open |

### Main scenario

1. The owner opens the restaurant page while signed in.
2. The system loads the listing and asks the server whether this viewer may manage its photo.
3. The system renders the photos section: the current photo, or a labelled empty frame; **and** the *Add photo* / *Replace photo* control when the manage signal allows it.
4. The owner activates the control.
5. The system opens the photo dialog offering **Choose a photo** and **Take a photo**.
6. The owner picks one; the device's file picker or camera opens.
7. The owner selects or captures an image.
8. The system checks that a file was chosen and that it is at most 8 MiB.
9. The system uploads the file to `PUT /v1/listings/{id}/image` with the session's access token, showing an *Uploading…* state.
10. The backend authenticates the request, loads the listing, and confirms the caller is its recorded owner.
11. The backend decodes the bytes, derives the media type from them, resizes every thumbnail width, and stores the FULL image plus each thumbnail.
12. The backend stamps the listing's photo version and answers `204 No Content`.
13. The system re-reads the listing; its image URLs carry the new version; the dialog closes and success is announced.
14. Focus returns to the control, and the photo shown on the page is the new one.

### Alternates

| # | Branch | Difference from the main scenario |
|---|---|---|
| A1 | **Replace** an existing photo | Step 3 labels the control *Replace photo*; steps 4–14 are identical; the previous image is overwritten (last write wins) |
| A2 | **Take a photo** | At step 6 the owner chooses the camera; the file input carries `capture="environment"` so phones open the rear camera |
| A3 | Owner cancels | At step 5 or 7 the dialog closes; nothing is uploaded; the listing is unchanged |

### Exceptions → resulting state

| # | Condition | Response | Resulting state |
|---|---|---|---|
| E1 | No valid session (expired and refresh failed) | `401`; UI replaces the dialog with a sign-in prompt that returns to this page | Signed out; **no write** |
| E2 | Signed in, but not the listing's owner (a forced upload) | `403 not_listing_owner`; explicit message | **No write**; photo unchanged |
| E3 | File larger than 8 MiB | Client refuses before sending; a bypassing client gets `413 payload_too_large` | **No write**; photo unchanged |
| E4 | Bytes are not a decodable image (including iPhone **HEIC**) | `400 invalid_input`; "choose a JPEG or PNG" | **No write**; storage untouched |
| E5 | Listing deleted between page load and upload | `404 not_found`; page shows the not-found state | **No write** |
| E6 | Object storage or DB fails during the write | `500 internal_error`; dialog stays open with *Try again* | Previous version remains current; some variants may already be replaced (Q-2) |
| E7 | Network drops mid-upload | No response; dialog shows an error with *Try again* | Unknown-to-client; the next successful upload resolves it (idempotent replace) |
| E8 | Listing has no photo and the owner never uploads | — | Empty state stays; cards keep their placeholder |

### Postconditions

- On success: the listing has exactly one hero photo, its version is newer than before, and every variant belongs to that version.
- No other listing, and no field other than the photo and its version, is modified.
- On any failure: `imageVersion` and the stored bytes are exactly what they were before the attempt (E6 partial-variant caveat excepted).

### AC coverage

| AC | Steps |
|---|---|
| AC-1 signed-in owner sees the control | 2–3 |
| AC-2 upload an existing image or take a new one | 5–7 (A2) |
| AC-3 add or replace from the restaurant page | 1–12 (A1) |
| AC-4 show the new image, durably | 13–14 |
| AC-5 non-owner cannot change it | E2 |

## Open questions

| # | Question | Owner | Where decided |
|---|---|---|---|
| Q-1 | Does the control wait for a server manage signal, or show to every signed-in user? | founder | `08` D-2 |
| Q-2 | Accept the partial-variant window of E6, or add a revert/cleanup work item? | founder + Hamza | `08` D-6 |
| Q-5 | Transcode HEIC in the browser? | founder | not this story; E4 copy is explicit |
