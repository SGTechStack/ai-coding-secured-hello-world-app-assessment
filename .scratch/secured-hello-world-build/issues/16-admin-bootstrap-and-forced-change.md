# 16: Admin bootstrap and forced-change credential

**What to build:** An operator starts the app for the first time with no database edits, and the seeded admin is forced to change their password (ADR-047; ADR-046).

- **Validation** runs at refresh, and **seeding** runs in a runner. The seed happens only when no `ADMIN` row exists. It never seeds around a disabled admin, and it fails fast on a tombstoned or reserved username. Credentials come from the environment only.
- **The seed is a forced-change credential**: `force_password_change` is set and `credential_issued_at` is stamped.
- **Forced-change allowlist.** Exactly five paths: `POST /api/login`, `GET /api/csrf`, `GET /api/profile`, `POST /api/logout` and `PATCH /api/profile/password`. Anything else, including `/api/mfa/**` and `/api/hello`, gets 403 `PASSWORD_CHANGE_REQUIRED` (ADR-023; R-STD-029; REJ-055).
- **Expiry.** A forced-change credential unused for 30 days fails at login (lazy, in pre-auth checks) with the uniform 401 (T-ADM-015; T-ADM-030). Setting a user-chosen password clears `credential_issued_at` (T-ADM-031).
- **SPA:** the first gate in gate order sends a forced-change user straight to the change-password page.

**Blocked by:** 06, 14

**Status:** ready-for-agent

- [ ] In the restart harness, the first boot on an empty database seeds one admin, and the second boot seeds nothing.
- [ ] A disabled existing admin, a tombstoned seed username, or a reserved one stops startup.
- [ ] The seeded admin can sign in but gets `PASSWORD_CHANGE_REQUIRED` everywhere outside the allowlist.
- [ ] After 30 days on the `Clock`, the unused seed credential gets the uniform 401.
- [ ] Completing the change clears both flags.
