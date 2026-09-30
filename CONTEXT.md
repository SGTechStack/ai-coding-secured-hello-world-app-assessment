# Hello World Auth App

A reference implementation of cookie-session authentication: a React frontend and a Spring Boot
REST backend on separate origins, built to a production security baseline rather than a demo one.
Source of truth is `docs/prd/assessment-prd.md`.

## Language

**Account**:
The stored record of someone who can authenticate — username, email, BCrypt password hash, role,
`enabled` flag, and lockout state. The thing an admin manages. Deleting an account removes the
record; disabling it leaves the record and refuses logins.
_Avoid_: profile, member, credentials (an account has credentials, it is not credentials)

**Visitor**:
Someone with no session. Can register or log in, and nothing else. Not a role stored on an
account — it is the absence of authentication.
_Avoid_: guest, anonymous user

**User**:
Someone authenticated against an account whose role is `USER`. Where the distinction from `Admin`
doesn't matter, "user" also reads naturally as "whoever is logged in"; when the role is what
matters, say `USER` in caps to mean the role specifically.
_Avoid_: customer, principal (outside Spring Security code)

**Admin**:
Someone authenticated against an account whose role is `ADMIN`. Can list, enable, disable,
re-role and delete other accounts — never their own. The first one is seeded at startup from
configuration.
_Avoid_: superuser, root, moderator

**Role**:
The single authority on an account, either `USER` or `ADMIN`. Checked server-side by Spring
Security and never inferred from anything the client sends.
_Avoid_: permission, group, scope — there is exactly one role per account and no finer grain

**Session**:
Server-side evidence that someone authenticated, referenced by an opaque `HttpOnly` cookie the
browser sends with each request. Ends on logout, on password reset, or on idle timeout. A cookie
whose session has ended is not a session; replaying it gets a 401.
_Avoid_: login (the act, not the state), token, JWT (documented as an alternative, not built)

**Lockout**:
Per-account state that refuses logins after repeated consecutive failures, recorded as
`failed_login_attempts` and `locked_until`. Time-limited and self-lifting. Distinct from
throttling: lockout protects one account and is keyed to it.
_Avoid_: ban, suspension (that's an admin disabling an account, which is deliberate and open-ended)

**Throttling**:
Per-IP rejection of excessive authentication traffic, counted across all accounts. Deliberately
independent of lockout, so failing one victim's password from one address cannot lock that
victim out.
_Avoid_: rate limit (fine in prose, but "throttling" is the term in code and logs)

**Reset token**:
A single-use, short-lived secret sent to an account's registered email to authorize a password
change. Stored only as a hash; the plaintext exists in the email and nowhere else. Redeeming it
changes the password, marks the token used, and ends every session for that account.
_Avoid_: reset link (that's the delivery vehicle), OTP, code

**Enumeration resistance**:
The property that a response never reveals whether a username or email is registered. Login
returns one generic error for both "no such account" and "wrong password"; reset-request returns
the same success either way. About response content, not response timing.
_Avoid_: obfuscation, security through obscurity — this is a specific, testable property
