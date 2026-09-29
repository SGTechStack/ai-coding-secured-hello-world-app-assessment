# 06: Login and session establishment

**What to build:** A registered user submits correct credentials and gets an authenticated
session — a server-side session created, a secure session cookie set on their browser, and the
frontend aware that it now has a logged-in user. Only the success path is here; failures are
ticket 08 and the thing you see once logged in is ticket 07.

Covers PRD Story 2, success path.

**Blocked by:** 04.

**Status:** ready-for-agent

**IM8 controls:** `as-11` Session Management; `as-6` Password Salting and Hashing; `lm-4` Audit
Logging; `as-7` Access Control Check Enforcement. *ASVS: V2.2 General Authenticator, V3.2
Session Binding, V3.4 Cookie-based Session Management, V3.7 Defenses Against Session Management
Exploits, V7 Logging.*

- [ ] A registered, enabled, non-locked account submitting the correct password gets a
      server-side session created and a session cookie set
- [ ] Credential verification goes through the BCrypt encoder — the submitted password is
      never compared as plaintext against anything
- [ ] `failed_login_attempts` resets to zero on successful authentication
- [ ] Session-fixation protection is active: the session identifier issued after authentication
      is **not** the one the client presented before it
- [ ] The session cookie carries the attributes established in ticket 01 and is not readable
      from JavaScript
- [ ] A disabled account cannot authenticate even with the correct password
- [ ] A login success audit event is emitted through the ticket 03 seam, recording the principal
      and outcome, and containing no credential material
- [ ] The frontend has a login form and an auth context that knows whether a user is
      authenticated; authentication state is **derived from the server**, never from a
      client-held flag that client code could set
- [ ] Test: successful login sets a session cookie and rotates the session identifier
- [ ] Test: successful login zeroes a previously non-zero failed attempt count
- [ ] Test: a disabled account is refused despite correct credentials
