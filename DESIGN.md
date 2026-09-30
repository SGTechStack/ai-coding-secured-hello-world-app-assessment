---
name: Secured Hello World App
description: A restrained, correctness-first console for a username/password auth reference implementation.
colors:
  signal-blue: "#2563eb"
  signal-blue-deep: "#1d4ed8"
  selection-wash: "#dbeafe"
  ink: "#0f172a"
  slate: "#475569"
  steel: "#64748b"
  mist: "#94a3b8"
  hairline: "#e2e8f0"
  fog: "#f8fafc"
  row-hover: "#fbfcfe"
  paper: "#ffffff"
  danger: "#dc2626"
  danger-ink: "#b91c1c"
  danger-wash: "#fef2f2"
  success: "#16a34a"
  success-ink: "#15803d"
  success-wash: "#f0fdf4"
  warning: "#d97706"
  warning-ink: "#b45309"
  warning-deep: "#92400e"
  warning-wash: "#fffbeb"
typography:
  title:
    fontFamily: "system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif"
    fontSize: "28px"
    fontWeight: 600
    lineHeight: 1.2
  section-title:
    fontFamily: "system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif"
    fontSize: "14px"
    fontWeight: 600
    lineHeight: 1.3
  body:
    fontFamily: "system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif"
    fontSize: "16px"
    fontWeight: 400
    lineHeight: 1.5
  table-body:
    fontFamily: "system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif"
    fontSize: "15px"
    fontWeight: 400
    lineHeight: 1.5
  label:
    fontFamily: "system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif"
    fontSize: "14px"
    fontWeight: 500
    lineHeight: 1.3
  column-header:
    fontFamily: "system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif"
    fontSize: "13px"
    fontWeight: 500
    lineHeight: 1.3
    letterSpacing: "0.02em"
  small:
    fontFamily: "system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif"
    fontSize: "13px"
    fontWeight: 400
    lineHeight: 1.4
  button:
    fontFamily: "system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif"
    fontSize: "16px"
    fontWeight: 600
    lineHeight: 1.5
  button-sm:
    fontFamily: "system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif"
    fontSize: "13px"
    fontWeight: 600
    lineHeight: 1.5
  literal:
    fontFamily: "ui-monospace, 'SF Mono', Consolas, monospace"
    fontSize: "12px"
    fontWeight: 500
    lineHeight: 1
    letterSpacing: "0.04em"
rounded:
  xs: "4px"
  sm: "6px"
  md: "8px"
spacing:
  "2xs": "4px"
  xs: "6px"
  sm: "8px"
  md: "12px"
  lg: "16px"
  xl: "20px"
  "2xl": "24px"
  "3xl": "32px"
  "4xl": "48px"
