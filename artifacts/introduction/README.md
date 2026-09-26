# Hello Auth comic introduction

Open `index.html` directly in a browser. All artwork, styles, scripts and source excerpts are local. No installation, backend, credentials or network access is needed for reading. External reference links open only when selected.

Edit `content.json`, then regenerate both the reader and the root `introduction.md`:

```powershell
node artifacts/introduction/build.mjs
```

The generator verifies its source anchors against the working tree. If application code changes, re-review the claims and update the recorded revision before regenerating; a successful build checks anchors, not semantic freshness.

`evidence.json` records excerpts, source ranges and supporting chapter IDs. `coverage.json` states the scope and exclusions. `stack.json` distinguishes declared versions from unresolved managed versions. Test credentials appearing in excerpts are repository test fixtures, not deployment credentials.

## Diagrams

Seven Archify diagrams illustrate how the frontend and backend interact (trust boundaries, CSRF cookies, session lifecycle, login, brute-force defence, proxy trust, password reset). Each panel in `content.json` lists its diagrams by ID.

- `diagrams/*.json` — Archify source specifications (edit these).
- `diagrams/*.html` — interactive, self-contained viewers (pan/zoom, themes, export).
- `diagrams/*.visual-check.json` — Archify browser-check receipts.
- `img/*.png` — static captures embedded in the reader and `introduction.md`.

After editing a diagram, validate, deliver and re-capture it, then rebuild:

```powershell
$A = ".kiro/skills/archify/bin/archify.mjs"
cd artifacts/introduction/diagrams
node ../../../$A validate sequence login.sequence.json --quality showcase --json
node ../../../$A deliver sequence login.sequence.json login.html --quality showcase --json
node shot.cjs login.html ../img/login.png
cd ../../..; node artifacts/introduction/build.mjs
```

`build.mjs` fails if a referenced diagram HTML or PNG is missing.

## Review corrections

- The Java fluent configuration order is not the runtime filter order.
- The public route policy includes a password-reset subtree wildcard; `/me` also checks identity in its handler.
- CORS permission, SameSite and cookie host visibility must all work together for the browser client.
- Startup validation includes `SecurityTunablesValidator`; it is not limited to admin/database settings.
- Admin password length follows the configured minimum, and the validator does not reject unresolved-placeholder syntax explicitly.
- Missing database values do not have a uniform explicit nonblank-validation contract. The prod-profile test uses an empty database password.
- A blank reset URL does not disable reset processing, and the email adapter remains a stub in production.
- API response headers do not establish the separately hosted frontend document’s policy.
- Dev and prod can both be active. Deployment must enforce the intended profile set.
- A threshold inequality is not proof of distributed or concurrent rate-limit guarantees.
- Successful validation does not prove seeding succeeded, and seed environment changes do not rotate an existing administrator’s password.

## Verification

The reader check uses the repository’s existing Playwright package and locally installed Microsoft Edge:

```powershell
node artifacts/introduction/verify.cjs
```

It checks search/no-results/clear, navigation after filtering, chapter deep-link targets, source expansion using mouse and keyboard, route filtering, local links, browser errors and page overflow at four viewport widths. Results are saved to `verification.json`; desktop/mobile screenshots are included for visual inspection. No backend tests, migrations, production calls or deployment checks were performed for this documentation update.

## Artwork provenance

`developers.png` was created using the built-in image-generation tool. The reader uses CSS framing of the same illustration for consistent senior/junior portraits; the Markdown edition uses the complete illustration. Existing user images were left unchanged.

Final prompt:

> Create one polished wide editorial comic illustration for a developer security playbook, landscape 3:2. Two distinct characters facing each other across a laptop: on left senior software engineer, middle aged man with warm brown skin, short salt-and-pepper curls, round dark glasses, short neat beard, mustard yellow overshirt over navy tee, calmly explaining with open hand; on right junior software engineer, adult woman with medium brown skin, dark hair in high bun, teal jacket, curious confident expression, holding a small notebook. Waist-up expressive portraits, crisp hand-inked black outlines, sophisticated Franco-Belgian graphic novel look, restrained halftone shading, warm ivory paper background, amber and teal accents, tactile print aesthetic. Clear silhouette and expressive human faces, balanced negative space, professional and friendly. Subtle abstract shield motif behind laptop, no code, no lettering, no speech bubbles, no text, no watermarks. This is a finished illustration to replace emoji portraits in a comic-style technical introduction.
