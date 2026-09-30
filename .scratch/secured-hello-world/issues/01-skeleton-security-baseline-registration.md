# 01: App skeleton, security baseline & registration

**What to build:** A visitor can open the React app (its own origin) and register an account with a username, email, and password against a Spring Boot REST backend (its own origin). This ticket also lays the cross-cutting security foundation every later ticket builds on: CORS allow-listing the frontend origin with credentials enabled, CSRF protection wired for state-changing requests, the session-cookie filter chain (HttpOnly, `Secure` in prod, `SameSite`, session-fixation protection) even though login itself lands in ticket 02, security response headers, sanitized error responses, a redacting audit-logging seam, a secured metrics/health surface, and the `users` / `password_reset_tokens` JPA schema over H2.

**Blocked by:** None (can start immediately)

**Status:** done

- [x] React app (e.g. `localhost:3000`) and Spring Boot REST API (e.g. `localhost:8080`) run and talk to each other over a credentialed HTTP client (cookies included, CSRF token attached to state-changing requests)
- [x] CORS explicitly allow-lists the frontend origin(s) with `Access-Control-Allow-Credentials: true` (IM8 `as-7`)
- [x] CSRF protection is enabled for all state-changing endpoints; register uses it now, and login/logout/reset/admin mutations in later tickets reuse this same configuration (IM8 `as-1`)
- [x] Session cookie is configured `HttpOnly`, `Secure` when the prod profile is active, `SameSite`, with session-fixation protection — the filter chain and Spring Session config exist now even though no endpoint issues a session yet (IM8 `as-11`)
- [x] Server-side sessions are backed by **Spring Session** (per the PRD's stated primary auth mechanism), not bare container `HttpSession`, so the session store is swappable later without touching the security config
- [x] Content-Security-Policy and HSTS (prod) response headers are set; error responses never leak stack traces or other internal details (IM8 `as-9`, `as-10`, `as-13`)
- [x] Password hashing uses the standard `BCryptPasswordEncoder`, no custom hashing (IM8 `as-6`, `as-14`)
- [x] No secret (admin seed credential, DB config) is hardcoded or committed; all secrets are supplied via externalized configuration (IM8 `as-8`)
- [x] A minimal metrics/health endpoint exists and is either authenticated or bound to a separate management port — not left open — as the future hook for key-signals monitoring (IM8 `lm-16`)
- [x] `users` table matches the PRD data model (id, username unique, email unique, password_hash, role enum, enabled, failed_login_attempts, locked_until, created_at); `password_reset_tokens` table exists for ticket 04
- [x] Schema is defined with standard JPA/SQL types only, no H2-specific dialect features, so it is portable to Postgres/MySQL later via a configuration/dialect change alone (per the PRD's stated persistence goal)
- [x] A visitor can submit a unique username, unique email, and a password ≥12 characters; the account is created with role `USER`, `enabled = true`, and the password stored as a BCrypt hash
- [x] A username or email that's already registered is rejected with a clear validation error and no account is created
- [x] A password that fails the strength policy is rejected with a validation error and no account is created (IM8 `as-1`, `as-5`)
- [x] The plaintext password is never logged or stored, on any path (IM8 `lm-19`)
- [x] A structured, redacted audit log line is emitted for registration attempts (IM8 `lm-4`, `lm-15`)
- [x] Integration test coverage: successful registration, duplicate username/email, weak password rejection

## Comments

Implemented in backend/frontend (commit `cce8cf49`, "Implement tickets 01-02"). 8 integration tests
(RegistrationControllerTest, SecurityBaselineTest) pass; full register→login→hello→logout flow also
verified against a real running server, not just MockMvc.
