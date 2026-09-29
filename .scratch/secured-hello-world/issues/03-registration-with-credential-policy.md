# 03: Registration with the credential policy

**What to build:** A Visitor registers on the register screen with a username, email and password. If anything is wrong, they are told exactly which rule they broke. If it succeeds, they land on the login page, and a User-role Account exists with a properly hashed password. The Credential policy module built here is reused later by Password Change, reset and the Bootstrap Admin. See the spec's stories 1–9, 12–13 (the rate limit is ticket 07), 88–89 and 113, "Credential policy", "API contract" and "Schema". Use the `CONTEXT.md` terms (Account, Visitor).

**Blocked by:** 02

**Status:** ready-for-agent

- [ ] A Flyway migration creates `users` (UUID id, lowercase unique username and email, password hash, role USER/ADMIN, enabled, `password_change_required` default false, failed-login counter, nullable `locked_until`, created-at) and `password_history`, written to run on H2, Postgres and MySQL.
- [ ] `POST /api/register` (JSON `{username, email, password}`) returns 201 and creates an enabled Account with role USER. Any `role` in the body is ignored.
- [ ] Usernames and emails are lowercased before storage and comparison; "Alice" and "alice" clash.
- [ ] Input checks run before any business logic: username 3–32 `[A-Za-z0-9]` and valid email of at most 254 characters. Violations return 400 `validation`. The password caps (at most 64 characters and 72 UTF-8 bytes) are Credential policy rules, also checked before any business logic, and return 400 `password_policy` with `max_length` / `max_bytes` violations (spec story 2, Testing Decisions, Further Notes).
- [ ] The Credential policy rejects passwords shorter than 12 characters or missing an uppercase letter, lowercase letter, digit, or special character (any printable non-letter/digit, whitespace included), and passwords in the bundled common-password list (case-insensitive). It returns 400 `password_policy` with a `violations` list naming every broken rule.
- [ ] Passwords are hashed with BCrypt at cost 12 and recorded as the first Password History entry.
- [ ] A clash on username or email returns 400 `user_exist` with `detail` "user exist", without saying which field clashed.
- [ ] Queries use derived queries or bound parameters only.
- [ ] Registration emits a `user-provisioning` audit event (INFO) keyed by the new Account's UUID. Validation failures emit a WARN `access-control` event naming only the failing fields.
- [ ] The register screen shows each violation, has a "Confidential" label next to every input field, and goes to the login page on success.
- [ ] Tests cover each password rule and the listed violations, the 64-character and 72-byte caps, a common password, the username and email format and length rules, `user_exist`, case-insensitive clashes, the ignored `role`, and the audit events. Test data is synthetic.
