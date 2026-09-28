# 01: Project scaffold & security baseline

**What to build:** A running React frontend (own origin) and Spring Boot backend (own origin) wired together, with the cross-cutting security baseline in place so every later slice inherits it. A visitor can load the frontend, which can reach a trivial public health endpoint on the backend cross-origin with credentials. This is the prefactor that makes the later "easy changes" easy.

**Blocked by:** None (can start immediately).

**Status:** ready-for-agent

- [ ] React app builds and serves on its own origin (e.g. localhost:3000); Spring Boot serves REST on its own origin (e.g. localhost:8080).
- [ ] Persistence configured via Spring Data JPA over H2 (dev profile), schema kept portable to Postgres/MySQL.
- [ ] Spring Session server-side sessions enabled; session cookie is `HttpOnly`, `SameSite`, and `Secure` in the prod profile.
- [ ] CORS restricted to an explicit frontend-origin allow-list with `Access-Control-Allow-Credentials: true`.
- [ ] CSRF protection enabled for all state-changing endpoints (cookie-based auth).
- [ ] Default-deny authorization: all endpoints require auth unless explicitly public. (IM8 ac-1)
- [ ] Minimally-permissive Content-Security-Policy response header set on frontend responses (`default-src 'self'`, no inline/unsafe-eval). (IM8 as-9 AUTO-FIX)
- [ ] HSTS header configured for the prod/HTTPS profile with ≥ 1 year max-age; local HTTP documented as an accepted dev-only gap. (IM8 as-10, dp-3)
- [ ] Global error handling returns generic messages with no stack traces / internal details to clients. (IM8 as-13)
- [ ] Structured (JSON/ECS) logging configured; a log filter guarantees passwords and reset tokens are never logged. (IM8 lm-15, lm-19)
- [ ] Absolute maximum session lifetime / idle timeout configured, after which re-authentication is forced. (IM8 as-11 AUTO-FIX)
