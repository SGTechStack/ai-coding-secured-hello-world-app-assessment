---
status: accepted
---

# ADR-038: The session id rotates on factor grant and credential change, with one `AUTH_INSTANT` stamping point

The session id rotates at password sign-in, at every second-factor grant (enrolment confirmation, verification and
each step-up), and when the user changes their own password. Only password sign-in writes `AUTH_INSTANT`, the
anchor of the 8-hour absolute lifetime. The other rotations use a minimal strategy that never touches it. The
filter enforcing the absolute lifetime sits after `SecurityContextHolderFilter` and before `CsrfFilter`. Each of
these looks arbitrary in code, and each one breaks silently when "tidied".

## Context

- OWASP ASVS 5.0 **7.2.4 (L1)** requires a new session token on authentication, including re-authentication. The
  OWASP Session Management Cheat Sheet asks for renewal on any privilege change, and a factor grant is one.
- The absolute lifetime cannot use the session's creation time. `changeSessionId()` preserves it, so it would date
  from before sign-in. It is measured from an `AUTH_INSTANT` attribute instead.
- Spring Security 7.1's `AbstractAuthenticationProcessingFilter` calls the session strategy on **every** successful
  attempt, a second factor included. A filter configured through the DSL receives the shared strategy, which here is
  the login composite. So the framework's default path re-stamps `AUTH_INSTANT` on every step-up, and a working admin
  who steps up every ten minutes never reaches the absolute limit. The same failure appeared on three separate paths
  during design: factor grant, step-up and password-change rotation.
- Password login uses a custom JSON authentication filter with a hand-built `CompositeSessionAuthenticationStrategy`.
  The DSL adds `CsrfAuthenticationStrategy` to its own composite for free. A hand-built one does not, and without it
  a CSRF token planted before sign-in survives sign-in.

## Decision

- **Login composite, the only place `AUTH_INSTANT` is stamped:** concurrent-session control, the audit session-start
  step, session-id change, session registration and `AUTH_INSTANT` stamping, plus `CsrfAuthenticationStrategy`
  listed explicitly (with a comment saying why, since the DSL would have added it) and a reset of the session's idle
  interval to the configured value. Two positions are fixed. The audit step comes **after** concurrent-session
  control, so the displaced session is already known, and **before** the id changes, so its row carries the
  pre-rotation session hash that joins it to the failed attempts before it (T-AUD-015). The interval reset sits
  beside the stamping strategy, not inside it, and running it twice is harmless.
- **Minimal strategy** for factor grants and self-service or forced password change: session-id change,
  `CsrfAuthenticationStrategy`, and an explicit save of the security context. It never stamps `AUTH_INSTANT`. The
  factor-grant filters are registered with `addFilterBefore` and get this strategy set by hand, not through a
  configurer, which would inject the login composite.
- **A factor grant replaces the `Authentication`.** `FactorGrantedAuthority#getIssuedAt()` is the clock the factor
  rule's `validDuration` reads, so a step-up builds a new authority set and saves it. It does not append a second
  factor authority, because the authorization check uses the first match it finds.
- **Filter order:** the source rate limiter first in the chain, then `SecurityContextHolderFilter`, then the
  absolute-lifetime filter, then `CsrfFilter`. An absolutely expired session posting a mutation gets
  `401 AUTHENTICATION_FAILED`. If the filter ran after `CsrfFilter`, it would get a misleading 403, a pointless token
  re-fetch and retry, and only then the 401. (Amended 2026-09-29.) It also runs after `HeaderWriterFilter` and
  `CorsFilter`: before them, its 401 carried no CORS headers, so the SPA on its own origin saw a network error instead
  of `AUTHENTICATION_FAILED`, and no security headers (T-SES-037).
- The same filter's second branch handles anonymous sessions, which have no `AUTH_INSTANT`, by pinning their expiry
  (REJ-091).

## Consequences

- Every rotation also rotates the CSRF token, so the SPA re-fetches it after sign-in, sign-out, factor verification
  and enrolment confirmation (ADR-036, ADR-040).
- `AUTH_INSTANT` and the principal name are both set only at password sign-in, and two definitions of "anonymous
  session" rely on that. Setting either one anywhere else reopens ADR-040 and ADR-041.
- Tests: T-SES-005 (login rotates the id), T-SES-006 (lifetime runs from authentication, not creation, and an expired
  session posting gets 401, not 403), T-SES-034 (the interval reset does not re-stamp), T-AUD-015 (audit step order),
  T-CSRF-007 (a pre-login token fails after login).

## Sources

- OWASP ASVS 5.0, V7.2: 7.2.4 (L1); V7.3: 7.3.2 (L2).
- OWASP Session Management Cheat Sheet, "Renew the Session ID After Any Privilege Level Change" (names password
  changes and permission changes).
- Spring Security 7.1.x source: `AbstractAuthenticationProcessingFilter#doFilter` (session strategy on every
  success), `AbstractAuthenticationFilterConfigurer#configure` (shared strategy injected), `FactorGrantedAuthority`
  (`getIssuedAt()`), `AllRequiredFactorsAuthorizationManager` (first matching authority decides).
- Standalone User Access Control Application Standard §3.5 (absolute lifetime default of 8 hours), §5 Authentication
  and Session Tests.
