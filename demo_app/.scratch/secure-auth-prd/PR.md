# feat: Secure Auth — full PRD with OWASP Top 10 hardening

**Status:** Draft

This PR implements the Secure Auth spec: registration, lockout and per-IP throttling, password reset, the admin module and bootstrap, audit logging, the cross-origin topology and a strict SPA CSP, with hardened API errors throughout.

Closes `.scratch/secure-auth-prd/spec.md`
Closes `.scratch/secure-auth-prd/issues/01-hardened-api-errors-clock-and-csrf-body.md`
Closes `.scratch/secure-auth-prd/issues/02-audit-log-for-login-and-logout.md`
Closes `.scratch/secure-auth-prd/issues/03-cross-origin-topology.md`
Closes `.scratch/secure-auth-prd/issues/04-strict-spa-csp-and-e2e-on-production-build.md`
Closes `.scratch/secure-auth-prd/issues/05-account-schema-roles-me-role-and-hello.md`
Closes `.scratch/secure-auth-prd/issues/06-per-ip-login-throttling.md`
Closes `.scratch/secure-auth-prd/issues/07-registration-end-to-end.md`
Closes `.scratch/secure-auth-prd/issues/08-account-lockout.md`
Closes `.scratch/secure-auth-prd/issues/09-password-reset-end-to-end.md`
Closes `.scratch/secure-auth-prd/issues/10-admin-bootstrap.md`
Closes `.scratch/secure-auth-prd/issues/11-admin-user-list-end-to-end.md`
Closes `.scratch/secure-auth-prd/issues/12-admin-account-actions-end-to-end.md`

## Progress

- [x] 01 Hardened API errors, Clock seam and CSRF token in the body
- [x] 02 Audit log for login and logout
- [ ] 03 Cross-origin topology
- [ ] 04 Strict SPA CSP, with e2e on the production build
- [x] 05 Account schema, roles, `/me` role and `/hello`
- [ ] 06 Per-IP throttling on login
- [ ] 07 Registration, end to end
- [ ] 08 Account lockout
- [ ] 09 Password reset, end to end
- [ ] 10 Admin bootstrap
- [ ] 11 Admin user list, end to end
- [ ] 12 Admin account actions, end to end
- [ ] Code review fixes
