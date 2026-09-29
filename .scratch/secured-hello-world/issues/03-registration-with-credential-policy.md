# 03: Registration with the credential policy

**What to build:** A Visitor registers on the register screen with a username, email and password. If anything is wrong, they are told exactly which rule they broke. If it succeeds, they land on the login page, and a User-role Account exists with a properly hashed password. The Credential policy module built here is reused later by Password Change, reset and the Bootstrap Admin. See the spec's stories 1–9, 12–13 (the rate limit is ticket 07), 88–89 and 113, "Credential policy", "API contract" and "Schema". Use the `CONTEXT.md` terms (Account, Visitor).

**Blocked by:** 02

**Status:** resolved

- [x] A Flyway migration creates `users` (UUID id, lowercase unique username and email, password hash, role USER/ADMIN, enabled, `password_change_required` default false, failed-login counter, nullable `locked_until`, created-at) and `password_history`, written to run on H2, Postgres and MySQL.
- [x] `POST /api/register` (JSON `{username, email, password}`) returns 201 and creates an enabled Account with role USER. Any `role` in the body is ignored.
- [x] Usernames and emails are lowercased before storage and comparison; "Alice" and "alice" clash.
- [x] Input checks run before any business logic: username 3–32 `[A-Za-z0-9]` and valid email of at most 254 characters. Violations return 400 `validation`. The password caps (at most 64 characters and 72 UTF-8 bytes) are Credential policy rules, also checked before any business logic, and return 400 `password_policy` with `max_length` / `max_bytes` violations (spec story 2, Testing Decisions, Further Notes).
- [x] The Credential policy rejects passwords shorter than 12 characters or missing an uppercase letter, lowercase letter, digit, or special character (any printable non-letter/digit, whitespace included), and passwords in the bundled common-password list (case-insensitive). It returns 400 `password_policy` with a `violations` list naming every broken rule.
- [x] Passwords are hashed with BCrypt at cost 12 and recorded as the first Password History entry.
- [x] A clash on username or email returns 400 `user_exist` with `detail` "user exist", without saying which field clashed.
- [x] Queries use derived queries or bound parameters only.
- [x] Registration emits a `user-provisioning` audit event (INFO) keyed by the new Account's UUID. Validation failures emit a WARN `access-control` event naming only the failing fields.
- [x] The register screen shows each violation, has a "Confidential" label next to every input field, and goes to the login page on success.
- [x] Tests cover each password rule and the listed violations, the 64-character and 72-byte caps, a common password, the username and email format and length rules, `user_exist`, case-insensitive clashes, the ignored `role`, and the audit events. Test data is synthetic.

## Comments

### Verification (2026-09-29)

**Implemented:** `POST /api/register` creates an enabled USER Account (any `role` ignored) with a BCrypt cost-12 hash and a first Password History entry. Usernames and emails are lowercased before storage and comparison. Username/email format errors return 400 `validation` with `fields`. A reusable `CredentialPolicy` returns 400 `password_policy` with `violations` (`min_length`, `max_length`, `max_bytes`, `uppercase`, `lowercase`, `digit`, `special`, `common_password`); the common-password list is SecLists 10k (MIT) plus 18 policy-passing entries. Clashes, including a concurrent unique-constraint clash, return a generic 400 `user_exist`. Audit: INFO `user-provisioning` keyed by the new Account UUID; WARN `access-control` naming only failing fields; WARN `user-provisioning` on `user_exist` with no personal data. V2 migrations for H2, Postgres and MySQL (`DATETIME(6)` on MySQL). Register screen with per-rule errors, a `role="alert"` summary, "Confidential" labels and redirect to login.

**Deviations and decisions:** The 64-character / 72-byte caps return `password_policy`, following the spec; this criterion was reworded (see `docs/agents/reviewer-decisions.md`). Hibernate's `org.hibernate.orm.jdbc.error` and `SqlExceptionHelper` loggers are off so a unique-constraint clash cannot log the username or email; unexpected database errors are still logged once, sanitised, by the global handler. Running the Postgres and MySQL migrations on real databases moves to issue 16.

**Verification steps:** `./mvnw verify` passed: 103 tests, 0 failures, 96.8% line / 87.1% branch coverage. Frontend typecheck, lint, format check and 28 tests passed (≥97% coverage). Reviewer loop clean after one correction pass (Must-fix 0, human decisions resolved). KB retrieval and code-reviewer compliance gates were skipped by request; mutation testing was skipped.

**Checklist:** all acceptance-criteria boxes ticked.

Commits: `e197fc5 feat(registration): Add registration with credential policy`, `078e062 docs: Record issue 03 reviewer decisions`
