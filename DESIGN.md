---
name: Tahir's List
description: Halal food finder in kraft paper and butcher's stamps — verified, hand-cut, trusted.
colors:
  brand-red: "#c6381f"
  brand-red-deep: "#a82d17"
  brand-red-tint: "#fbede9"
  stamp-green: "#1f5c3d"
  stamp-green-tint: "#edf3ee"
  stamp-green-line: "#c2d6c8"
  paper-cream: "#fbf7ee"
  paper-white: "#fffdf6"
  kraft-band: "#f5eedf"
  kraft-line: "#dccfaf"
  kraft-deep: "#c9b98f"
  ink-heading: "#2b2118"
  ink-body: "#453a2b"
  ink-muted: "#6b5f4b"
  ink-disabled: "#b8ab90"
  danger-red: "#b3261e"
  warning-amber: "#8a6114"
typography:
  display:
    fontFamily: "Archivo Variable, ui-sans-serif, system-ui, sans-serif"
    fontSize: "2.5rem"
    fontWeight: 800
    lineHeight: "2.75rem"
  title:
    fontFamily: "Archivo Variable, ui-sans-serif, system-ui, sans-serif"
    fontSize: "1.5rem"
    fontWeight: 700
    lineHeight: "1.875rem"
  page-title:
    fontFamily: "Archivo Variable, ui-sans-serif, system-ui, sans-serif"
    fontSize: "2rem"
    fontWeight: 800
    lineHeight: "2.375rem"
  heading:
    fontFamily: "Archivo Variable, ui-sans-serif, system-ui, sans-serif"
    fontSize: "1.125rem"
    fontWeight: 700
    lineHeight: "1.5rem"
  body:
    fontFamily: "Space Grotesk Variable, ui-sans-serif, system-ui, sans-serif"
    fontSize: "0.9375rem"
    fontWeight: 400
    lineHeight: "1.5rem"
  label:
    fontFamily: "Archivo Variable, ui-sans-serif, system-ui, sans-serif"
    fontSize: "0.875rem"
    fontWeight: 600
    lineHeight: "1.125rem"
  small:
    fontFamily: "Space Grotesk Variable, ui-sans-serif, system-ui, sans-serif"
    fontSize: "0.8125rem"
    fontWeight: 400
    lineHeight: "1.125rem"
  wordmark:
    fontFamily: "Archivo Black, ui-sans-serif, system-ui, sans-serif"
    fontSize: "1.5rem"
    fontWeight: 900
    letterSpacing: "-0.03em"
rounded:
  sm: "4px"
  md: "6px"
  lg: "10px"
  full: "9999px"
spacing:
  "1": "4px"
  "2": "8px"
  "3": "12px"
  "4": "16px"
  "5": "20px"
  "6": "24px"
  "8": "32px"
  "10": "40px"
  "12": "48px"
  "16": "64px"
components:
  button-primary:
    backgroundColor: "{colors.brand-red}"
    textColor: "{colors.paper-cream}"
    rounded: "{rounded.md}"
    padding: "0 16px"
    height: "44px"
  button-primary-hover:
    backgroundColor: "{colors.brand-red-deep}"
    textColor: "{colors.paper-cream}"
  button-dark-chip:
    backgroundColor: "{colors.ink-heading}"
    textColor: "{colors.paper-cream}"
    rounded: "{rounded.md}"
    padding: "0 20px"
    height: "44px"
  chip-browse-active:
    backgroundColor: "{colors.brand-red}"
    textColor: "{colors.paper-cream}"
    rounded: "{rounded.full}"
    padding: "0 20px"
    height: "44px"
  chip-browse-inactive:
    backgroundColor: "{colors.ink-heading}"
    textColor: "{colors.paper-cream}"
    rounded: "{rounded.full}"
    padding: "0 20px"
    height: "44px"
  badge-verified:
    backgroundColor: "{colors.stamp-green-tint}"
    textColor: "{colors.stamp-green}"
    rounded: "{rounded.sm}"
    padding: "4px 10px"
  badge-verified-on-photo:
    backgroundColor: "{colors.stamp-green}"
    textColor: "{colors.paper-cream}"
    rounded: "{rounded.sm}"
    padding: "4px 10px"
  tag-unverified:
    backgroundColor: "#f1ead9"
    textColor: "{colors.ink-muted}"
    rounded: "{rounded.sm}"
    padding: "4px 10px"
  card-listing:
    backgroundColor: "{colors.paper-white}"
    rounded: "{rounded.lg}"
  input-field:
    backgroundColor: "{colors.paper-white}"
    textColor: "{colors.ink-heading}"
    rounded: "{rounded.md}"
    padding: "0 12px"
    height: "44px"
---

# Design System: Tahir's List

## Overview

**Creative North Star: "The Butcher's Stamp"**

The product looks like the paper a halal butcher wraps food in: a warm cream ground,
kraft-toned rules, ink-dark type, and a red brand stamp pressed onto it. Trust is not
a green dot — it is a seal, rendered as a literal stamp with a dashed edge, because
the product's promise is that a human reviewed a certificate. Nothing is clinical,
nothing glows, nothing floats.