components:
  button-primary:
    backgroundColor: "{colors.signal-blue}"
    textColor: "{colors.paper}"
    typography: "{typography.button}"
    rounded: "{rounded.md}"
    padding: "12px 20px"
  button-primary-hover:
    backgroundColor: "{colors.signal-blue-deep}"
  button-secondary:
    backgroundColor: "transparent"
    textColor: "{colors.ink}"
    typography: "{typography.button}"
    rounded: "{rounded.md}"
    padding: "12px 20px"
  button-secondary-hover:
    backgroundColor: "{colors.fog}"
  button-ghost:
    backgroundColor: "transparent"
    textColor: "{colors.slate}"
    typography: "{typography.button-sm}"
    rounded: "{rounded.sm}"
    padding: "6px 10px"
  button-ghost-hover:
    backgroundColor: "{colors.paper}"
    textColor: "{colors.ink}"
  button-destructive:
    backgroundColor: "transparent"
    textColor: "{colors.danger-ink}"
    typography: "{typography.button-sm}"
    rounded: "{rounded.sm}"
    padding: "6px 10px"
  button-destructive-hover:
    backgroundColor: "{colors.danger}"
    textColor: "{colors.paper}"
  button-destructive-solid:
    backgroundColor: "{colors.danger}"
    textColor: "{colors.paper}"
    typography: "{typography.button-sm}"
    rounded: "{rounded.sm}"
    padding: "6px 10px"
  button-destructive-solid-hover:
    backgroundColor: "{colors.danger-ink}"
  button-caution:
    backgroundColor: "{colors.warning-ink}"
    textColor: "{colors.paper}"
    typography: "{typography.button-sm}"
    rounded: "{rounded.sm}"
    padding: "6px 10px"
  button-caution-hover:
    backgroundColor: "{colors.warning-deep}"
  button-row-action:
    typography: "{typography.button-sm}"
    rounded: "{rounded.sm}"
    height: "32px"
    padding: "6px 10px"
  input:
    backgroundColor: "{colors.paper}"
    textColor: "{colors.ink}"
    typography: "{typography.body}"
    rounded: "{rounded.md}"
    padding: "12px 14px"
  input-disabled:
    backgroundColor: "{colors.fog}"
    textColor: "{colors.mist}"
  card:
    backgroundColor: "{colors.paper}"
    rounded: "{rounded.md}"
    padding: "32px"
    width: "400px"
  alert-error:
    backgroundColor: "{colors.danger-wash}"
    textColor: "{colors.danger-ink}"
    rounded: "{rounded.md}"
    padding: "12px 14px"
  alert-success:
    backgroundColor: "{colors.success-wash}"
    textColor: "{colors.success-ink}"
    rounded: "{rounded.md}"
    padding: "12px 14px"
  alert-warning:
    backgroundColor: "{colors.warning-wash}"
    textColor: "{colors.warning-ink}"
    rounded: "{rounded.md}"
    padding: "12px 14px"
  badge-role:
    backgroundColor: "{colors.paper}"
    textColor: "{colors.slate}"
    typography: "{typography.literal}"
    rounded: "{rounded.sm}"
    padding: "5px 7px"
  badge-role-admin:
    textColor: "{colors.ink}"
  table-header:
    backgroundColor: "{colors.fog}"
    textColor: "{colors.slate}"
    typography: "{typography.column-header}"
    padding: "10px 12px"
  table-row:
    backgroundColor: "{colors.paper}"
    textColor: "{colors.ink}"
    typography: "{typography.table-body}"
    height: "56px"
    padding: "8px 12px"
  table-row-hover:
    backgroundColor: "{colors.row-hover}"
  table-row-disabled:
    backgroundColor: "{colors.fog}"
    textColor: "{colors.slate}"
  table-row-confirm-delete:
    backgroundColor: "{colors.danger-wash}"
  table-row-confirm-grant:
    backgroundColor: "{colors.warning-wash}"
---

# Design System: Secured Hello World App

## Overview

**Creative North Star: "The Instrument Panel"**

This is a reference implementation whose entire value proposition is that the security behavior is correct and legible: lockouts, generic errors, role gating, session lifecycle. The interface reads like a well-made instrument panel. Every control does exactly one thing, state is always visible, and nothing decorative competes with the readout. No brand metaphor, no marketing voice, no illustration; the app has no brand to express, only behavior to make trustworthy and easy to verify at a glance.

The palette is restrained (neutrals plus one functional accent), not committed or drenched. The visitor is a technical reviewer completing a task, and color's only job is to mark state (focus, error, success, danger) unambiguously. Two density registers coexist: narrow, quiet auth cards, and a full-width admin table tuned for scanning many rows. Confirmed visual rejections: no gradients, no glassmorphism, no decorative iconography standing in for content, no generic SaaS-dashboard clip-art feel.

**Key Characteristics:**
- Quiet, high-legibility forms with generous whitespace and a single clear primary action per screen
- One accent reserved for primary actions and focus; its scarcity is what makes it read as "the thing to do here"
- Flat surfaces with hairline borders; depth comes from spacing, Fog/Paper contrast, and borders, never elevation
- Error, lockout, success, and destructive-confirmation states treated as first-class UI blocks, not inline text color
- Every text and control-edge color meets WCAG 2.2 AA contrast; there is no "subtle but unreadable" tier

## Colors

Restrained: a cool slate neutral ramp, one blue accent, and semantic state colors that each come as a base shade (edges, dots, fills), a darker ink shade (text), and a pale wash (block backgrounds).

### Primary
- **Signal Blue** (`signal-blue`): primary buttons, focus rings (2px outline, 2px offset), links, focused input borders, and the text caret. The "do this" color, never a background region.
- **Signal Blue Deep** (`signal-blue-deep`): hover/pressed state of the primary button only.
- **Selection Wash** (`selection-wash`): text-selection highlight behind Ink text. The one sanctioned tint of the accent.

