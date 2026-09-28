# 01: Backend + frontend scaffold, CORS, base security config

**What to build:** A running two-origin skeleton a developer can boot: a Spring Boot backend on `:8080` (Spring Web, Spring Data JPA over H2 dev profile, Spring Security, Spring Session) and a React frontend on `:3000`. CORS is configured on the backend as an explicit allow-list of the frontend origin with `Access-Control-Allow-Credentials: true` so the session cookie can travel cross-origin. Base Spring Security config is in place: CSRF enabled for state-changing endpoints, session cookie attributes (`HttpOnly`, `SameSite`, `Secure` in a prod profile), and session-fixation protection — so every later slice inherits these rather than re-adding them. A trivial unauthenticated `GET /api/ping` returns 200 to prove the wiring end to end.

**Blocked by:** None (can start immediately).

**Status:** done

- [x] Backend boots on `:8080` with H2 (dev profile) and the four Spring starters wired
- [x] Frontend boots on `:3000` and can call the backend cross-origin with credentials
- [x] CORS allow-lists the frontend origin explicitly; `Access-Control-Allow-Credentials: true`
- [x] Base security config: CSRF enabled; cookie `HttpOnly` + `SameSite`; `Secure` bound to the prod profile; session-fixation protection on
- [x] `GET /api/ping` returns 200 unauthenticated; a CORS preflight from the frontend origin succeeds
- [x] Schema kept portable (no H2-only DDL) so it can move to Postgres/MySQL later
