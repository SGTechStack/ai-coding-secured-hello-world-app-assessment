# 13: Admin bootstrap seed + privileged-account hardening

**What to build:** On first startup with no ADMIN present, the app seeds one admin so the admin module is reachable without manual DB edits; restarts don't duplicate it. This slice also resolves the three IM8 FAIL findings that all cluster on the privileged-account path (secrets, first-login change, MFA decision).

**Blocked by:** 02 (user schema + BCrypt hashing), 03 (login path), 10/11/12 (admin actions the seeded admin unlocks).

**Status:** ready-for-agent

- [ ] Given no ADMIN user exists at startup, one is seeded with the password hashed identically to any other account (BCrypt).
- [ ] Given an ADMIN already exists on restart, no duplicate seed account is created.
- [ ] **IM8 as-8 (secrets management):** the seed admin credentials, DB credentials, and session/reset-token secret are sourced from a managed secret store / environment-injected secrets, NOT committed plaintext config. The chosen secret store is documented. (Now mandated by the spec's Secrets management NFR.)
- [ ] **IM8 ac-6 (forced first-login change):** the seeded admin (and any admin-issued/temporary credential) carries `must_change_password`; login into the admin module is blocked until the password is changed. (Mandated by Story 12.)
- [ ] **IM8 ac-2 (privileged MFA) — WAIVED (out of scope):** MFA/step-up is NOT built; the waiver and compensating controls (lockout, IP throttling, forced first-login change, secure sessions, audit logging) are recorded in the spec's Compliance Waivers section. Do not implement MFA.
- [ ] Seed / first-login-change events are audit-logged. (IM8 lm-4)
- [ ] Integration test: forced first-login password change blocks admin actions until changed. (IM8 ac-6)