The system is deliberately physical. Corners are nearly square (4–10px), shadows are
hard offset blocks of ink rather than soft blur, and pressing a control translates it
into its own shadow. Type does the hierarchy work: Archivo carries voice (wordmark,
headings, labels, buttons), Space Grotesk carries reading (body, meta, numerals).

Density is calm and scannable. One page background on every screen, one accent for
actions, one reserved color family for verification, and a narrow band of danger red
that only ever means "something failed". The look must be identical across home,
search, detail, auth, and committee screens — a second visual idea is a regression.

**Key Characteristics:**
- Cream paper ground (#fbf7ee) everywhere, kraft-rule borders, ink type.
- Butcher red for actions and the wordmark apostrophe; stamp green **only** for
  verification.
- Verification drawn as a seal: dashed stamp-200 edge, stamp-50 fill, stamp-700 mark.
- Hard offset shadows; press-down interaction; no soft blur, no glass, no gradients.
- Colorblind-safe by construction: every status pairs a distinct glyph family with a
  word.

## Colors

A warm paper palette with one loud accent and one sacred one.

### Primary
- **Butcher Red** (#c6381f): primary buttons, links, the wordmark's apostrophe, active
  filter chip, price/urgency accents. Hover #a82d17, pressed #8a2411.
- **Red Tint** (#fbede9): the only tint allowed behind red content (favorite button
  active state, subtle brand fills).

### Secondary
- **Stamp Green** (#1f5c3d): verification only — the seal mark, badge text, icon fill.
  Tint #edf3ee behind it, edge #c2d6c8. Never a generic success color.

### Neutral
- **Paper Cream** (#fbf7ee): page background, and text on red/ink surfaces.
- **Paper White** (#fffdf6): cards, inputs, the sheet that sits on the paper.
- **Kraft Band** (#f5eedf): alternate section banding, never a card color.
- **Kraft Line** (#dccfaf): default borders; **Kraft Deep** (#c9b98f) for strong
  borders and the dashed seal edge.
- **Ink Heading** (#2b2118): headings and the dark chip surface.
- **Ink Body** (#453a2b): body copy. **Ink Muted** (#6b5f4b): meta, helper text.
- **Ink Disabled** (#b8ab90): disabled text and placeholder icons.
- **Danger Red** (#b3261e) and **Warning Amber** (#8a6114): errors and informational
  expiry only. Danger always carries the alert glyph; amber never appears as a badge.

**The Two Reds Rule.** Brand red and danger red never share a component. Errors are
recognized by the alert triangle plus wording, not by hue — #c6381f leans orange,
#b3261e leans cold.

**The Green Means Reviewed Rule.** Stamp green is reserved for a certificate a
committee actually reviewed. Form success is neutral ink confirmation; a lapse is
informational, never green and never red.

## Typography

**Display Font:** Archivo Variable (fallback: ui-sans-serif, system-ui)
**Body Font:** Space Grotesk Variable (fallback: ui-sans-serif, system-ui)
**Wordmark Font:** Archivo Black

**Character:** A grocery-signage grotesque over a technical reading face. Archivo at
800 is the shout on the stamp; Space Grotesk at 400 keeps long addresses, hours and
meta legible at 15px without competing.

### Hierarchy
- **Display** (800, 40/44): homepage hero only.
- **Page title** (800, 32/38): the restaurant name on the detail page.
- **Title** (700, 24/30): screen titles, empty-state headings.
- **Heading** (700, 18/24): card names, panel headings, restaurant name on a card.
- **Body** (400, 15/24): copy, addresses, hours, form values. Keep measures ≤75ch.
- **Label** (600, 14/18): buttons, chips, badges, form labels — the control voice.
- **Small** (400, 13/18): meta rows, helper text, captions.
- **Mono** (13/18): certificate IDs and technical strings only.

**The Two-Face Rule.** Archivo never sets a paragraph and Space Grotesk never sets a
button. If a control's voice matters, it is Archivo label; if it is read, it is Space
Grotesk body.

## Layout

A single 1200px content container with 20px gutters, on one cream ground, at every
breakpoint. Vertical rhythm runs on a 4px base with section gaps at 48px; cards pad
20px on mobile and 24px on desktop. The header is 64px tall and sticky, bottom-ruled
by a 1.5px kraft line. Mobile web is not a squeezed desktop: content gets
`padding-bottom` equal to the 56px tab bar plus safe-area, and the tab bar is the
primary navigation below 1024px.

The listing grid is 1 column below 640px, 2 at 640–1023px, 3 at 1024–1279px and 4 at
1280px and up, with 24px gaps. Cards keep a 16:9 photo, then name + status in one
row, meta, then attribute chips — the same slot for Verified and Unverified so the
grid never shifts between states.

**The One Ground Rule.** No page introduces a second background color. Banding, if
used, is Cream-tinted; panels sit on it as paper-white sheets.

## Elevation & Depth

Depth is printed, not lit: shadows are hard offsets of ink with zero blur, like a
block pressed onto paper. Cards lift 2px on hover and swap to the deeper shadow;
controls press *into* their shadow (translate 2px, shadow to 0). Nothing fades in
with a glow and no surface uses backdrop blur as decoration.

### Shadow Vocabulary
- **Card** (`3px 3px 0 rgb(43 33 24 / 0.9)`): listing cards, panels, skeletons.
- **Chip** (`2px 2px 0 rgb(43 33 24 / 0.9)`): buttons, chips, small controls.
- **Stamp** (`0 0 0 1px #c2d6c8, 2px 2px 0 rgb(31 92 61 / 0.35)`): verification badge
  and certificate panel only.
- **Pop** (`4px 4px 0 rgb(43 33 24 / 0.9)`): dropdowns, mobile menu, toasts, modals.

**The Press-Down Rule.** Every pressable control translates by its own shadow offset
and collapses the shadow. Under `prefers-reduced-motion` the translation is removed
and only the shadow changes.

## Shapes

Nearly square by intent: 4px chips and tags, 6px inputs and buttons, 10px cards and
modals. `9999px` belongs to pills and avatars only — never to a CTA, which is where
most interfaces drift generic. Borders are 1.5px; the dashed variant (1.5px dashed
Kraft Deep) is the seal edge and section-divider language, and is never applied to an
interactive control.

**The No-Soft-Corner Rule.** If a surface needs to feel soft, it is wrong for this
world — tighten the radius and let the type carry the warmth.

## Components

### Buttons
- **Shape:** 6px radius, 44px tall, Archivo label at 14/600, icon gap 8px.
- **Primary:** butcher red on cream, chip shadow; hover deepens to #a82d17; pressed
  translates 2px and drops the shadow; disabled is ink-200 fill with ink-500 text and
  no shadow.
- **Secondary (dark chip):** ink-900 fill, cream text, chip shadow — used for the
  result-level actions (Directions, Call, Retry) and for chips.
- **Ghost:** transparent, ink-700 text, kraft border where a boundary is needed.
- **Focus:** 2px butcher-red ring, 2px offset, never removed.

### Chips
- **Style:** browse filters are ink-900 pills with cream text; the selected one turns
  butcher red. Attribute chips (Hand-cut, Delivery) are quiet kraft-edged paper chips
  at 14px, radius 4px — information, not judgment.
- **State:** selection is `brand-500` fill plus `aria-pressed`, never hue alone.

### Cards / Containers
- **Corner Style:** 10px.
- **Background:** paper white on cream.
- **Shadow Strategy:** card shadow at rest, pop shadow while hovered and translated.
- **Border:** 1.5px kraft line.
- **Internal Padding:** 20px mobile / 24px desktop.
- **Photo:** 16:9, kraft placeholder while loading, never a broken-image icon.

### Inputs / Fields
- **Style:** paper white, 1.5px kraft-deep border, 6px radius, 44px tall, ink body
  text, ink-400 placeholder.
- **Focus:** butcher-red ring at 2px offset; the border does not change hue alone.
- **Error / Disabled:** error border danger-500 plus an inline alert glyph and text via
  `aria-describedby`; disabled uses ink-200 fill.

### Navigation
- **Header:** 64px, cream, sticky, 1.5px kraft rule; C2 lockup left (Tahir head icon +
  wordmark with red apostrophe + tagline), secondary links and auth right.
- **Mobile tab bar:** four tabs (Search, Add, Saved, Account), 56px, paper-white, top
  kraft rule, safe-area padded, active tab carried by brand red *and* a 3px underline.
- **Focus:** in-flow, 2px red ring; inside the tab bar the ring is inset so it is not
  clipped.

### Verification Seal (signature)
The trust mark is a stamp, not a pill. **Verified:** dashed #c2d6c8 edge on #edf3ee,
#1f5c3d check + the word "Verified", 4px radius, stamp shadow; over photography it
inverts to solid #1f5c3d with cream text. **Unverified:** outlined circle glyph on
ink-100 with a 1.5px ink-300 border and ink-500 text — same metrics, quieter, and
**never red**. Icons are `aria-hidden`; the words carry the meaning.

## Do's and Don'ts

### Do:
- **Do** use tokens for every color, radius and shadow; a hardcoded hex is a bug.
- **Do** pair every status color with a glyph family and a word.
- **Do** keep Verified and Unverified metrics identical so grids do not shift.
- **Do** keep interactive targets at least 44×44px, including icon-only controls.
- **Do** reserve the dashed border for seals, dividers and empty panels.
- **Do** show the press-down translation on pressable controls, and remove it under
  `prefers-reduced-motion`.

### Don't:
- **Don't** use stamp green for generic success, or danger red for "Unverified".
- **Don't** put brand red and danger red in the same component.
- **Don't** soften to large radii, blurred shadows, glass panels, or gradient text.
- **Don't** introduce a second page background or a second accent.
- **Don't** add a border-left/right accent stripe on alerts or cards.
- **Don't** invent imagery, testimonials, ratings or certifiers that are not in data.
