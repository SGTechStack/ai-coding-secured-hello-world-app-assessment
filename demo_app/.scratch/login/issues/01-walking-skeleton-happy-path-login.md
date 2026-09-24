# 01: Walking skeleton — happy-path login

**What to build:** A registered user opens `/login`, enters `johndoe` / `Password123!`, clicks "Log in", and lands on `/` with a server-side session established. This ticket stands up both apps (backend + frontend) and every quality gate, so later tickets only add behaviour. See spec §Implementation Decisions for the stack, API contract, CSRF and session decisions.

**Blocked by:** None (can start immediately)

**Status:** done

**Spec scenarios:** Story 1 · Scenario 1

- [x] `backend/` Spring Boot app (Java 21, Maven) with H2 + Flyway; migration creates the user account table and seeds `johndoe` (first name `John`, BCrypt hash of `Password123!`)
- [x] `POST /api/v1/auth/login` returns `200 {"username","firstName"}` for valid credentials, stores the security context in the `HttpSession`, rotates the session id, and sets an HttpOnly `JSESSIONID` cookie
- [x] CSRF enabled via cookie token repository (`XSRF-TOKEN` → `X-XSRF-TOKEN`); a `GET` under `/api/v1/auth/**` issues the token so the SPA can obtain it before logging in
- [x] Unauthenticated `/api/**` requests get `401` JSON, never a redirect/HTML
- [x] `frontend/` Vite + React 19 + TypeScript app with TanStack Router (`/login`, `/`) and TanStack Query; dev server proxies `/api` to the backend
- [x] `/login` renders a username field, password field, and "Log in" button; a successful login navigates to `/` (placeholder landing page is fine)
- [x] API client module sends credentials + CSRF header and classifies failures (spec §Frontend)
- [x] Backend MockMvc integration test covers successful login (status, body, session cookie) and a CSRF-less POST being rejected
- [x] Frontend Vitest + RTL + MSW test covers: type credentials, click "Log in", URL becomes `/`
- [x] Gates wired and passing: `mvn verify` with JaCoCo ≥80%; `prettier --check`, `tsc --noEmit`, `vitest run --coverage` (≥80% thresholds in `vitest.config.ts`), `vite build`
- [x] Root `README.md` explains how to run both apps and the tests

## Comments

- Implementer (01): added `GET /api/v1/auth/csrf` (`204`, permitAll) as the CSRF-priming GET until `/me` exists; `CsrfCookieFilter` makes any response set `XSRF-TOKEN`. Missing/invalid CSRF returns `403 {"message":"Forbidden"}` JSON. The `JSESSIONID` HttpOnly/SameSite assertion runs over real HTTP (`SessionCookieTest`) because MockMvc has no servlet container to write that cookie. Bad credentials currently yield the entry point's generic `401 {"message":"Unauthorized"}`; ticket 03 owns the final body.
