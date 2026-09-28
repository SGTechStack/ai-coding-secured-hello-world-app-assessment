# 03: Registration (Story 1)

**What to build:** A visitor can register from the React app with a username, email, and password, and get a usable account. The backend endpoint creates an account with role USER, `enabled = true`, and the password stored as a BCrypt hash — only when the username and email are both unique and the password meets the strength policy (length ≥ 12). Duplicate username/email is rejected with a clear validation error and no account is created; a weak password is rejected with a validation error and no account is created. The plaintext password is never logged or stored. A React registration form drives it end to end.

**Blocked by:** 02.

**Status:** done

- [x] `POST` register endpoint creates a USER, enabled, BCrypt-hashed account on valid unique input
- [x] Duplicate username or email → validation error, no account created
- [x] Password shorter than 12 chars → validation error, no account created
- [x] Plaintext password never logged or persisted (verified)
- [x] CSRF token required and enforced on the endpoint
- [x] Audit log line on registration (actor/username, no password)
- [x] React register form submits, shows validation errors, and lands a created account
- [x] Integration tests: happy path, duplicate username, duplicate email, weak password
