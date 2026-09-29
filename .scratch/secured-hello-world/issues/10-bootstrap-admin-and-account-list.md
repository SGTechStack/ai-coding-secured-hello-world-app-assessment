# 10: Bootstrap Admin and the admin Account list

**What to build:** On first deployment, an Admin exists without anyone editing the database, created from configured credentials and email. They log in and see an admin screen listing every Account with its role, enabled and Locked state, and creation date. A User can't see that screen and gets 403 on every admin endpoint however the request is crafted. Viewing the list is audited, because it exposes emails. See the spec's stories 43, 63–65 and 76–81, "Bootstrap Admin initializer", "Account administration service", "Security configuration" (authorisation, secrets), and `CONTEXT.md`'s Bootstrap Admin. The required-password-change flag is set here; ticket 13 enforces it.

**Blocked by:** 04

**Status:** ready-for-agent

- [ ] At startup, when no Account holds the Admin role (enabled or not), the initializer creates one from the configured username, password and email. The password goes through the Credential policy and BCrypt, and the Account is created with `password_change_required` set.
- [ ] Restarting while any Admin exists creates nothing.
- [ ] In `dev`, missing values fall back to `admin` / `password` / `admin@localhost`, skipping the policy, with a loud WARN. In any other profile, a missing username, password or email fails startup. Outside `dev` these values come from the secrets manager.
- [ ] Startup fails with a clear message when the configured username already belongs to an Account. Blocking tombstoned usernames is added in ticket 15.
- [ ] Creating the Bootstrap Admin emits a `user-provisioning` audit event.
- [ ] `GET /api/admin/users` returns `{id, username, email, role, enabled, locked, createdAt}` for every Account, never password hashes or history.
- [ ] `/api/admin/**` requires the ADMIN role in the filter chain, and every Account administration service method also carries `@PreAuthorize("hasRole('ADMIN')")`. A User gets 403 on every admin path, including ones naming their own Account.
- [ ] Viewing the list emits an INFO `user-administration` audit event. A 403 emits a WARN `access-control` event with `user.id`, path and method.
- [ ] SPA: an admin users screen reachable from hello for Admins only; admin routes are hidden from Users.
- [ ] Bootstrap tests run separate application contexts per profile and property set, asserting: creation once with the configured email and the flag set; no re-creation on restart; the `dev` fallback plus its WARN; non-`dev` failure for each missing value; and failure when the username is taken.
- [ ] API tests cover: the list for an Admin, its fields and the absence of hashes; a User getting 403 on the list and on every admin path; and the audit events.
