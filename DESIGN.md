<!-- SEED: established with the user before implementation; re-run /impeccable document once there's code to capture the actual tokens and components. -->

---
name: Secured Hello World App
description: A restrained, correctness-first console for a username/password auth reference implementation.
---

# Design System: Secured Hello World App

## Overview

**Creative North Star: "The Instrument Panel"**

This is a reference implementation whose entire value proposition is that the security behavior is correct and legible — lockouts, generic errors, role gating, session lifecycle. The interface should read like a well-made instrument panel: every control does exactly one thing, state is always visible, and nothing decorative competes with the readout. No invented brand metaphor, no marketing voice, no illustration — this app has no brand to express, only behavior to make trustworthy and easy to verify at a glance.

The palette stays restrained (neutrals plus one functional accent) rather than committed or drenched: a Persuade or Experience surface earns boldness because expression is the point, but here the visitor is a technical reviewer completing a task, and color's only job is to mark state (focus, error, success, danger) unambiguously. Confirmed visual rejection: no gradients, no glassmorphism, no decorative iconography standing in for real content, no generic SaaS-dashboard clip-art feel.

**Key Characteristics:**
- Quiet, high-legibility forms with generous whitespace and a single clear primary action per screen
- One accent color reserved almost entirely for primary actions and focus states — its scarcity is what makes it readable as "the thing to do here"
- Flat surfaces with hairline borders instead of shadows; depth is expressed through spacing and contrast, not elevation
- Error and status messaging treated as first-class UI, not an afterthought `<p>` tag — this is a security demo, so the moments where the system says "no" matter as much as where it says "yes"

## Colors

Restrained strategy: neutral ground and text, one accent used sparingly for primary actions and focus, plus semantic state colors for error/success/warning — never used interchangeably with the accent.

### Primary
- **Signal Blue** (`#2563eb`): primary buttons, active nav/tab state, focus rings, links. Used on a small minority of any given screen — the "do this" color, not a background color.

### Neutral
- **Ink** (`#0f172a`): headings, primary body text.
- **Slate** (`#475569`): secondary text, field labels, helper copy.
- **Mist** (`#94a3b8`): placeholder text, disabled text, table secondary metadata.
- **Paper** (`#ffffff`): page and card background.
- **Fog** (`#f8fafc`): subtle section backgrounds (table header row, disabled field fill).
- **Hairline** (`#e2e8f0`): borders, dividers, table rules.

### Semantic (state)
- **Danger** (`#dc2626`): error text, error field borders, destructive actions (delete, disable).
- **Success** (`#16a34a`): success confirmations (e.g. "reset link sent").
- **Warning** (`#d97706`): lockout/throttle notices — a distinct register from hard errors, since a lockout is expected system behavior, not a failure.

### Named Rules
**The One Accent Rule.** Signal Blue appears on at most one primary action per screen plus focus rings. It never becomes a background fill, a decorative shape, or a repeated badge color — its rarity is what makes "primary action" legible without a label.

**The Error Is Not Red Text Rule.** Every error state (`role="alert"` regions already in the code) gets a full treatment: Danger-colored left border or icon, Danger text, and enough surrounding space that it reads as a distinct block, not an inline color change easy to miss.

## Typography

**Body/UI Font:** Inter (system-ui, -apple-system, "Segoe UI", Roboto, sans-serif as fallback stack)
**Mono Font:** ui-monospace, "SF Mono", Consolas, monospace — reserved for any literal system values (timestamps, IDs) if they appear.

**Character:** A single workhorse grotesque doing every job in the system — headings, labels, body, buttons — differentiated only by weight and size, never by switching families. This is an instrument panel, not an editorial page; the type has no personality of its own beyond being extremely legible at small sizes in a form.

### Hierarchy
- **Title** (600 weight, 28px, 1.2 line-height): page-level heading ("Log in", "Manage users").
- **Body** (400 weight, 16px, 1.5 line-height): form field values, table cells, paragraph copy.
- **Label** (500 weight, 14px, 1.3 line-height): form field labels, table column headers — column headers additionally at Slate color and 0.02em letter-spacing.
- **Small** (400 weight, 13px, 1.4 line-height): helper text, timestamps, secondary metadata under primary values.

### Named Rules
**The One Family Rule.** Every screen uses Inter exclusively. Weight and size carry all hierarchy; no second display or serif face is introduced anywhere in the system.

## Layout

Single-column, centered forms for the auth flow (Login, Register, Forgot Password, Reset Password, Hello): max-width 400px, vertically centered in the viewport on desktop, full-width with fixed side padding (24px) on mobile. Generous field spacing (20px between fields) over dense packing — this is a low-frequency, high-stakes flow (a handful of fields, once), not a data-entry grind.

Admin Users is a distinct density register: a full-width table (max-width 960px, centered) with comfortable but not spacious row height (48px), because a reviewer scanning a user list benefits from seeing more rows at once than the auth forms need.

