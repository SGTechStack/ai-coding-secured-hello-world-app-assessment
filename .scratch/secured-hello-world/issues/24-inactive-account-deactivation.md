# 24: Inactive account deactivation

**What to build:** Accounts that have not been used for 90 days are disabled automatically, and
the data needed to determine that is recorded in the first place.

IM8 `ac-3` requires deactivation after 90 days of inactivity. The PRD has no such requirement and
does not exclude it, and the `users` table has no column it could be computed from — `created_at`
records when the account was made, not when it was last used, so an account registered two years
ago and used yesterday is indistinguishable from its opposite.

The deactivation reuses the existing `enabled` flag rather than introducing a second state.
A disabled account already cannot log in (ticket 19), so the enforcement path is built; what is
missing is the trigger. Keeping one flag also means an admin can re-enable an account that aged
out, using the machinery that already exists, with the re-enable already audited.

Covers IM8 `ac-3`, and supplies the `last_login_at` field that makes `ac-4` access review
meaningful.

**Blocked by:** 06, 19.

**Status:** ready-for-agent

**IM8 controls:** `ac-3` Inactive and Expired Accounts (primary); `ac-4` Access Review;
`lm-4` Audit Logging. *ASVS: V2.3 Authenticator Lifecycle, V4 Access Control, V7 Logging.*

- [ ] A `last_login_at` timestamp column is added to `users`, nullable for accounts that have
      registered but never logged in
- [ ] It is written on every **successful** authentication, and not on a failed one — a failed
      attempt is not authorised use and must not keep a dormant account alive
- [ ] It is written for the bootstrap admin too, so a seeded admin that is never used ages out
- [ ] A scheduled task disables any `enabled` account whose `last_login_at` is more than 90 days
      past, and for accounts that have never logged in, measures from `created_at` instead
- [ ] The threshold is configurable, defaulting to 90 days, so the value is reviewable rather than
      buried in a literal
- [ ] The task runs **at least daily**. The control's other half requires access be removed within
      5 days of last authorised use, so a job scheduled weekly or monthly cannot satisfy it no
      matter how the 90-day threshold is set — the cadence is part of the control, not a detail
- [ ] The "within 5 days of last authorised use" limb of `ac-3` is recorded as **N/A with
      rationale**: it presupposes an external signal that authorisation ended — a leaver feed from
      an HR system or IdP — and there is no IdP in scope (see the `ac-8` waiver). Inactivity is the
      only signal this application has. Recorded in the dossier (ticket 27) rather than left silent.
- [ ] The task is **idempotent** — running it twice disables nothing extra and emits no duplicate
      audit events for an account already disabled
- [ ] Each deactivation emits an audit event naming the account, the reason (`INACTIVITY`) and the
      `last_login_at` it acted on, with the actor recorded as the system rather than a user
- [ ] Deactivating the last remaining enabled `ADMIN` is refused, and the refusal is logged as a
      warning — ageing out the only route into the admin module would lock the system open to
      nobody and require a database edit to recover, which is exactly what ticket 16 exists to
      avoid
- [ ] `last_login_at` is added to the ticket 18 admin user list response, since an access review
      cannot judge whether a privilege is still needed without knowing whether the account is live
- [ ] `last_login_at` is not exposed on any non-admin endpoint
- [ ] The Roles table in the spec is the declared per-account permission baseline for `ac-4`; the
      admin list plus this column is the review surface. Recorded in the dossier (ticket 27).
- [ ] Test: a successful login updates `last_login_at`; a failed login does not
- [ ] Test: an account last used 91 days ago is disabled by the task and can no longer log in
- [ ] Test: an account last used 89 days ago is untouched
- [ ] Test: an account that never logged in is judged on `created_at`
- [ ] Test: running the task twice produces one audit event per affected account
- [ ] Test: the sole enabled admin is not disabled by inactivity
- [ ] Test: an admin can re-enable an account disabled by inactivity, and that account can then
      log in
