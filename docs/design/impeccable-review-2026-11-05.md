# Impeccable design pass — whole frontend (2026-11-05)

Status: DRAFT for review · Acting specialist: **maryam** (frontend, executing this pass)
· Design authority: Fatima (`docs/design/*`) · Founder approval still required for anything visual
beyond the ratified token set.

Tooling: [Impeccable](https://impeccable.style) installed for OpenCode at project scope
(`.opencode/skills/impeccable`, `.opencode/commands/impeccable.md`), engine run inside containers.
This pass ran `init`, `document`, `critique`, `audit`, then `polish`.

## 1. Context artifacts produced

| File | What it is |
|---|---|
| `PRODUCT.md` | Durable product truth for Impeccable commands: platform, users, purpose, positioning, constraints, brand commitments, evidence on hand, principles, accessibility. Derived from `README.md`, `docs/handoff/01-product-vision.md`, `ARCHITECTURE.md`, `brand/tahir/BRAND-DECISIONS.md`, and the ratified specs. Assumptions confirmed with the founder 2026-11-04 (consumer-first UI; dead routes flagged, not invented). |
| `DESIGN.md` | The incumbent visual system in the DESIGN.md spec format: Butcher's Stamp north star, tokens, typography, layout, elevation, shapes, components, do/don't rules. Source of truth remains `docs/design/tokens.md`; this file must not diverge from it. |
| `.impeccable/design.json` | Sidecar for the live panel (shadow/motion/breakpoint extensions + 8 component snippets). Shared artifact, tracked. |

## 2. Critique — scored against the ratified specs

Surfaces reviewed at 1440×1000 and 390×844 against `search-browse.md`, `detail-page.md`,
`trust-components.md`, `mobile-bottom-tab.md`, `auth-screens-v2-delta.md`, plus real backend data.

| Heuristic | Score | Note |
|---|---|---|
| Match between system and real world | 4/5 | Stamp/seal language holds; "spots" vs "restaurants" is used consistently enough. |
| Consistency and standards | 4/5 | Tokens are followed well; the drift was in radii, label sizes, and one alert stripe (fixed below). |
| Visibility of system status | 3/5 | Loading states exist; result count claimed "Finding halal spots…" during errors (fixed); no suggestion dropdown yet (spec'd, unbuilt). |
| User control and freedom | 3/5 | Mobile menu never closed after navigating and could not be dismissed with Esc (fixed). Dead links remain (below). |
| Error prevention / recovery | 4/5 | Field errors, anti-enumeration copy, retry paths all present and consistent. |
| Recognition over recall | 4/5 | Hand-cut and Verified are readable at a glance; unverified never reads as failure. |
| Aesthetic and minimalist design | 4/5 | Paper/stamp world is coherent; the one AI tell (side-tab alert) is gone. |
| Accessibility | 3/5 | Strong semantics and icon+text pairing; focus was inconsistent and two touch targets were undersized (fixed). |

### P0 — broken or misleading paths

1. **`/account` mobile tab has no route** (404). Spec: `mobile-bottom-tab.md` §1. *Flagged, not fixed* — needs a story.
2. **`/about` and `/how-verification-works` have no route** (404) but are linked from the footer, the detail page and the certificate panel — the trust anchor 404s. *Flagged, not fixed* (no invented product copy).
3. **Mobile menu could not be closed** except by tapping the trigger again, and it stayed open across navigations; it also omitted "Add a restaurant" and every auth entry. *Fixed.*

### P1 — states, hierarchy, system drift

4. **Error banner used a 4px left accent stripe** (`Alert.svelte`) — the AI-tell the detector flags, and the only place the system's border language broke. *Fixed* → full 1.5px danger border.
5. **Card "save" control was 36×36** — below the binding 44px minimum. *Fixed* → 44×44.
6. **Focus treatment was inconsistent**: brand ring was set ad hoc per component, several links had only the UA default, inputs used `outline-offset: 0`. *Fixed* → one zero-specificity global `:focus-visible` rule on the token ring (2px brand-500, 2px offset) plus per-component overrides kept only where needed (e.g. inset ring in the tab bar).
7. **Placeholder text used `ink-400`** (#8a7d63 ≈ 3.95:1 on paper) — below 4.5:1 for 15px text. *Fixed* → `ink-500` (≈6:1) in search, auth fields and the deny-reason input. Icon-only uses of `ink-400` stay (they only need 3:1).
8. **UnverifiedTag border was 1px**, spec says 1.5px like the stamp. *Fixed.*
9. **Radius drift**: `Retry` / empty-state CTAs were pills (`rounded-full`) in four screens and 6px buttons elsewhere. *Fixed* → `radius-md` for every control; `rounded-full` kept for browse chips (spec'd) and circular icon buttons.
10. **Control typography drift**: buttons were 15px Space Grotesk (`text-body`) instead of the token's 14px Archivo label. *Fixed* → `text-label` in `Button.svelte`, list/empty/error CTAs and `+error.svelte`.
11. **Detail page title hardcoded** `text-[2rem] leading-[2.375rem]` — a spec'd value with no token. *Fixed* → new `--text-page-title` token (32/38, Archivo 800).
12. **Hours "Open" pill carried an inline `style=` background** and had no closed state. *Fixed* → token class + `DayHours.closed` branch rendering an outlined "Closed" pill with a drawn circle glyph.

### P2 — polish

13. **Skeletons were static** (spec asks for a shimmer). *Fixed* → `animate-pulse` with `motion-reduce:animate-none` on the grid skeleton.
14. **Result count lied during errors** and read "0 verified · 30 spots near you" when nothing is verified. *Fixed* → count only on success; the verified segment appears only when there is at least one.
15. **Search zero-result copy contradicted the empty panel** ("No exact match — showing similar" above "Nothing matches yet"). *Fixed* → the panel speaks alone.
16. **No visual break between the hero and results.** *Fixed* → the token-sanctioned dashed kraft divider on home and search.
17. **Detail hero was 16:7 at every width**; the spec calls for 16:9 on mobile. *Fixed* → `aspect-[16/9] md:aspect-[16/7]`.
18. **Mobile tab bar border** was 1px `kraft-300`; spec is 1.5px `kraft-200`. *Fixed.*
19. **Browser surfaces were unowned**: text selection, caret, checkbox accent and the mobile browser chrome colour came from the OS. *Fixed* → `::selection`, `caret-color`, `accent-color`, `theme-color`, `color-scheme: light`.
20. **Dead markup**: an empty grid `<div>` left over in `ListingGrid`'s error branch. *Fixed.*

## 3. Audit — technical quality

- **A11y:** semantics are good (real `<dl>`, `aria-pressed`, `aria-current`, icon+text status, `aria-live` result region, labelled iframe). Remaining: `AuthCard`/login render `href="#"` links ("Terms", "Privacy Policy", "Forgot password?") that are prevented on click — they are announced as links but do nothing. `Alert` carries `tabindex="-1"` on a non-interactive element (svelte a11y warning). `MobileTabBar` lists `/account` (404). *All flagged; the `href="#"` ones need product copy or real pages.*
- **Perf:** card images are correct (srcset + `sizes="100vw"` per the sc-183 founder baseline — deliberately not narrowed). The grid renders 30+ cards with no virtualisation; acceptable for the current set. The Google Maps iframe is `loading="lazy"`. Fonts are self-hosted.
- **Responsive:** 1/2/3/4-column grid, stacked auth, tab bar with safe-area padding, detail page single column — all verified at 390 and 1440. Hero measure stays under 75ch.
- **Contrast:** ink-700 on cream-50 9.2:1, ink-500 on paper 5.8:1, stamp-700 on stamp-50 ≥7:1, cream on brand-500 4.6:1 — all pass. Fixed the one failing pair (placeholder).
- **Detector:** `impeccable detect --json src` now returns **0 findings** (was 1: `side-tab`).
- **Security-adjacent (needs Omar, not changed):** `hooks.server.ts` sends `Strict-Transport-Security` and CSP `upgrade-insecure-requests` in **dev** too. Any http dev origin (including `dev.tahirslist.com` over http) has its subresources force-upgraded to https, which breaks images/fonts/JS in a plain browser. Production is unaffected. Recommendation: gate both on `!isDev` (or on the request protocol) in a security-reviewed PR.

## 4. Data gaps found while reviewing real screens

- Every seeded listing is **`UNVERIFIED`, with empty `cuisine` and no rating/distance**, so no card shows a Verified stamp and no card shows a meta row. The trust language is therefore unexercised on the deployed data — worth a dedicated seed story.
- Only a handful of seed listings have a photo; the rest render the kraft placeholder (correct behaviour, sparse page).
- `DayHours.closed` is never set by seed data, so the new "Closed" pill is present but dormant.

## 5. Verification performed

- `./scripts/frontend-test.sh` (containerized): `svelte-check` **0 errors** (15 pre-existing warnings), `vitest` **236/236 passing** (25 files — 5 tests added during review), `vite build` green.
- `pnpm format` applied before commit (prettier + tailwind class sorting).
- Rendered review in Chromium (Playwright container) at 1440×1000 and 390×844 for: home, home (mobile menu open), search + empty search, restaurant detail (desktop/mobile), login (idle + failed login), signup, saved, verification, 404.
- Keyboard pass: tab from the top of home lands on "Log in" with a 2px brand-500 ring at 2px offset.

## 6. Review round — Omar (code/security) + Yusuf (QA)

Both reviews were run as subagents and posted as PR review comments. Round 1: **Omar BLOCK (1
blocking)** · **Yusuf FAIL (12 findings)**. Blocking and P1 items are fixed and re-verified; the rest
are accepted with a rationale.

### Blocking (Omar) — fixed

`--text-*--font-family` is not supported by Tailwind v4 (the `--text-*` companions only cover
line-height / letter-spacing / font-weight). The new `text-page-title` therefore dropped Archivo from
the detail-page name, and because the diff removed the last `font-display` usage, *every* heading and
button fell back to Space Grotesk. Proven from the built CSS. Fixed by binding the family in
`app.css` (`@layer components`: display / page-title / title / heading / label) plus an explicit
`font-display` on the detail h1. Verified in the built stylesheet:
`.text-page-title,.text-title,.text-heading,.text-label{font-family:var(--font-display)}`.

### P1 (Yusuf) — fixed

1. **Card favourite was unclickable** — the card's stretched `after:inset-0` link painted above the
   heart, so clicking it navigated to the restaurant instead of saving. Fixed with `z-10` on the
   control's wrapper; verified it now routes to `/login` when signed out.
2. **Bottom tab bar covered the footer nav** (25px at 390px) — "How verification works" hit-tested as
   the Search tab. Fixed by reserving the bar height + safe area on the shell (link bottom 756px vs
   bar top 787px).
3. **`/account`, `/about`, `/how-verification-works` are 404s** — still flagged per the founder's
   "flag, don't invent" decision, but the 404 page now renders the app chrome so a user is not
   stranded without navigation.

### P2/P3 — fixed

- **Tab-bar ring was clipped**: the global focus rule was unlayered and beat the utility layer's inset
  `-2px`. Moved to `@layer base`; measured `-2px` again.
- **Map iframe had no focus indicator** (Chrome never matches `:focus`/`:focus-within` on a
  cross-origin frame). The map panel now tracks the window blur/focus pair; verified
  `rgb(198,56,31) solid 2px` at 2px offset.
- **Focus ring faded in** because Tailwind v4's `transition-colors` includes `outline-color`.
  Pre-seeded `outline-color` so the ring is brand-500 immediately.
- **Loading/disabled label contrast** 4.16:1 → 7.4:1.
- **Sub-44px targets** (header auth, mobile-menu auth row, "← Back to results", "Forgot password?",
  footer links) now meet 44px.
- **`backdrop-blur`** removed from the card save control (glass decoration, against DESIGN.md).
- **404 page** renders inside `SiteShell`.
- **Tests added**: `SiteHeader.test.ts` (open, Esc + focus restore, close-on-navigate), an empty-result
  count assertion, and Open/Closed hours-pill cases.
- **Docs/token drift**: `--text-page-title` added to `tokens.md` (+ frontmatter); DESIGN.md hierarchy
  corrected; design sidecar placeholder colour, `refersTo` and `page-title` metadata fixed.
- **Dead token companions removed** (round-2 P3): the five `--text-*--font-family` declarations are
  gone from `app.css` and the `tokens.md` snippet, with a note — Tailwind silently discarded them, and
  leaving them in invites the exact regression that caused the blocking finding.
- **Auth-close effect tracks stable identity** (round-2 P3): `sessionState.current?.accountId ?? null`,
  so a silent token rotation cannot close the panel.

### Accepted, not fixed (recorded, not silently dropped)

- **Card name + badge squeeze** (also Yusuf's unbroken-name case, and the visible mid-word break in
  "Mediterranean"): pre-existing, caused by the ratified "name and badge share one row" composition in
  a 4-column grid. Fixing it means stacking or shortening the badge — a Fatima decision. My
  `min-w-0`/wrap attempt was reverted so this PR does not change the composition.
- **26 of 30 seed images 404** (`docker logs tahirs-list-backend`: `FETCH_FAILED … HTTP 403` from the
  Google image URLs). Backend/data; the placeholder fallback works.
- **`href="#"` affordances** ("Forgot password?", Terms, Privacy) — product copy/pages missing.
- **Vendored Impeccable bundles** (~1.7 MB, un-checksummed; the engine binary is checksum-verified) —
  supply-chain follow-up recorded, not patched locally.
- **Dev-only CSP/HSTS** — untouched; needs its own security-reviewed change.
- **Open-hours pill uses stamp green**, which `tokens.md` reserves for verification while
  `detail-page.md` §1.3 prescribes it. Two ratified docs disagree; left per the screen spec, flagged.
- **`aria-controls`** on the menu trigger references an unmounted element while collapsed —
  harmless alongside `aria-expanded`.
- **Test coverage gaps for two new transitions** (round-2 P3): the auth-change close and MapPreview's
  window blur/focus pair are verified in the browser but not unit-tested.

### Round 2 — re-verification after the fixes

Containerized suite green (0 errors, 236/236, build ok), detector 0 findings, and a targeted Chromium
re-run of every fixed finding: **8/8 pass**.


## 7. Open items for Adnan / Fatima / Omar

1. Story for `/account`, `/about`, `/how-verification-works` (blocker for the mobile tab bar and the trust anchor).
2. Seed data: verified listings, cuisines, hours with `closed`, photos (Aisha). 26 of 30 images currently 404.
3. Omar: dev-only CSP/HSTS fix (above).
4. Fatima: the seal mark reads as a checkbox at 40–48px; consider a dashed-edge seal treatment for decorative uses.
5. Fatima: card name/badge squeeze in the 4-column grid (long names break mid-word); needs a composition decision.
6. Product: "Forgot password?" and Terms/Privacy links currently do nothing — either build or remove.
7. Unbuilt spec items observed: search suggestion dropdown, List/Map toggle, collapsible mobile hours, certificate lightbox, owner claim flow.
