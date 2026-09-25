# feat: Secure Auth — full PRD with OWASP Top 10 hardening

**Status:** Ready for review

This PR implements the Secure Auth spec: registration, lockout and per-IP throttling, password reset, the admin module and bootstrap, audit logging, the cross-origin topology and a strict SPA CSP, with hardened API errors throughout.

Closes `.scratch/secure-auth-prd/spec.md`
Closes `.scratch/secure-auth-prd/issues/01-hardened-api-errors-clock-and-csrf-body.md`
Closes `.scratch/secure-auth-prd/issues/02-audit-log-for-login-and-logout.md`
Closes `.scratch/secure-auth-prd/issues/03-cross-origin-topology.md`
Closes `.scratch/secure-auth-prd/issues/04-strict-spa-csp-and-e2e-on-production-build.md`
Closes `.scratch/secure-auth-prd/issues/05-account-schema-roles-me-role-and-hello.md`
Closes `.scratch/secure-auth-prd/issues/06-per-ip-login-throttling.md`
Closes `.scratch/secure-auth-prd/issues/07-registration-end-to-end.md`
Closes `.scratch/secure-auth-prd/issues/08-account-lockout.md`
Closes `.scratch/secure-auth-prd/issues/09-password-reset-end-to-end.md`
Closes `.scratch/secure-auth-prd/issues/10-admin-bootstrap.md`
Closes `.scratch/secure-auth-prd/issues/11-admin-user-list-end-to-end.md`
Closes `.scratch/secure-auth-prd/issues/12-admin-account-actions-end-to-end.md`

## Progress

- [x] 01 Hardened API errors, Clock seam and CSRF token in the body
- [x] 02 Audit log for login and logout
- [x] 03 Cross-origin topology
- [x] 04 Strict SPA CSP, with e2e on the production build
- [x] 05 Account schema, roles, `/me` role and `/hello`
- [x] 06 Per-IP throttling on login
- [x] 07 Registration, end to end
- [x] 08 Account lockout
- [x] 09 Password reset, end to end
- [x] 10 Admin bootstrap
- [x] 11 Admin user list, end to end
- [x] 12 Admin account actions, end to end
- [x] Code review fixes

## What's in it

**Backend** (`backend/`: Spring Boot 3):
- Hardened errors: every MVC and `/error` response is a JSON `ApiError` with a generic code; a 16 KB body cap (`413`); `GET /api/v1/auth/csrf` returns the token in the body; an injectable `Clock` seam.
- Structured `AUDIT` log (ECS JSON outside `dev`) with a fixed `AuditEvent` vocabulary, control characters neutralised. Controllers build an `Actor(username, ip)`, so services never take the request.
- CORS for an exact-origin allow-list, per-IP fixed-window throttles (login, registration, reset request; `429` + `Retry-After`) and account lockout (5 failures / 15 min). The password is always checked before the lock and enabled flags, so every refusal is the same `401`.
- Schema V2 (email, role, lockout, reset tokens; lowercase usernames), `/me` returns `role`, and there is a new `/hello`. Registration applies a password policy (12–64 chars, ≤72 bytes, not on the top-10k list).
- Password reset uses hashed single-use tokens (30 min). It clears the lockout, and all sessions end once the change commits.
- The admin module has a bootstrap `ApplicationRunner`, `GET /api/v1/admin/users` (an allow-listed view), and `PATCH` status/role and `DELETE`. Authorisation is checked twice: the URL rule plus `@PreAuthorize` on the service. Self-actions are refused (`409`), and after commit the target's sessions end and the change is audited.
- Review fixes: reset-request timing parity, `LoginService` pulled out of `AuthController`, `SessionExpiry.expireAllSessionsOnCommit` shared by reset and admin actions, and `AuditLog.withTarget`/`AdminAction` added to tidy up the code.

**Frontend** (`frontend/`: React 19):
- Registration, forgot/reset password (the token is read from the URL hash and then removed) and login notices. The SPA reads the CSRF token from the response body and talks to the API cross-origin through `VITE_API_BASE_URL`.
- An admin Users page (a semantic table and a navbar link for admins only) with per-row Disable/Enable, Make admin/Make user and Delete. Delete asks for confirmation first, and the admin's own row is disabled.
- Review fixes: any `401` while signed in clears the cache and sends the user to `/login`, admin actions retry once on a stale CSRF token, focus returns to the heading after a delete, and banner messages, the password rule and error codes are shared.

**E2E** (`frontend/e2e/`):
- Stories 4–7 (registration, lockout, password reset, admin), run against the production build with the strict CSP. A fixture fails any test that raises a CSP or Trusted Types violation. Reset links are read from the backend log file.

**Notable decisions:**
- The admin delete confirmation uses the native `<dialog>` (`showModal()`), not Radix `AlertDialog`, whose scroll lock injects a runtime `<style>`. That keeps the SPA CSP strict (`style-src 'self'`, Trusted Types) with nothing relaxed.
- The reset link is issued and emailed off the request thread (the application `TaskExecutor`), so known and unknown emails take the same time to answer. We chose this over padding to a fixed delay.
- New required config: `app.cors.allowed-origins`, `app.admin.{username,email,password}` (only needed until an admin exists) and `app.frontend-url`. `dev` and `test` set defaults; `prod` fails at startup without them (`APP_CORS_ALLOWED_ORIGINS`, `APP_ADMIN_*`, `APP_FRONTEND_URL`).

**Checks:** `mvn verify` (222 tests, JaCoCo met), `npm run check` (148 tests), Playwright 38/38 with `CI=1`.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