### Neutral
- **Ink** (`ink`): headings, primary body text, table values, ADMIN badge text and border.
- **Slate** (`slate`): secondary text, field labels, column headers, helper copy, email and date cells, disabled-row text, the "(you)" note.
- **Steel** (`steel`): text-input borders at rest. The only neutral dark enough to outline a control (4.8:1 on Paper).
- **Mist** (`mist`): disabled input text and decorative separators (the dots between counts) only. Never readable content.
- **Hairline** (`hairline`): dividers, card and table frames, table rules, secondary-button and badge outlines, skeleton bars.
- **Fog** (`fog`): page background, table header row, disabled-row wash, disabled input fill.
- **Row Hover** (`row-hover`): barely-there hover tint on table rows.
- **Paper** (`paper`): card, table body, and input surfaces.

### Semantic (state)
- **Danger** (`danger`), **Danger Ink** (`danger-ink`), **Danger Wash** (`danger-wash`): errors, invalid fields, destructive actions. Base for invalid-input borders, the destructive button outline, and solid destructive fills (white text on it is 4.8:1). Ink for error text and destructive labels. Wash behind error alerts and the row awaiting delete confirmation.
- **Success** (`success`), **Success Ink** (`success-ink`), **Success Wash** (`success-wash`): confirmations ("reset link sent", "alice disabled."). Base for the enabled-state dot and alert edge; Ink for text.
- **Warning** (`warning`), **Warning Ink** (`warning-ink`), **Warning Deep** (`warning-deep`), **Warning Wash** (`warning-wash`): caution, for actions that are consequential but reversible. Today that means granting ADMIN: the row being confirmed takes the Wash, the prompt is Warning Ink, and the Grant admin button is filled Warning Ink (Deep on hover). Also reserved for throttle notices that don't identify an account.

### Named Rules
**The One Accent Rule.** Signal Blue appears on at most one primary action per screen plus focus rings. It never becomes a background fill, a decorative shape, or a badge color. Even ADMIN is marked with Ink and weight, not blue.

**The Readable-Or-Decorative Rule.** Text is ≥4.5:1 against its actual background (≥3:1 at 24px+, or 18.66px+ bold); input borders and focus indicators are ≥3:1. Mist and Hairline fail those thresholds, so they are for disabled and decorative use only. The test: if a reviewer needs to read it or find it, it may not be Mist or Hairline.

**The Ink-Shade Rule.** Semantic text always uses the `-ink` shade, never the base. The base shades (`danger`, `success`, `warning`) measure 3.1–4.4:1 on their own washes, which fails AA for 14px text.

**The No Lockout Tell Rule.** A locked or disabled account shows exactly the same "Invalid username or password" block as a wrong password. Showing a distinct lockout state would reveal which usernames exist (enumeration), so the Warning register is never used on the login form.

**The Error Is Not Red Text Rule.** Every error state gets a full block: Danger Wash fill, a Danger left edge, Danger Ink text, and enough space around it to read as a distinct block.

## Typography

**Body/UI Font:** the platform system UI face (`system-ui`, with -apple-system, Segoe UI, Roboto, sans-serif fallbacks)
**Literal Font:** the platform monospace (`ui-monospace`, SF Mono, Consolas, monospace)

**Character:** A single workhorse grotesque doing every job, differentiated only by weight and size. The system face is deliberate: the CSP is `font-src 'self'` and no webfont ships, so the app renders instantly and matches the reviewer's OS.

### Hierarchy
- **Title** (`title`): page heading ("Hello, alice", "Manage users"). Balanced wrapping; long usernames break anywhere rather than overflow.
- **Section Title** (`section-title`): in-card section heading ("Administration").
- **Body** (`body`): paragraph copy, input values, the session readout values.
- **Table Body** (`table-body`): table cells. Usernames at 600; dates at 14px Slate with tabular numerals.
- **Label** (`label`): form labels and readout keys, in Slate.
- **Column Header** (`column-header`): table headers, Slate, never wrapping.
- **Small** (`small`): helper text, footnotes, the "(you)" note, counts line (14px variant).
- **Button / Button Small** (`button`, `button-sm`): all button labels; 600 weight.
- **Literal** (`literal`): system values rendered verbatim, currently the USER/ADMIN role badges.

### Named Rules
**The One Family Rule.** One sans family carries every heading, label, body, and button. No display or serif face is introduced.

**The Literal-Only Mono Rule.** Monospace marks values the system emits verbatim (role enums, IDs, timestamps if shown raw). It is never a costume for "technical" copy.

**The Tabular Numbers Rule.** Counts and dates use `font-variant-numeric: tabular-nums` so columns of numbers align.

## Layout

Every view renders inside a single `<main>` landmark. One page wrapper applies all outer padding (24px on mobile, 48px from 640px up) and a 150ms cross-fade on view change (disabled under reduced motion).

