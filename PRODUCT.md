# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

**Primary — the halal diner (U).** A Muslim deciding where to eat near them, usually
on a phone, often while out or planning a meal with family. Two questions drive the
decision and they ask them in this order: *is the meat hand-cut (Zabiha)?* and *is the
halal claim actually verified, or just self-asserted?* They are not browsing for
inspiration; they are deciding whether a place is permissible to eat at.

**Secondary — the restaurant owner (RO/VRO) and the Verification Committee (VC).**
Owners claim a listing and submit proof of ownership plus a halal certificate; the
committee reviews certification. These are roles on ordinary accounts, not separate
account types.

**Audience scope of the current UI (confirmed 2026-11-04):** the shipped screens are
consumer-first. The owner claim/submission flow and the `/account` surface are
follow-up stories, not implemented features. `/verification` exists as a working
committee queue.

## Product Purpose

Tahir's List (tahirslist.com) is a listing-first halal food directory. Anyone can add
a restaurant; a listing starts **unverified** and is only shown as verified after a
human committee reviews the owner's submitted certification. Success means a diner
can confirm in seconds that a place is genuinely halal and hand-cut, and can trust
that a "Verified" stamp was earned rather than typed in.

## Positioning

Verification is a formal, auditable process with a visible outcome — a stamp that
means "a committee reviewed a certificate", not "someone said halal". Paired with the
hand-cut (Zabiha) attribute as a first-class filter, this is what a generic halal
directory or a map listing cannot truthfully copy: they carry the claim without the
review.

## Operating Context

- Diner use happens on mobile, in the browser, often one-handed and outdoors.
- Verification runs as a queue: owner submits proof + certificate photo → image model
  suggests a verdict → committee member approves or denies with a reason.
- Certificate expiry is a live state: a lapse downgrades the Verified stamp everywhere
  rather than becoming an error.
- The project is built and tested only in containers (Docker is the build runtime);
  the host has no language toolchains.

## Capabilities and Constraints

**Shipped:** searchable listing grid with browse filters (All / Hand-cut / Delivery),
text search via `?q=`, restaurant detail page with certification panel, hours,
location and directions, favorites (`/saved`), auth (sign up / log in / sign out),
add-listing form, owner edit form, committee verification queue.

**Constraints that future work must keep:**

- Same-origin only: all API calls go through the `/v1` proxy; no cross-origin calls.
- CSP is narrow (`connect-src 'self'`, `img-src 'self'`) — new assets, fonts, and
  embeds must not assume external origins.
- Access token in memory only; the refresh token never exists in JavaScript.
- Hand-cut is a **boolean** claim. There is no machine-cut state; when the field is
  false or unknown the indicator is omitted entirely.
- "Unverified" is neutral ink, never red and never a warning triangle.
- Danger red is reserved for genuine errors, and always pairs with an alert glyph.

**Deliberately undecided / not built here:** partial-halal (menu-level) surfacing,
certification revocation, `/account`, `/about`, `/how-verification-works`, map view
(spec'd as a v1 placeholder toggle, not implemented), search suggestion dropdown,
multi-cuisine AND/OR chaining.

## Brand Commitments

- Name: **Tahir's List**; tagline **"find halal food"** (lowercase). Never "Halal
  Market" or "Halal Food Finder" in user-facing copy.
- Wordmark: Archivo Black, tracking ≈ -3%; **the apostrophe is always brand red**
  (#C6381F). The wordmark is never rendered without it.
- Primary lockup C2: Tahir head app-icon + wordmark + tagline, used on every screen
  (desktop 56px icon, mobile 40px), plus favicon and app icon.
- Mascot Tahir is canonical per `brand/tahir/CHARACTER.md`; no redrawn variant.
- One look across every page is a founder requirement. Light (paper/kraft) is the
  launch theme; a dark pass is backlog, not a second theme to half-build.

## Evidence on Hand

- Founder-ratified token set: `docs/design/tokens.md` (mirrored in
  `frontend/src/app.css` `@theme`).
- Screen specs: `docs/design/search-browse.md`, `detail-page.md`,
  `trust-components.md`, `mobile-bottom-tab.md`, `auth-screens-v2-delta.md`.
- Approved sketches: `sketches/006-stamps-search`, `007-detail-stamps`,
  `008-stamps-mobile` (HTML + preview PNGs).
- Real seed listing data and photos: `seed_restaurants_geocoded.json`,
  `docs/research/seed-photos-2026-08-30.json`.
- **No testimonials, press, logos of certifiers beyond seed data, user counts, or
  ratings that are not in the seed data may be invented.**

## Product Principles

1. **Verification is earned, and the UI says so.** A trust mark always names the
   process behind it; never imply review that did not happen.
2. **Amanah over polish.** Halal accuracy is a communal trust. When a fact is
   unknown, omit it rather than guess.
3. **Two decisions, fast.** Hand-cut and verified status must be readable at a
   glance, in that order, on a phone.
4. **Never punish the contributor.** Listing-first means unverified is a normal
   state, not a failure.
5. **One look.** New surfaces inherit the stamps/paper world instead of inventing a
   second identity.

## Accessibility & Inclusion

- Colorblind-safe is binding: status is never encoded in hue alone. Verified and
  unverified differ by filled-stamp vs outlined circle, glyph, and the word itself.
- Body text ≥4.5:1; large text ≥3:1. Focus rings are never removed.
- Interactive targets ≥44×44px, including icon-only controls.
- Mobile web is a first-class target (fixed bottom tab bar, safe-area insets).
