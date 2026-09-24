# Map: Hello World Auth App

Status: complete — the way to the destination is clear

## Destination

A running React + Spring Boot auth demo implementing `assessment-prd.md` end to end. This map is **decisions only**: it is done when every decision standing between the spec and implementation is resolved. Building happens in later sessions, outside the map.

## Notes

- **Canonical spec:** `assessment-prd.md` (Connextra stories + Given/When/Then ACs). `assessment-wayfinder.md` is supporting context — its concrete endpoint paths (`POST /api/auth/login`, `PATCH /api/admin/users/{id}/status`, etc.) are the default wire contract to ratify in ticket 07.
- **Stack pins (charting answers):** Java 21 + Maven; Spring Boot latest stable compatible with Java 21 (ticket 01 confirms exact line). Frontend: Vite + TypeScript + a UI component library (library choice is ticket 04).
- **Topology:** dev-only demo, always two origins — `localhost:3000` (React) ↔ `localhost:8080` (Spring Boot). No same-origin prod serving.
- **Appfw-*-Standards dirs in this workspace:** context/reference only. PRD wins on any conflict; no conformance gate.
- **Tracker:** local markdown — this file is the map; tickets live in `./issues/`. Skills to consult while working tickets: domain-modeling (glossary in `CONTEXT.md` at workspace root), research, prototype, grilling conventions.
- **Build-time preference (from ticket 04):** SPA styling should invoke the `design-taste-frontend` skill — the demo aims for design-taste polish, not bare library defaults.

## Decisions so far

<!-- one line per resolved ticket: gist + link -->

- [Account lockout and IP throttling mechanism](issues/03-lockout-and-ip-throttling.md): service-level enforcement ordered IP-throttle → lock → credentials; Caffeine in-memory IP bucket; `getRemoteAddr()` only (never raw `X-Forwarded-For`); success resets account counter but not IP bucket; flags adding `last_failed_at` to `users` so the failure window is literal.
- [Security filter-chain design](issues/01-security-filter-chain-design.md): Spring Boot 4.1.x / Security 7.1.x / Session 4.x on Java 21. Controller-calls-`AuthenticationManager` login, but the controller must save the context (`securityContextRepository.saveContext`) and invoke a `SessionAuthenticationStrategy` itself (session-fixation DSL is a no-op without an auth filter). CSRF via Security 7's `csrf.spa()` — but tokens stay deferred, so a `permitAll` `GET /api/auth/csrf` bootstrap endpoint is still required. Lockout gate via `UserDetails.isAccountNonLocked()`; counters via auth-event listeners + `DefaultAuthenticationEventPublisher`.
- [Session store and per-user invalidation](issues/02-session-store-and-invalidation.md): Spring Session **JDBC over H2** — in-memory repo can't enumerate a user's sessions (no `FindByIndexNameSessionRepository`), JDBC's `findByPrincipalName` + `deleteById` covers reset/disable/delete invalidation; schema auto-created on H2. Caveat for ticket 01: `SessionManagementFilter` is off by default since Security 6, so a controller-driven login must call `request.changeSessionId()` + `securityContextRepository.saveContext(...)` itself. `server.servlet.session.cookie.*` (incl. `same-site`) still maps onto `DefaultCookieSerializer`.
- [Security-path test strategy](issues/05-security-path-test-strategy.md): MockMvc + `@SpringBootTest` on H2; CSRF enabled with real token fetch via `/api/auth/csrf`; `Clock` bean for lockout/expiry control; `.remoteAddress()` for IP-throttle tests; scope = broad suite (PRD minimum + near-full endpoint coverage).
- [Profiles, config and audit logging](issues/06-profiles-config-and-audit-logging.md): dev+prod profiles (Secure flag, H2 console, CORS origins differ); all security tunables externalized as `app.*` props; admin seed via env vars with documented dev default + prod fail-fast; audit logs as JSON via logstash-logback.
- [API contract details](issues/07-api-contract-details.md): 12 endpoints ratified (incl. `/api/auth/csrf` + `/api/auth/me`); RFC 7807 problem+json errors; canonical generic messages; password policy = length ≥ 12 only; `last_failed_at` column ratified (spec updated); Actuator health only; reset token = SecureRandom 32B + SHA-256 storage.
- [Frontend skeleton](issues/04-frontend-skeleton.md): shadcn/ui + Tailwind on Vite+TS; all six pages incl. admin panel; session detection via new `GET /api/auth/me` endpoint (feeds ticket 07); CSRF handled per ticket 01 (bootstrap call + `X-XSRF-TOKEN` header); polish bar = design-taste-frontend at build time.

## Not yet specified

None remaining — all fog resolved or folded into ticket 07's answer (token mechanics → SecureRandom + SHA-256; EmailService stub → logs the reset link; observability → Actuator health only).

## Out of scope

- JWT implementation (appendix design only), MFA/2FA, real SMTP, containers/CI/hosting, local HTTPS — per `assessment-prd.md` Out of Scope.
- Granular per-resource authorization beyond USER/ADMIN.
- Same-origin production serving of the SPA (dev-only topology chosen at charting).
- Appfw standards conformance (context only).
