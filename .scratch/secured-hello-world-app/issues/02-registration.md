# 02: Registration (Story 1)

**What to build:** A visitor can register an account with username, email,
and password; the account is created with role `USER`, `enabled = true`,
and a BCrypt password hash — with duplicate username/email and weak
passwords rejected, and the plaintext password never logged or stored.

**Blocked by:** 01 (Backend project scaffold + data model)

**Status:** ready-for-agent

- [ ] `POST /api/register` accepts username, email, password
- [ ] Password strength policy enforced: length ≥ 12; validation error
      (not account creation) on failure
- [ ] Username/email uniqueness enforced; validation error (username/email
      conflict) on failure, no account created
- [ ] On valid submission: account created with role `USER`,
      `enabled = true`, password stored as a BCrypt hash
      (`BCryptPasswordEncoder`)
- [ ] Plaintext password never appears in logs (including error/validation
      logs) or in any persisted column
- [ ] Integration tests: successful registration; duplicate username
      rejected; duplicate email rejected; weak password rejected; assert no
      plaintext password is logged (e.g. via a log-capture assertion) and no
      account is created on any rejection path
