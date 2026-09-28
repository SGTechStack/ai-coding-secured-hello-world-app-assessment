# 03: User login & session establishment

**What to build:** A registered user logs in through a React form; on correct credentials a server-side session is created and a secure session cookie is set. Wrong credentials return a generic error that does not reveal whether the username exists.

**Blocked by:** 02 (accounts must exist to log in).

**Status:** ready-for-agent

- [ ] Correct credentials on an enabled, non-locked account create a server-side session, set the secure session cookie, and reset `failed_login_attempts` to 0.
- [ ] Session-fixation protection: session id is regenerated on successful authentication.
- [ ] Incorrect credentials return a single generic error that does not disclose whether the username exists, and increment `failed_login_attempts`. (IM8 as-13, enumeration resistance)
- [ ] A currently-locked account (`locked_until` in the future) is rejected even with correct credentials until the lockout expires.
- [ ] Login success and failure are audit-logged (structured, actor + outcome, no password). (IM8 lm-4, lm-15, lm-19)
- [ ] Integration tests: login success; wrong password; unknown username (identical generic error); account locked. (Testing Requirements, Story 2)
