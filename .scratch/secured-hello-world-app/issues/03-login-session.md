# 03: Login + session + generic errors (Story 2)

**What to build:** A registered user can log in with username and password
to get a server-side session and secure session cookie; wrong credentials
or an unknown username both produce an identical generic error (no
enumeration); a locked account's login is rejected even with the correct
password until lockout expires.

**Blocked by:** 02 (Registration)

**Status:** ready-for-agent

- [ ] `POST /api/login` accepts username and password
- [ ] Correct credentials on an enabled, non-locked account: server-side
      session created, secure session cookie set (`HttpOnly`, `SameSite`
      attribute, `Secure` gated to non-dev profile), `failedLoginAttempts`
      reset to 0
- [ ] Incorrect credentials (wrong password OR unknown username): rejected
      with an identical generic error message in both cases;
      `failedLoginAttempts` increments for a known username
- [ ] Account with `lockedUntil` in the future: login rejected even with
      the correct password, until the lockout expires (lockout mechanics
      themselves are ticket 04 — this ticket only needs the check honored
      if `lockedUntil` is already set)
- [ ] Session-fixation protection enabled (Spring Security default session
      strategy)
- [ ] Integration tests: login success; wrong password; unknown username
      (assert identical generic error to wrong-password case); login
      attempt against an account with `lockedUntil` in the future is
      rejected