- **Auth flow + Hello:** a single centered card, 400px max width, vertically centered. 20px between fields, 24px before the action group.
- **Hello page:** title, then a key/value session readout (48px rows divided by Hairline), an optional Administration section 32px below, and a footer (32px above, Hairline top rule) holding Log out, right-aligned.
- **Admin console:** 960px max width, top-aligned with 48px top padding. Ghost Back button, then a header with the title and a counts line ("4 accounts · 2 admins · 1 disabled"), with the success notice right-aligned on the same line (wrapping below on narrow screens; its line is always reserved so the header never jumps). 20px to the table, then a legend line explaining the self row.
- **Responsive table:** the table sits in a scroll frame. Below 640px it scrolls horizontally rather than squeezing columns, the Username column stays pinned to the left edge (Hairline right rule) so every row stays identifiable, cell padding drops to 8px 12px, and row actions become 32px icon-only buttons. While more columns remain off to the right, a Small Slate hint ("Scroll sideways for more columns →") sits under the frame. It's text, not a fade, because the system has no gradients. Emails truncate with an ellipsis at 220px and show the full address on hover.

Breakpoint: 640px, the only one.

## Elevation & Depth

Flat by design. There are no box-shadows anywhere. Surfaces are separated by the Paper-on-Fog value step and 1px Hairline frames; state is shown by washes (Fog for disabled rows, Danger Wash for a pending delete), never lift.

### Named Rules
**The Flat-By-Default Rule.** No shadows at rest, on hover, or on focus. A card or table stands apart from the page by a Hairline border and/or Fog background only.

## Shapes

Softened, never pill: gently rounded 8px (`md`) on cards, inputs, primary/secondary buttons, alerts, and the table frame; 6px (`sm`) on small buttons and badges; 4px (`xs`) on link focus rings and skeleton bars. Status dots are circles. Borders are 1px at rest; the focused input border becomes 1.5px Signal Blue, a real border change, not a glow. The table's rounding lives on its scroll frame because a collapsed table cannot round its own corners.

## Components