Consistent page padding: 24px on mobile, 48px on desktop, applied via a single layout wrapper rather than per-page.

## Elevation & Depth

Flat by design. No box-shadows anywhere in the system — the "instrument panel" reads as printed on the surface, not floating above it. Depth and grouping are expressed through the Hairline border and Fog background fill only.

### Named Rules
**The Flat-By-Default Rule.** No shadows at rest or on hover. A card or table is set apart from the page by a 1px Hairline border and/or a Fog background, never a shadow.

## Shapes

Soft-cornered but not rounded-pill: 8px radius on cards, inputs, and buttons; 6px on small elements (badges, table action buttons). Borders are always 1px solid Hairline at rest, transitioning to 1.5px Signal Blue on focus — no glow, no outline offset trick, a real border-color change so it reads correctly even with reduced-motion/reduced-transparency settings.

## Components

### Buttons
- **Shape:** 8px radius, 1px border at rest for secondary/ghost variants, no border for primary.
- **Primary:** Signal Blue background, white text, 600 weight, 12px vertical / 20px horizontal padding. Used for the single primary action per form (Log in, Register, Send reset link, Set new password).
- **Secondary/Ghost:** transparent background, Hairline border, Ink text — used for "Back", cancel-style, and the Admin table's non-destructive actions (Enable/Disable, Make ADMIN/USER).
- **Destructive:** transparent background, Danger border and text at rest, Danger fill with white text on hover/focus — used only for Delete in the Admin table.
- **Hover/Focus:** primary darkens by one step (`#1d4ed8`); all buttons get a visible 2px Signal Blue focus ring offset 2px, satisfying keyboard-navigation visibility without relying on browser defaults.
- **Disabled (submitting state):** 60% opacity, no pointer events; label swaps to a present-tense progress string already in the code ("Logging in…") rather than a spinner-only state.

### Cards / Containers
- **Corner Style:** 8px radius.
- **Background:** Paper, on a Fog page background so the card itself reads as a distinct surface without a shadow.
- **Border:** 1px Hairline.
- **Internal Padding:** 32px on desktop, 24px on mobile.
- Each auth form (Login, Register, Forgot/Reset Password) renders inside exactly one such card, centered on the page.

### Inputs / Fields
- **Style:** 1px Hairline border, 8px radius, Paper background, 12px vertical / 14px horizontal padding, Body-size text.
- **Label placement:** label sits above the field (already the DOM order in the code), Label typography, 6px gap to the field.
- **Focus:** border becomes 1.5px Signal Blue; no separate glow/shadow.
- **Error:** border becomes Danger, and the field's associated alert region (already `role="alert"` in the code) renders directly beneath the field it concerns rather than only at the top of the form, so the reviewer can see exactly which input failed.
- **Disabled:** Fog background, Mist text, Hairline border unchanged.

### Navigation
No persistent nav chrome. The auth flow moves by full view swap (already the app's model — `login` / `register` / `forgot-password` / `reset-password` / `hello` / `admin`), each transition a page-level cross-fade (150ms) rather than a slide, keeping the instrument-panel stillness. The Hello page's "Manage users" and Admin's "Back" are the only inter-view controls, styled as Secondary buttons, top-left of their card/table.

### Tables (Admin Users)
- **Header row:** Fog background, Label typography in Slate, 1px Hairline bottom border, no vertical borders.
- **Body rows:** Paper background, 1px Hairline bottom border per row, no zebra striping — striping would compete with the row-level state (enabled/disabled) as the thing that visually varies.
- **Disabled user row:** entire row text drops to Mist to make "disabled" scannable at a glance down the list, not just legible in its own cell.
- **Row actions:** right-aligned button group (Secondary × 2, Destructive × 1), collapsing to icon-only Secondary/Destructive buttons with `aria-label`s under 640px width rather than wrapping the row.
- **Self row:** the existing "(you)" text treatment stays, set in Small/Mist to read as a note rather than a missing action.

## Do's and Don'ts

### Do:
- **Do** keep every existing `<label htmlFor>`, `autoComplete`, and `role="alert"` in place — styling is additive to the current semantics, never a replacement markup pass.
- **Do** use Signal Blue only for the single primary action and focus states on any given screen (**The One Accent Rule**).
- **Do** give every error, lockout, and success message its own visually distinct block with color, not just inline text color.
- **Do** keep the auth flow's card centered and narrow (400px) even as the Admin table goes full-width — the two surfaces are allowed different density registers.

### Don't:
- **Don't** add box-shadows, gradients, or glassmorphism anywhere — this system is flat by contract (**The Flat-By-Default Rule**).
- **Don't** introduce a second type family for headings or marketing-style display text — one face, weight/size only (**The One Family Rule**).
- **Don't** invent a logo, product name treatment, illustration, or marketing copy — PRODUCT.md records no brand commitments, and none should be fabricated here.
- **Don't** style the Admin table with dense zebra striping or heavy borders — row state (disabled, self) is the visual variable that must stay legible, not a decorative grid.
