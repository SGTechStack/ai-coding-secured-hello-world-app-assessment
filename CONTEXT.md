# Hello Desk

A username and password login app: a React page on its own origin, a Spring Boot API on its own origin, and a server-side session. This file is the shared vocabulary for the PRD, the spec, and reviews.

## Language

### Actors

**Visitor**:
Someone with no session. They can register or log in.
_Avoid_: guest, anonymous user

**User**:
An account holder signed in with the `USER` role. Registration always starts here.
_Avoid_: member, customer, end user

**Admin**:
An account holder signed in with the `ADMIN` role. They can list and change other accounts, and cannot change their own.
_Avoid_: administrator, superuser, operator (an operator is the person deploying the app, not a role stored in `users`)

**Account**:
One `users` row: username, email, password hash, role, enabled flag, lockout fields, and created time. One account has one username and one email.
_Avoid_: profile, login, user record

### Authentication

**Session**:
The server-side login record stored by Spring Session JDBC and named by the `HELLOSESSION` cookie. The cookie is `HttpOnly`. Logout deletes that session. A password reset, a disable, or a role change deletes every session for that account.
_Avoid_: token, JWT, auth state

**Credentials**:
The username and password sent to log in.
_Avoid_: login details, secrets

**Enumeration resistance**:
Login and password-reset request answers stay the same whether or not the username or email is registered. A locked or disabled account uses that same login message.
_Avoid_: anti-enumeration, user hiding

### Abuse controls

**Account lockout**:
After five failed passwords in a row, `locked_until` is set for 15 minutes. The right password is still rejected until that time passes. The next success sets `failed_login_attempts` back to zero.
_Avoid_: ban, suspension (that word is for an admin turning `enabled` off), account freeze

**IP throttling**:
A count of failed logins from one address, across every username, held in memory for this process. After 20 failures in 15 minutes the next attempt is 429, even if no single account is locked.
_Avoid_: rate limit (too broad), IP ban

**Disabled account**:
An account an admin has set `enabled` to false. It cannot log in, and its row stays. Lockout is automatic and expires. Disabling is an admin choice.
_Avoid_: deactivated, suspended, blocked

### Password reset

**Reset token**:
A single-use random value created when a registered email asks for a reset. The database stores the SHA-256 digest and a 20-minute expiry. The raw value appears once, inside the link the email stub logs. Using it sets a new password and ends that account's sessions.
_Avoid_: reset code, OTP. The link is only how the token is carried.

### Observability

**Audit event**:
An info log line for login success or failure, lockout, throttling, a reset request or completion, or an admin change. Admin lines include the actor and the target. Passwords and password hashes are never written. The email stub line includes the reset link, because that log is the stand-in for mail.
_Avoid_: audit trail, security log
