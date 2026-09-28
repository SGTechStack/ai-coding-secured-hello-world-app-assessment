# 02: SPA skeleton and document headers

**What to build:** A React 19 + Vite + strict TypeScript single-page app on `localhost:5173`, using shadcn/ui on Base UI, React Hook Form + Zod and TanStack Query. It renders a placeholder public route. The document is hardened from day one:
- the three-layer CSP, with a templated meta tag placed before every script (ADR-059; ADR-060);
- no inline script (R-HDR-001);
- an unsplit `style-src` (REJ-054);
- a document-wide `Referrer-Policy` meta tag first in `<head>` (REJ-053);
- no service worker (T-E2E-001).

The API origin is baked in at build time (R-BLD-012), and the `.env.*` files stay committed (R-CFG-012). The Vitest + MSW harness (level F) and the Playwright harness (level E, Chromium and Firefox) are set up. A Vitest or Playwright test puts its T-ID in the test name.

**Blocked by:** None (can start immediately)

**Status:** done

- [x] `npm run build` produces a bundle whose document has the CSP meta tag ahead of every script (T-BLD-010) and the `Referrer-Policy` meta first in `<head>`.
- [x] The production document contains no inline script.
- [x] No service worker is registered (T-E2E-001).
- [x] The dev and preview servers send `frame-ancestors 'none'` (R-HDR-010).
- [x] One Vitest test and one Playwright test run green, each carrying a T-ID in its name.
