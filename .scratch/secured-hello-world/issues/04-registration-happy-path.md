# 04: Registration happy path

**What to build:** A visitor fills in a username, email and password on a registration form and
gets an account. This is the first slice that creates a real user, so it brings the `users` table
with it. The rejection paths are deliberately **not** here — they are ticket 05, so this slice
stays a narrow complete path.

Covers PRD Story 1, happy path only.

**Blocked by:** 02, 03.

**Status:** ready-for-agent

**IM8 controls:** `as-6` Password Salting and Hashing; `lm-4` Audit Logging; `lm-19` Log
Sanitisation. *ASVS: V2.1 Password Security, V6 Stored Cryptography, V8 Data Protection.*

`as-1` Input Validation and `as-5` Password Requirements are **consumed** here and **delivered by
ticket 05** — do not define the validation constraints or the password strength policy in this
ticket. Ticket 05 requires the policy to live in exactly one reusable validator that registration
and password reset both call; a second copy written here is the defect that requirement exists to
prevent.

- [ ] The `users` table exists with the fields the PRD's data model specifies: identifier,
      unique username, unique email, password hash, role enum of `USER`/`ADMIN`, enabled flag,
      failed login attempt count, nullable lock expiry, creation timestamp
- [ ] Uniqueness of username and email is enforced by a **database constraint**, not only by
      application-level checking — concurrent registrations must not both win
- [ ] A visitor submitting a unique username, unique email and a policy-compliant password gets
      an account created with role `USER` and enabled set true
- [ ] The password is stored as a BCrypt hash via the framework's BCrypt encoder; no custom or
      hand-rolled hashing, and the plaintext is never persisted
- [ ] The plaintext password never reaches a log line, an exception message, or an audit event
- [ ] The registration response never echoes the password or the hash back to the client
- [ ] A registration form in the frontend collects the three fields and reports success
- [ ] Test: a successful registration persists a `USER` account whose stored hash verifies
      against the submitted password and is not equal to it
- [ ] Test: no log output produced during a registration contains the submitted password
