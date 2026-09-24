# 20 — Decide the origin topology: two origins, or one behind a reverse proxy

Type: grilling
Status: open
Blocked by: —

## Question

Do the SPA and the API stay on separate origins as the PRD specifies, or sit behind a single origin
via a reverse proxy?

This was never a ticket because the PRD appeared to settle it. Two independent findings from the
research tier have turned it back into a real decision, and it blocks both the session/CSRF ticket
and the frontend ticket.

## Why it reopened

**Finding 1 — the inherited cookie posture is wrong for two origins.** From "Verify the App
Standard's controls are still current practice": OWASP now names `SameSite=Strict` preferred over
`Lax`, and recommends the `__Host-` cookie prefix for session IDs. `SameSite` is scoped to the
registrable domain rather than the origin, so on our topology `Strict` costs nothing and is free
protection we are currently declining. But the App Standard mandates `Lax` (confirmed in two places
by "Inventory the prescribed recipes": the headers recipe §3 Step 2, and
`Standalone_Session_Login_with_CSRF_Bootstrap.md` line 40), and its recipes appear to assume a single
origin serves both.

**Finding 2 — the CSP obligation lands where it does nothing.** From "Inventory the prescribed
recipes": `Common_Security_Headers_and_SPA_CSRF_Configuration.md` sets
`default-src 'self'; object-src 'none';` inside the Spring Security filter chain, so the header ships
on **API** responses. The API returns JSON, never a document, so the policy governs no browsing
context, and `'self'` resolves to the API origin rather than where the SPA's scripts live. Worse, App
Standard §3.5 requires the headers "at the application level" and calls infrastructure-level
injection "not a substitute" — pointing the control at the one origin where it has no effect while
ruling out the one place it would work. The standard's own question inventory (Q26) only asks which
external domains to whitelist; it has no slot for *which origin the policy protects*. Meanwhile IM8
**as-9** FAILs outright if no CSP is found.

Both problems dissolve under a single origin. That is two independent arguments pointing the same
way, which is why they belong in one decision rather than being patched separately in tickets 08 and 14.

## What to decide

- **Two origins (PRD as written)** — keeps `localhost:3000` / `localhost:8080`, CORS with
  credentials, and accepts that: the SPA origin needs its own CSP delivered by whatever serves the
  static bundle (outside every recipe's scope, so we design it ourselves), `X-Frame-Options` on the
  API does not protect the SPA document so `frame-ancestors` must cover it, and the `SameSite` choice
  must be re-argued against the standard's `Lax` mandate.
- **One origin behind a reverse proxy** — the SPA and `/api/**` served from a single host. Closes the
  CSP gap (the recipe's CSP then lands on the SPA document as written), makes `Strict` trivially
  correct, keeps the standard's `Lax` mandate satisfiable, and removes CORS entirely. Costs: it
  contradicts the PRD's explicit "Cross-origin: CORS configured on the backend" premise and its
  stated `localhost:3000` / `localhost:8080` split, and infra is out of scope so the proxy is either
  a Vite dev proxy (dev only, papering over the production question) or a documented deployment
  requirement.
- **A hybrid** — separate origins in dev, single origin in production. Tempting, and dangerous: the
  cookie and CSP behaviour then differs between the environment we test and the one we ship, which is
  how `SameSite` and CSP bugs reach production undetected.

Whichever wins, decide and record:

- The `SameSite` value, and whether `__Host-` is used. Note from "Pin down the Spring Security 7
  config surface" that `__Host-` works with Spring Session but forces a **per-profile cookie name**,
  because browsers reject the prefix over plain HTTP.
- Which host emits which CSP, and the full directive set for the document context —
  `connect-src` must name the API origin, plus `frame-ancestors`, `form-action`, `base-uri`,
  `script-src`, `style-src`.
- Whether the API-side CSP is retained as defence in depth. Worth keeping (it hardens Boot's `/error`
  page) but it must be recorded as defence in depth, so a later reviewer does not read a passing
  `curl -I` against the API as the obligation being met.
- The deployment note that follows, since `SameSite` protection depends on the SPA and API remaining
  same-site. If they ever aren't, the cookie posture breaks — a deployment-blocking fact.

## Done when

The topology is chosen, the `SameSite` and `__Host-` decisions follow from it, the CSP owner and
directive set are written down for the document-serving origin, and the deployment constraint is
recorded for whoever ships this.