### Buttons
Quiet, exact, one job each.
- **Primary** (`button-primary`): the single primary action of a form (Log in, Register, Send reset link, Change password). Darkens to Signal Blue Deep on hover.
- **Secondary** (`button-secondary`): transparent with a Hairline outline and Ink text; Fog on hover. Used for non-destructive actions (Enable/Disable, Make ADMIN/USER, Log out, the Hello page's Manage users row).
- **Ghost** (`button-ghost`): Slate text, no outline until hover. Used for Back.
- **Destructive** (`button-destructive`): Danger outline, Danger Ink text; fills Danger with white text on hover or keyboard focus. Only for Delete.
- **Destructive Solid** (`button-destructive-solid`): filled Danger, Danger Ink on hover. Only for the second, confirming click (Confirm delete).
- **Caution** (`button-caution`): filled Warning Ink, Warning Deep on hover. Only for the second, confirming click of a privilege grant (Grant admin).
- **Link buttons:** inline text in Signal Blue, with a 4px/2px padding offset by a negative margin so the hit area is at least 24px tall (WCAG 2.5.8) without moving the text.
- **Navigation row:** a full-width Secondary button with a leading icon, left-aligned label, and trailing chevron that nudges 2px right on hover (150ms, `cubic-bezier(0.16, 1, 0.3, 1)`). Used for "Manage users".
- **Focus:** every button shows a 2px Signal Blue outline at 2px offset on `:focus-visible`.
- **Disabled / submitting:** 60% opacity, no pointer events, and the label becomes a progress string ("Logging in…"). Row actions mid-request lock instead; the row dims to 60%.

### Icons
A small authored set: 16px, 1.5px round-capped strokes in `currentColor`, always paired with a text label (visible, or visually hidden at mobile widths), and `aria-hidden`. Glyphs: arrow-left, arrow-right, chevron-right, users, log-out, lock, ban, check-circle, shield-up, shield-down, trash.

### Cards / Containers
- **Corner Style:** 8px.
- **Background:** Paper on the Fog page, with a 1px Hairline border.
- **Internal Padding:** 32px desktop, 24px mobile.
- One card per auth screen and for the Hello page.

### Inputs / Fields
- **Style:** Paper fill, 1px Steel border, 8px radius, 12px 14px padding, Body text. Label above (6px gap).
- **Focus:** border becomes 1.5px Signal Blue. No glow.
- **Error:** border becomes Danger; the alert for that field sits directly beneath it. Focus still wins: a focused invalid field shows the Signal Blue border.
- **Disabled:** Fog fill, Mist text.

### Alerts
Full blocks: 8px radius, 12px 14px padding, 14px text, a 3px left edge in the base shade, wash fill, ink-shade text. Error uses `role="alert"`; success confirmations use `role="status"`. The admin console's post-action notice is lighter: an inline check icon and Success Ink text in a polite live region, cleared on the next action.

### Access Denied (403)
A card with a distinct, calm register, not the red form-error block: a role-gating refusal is the system working as designed. It has a 40px Ink-outlined square holding a lock icon, the Title "Access denied", the refusal sentence in Ink (`role="alert"`), and a Slate detail line naming the required role with an ADMIN badge. A Hairline rule then separates the Back button. It never claims the viewer's current role, because the client's cached role may be stale; that stale role is exactly why the server refused.

### Role Badge
A literal-font tag. USER: Paper fill, Hairline outline, Slate text. ADMIN: Ink outline, Ink text, 700 weight. Used in the session readout and the Role column.

### Enabled State
The Yes/No value is the text; an 8px dot before it makes the column scannable. Yes shows a solid Success dot; No shows a hollow ring outlined in Slate.

### Tables (Admin Users)
- **Frame:** Paper, Hairline border, 8px radius, horizontal scroll.
- **Header row:** Fog, Column Header type in Slate, Hairline bottom rule, no vertical rules.
- **Body rows:** 56px, Hairline rules, no zebra striping (it would compete with row state). Row Hover tint on hover.
- **Disabled user row:** the whole row takes a Fog wash with Slate text, so "disabled" scans down the list and every value stays readable.
- **Self row:** Actions shows a lock icon and "(you)" in Small Slate, with no controls. A legend under the table (lock icon, Small Slate) explains it: "(you) marks your own account. Admins can't disable, demote, or delete themselves."
- **Row actions:** right-aligned, never wrapping: Enable/Disable, Make ADMIN/USER (Secondary), Delete (Destructive), each with an icon. Enable, Disable and Make USER act in one click: they are reversible and reduce access. Below 640px they collapse to 32px icon-only squares; the label stays in the DOM, visually hidden, as the accessible name.
- **In-row confirmation:** used for Delete (irreversible) and Make ADMIN (privilege escalation). The first click replaces the row's actions in place with a prompt that names the account, a Cancel button (Secondary), and the confirming button. Delete shows "Delete {username}?" in Danger Ink, a Danger Wash row and Confirm delete (Destructive Solid). Make ADMIN shows "Grant admin to {username}?" in Warning Ink, a Warning Wash row and Grant admin (Caution). The prompt is the group's accessible name. Focus lands on Cancel; Esc or Cancel restores the actions and returns focus to the button that opened the confirmation. Only one row confirms at a time. On mobile the prompt stays visible, wrapping above the buttons, so you always see whose account is affected.
- **Loading:** three skeleton rows of Hairline bars that pulse gently (1.2s, static under reduced motion); no spinner.
- **Empty (only the admin exists):** a Slate note under the table explaining that new registrations appear here and what can be done with them.

## Do's and Don'ts

### Do:
- **Do** keep every existing `<label htmlFor>`, `autoComplete`, `role="alert"`, and `role="status"`; styling is additive to the semantics.
- **Do** use Signal Blue only for the single primary action and focus states on any screen (**The One Accent Rule**).
- **Do** use the `-ink` shade for all semantic text and Steel for input borders (**The Readable-Or-Decorative Rule**).
- **Do** give every error, lockout, success, and pending-delete state its own visually distinct block or wash.
- **Do** require a second, explicit, in-place click for irreversible actions and privilege grants, with the account named and focus on the safe choice.
- **Do** give system refusals (403, self-protection) their own calm, explained treatment, never the form-error block.
- **Do** keep auth cards at 400px while the admin console runs to 960px; the two density registers are intentional.
- **Do** pair every icon with a text label, visible or visually hidden.

### Don't:
- **Don't** use Mist or Hairline for readable text or for the edge that identifies a control.
- **Don't** add box-shadows, gradients, or glassmorphism anywhere (**The Flat-By-Default Rule**).
- **Don't** introduce a second sans or serif family, or use monospace for anything but literal system values.
- **Don't** mark ADMIN, or any other state, with the accent color.
- **Don't** show a distinct lockout or disabled-account message on login (**The No Lockout Tell Rule**).
- **Don't** invent a logo, product name treatment, illustration, or marketing copy.
