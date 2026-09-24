# 04: Strict SPA CSP, with e2e on the production build

**What to build:** The built SPA is served under a strict Content-Security-Policy that blocks injected script even if an escaping bug slips through. The e2e suite runs against that production build and fails on any CSP violation. See spec §SPA hardening (XSS) and §Testing Decisions › E2E.

**Blocked by:** 03

**Status:** ready-for-agent

- [ ] The CSP is defined once in the frontend build config, with `connect-src` built from the API base URL: `default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; font-src 'self'; connect-src 'self' <API origin>; object-src 'none'; base-uri 'none'; form-action 'self'; frame-ancestors 'none'; require-trusted-types-for 'script'`.
- [ ] `vite preview` sends it together with `Referrer-Policy: no-referrer`, `X-Content-Type-Options: nosniff`, `Permissions-Policy: camera=(), geolocation=(), microphone=()` and `X-Frame-Options: DENY`. The README documents these as the headers a production static host must send.
- [ ] `index.html` has no inline script. The theme pre-paint script is a static same-origin file loaded with a blocking `<script src>` in `<head>`, and dark mode still doesn't flash. `<meta name="referrer" content="no-referrer">` is added.
- [ ] Any directive relaxed because a dependency breaks it (only `style-src` or Trusted Types, never `script-src`) is justified in the README and in this ticket's Comments.
- [ ] Playwright's frontend `webServer` builds and then runs `vite preview` on `FRONTEND_PORT` (default `3000`).
- [ ] A shared e2e fixture fails any test on a `securitypolicyviolation` event or a CSP console error, and every spec uses it.
- [ ] An e2e check asserts that the served document carries the CSP header.
- [ ] The existing Stories 1–3 pass under the CSP. `npm run check` is green.
