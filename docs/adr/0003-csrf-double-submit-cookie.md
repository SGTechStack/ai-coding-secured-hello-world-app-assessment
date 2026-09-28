# ADR-0003: CSRF via double-submit cookie with an SPA-specific token handler

## Status

Accepted

## Context

Since auth is cookie-based, CSRF protection is required on all
state-changing endpoints (per the PRD's non-functional requirements).
`SecurityConfig` implements this as:

```java
csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
    .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler());
```

- The token is issued as an `XSRF-TOKEN` cookie with `HttpOnly=false` (so
  frontend JS can read it) and must be echoed back on an `X-XSRF-TOKEN`
  header — the standard double-submit pattern.
- `CsrfCookieFilter` forces the token to render its cookie on *every*
  response, including the first unauthenticated page load. Without this,
  the cookie is only written lazily the first time something touches
  `CsrfToken.getToken()`, and the SPA would never see it in time to attach
  the header on its first state-changing request.
- `SpaCsrfTokenRequestHandler` overrides Spring Security's default
  `XorCsrfTokenRequestAttributeHandler`. The default XORs/masks the token
  for classic HTML-form use; an SPA reads the raw cookie value and sends it
  back unmodified, which fails the default's unmask-and-compare step and
  would 403 every request. This override is the fix documented in Spring
  Security's own SPA-CSRF reference guidance, not a workaround invented
  locally.
- The H2 console (`/h2-console/**`) is the only CSRF-exempt path, and only
  when the `dev` Spring profile is active — gated on
  `environment.acceptsProfiles(Profiles.of("dev"))`, deliberately *not* on
  `spring.h2.console.enabled`. That property only controls servlet
  registration; using it alone as the CSRF gate would leave an
  unauthenticated, CSRF-exempt SQL console exposed if it were ever left
  enabled outside dev.

**Non-obvious subtlety:** `permitAll()` (authorization) and CSRF-exemption
are two independent axes here. `/api/auth/login`, `/api/auth/register`, and
`/api/password-reset/**` are `permitAll()` but are *not* CSRF-exempt —
confirmed by tests asserting a 403 on those endpoints when the CSRF header
is missing. It would be easy for a future contributor to assume
"unauthenticated-reachable" implies "CSRF-exempt" and accidentally widen the
exemption list when adding a new public endpoint.

## Decision

Use the cookie-based double-submit pattern with a custom SPA request
handler, and gate the only CSRF exemption on the active Spring profile
rather than a feature-toggle property.

## Consequences

- Any new public (`permitAll()`) endpoint must still carry a valid CSRF
  token if it performs a state-changing action — `permitAll()` alone does
  not exempt it. This should be called out explicitly in review when new
  endpoints are added.
- The H2-console CSRF exemption must remain tied to the `dev` profile check,
  not to `spring.h2.console.enabled`, if this logic is ever refactored.
- `SpaCsrfTokenRequestHandler` must be kept in sync with whatever CSRF
  token-reading approach the frontend uses; if the frontend ever moves off
  reading `XSRF-TOKEN` directly (e.g. behind a proxy that renames headers),
  this handler needs to move with it.
