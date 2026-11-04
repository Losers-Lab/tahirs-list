---
type: handoff-document
title: "08 — Roadmap and Open Questions"
description: "What the team hoped to accomplish next: Sprint 6 scope, backlog themes, cost-hardening work, and the product decisions that still require the founder."
tags: [tahirs-list, roadmap, backlog, shortcut, open-questions, product-decisions]
status: active
generated: 2026-11-02
generated_by: adnan
verified: false
sources:
  - resource: app.shortcut.com/halal-food-finder/iteration/191
    title: "M1 Sprint 6 — Owner Tools: Manage Your Listing"
  - resource: app.shortcut.com/halal-food-finder
    title: "Epics and Prioritization backlog"
  - resource: ARCHITECTURE.md
    title: "Part 2 — unresolved decisions and open product questions"
  - resource: ~/.hermes/profiles/adnan/cron/jobs.json
    title: "Sprint planner prompt (epic scope and planning rules)"
---

# 08 — Roadmap and Open Questions

## Where the roadmap stood

**Established.** Milestone 1 was in progress. The README describes it as "MVP trust and
verification vertical slice" plus the surrounding product surface. The sprint planner's
instructions scoped M1 to **epics 111–115 and 158**, plus the later cost-hardening epic
196. The verification vertical slice plan
(`.hermes/plans/2026-08-29_mvp-trust-verification-vertical-slice.md`) framed the first
coherent slice as: add a restaurant → submit certification → AI + human review → users
see verified status.

That spine is built. The remaining M1 work is breadth and owner tooling.

## Sprint 6 — Owner Tools: Manage Your Listing

**Established** from Shortcut iteration 191. Window 2026-09-03 → 2026-09-10 (expired).
Six stories:

| Story | Name | Epic | State at handoff |
|-------|------|------|------------------|
| #53 | Add Image | 113 M1 Accounts & User Features | In Development |
| #23 | Restaurant Information Management | 114 M1 Owner Tools | In Development |
| #74 | Approve or Deny Restaurant Edits | 112 M1 Trust & Verification | Prioritization |
| #54 | Remove Image | 114 M1 Owner Tools | Prioritization |
| #47 | Edit Unverified Restaurant | 112 M1 Trust & Verification | Prioritization |
| #48 | Edit Verified Restaurant | 114 M1 Owner Tools | Prioritization |

**What this means in practice:** owner listing edit is built (backend and frontend,
`sc-23/47/48`), and owner image add/remove is built on the backend (`sc-53`). What is
missing is the **frontend UI** to add and remove images on the manage screen. That is
the blocked Kanban card. Stories #74 and the remaining edit flows are still in the
backlog.

**Recommended next slice:** finish owner tooling as one coherent unit — image add/remove
UI, the approve/deny restaurant edits flow, and the remaining edit paths. It is a natural
milestone, it has clear acceptance criteria, and the backend is already there. Do not
start browser extensions before owner tools are complete; extensions multiply surface
area without deepening the core loop.

## Backlog themes

**Established** from the epic summaries returned during this audit. Names are quoted
where known. The Shortcut UI is the authority; counts are approximate.

- **Epic 111 —** 21 stories. Search and browse breadth, listing details, and related
  product surface. This is the largest epic and the likely home of remaining M1 breadth.
- **Epic 112 — M1 Trust & Verification.** Verification, restaurant edits, partial-halal,
  approval flows. Includes #47 and #74.
- **Epic 113 — M1 Accounts & User Features.** Auth, favorites, images. Includes #53.
- **Epic 114 — M1 Owner Tools.** Owner edit and image management. Includes #23, #48, #54.
- **Epic 115 —** browser extensions for Google and Yelp. No code exists. In MVP scope per
  ARCHITECTURE.md.
- **Epic 158 —** referenced in the sprint planner's scope instruction; I did not read its
  contents. Check it in Shortcut before planning.
- **Epic 196 — M1 Platform & Cost Hardening.** Founder-directed cost hedge, created
  2026-09-04. Four open stories plus one designed-in item. See below.

**Backlog volume:** ~35 stories in Prioritization. That is roughly three to four
sprints' worth at the observed pace. Treat it as a menu, not a commitment.

## Epic 196 — Cost hardening (founder-directed)

**Established** from the epic description. Five geospatial/media cost traps the founder
raised to bake into the plan:

1. **Zero-egress object storage** (Cloudflare R2 or Backblaze B2) instead of paying egress.
   *Already designed in* at the code level via the S3 endpoint override — `S3ImagePort`
   takes a configurable endpoint, so R2 is a config change.
