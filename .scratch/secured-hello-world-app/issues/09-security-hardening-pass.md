# 09: Cross-cutting security hardening pass

**What to build:** A verification/completion pass across every endpoint
built so far, confirming CORS, CSRF, cookie attributes, and audit logging
are consistently and correctly applied — closing any gaps left by building
each story slice independently.

**Blocked by:** 02 (Registration), 03 (Login + session), 04 (Lockout + IP
throttling), 05 (Logout + protected hello), 06 (Password reset), 07 (Admin
user management)

**Status:** ready-for-agent

- [ ] CORS: explicit allow-list of the frontend origin only, with
      `Access-Control-Allow-Credentials: true`; verified against every
      `/api/**` endpoint
- [ ] CSRF: enabled and enforced for every state-changing endpoint
      (register, login, logout, password reset request/confirm, all
      `/api/admin/**` mutations); a CSRF token retrieval mechanism exists
      for the frontend to consume (e.g. `CookieCsrfTokenRepository`)
- [ ] Session cookie attributes verified consistently: `HttpOnly` always;
      `Secure` gated to non-dev profile; `SameSite` attribute set
- [ ] Audit logging verified present and consistent across: login
      success/failure, lockout triggered, password reset
      requested/completed, and role change/enable/disable/delete (actor +
      target) — confirm no endpoint was missed and no log line contains a
      password or plaintext reset token
- [ ] Integration or targeted tests filling any gap found during this pass
      (e.g. a CORS preflight test, a CSRF-rejection test for a mutating
      endpoint called without a token)
