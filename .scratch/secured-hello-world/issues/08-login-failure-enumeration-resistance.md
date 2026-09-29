# 08: Login failure handling and enumeration resistance

**What to build:** Login now fails correctly, which is a harder requirement than it sounds. A
wrong password and a username that doesn't exist must be **indistinguishable** to the caller —
same message, same status, and ideally same timing — so an attacker cannot harvest valid
usernames from the login form. Failures also increment the attempt counter that ticket 11's
lockout depends on, and a locked account is refused even when the password is right.

Covers PRD Story 2, failure paths.

**Blocked by:** 06.

**Status:** ready-for-agent

**IM8 controls:** `as-13` Exposure of Internal System Details; `as-4` Authentication Mechanism
Rate-Limiting; `lm-4` Audit Logging. *ASVS: V2.2 General Authenticator, V7 Error Handling and
Logging, V11 Business Logic.*

- [ ] Incorrect credentials are rejected with a **generic** error that does not reveal whether
      the username exists
- [ ] The response for an unknown username and the response for a known username with a wrong
      password are identical: same status code, same body, same headers
- [ ] The two paths do not differ meaningfully in **response time** — a password hash
      verification is performed (or equivalent work done) even when the username is unknown, so
      timing does not leak account existence
- [ ] `failed_login_attempts` increments on each failed attempt against an existing account
- [ ] An account whose lock expiry is in the future is rejected **even with the correct
      password**, and the rejection does not reveal that the account is locked rather than
      wrong-passworded
- [ ] A login failure audit event is emitted recording the attempted principal and outcome, with
      no credential material; the audit log **may** distinguish failure reasons even though the
      client response must not
- [ ] The frontend renders the generic failure message without embellishing it into something
      more specific
- [ ] Test: unknown username and wrong password produce byte-identical client responses
- [ ] Test: response times for unknown-username and wrong-password are within a tolerance of
      each other
- [ ] Test: a failed attempt increments the counter
- [ ] Test: a currently locked account with the correct password is still refused
