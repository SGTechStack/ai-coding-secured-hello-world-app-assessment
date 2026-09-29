# Secured Hello World Auth

A reference application demonstrating a secure username/password login flow: a React SPA over a Spring Boot REST API, using server-side sessions. This glossary pins the domain language shared across the PRD, spec, and tickets.

## Language

### Actors

**Visitor**:
An unauthenticated person. Can register or log in, nothing more.
_Avoid_: guest, anonymous user

**User**:
An authenticated account holder with the `USER` role. The default identity every registration produces.
_Avoid_: member, customer, end user

**Admin**:
An authenticated account holder with the `ADMIN` role, permitted to manage other accounts.
_Avoid_: administrator, superuser, operator (operator refers to the human deploying the app, not a stored role)

**Account**:
The stored record for a single person — the `users` row carrying identity, credentials, role, and status. One Account is identified by exactly one **username** and one **email**.
_Avoid_: profile, login, user record

### Authentication

**Session**:
The server-side authenticated state established at login, referenced by a secure `HttpOnly` cookie. Ending a Session is what "logout" means.
_Avoid_: token, JWT (the primary mechanism is deliberately not a token), auth state

**Credentials**:
A username paired with a password, submitted to prove identity.
_Avoid_: login details, secrets

**Enumeration Resistance**:
The property that a response never reveals whether a given username or email exists. Login failures and reset requests return identical generic responses regardless of account existence.
_Avoid_: anti-enumeration, user hiding

### Abuse controls

**Account Lockout**:
A per-Account block: after a threshold of consecutive failed logins, the Account is locked until a cooldown expires (`locked_until`). Correct credentials are still rejected while locked.
_Avoid_: ban, suspension (suspension is an Admin disabling an Account), account freeze

**IP Throttling**:
A per-source-address block that limits failed-login volume from one IP across many usernames, independent of any single Account's lockout. Prevents an attacker from locking out a victim by failing that victim's password from one source.
_Avoid_: rate limiting (too generic), IP ban

**Disabled Account**:
An Account whose `enabled` flag an Admin has turned off. A Disabled Account cannot log in, but its data is retained. Distinct from a locked Account (lockout is automatic and time-limited; disabling is a deliberate Admin action).
_Avoid_: deactivated, suspended, blocked

### Password reset

**Reset Token**:
A single-use, short-lived secret issued when a User requests a password reset. Only its hash is stored; the plaintext travels once via the (stubbed) email. Consuming it sets a new password and invalidates all of that User's Sessions.
_Avoid_: reset code, reset link, OTP

### Observability

**Audit Event**:
A structured log line recording a security-relevant action (login outcome, lockout, throttle, reset lifecycle, admin mutation), including actor and target where applicable. Never contains passwords, tokens, or hashes.
_Avoid_: audit trail, event log, security log