2. **Downscale certificate images before hosted vision AI** (story #197). Reduces token
   cost and upload hygiene risk.
3. **Geocode cache table** (story #198). Avoids re-billing Google Geocoding for the same
   address.
4. **Quantize search coordinates + cache headers** (story #199). Reduces payload and CDN
   cost.
5. **Pin JVM footprint for low-cost compute** (story #200). Keeps the VPS at CX-class
   size.

None of these were started. They are small, well-scoped, and directly reduce run-rate.
They are good candidates for a single "hardening" sprint before scaling usage.

## Open product questions requiring the founder

**Unresolved.** These are product decisions, not engineering choices. Do not answer them
in code without an explicit founder decision. They are carried from ARCHITECTURE.md
Part 2 and the PRD's "Open Questions" and "Edge Cases."

### 1. Partial-halal semantics (U-14)

**Status:** Confirmed **in MVP scope** by the founder on 2026-08-29. Exact modeling still
open.

The verification tag currently implies whole-restaurant status. Partial-halal needs its
own representation: a field, a tag, a filter, or some combination. The code has a
partial-halal representation (`V18` migration, `sc-119` cross-contamination gate), but the
product semantics were never fully ratified. Questions to resolve:

- Is "partial halal" an attribute of the restaurant, the menu, or both?
- Does a partial-halal restaurant qualify for verification? If so, what does the badge
  mean?
- How should search and filters treat partial-halal restaurants — included, excluded, or
  separately labelled?
- How is cross-contamination risk communicated without implying a guarantee?

### 2. Alcohol-served attribute (U-15)

**Status:** In MVP scope; field and filter exist (`V11`, `sc-118`).

Open questions:

- Should "serves alcohol" affect verification status, or is it purely informational?
- Is it a filter users can apply, a warning badge, or both?
- Does the copy need to distinguish "serves alcohol" from "alcohol in food"?

### 3. Certification expiry and revocation

**Status:** Open flag. Founder flagged it on 2026-08-29 and it was never resolved.

Questions:

- Should the verified page display the certificate's expiry date?
- What happens to the verified badge when a certificate lapses — auto-downgrade to
  unverified, show an "expired" state, or notify the owner?
- What is the revocation flow if a certificate is withdrawn or found invalid? Who can
  revoke, and is the listing's history retained?

This is the highest-stakes open question for trust. A verified badge that stays green
after the certificate expires is worse than no badge.

### 4. Kosher restaurants

**Status:** Unresolved in the PRD. Should kosher establishments be listed? If yes, does
the verification process differ?

### 5. Beyond restaurants

**Status:** Unresolved. Meat shops, grocers, food trucks, caterers? The listing model is
restaurant-shaped today. Broadening it is a product and schema decision.

### 6. Browser extensions in M1

**Status:** Ambiguous. ARCHITECTURE.md says in MVP scope; no code exists. Confirm with the
founder whether they are still M1 or have moved to a later milestone. If they stay in M1,
they are the largest unstarted workstream and should be planned as such.

### 7. Deferred by the PRD — do not build

Menu-item entries and analytics date-range customizability. The PRD explicitly deferred
them. Leave them deferred unless the founder changes the decision.

## The definition of "done" for M1

**Inferred, not established.** I did not find a single written definition of done for M1.
Based on the PRD pillars and the epics in scope, M1 done probably means:

- A user can search by location and filter by hand-cut, price, cuisine, rating, and
  distance, and get correct results.
- A user can add a restaurant, and it appears as unverified.
- An owner can claim a listing, consent to AI review, upload proof and a certificate.
- The AI suggests a verdict; a human VC approves or denies; the listing becomes verified
  and shows the certificate and badge.
- An owner can edit their listing and add/remove images.
- Favorites work.
- Public listing pages are crawlable (SEO decision).
- The browser extensions exist, if they remain in scope.

**This definition should be written down and ratified by the founder before the next
sprint is planned.** It is currently a gap. Without it, "M1" will keep absorbing new work.

## Planning rules that were in force

**Established** from the sprint planner prompt. These were founder-set and are worth
keeping or consciously replacing:

- The sprint planner runs with full autonomy and does not ask the founder questions.
- A sprint is diagnosed **from work, not the calendar**. An end date alone is not evidence
  the sprint is alive.
- A sprint is **exhausted** when no card is running and no ready/todo card is executable.
  Founder-gated cards do not count as executable work.
- If exhausted early, close it early and open the next one. Keeping work moving beats the
  calendar.
- New sprint naming: `M1 Sprint N — <Theme>`, with a real ~7-day window starting today.
- Plan a coherent milestone across backend, frontend, edge cases, and review gates. Narrow
  slices get burned in a day.
- Scope only from real Shortcut stories in backlog epics that are not done. **Never invent
  features.** Own-initiated tickets are bug fixes only.
- Reconcile against the repo before queueing: inspect what is already built so you do not
  re-card done work. Verify the foundation is green before dispatching on top of it.
- Every story goes in its epic **and** its iteration (founder rule).

**One caution:** "do not ask the founder questions" is fine for sequencing but wrong for
the open product questions above. Those need the founder. Autonomy should not extend to
inventing product semantics.

## My recommendation for the first two weeks

Stated as a recommendation, not a requirement:

**Week 1 — stabilize.**

1. Commit this handoff on a clean branch.
2. Back up Postgres and MinIO; write and test a restore runbook.
3. Restore the preview end to end and verify it.
4. Fix the watchdog's liveness check and make failures loud.
5. Add CI for both test scripts.
6. Reconcile Shortcut: close or extend Sprint 6, move stale "In Development" stories.

**Week 2 — finish the core loop.**

7. Clear the blocked image-management card (frontend add/remove UI).
8. Finish the owner-tools slice: approve/deny restaurant edits plus remaining edit flows.
9. Write the M1 definition of done and get it ratified.
10. Get the founder's decisions on certification expiry/revocation and partial-halal
    semantics, because both affect the verification UX you will build next.

Do not start browser extensions, dark theme, or cost hardening until the core loop is
demonstrably working end to end in a preview a human can click through. The product's
value is trust; a working verification loop is the proof.
