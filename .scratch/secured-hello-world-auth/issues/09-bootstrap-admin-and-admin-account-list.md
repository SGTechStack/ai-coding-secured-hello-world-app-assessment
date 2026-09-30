# 09: Bootstrap admin and the admin Account list

**What to build:** On first deployment an operator gets a Bootstrap admin from configuration, with no editing of the database. That Admin can open an admin page in the SPA that lists live Accounts. Regular users can't see the page, and the server rejects them anyway. See ADR-0008.

**Blocked by:** 03 (Registration rules and password policy), 04 (Structured logging and the audit trail)

**Status:** ready-for-agent

- [ ] At startup in every profile, a Bootstrap admin is created from `app.admin.username`, `app.admin.password` and `app.admin.email`, but only if no active Admin exists.
- [ ] Restarting creates no duplicate Bootstrap admin.
- [ ] Outside the dev profile, startup fails if any of the three settings is missing.
- [ ] The Bootstrap admin's password goes through the same Password policy and hashing as any other Account.
- [ ] Development-only seed accounts, if any, exist only in the dev profile.
- [ ] `GET /admin/users?page=&size=` (ADMIN) returns a paginated list (default size 50) with id, username, email, role, enabled, locked and created date. It excludes Tombstones and never includes password hashes.
- [ ] Admin handlers carry `@EnableMethodSecurity` role checks on top of the URL rules.
- [ ] A Regular user gets 403 `forbidden` on every `/admin/**` endpoint, and the 403 is logged at WARN.
- [ ] The SPA shows the admin users page and its navigation only when `/me` reports ADMIN.

## Comments

**2026-09-29 — settings required outside dev, always.** Following ADR-0008's wording, startup outside the dev profile fails when any `app.admin.*` setting is missing, even if an active Admin already exists. The password policy runs only when the Bootstrap admin is actually created, and that includes the breach check. If Have I Been Pwned can't be reached at first startup, startup fails: no password is set unchecked. The username and email must pass registration's own Bean Validation constraints, which are checked against `RegistrationRequest`. A clash with an existing Account or a Tombstone fails startup. Creation is audited as `user-provisioning`, with no request fields.

**Dev profile.** `application-dev.yml` has a dev-only Bootstrap admin (`devadmin` / `dev-only-admin-password`). In dev, blank settings mean no admin is created.

**Test impact.** Every full-context test now creates the Bootstrap admin at startup. `IntegrationTest` empties the database before each test, and admin tests promote a registered Account with `loggedInAdmin(...)`. `SessionCookieStartupTest` and `H2ConsoleTest` start the real app, so they now include `OfflineCompromisedPasswords`; otherwise the Bootstrap admin's password would be checked over the network.

**List.** `GET /admin/users` returns Spring Data's `PagedModel` shape: `content` plus `page.{size,number,totalElements,totalPages}`. Size defaults to 50 with a maximum of 100. Accounts come oldest first, with username as the tiebreaker. Sorting isn't accepted from the client. Out-of-range or non-numeric `page`/`size` gets 400 `validation failed`. Out-of-range values now list their errors too.

**403 audit.** Denials in the security filter chain are audited at WARN (`access-control`, `error_code 403`, the caller's `user.id` when logged in). These are the admin URL rule and CSRF failures. A denial by `@PreAuthorize` itself is reached only if the URL rules were loosened, and it is not audited (known gap).
