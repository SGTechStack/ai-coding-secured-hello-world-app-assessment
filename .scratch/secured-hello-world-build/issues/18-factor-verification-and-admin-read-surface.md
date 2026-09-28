# 18: Factor verification and admin read surface

**What to build:** An admin signs in, is sent straight to enrolment or the challenge (ADR-023), passes it, and sees the user list. A USER never sees the admin surface.

- **`POST /api/mfa/totp/verification`** (`ROLE_ADMIN`, password held) takes the code in the JSON body and grants or renews `FACTOR_TOTP`. Replay is rejected under the row lock (R-MFA-017). The username comes from the security context, never from the body. The session id rotates on grant.
- **Authorization** rules are hand-composed, role first, with no `@EnableMultiFactorAuthentication` and no `RoleHierarchy` (ADR-026). `hasRole` is used (REJ-023), and `@PreAuthorize` repeats only the role check.
  - The whole `/api/admin/**` surface requires the factor. Reads accept a factor of any age within the session (ADR-021).
  - A missing or expired factor gets 412 `MISSING_FACTOR` (reason `MISSING` or `EXPIRED`). A wrong code gets 412 `INVALID_FACTOR`.
  - An unenrolled admin gets 422 `FACTOR_ENROLMENT_REQUIRED`.
  - A USER gets 403 `ACCESS_DENIED` on every admin route and is never asked for a factor.
- **`GET /api/profile`** `factors` has four members: `held`, `required`, `enrolled` and `rebindRequired`.
- **`GET /api/admin/users`** lists username, email, role, enabled and creation date, never a hash (T-ADM-008). Its audit row carries `user.target.count`. **`GET /api/admin/users/{uuid}`** returns one user.
- **Rate limit:** add the verification row (20 / 1 per 3 s).
- **SPA:** gate order is forced change, then (later) terminal factor state, then enrolment, then challenge, then the app. The challenge page, the user-list table and the detail view are keyboard-navigable. Guards are UX only: a test deletes a guard and asserts that admin data still doesn't render.

**Blocked by:** 17

**Status:** ready-for-agent

- [ ] A USER gets 403 on every admin route and never 412 or 422.
- [ ] An enrolled admin without the factor gets 412 `MISSING_FACTOR`. After verification the list loads, with no hash field.
- [ ] Replaying the same code within its step is refused.
- [ ] The `factors` object reflects each state.
- [ ] A Playwright test covers admin sign in → challenge → user list.
- [ ] The TOTP window and replay logic meet 85% mutation score.
