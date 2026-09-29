# Context: Secured Hello World Auth App

Glossary of domain terms for the username/password auth application (React + Spring Boot).
This file is a glossary only — no implementation details.

## Terms

### Visitor
An unauthenticated user. Can register or log in. Has no session.

### User
An authenticated account holder with role `USER`. Identified for login by **Username**.

### Admin
An authenticated account holder with role `ADMIN`. Can manage other accounts
(list, enable/disable, change role, delete) but cannot perform those actions on
their own account (the **Admin Self-Action Guard**).

### Username
The **sole login identifier**. Unique per account. The only credential used to
authenticate. Email is never accepted as a login identifier.

### Email
A unique per-account address used **only** for password reset delivery, and
visible to Admins in the user-management list. Not a login credential.

### Session
A server-side session established on successful login, carried by a secure
HttpOnly cookie (Spring Session). Ended on logout and on password reset.

### Session Invalidation (on reset)
A successful password reset revokes **all** of the user's existing sessions,
not just the current one (true multi-session revocation).

### Account Lockout
A per-account state: after N consecutive failed logins within a window, the
account is locked until `locked_until`. Correct credentials are still rejected
while locked. Distinct from **IP Throttling**.

### IP Throttling
A per-source-IP rate limit on login attempts, applied **independently** of
Account Lockout so an attacker cannot lock out a legitimate user by failing
that user's password from one IP. Scoped per application instance for this build.

### Reset Token
A single-use, short-lived credential for password reset. Only its **hash** is
stored; the plaintext is delivered via the (stubbed) email service. Consumed on
successful reset.

### Enumeration Resistance
The property that login and password-reset-request responses never reveal
whether a given username or email exists. All login failure modes (bad
credentials, locked account, throttled IP) return the **same generic error
body**; only the HTTP status may differ (e.g. 429 for throttling).

### Auth Failure Ordering
The fixed evaluation order for a login attempt: **IP Throttle → Account
Lockout → Credential Check**. Cheapest, most-protective check first; a
throttled attacker never reaches credential evaluation.

### Reset Token (detail)
A 256-bit (32-byte) cryptographically-random, Base64URL-encoded value, stored
only as its SHA-256 hash (high entropy → fast hash, unlike passwords which use
BCrypt). Expires **30 minutes** after issue. Single-use. Issuing a new token
**invalidates any existing unexpired token** for that user — at most one live
Reset Token per user.

### Admin Self-Action Guard (detail)
Defined by **authenticated principal id == target account id** (id-based, not
username, which may change). Enforces only the literal self-action prohibition
(no self disable/demote/delete). The "at least one Admin must remain" invariant
is **out of scope** (not stated in the PRD); the seeded bootstrap Admin
mitigates total lockout.

### Password Strength Policy
A password must be **at least 12 characters** with a bounded maximum (128) and
**no composition rules** (no forced mix of character classes). No breached-
password check (out of scope).

### Audit Event
A structured log line (not a DB table) emitted for security-relevant actions:
login success/failure, lockout triggered, reset requested/completed, and
role-change/enable/disable/delete. Fields: `event`, `actor` (principal or
"anonymous"), `target` (affected account, where applicable), `outcome`,
`timestamp`, `correlationId`. Never contains passwords, token plaintext, or
password hashes.
