---
type: handoff-document
title: "04 — Team and Roles"
description: "The specialist personas that worked on Tahir's List, what each actually owned and delivered, and the known persona debt you inherit."
tags: [tahirs-list, team, agents, roles, personas, handoff]
status: active
generated: 2026-11-02
generated_by: adnan
verified: false
sources:
  - resource: ~/.hermes/profiles/*/SOUL.md
    title: "Agent persona definitions"
  - resource: AGENTS.md
    title: "AGENTS — contact/coordination section"
  - resource: ~/.hermes/kanban/boards/halal-food-finder/kanban.db
    title: "Task assignee history"
---

# 04 — Team and Roles

## The roster

**Established** from the profile directories and AGENTS.md. Eight profiles exist;
seven worked on Tahir's List. The eighth (`olive`) is unrelated.

| Profile | Role | Domain |
|---------|------|--------|
| `adnan` | Product & Engineering Manager | Product vision, sequencing, dependencies, review gates, founder escalation |
| `fatima` | Product Designer | Flows, layouts, IA, accessibility, trust/verification messaging, design handoff |
| `hamza` | Backend & Systems Engineer | APIs, schema, PostGIS, business logic, TDD, migrations |
| `maryam` | Frontend Engineer | Next.js implementation, API integration, UI states, tests |
| `aisha` | Research & Data Specialist | External APIs, datasets, provenance, licensing, seed data research |
| `yusuf` | QA & Testing Engineer | Acceptance verification, edge cases, regressions, break-it pass |
| `omar` | Security Reviewer | Static code review, threat models, attack cases, security sign-off |
| `olive` | Business intelligence / marketing ops | **Unrelated.** Cloud Modest Apparel. Do not confuse with Tahir's List. |

## What each actually delivered

**Established** from the Kanban ledger (115 completed cards) and git history. This
is the honest version, including duplication and oddities.

### Adnan — Product & Engineering Manager

Owned sequencing and coordination. Delivered the MVP execution plan
(`.hermes/plans/2026-08-29_mvp-trust-verification-vertical-slice.md`), kept the
Kanban moving, ran merge review, and made the call to defer the stamps design
review to the next sprint. Also the author of this handoff.

**Debt note:** I did not produce a human-readable daily digest. That is the single
biggest process failure that led to this transition. See [00](00-executive-handoff.md).

### Fatima — Product Designer

Delivered the design foundation (tokens + trust components + auth screen specs), the
Tahir mascot and logo identity exploration, the mascot v2 art swap across screens,
and the production favicon/app-icon set. Also produced design docs for detail page,
search/browse, trust components, auth screens, mobile bottom tab, verification seam,
partial-halal, AI consent, owner claim, image variants, and the Tahir brand.

**Where her work lives:** `docs/design/`, `brand/tahir/`, `sketches/`.

**Note:** Fatima's design docs are *intent*. Not all of them are implemented. Check
the ledger and code before assuming a design is live.

### Hamza — Backend & Systems Engineer

The heaviest contributor. Delivered the backend skeleton and TDD harness, account
creation, login, JWT verification filter, refresh-token cookie lifecycle, fail-fast
config and security headers, listing create/read, PostGIS search, filters (hand-cut,
price, cuisine, rating, distance), favorites endpoints, verification provider seam
and hosted-AI adapter, conservative verdict policy, VC review persistence and RBAC,
certification metadata exposure, MinIO image variants behind an `ImagePort`, seed
photo ingest, owner listing update, owner image add/remove endpoints, partial-halal
modeling, alcohol-served attribute, pickup/delivery service mode, structured address,
and Photon geocoder adapter.

He also did substantial conflict resolution and rebasing: PR #46 favorites conflict,
PR #61 structured-address conflict, PR #54 review blockers.

**Where his work lives:** `backend/`, especially `domain`, `application`,
`adapters/*`, and the migrations.

### Maryam — Frontend Engineer

Delivered the frontend skeleton and design-token base, signup/login UI, cookie-based
session lifecycle, security headers via `next.config`, the VC review UI, hand-cut
boolean UI, the QA fix for the duplicate Unverified badge and desktop bottom nav,
responsive multi-width `srcset` for card thumbs, pickup/delivery display and filter
UI, structured address rendering and map preview with pin, the owner listing
manage/edit screen, and the responsive restaurant images work.

**Blocked:** "Listing image management (frontend) — add/remove image UI on manage
screen [sc-53/sc-54]" (`t_63bc37f1`). This is the one unfinished card.

**Where her work lives:** `frontend/src/`.

### Aisha — Research & Data Specialist

Delivered the seed restaurant research for NYC, Dallas, Austin, and Toronto, and the
`sc-157` seed hero photos manifest. Her briefs are in `docs/research/`.

**Method she followed:** Fact → Evidence → Interpretation → Recommendation. She did
not present assumptions as facts. That discipline is worth keeping.

### Yusuf — QA & Testing Engineer

Delivered acceptance-criteria reviews at story start, break-it passes on the refresh
cookie flow and auth surfaces, and the QA assessment and testing conventions in
`docs/qa/`. He reported defects rather than fixing them, per his scope discipline.

**Where his work lives:** `docs/qa/` and PR review comments prefixed `[yusuf]`.

### Omar — Security Reviewer

Delivered security reviews on the JWT verification filter, the refresh-cookie flow,
auth rate limiting, and multiple PR approvals across auth and verification work.
His review is in `docs/security/auth-security-review-2026-08-29.md` and on PRs
prefixed `[omar]`.

**⚠ Known persona debt:** `omar`'s `SOUL.md` is the **generic Hermes Agent prompt**,
not a security-reviewer persona. It contains only a PR-review efficiency note. The
security-reviewer role was enforced by AGENTS.md and by task assignment prompts, not
by his persona file. If you keep Omar, fix his `SOUL.md` first. If you collapse to one
agent, carry the security rules forward explicitly — do not rely on a persona file
that does not contain them.

### Olive — unrelated

`olive` is the business-intelligence and marketing-operations agent for **Cloud Modest
Apparel**, a Shopify store. Her `SOUL.md` covers competitor intelligence, email
strategy, pop-up/event hunting, and partnership commissions. She has nothing to do
with Tahir's List. Do not assign her HFF work, and do not assume her presence means
the HFF team is larger than it is.

## How the personas were expected to behave

**Established** from their `SOUL.md` files. Summarized because the working style is
part of the handoff — the next owner may keep some of these.

- **Adnan (PM):** calm, organized, quietly demanding. Turns mess into five bullets.
  Comfortable saying "we don't know that yet", "that's an assumption, not a
  requirement", "let's ask Hamza", "Fatima should look at this before we build it",
  "we're solving the wrong problem". Dislikes unnecessary meetings, abstractions, and
  work. Keeps "a little barakah" — do things properly, be honest about uncertainty,
  do not sacrifice quality to appear productive. Distinguishes documentation, technical
  fact, inference, and founder decision. Never presents an inference as a requirement.
- **Fatima (Design):** thoughtful, creative, detail-oriented, slightly opinionated
  about ugly interfaces. Believes most interfaces have too many buttons. Asks "what
  does the user think is happening here?" Does not accept "that's how the API works"
  as a UX justification. Treats a halal verification badge as a trust artifact, not
  decoration. "A little ihsan" — make it beautiful, but useful first.
- **Hamza (Backend):** methodical, skeptical, pragmatic. Favorite question: "okay, but
  what happens when it fails?" Prefers boring, reliable infrastructure. Cares about
  data integrity — "a database isn't a bucket where the frontend throws JSON."
  Occasionally responds to ambition with "we could do that. I'm not sure we should."
  "Measure twice. Migrate once."
- **Maryam (Frontend):** practical, detail-oriented, allergic to unnecessary
  abstractions. Skeptical of premature abstraction, giant component files, clever
  state management, unnecessary dependencies, and CSS requiring spiritual intervention.
  Cares about accessibility, responsive behavior, performance, polish. "Ship clean
  code. Leave tomorrow's Maryam something she won't hate."
- **Aisha (Research):** separates fact, evidence, interpretation, recommendation.
  Prefers primary sources. Says when evidence is insufficient. Does not invent missing
  information to unblock someone. Finishes the assigned question and stops.
- **Yusuf (QA):** assumes the happy path is already tested. Prioritizes malformed input,
  empty/missing data, permission failures, network failures, repeated actions, stale
  state, boundaries, regressions. Distinguishes expected behavior / defect / uncertainty.
  Reports what was actually verified. Does not implement fixes unless assigned.
- **Omar (Security):** static, code-level. Reviews diffs, not whole files (he has a
  note telling him to use `gh pr diff`/`git diff`). Defines attack cases; does not run
  exploits. Adjudicates risk.

## Coordination rules that mattered

**Established** from AGENTS.md:

- Adnan coordinated requirements, engineering, QA, security review, and founder
  escalation. Specialists owned their domains and were expected not to need
  micromanagement.
- When a decision materially affected architecture or the product, the specialist was
  consulted and the founder involved. Consequential assumptions were not to be made
  silently.
- "Know when another specialist's expertise is needed. Don't involve people
  unnecessarily, but don't make domain decisions yourself when the team has a
  specialist who should answer them."

## My honest assessment of the team model

Stated as an assessment, not a requirement:

The personas were well-written and produced genuinely good, well-reviewed work. The
backend is the strongest evidence: 68 test files, TDD throughout, real security review,
real adversarial QA. The design thinking is also strong and well documented.

The failure was structural, not individual. Seven specialists plus one manager, all
communicating asynchronously, meant that understanding the state of the project
required reading Kanban cards, PR comments, design docs, research briefs, and Discord
threads. No single artifact told the founder what was happening. That is why this
handoff exists and why a synchronous model is the right next step.

If you keep specialists, keep **three**: backend, frontend, and one reviewer/QA
function. Design can be a checkpoint rather than a standing persona. Research can be
a task rather than an agent. That would cut the coordination surface roughly in half
without losing the quality controls that actually worked.
