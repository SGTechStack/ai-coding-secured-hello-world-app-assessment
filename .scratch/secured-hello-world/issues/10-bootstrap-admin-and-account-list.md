# 10: Bootstrap Admin and the admin Account list

**What to build:** On first deployment, an Admin exists without anyone editing the database, created from configured credentials and email. They log in and see an admin screen listing every Account with its role, enabled and Locked state, and creation date. A User can't see that screen and gets 403 on every admin endpoint however the request is crafted. Viewing the list is audited, because it exposes emails. See the spec's stories 43, 63–65 and 76–81, "Bootstrap Admin initializer", "Account administration service", "Security configuration" (authorisation, secrets), and `CONTEXT.md`'s Bootstrap Admin. The required-password-change flag is set here; ticket 13 enforces it.

**Blocked by:** 04

**Status:** resolved

- [x] At startup, when no Account holds the Admin role (enabled or not), the initializer creates one from the configured username, password and email. The password goes through the Credential policy and BCrypt, and the Account is created with `password_change_required` set.
- [x] Restarting while any Admin exists creates nothing.
- [x] In `dev`, missing values fall back to `admin` / `password` / `admin@localhost`, skipping the policy, with a loud WARN. In any other profile, a missing username, password or email fails startup. Outside `dev` these values come from the secrets manager.
- [x] Startup fails with a clear message when the configured username already belongs to an Account. Blocking tombstoned usernames is added in ticket 15.
- [x] Creating the Bootstrap Admin emits a `user-provisioning` audit event.
- [x] `GET /api/admin/users` returns `{id, username, email, role, enabled, locked, createdAt}` for every Account, never password hashes or history.
- [x] `/api/admin/**` requires the ADMIN role in the filter chain, and every Account administration service method also carries `@PreAuthorize("hasRole('ADMIN')")`. A User gets 403 on every admin path, including ones naming their own Account.
- [x] Viewing the list emits an INFO `user-administration` audit event. A 403 emits a WARN `access-control` event with `user.id`, path and method.
- [x] SPA: an admin users screen reachable from hello for Admins only; admin routes are hidden from Users.
- [x] Bootstrap tests run separate application contexts per profile and property set, asserting: creation once with the configured email and the flag set; no re-creation on restart; the `dev` fallback plus its WARN; non-`dev` failure for each missing value; and failure when the username is taken.
- [x] API tests cover: the list for an Admin, its fields and the absence of hashes; a User getting 403 on the list and on every admin path; and the audit events.

## Comments

### Verification (2026-09-29)

**Implemented:** `BootstrapAdminInitializer` runs at startup and, when no Account holds the Admin role (enabled or Disabled, via `existsByRole`), creates one from `app.bootstrap-admin.username/password/email` (`BootstrapAdminProperties`, which hides its values in `toString`). The password goes through the Credential policy and BCrypt, a Password History row is saved, and `password_change_required` is set. Creation is audited as INFO `user-provisioning` with `event.reason: bootstrap_admin` and the new `user.id`. In `dev`, each missing value falls back on its own to `admin` / `password` / `admin@localhost`; the policy is skipped only for the fallback password, and a WARN is logged when the Account is created. In any other profile, a missing or blank value fails startup with "`app.bootstrap-admin.<x>` must be injected from the secrets manager", and a policy-violating password fails startup naming only the broken rules. A taken username or email fails startup with a clear message. `GET /api/admin/users` (`AccountAdministrationController` / `AccountAdministrationService`) returns `AdminAccountView` `{id, username, email, role, enabled, locked, createdAt}`, with `locked` derived from the clock. `/api/admin/**` requires ADMIN in the filter chain for every method, `@EnableMethodSecurity` is on, and the service carries `@PreAuthorize("hasRole('ADMIN')")`. Viewing the list emits INFO `user-administration` (`event.type: ["access"]`). Every non-CSRF 403 emits WARN `access-control` with reason `access_denied`, `user.id`, path and method. The SPA shows a "Manage Accounts" link on hello for Admins only, and `/admin/users` sits behind an `AdminOnly` guard that sends a User back to hello without calling the list endpoint.

**Deviations and decisions:** Outside `dev`, all three admin values are required on every startup, even when an Admin already exists (story 80 read literally). The configured username must match the registration format (`[A-Za-z0-9]{3,32}`). Admin API tests promote a registered Account to Admin directly in the database instead of running the initializer, so ticket 13's required password change won't block them. Blocking tombstoned usernames is left to ticket 15, and enforcing `password_change_required` to ticket 13.

**Known gaps (reviewer notes, non-blocking):** (1) The bootstrap email gets no format or length check. (2) Two instances starting together on an empty database could race; the loser fails on the unique constraint and a restart succeeds. (3) Every non-CSRF 403 is now logged at WARN, including `denyAll` paths, which may add log noise. (4) The admin screen's "Loading…" text has no `aria-live`.

**Verification steps:** `./mvnw verify` passed: 223 tests (including `BootstrapAdminTest`, `AdminAccountListApiTest` and `AccountAdministrationServiceSecurityTest`). Frontend: vitest 86 passing; `tsc`, oxlint and prettier clean. The reviewer loop passed on the first round with Must-fix 0 and Human decisions 0. KB retrieval and the code-reviewer compliance gates were skipped by request; mutation testing was skipped.

**Checklist:** all acceptance-criteria boxes ticked.

Commit: `6516e8a feat(admin): Bootstrap Admin and Admin Account list`
