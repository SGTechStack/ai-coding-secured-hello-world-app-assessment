# 09: Password reset, end to end

**What to build:** A user who forgot their password can request a reset link by email, and always sees the same "if an account exists…" message. Following the link lets them set a new password. Doing so signs them out everywhere, clears any lockout, and lands them on the login page with "Password updated. Please log in." This ticket also introduces the "expire all sessions for a user" operation that the admin actions (12) reuse. See spec §Backend modules › Password reset and Session registry, §Frontend modules, and Acceptance scenarios › Story 6.

**Blocked by:** 06, 07

**Status:** ready-for-agent

- [ ] Spring Security's in-memory `SessionRegistry` and the HTTP session event publisher are registered through `sessionManagement` (unlimited concurrent sessions). A single "expire all sessions for user X" operation exists. An expired session's next request gets the JSON `401`, never a redirect.
- [ ] `POST /api/v1/auth/password-reset/request` with `{email}` always answers `202` with an empty body, for known, unknown, disabled and malformed emails alike. It is throttled at 10 per IP per 15 minutes.
- [ ] For an enabled account only: earlier unused tokens are invalidated, a new 256-bit `SecureRandom` token is created and stored as a SHA-256 hash with a 30-minute TTL (`app.security.password-reset.token-ttl`), and `EmailService.sendPasswordResetEmail` receives `<app.frontend-url>/reset-password#token=<token>`. The stub logs the link on its own non-audit logger, which is the only place a token is ever logged.
- [ ] `POST /api/v1/auth/password-reset/confirm` with `{token, newPassword}` and a valid token: the new BCrypt hash is set, the token is marked used, the lockout is cleared, every session of the user is expired, and it answers `204`. The new password works and the old one fails.
- [ ] A reused, expired (by the clock), superseded or unknown token → the same `400 INVALID_RESET_TOKEN` "This reset link is invalid or has expired.", with the password unchanged. A policy failure → `400 VALIDATION_FAILED` and doesn't consume the token.
- [ ] Audit events `PASSWORD_RESET_REQUESTED` (target `unknown` for unknown emails; the email itself is never logged), `PASSWORD_RESET_COMPLETED` and `PASSWORD_RESET_REJECTED`.
- [ ] The login page has a "Forgot password?" link. The `/forgot-password` and `/reset-password` routes are anonymous-only.
- [ ] The forgot-password page shows the generic message for any response.
- [ ] The reset page reads the token from `location.hash` on mount, removes the hash with `history.replaceState`, and keeps the token in component state only. An invalid token shows the message with a link to `/forgot-password`. Success navigates to `/login` with the notice. The in-memory CSRF token is dropped after a successful confirm.
- [ ] e2e Story 6, scenarios 1–4, passes under the CSP fixture, using freshly registered users. The ticket's Comments record how the e2e reads the reset link (backend log capture or a dev-only hook).
