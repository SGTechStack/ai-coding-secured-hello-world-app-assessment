# 13: Admin module + bootstrap seeding

**What to build:** Admins can manage accounts from the SPA panel. `GET /api/admin/users` lists `{id, username, email, role, enabled, createdAt}` — never password hashes; `PATCH /api/admin/users/{id}/status` toggles `enabled`; `PATCH /api/admin/users/{id}/role` switches `USER`/`ADMIN`; `DELETE /api/admin/users/{id}` removes the account. All three mutations reject self-targeting (an admin can't disable, demote, or delete themselves). A disabled user can no longer log in. On startup, if no ADMIN exists, one is seeded from `app.admin.username`/`app.admin.password` (dev default documented; prod fails fast when absent); restart never duplicates. The SPA admin page renders the user table with enable/disable, role-change, and delete controls, guarded to ADMIN.

**Blocked by:** 09: Auth spine — register → login → hello

**Status:** implemented

- [x] `GET /api/admin/users` returns all users with the specified fields, no password hashes
- [x] `USER` role calling any `/api/admin/**` → 403
- [x] Status toggle enables/disables another account; disabled user cannot log in
- [x] Role change switches USER ↔ ADMIN on another account
- [x] Delete removes another account
- [x] All three mutations reject self-targeting (disable, demote, delete own account)
- [x] First boot seeds one ADMIN from config (BCrypt-hashed like any account); restart with existing ADMIN seeds nothing
- [x] SPA admin panel: table + mutation controls work end-to-end; non-admin users can't reach the page

Implementation notes: everything lives in a new `admin` package —
`AdminController` (`/api/admin/users`: GET list, PATCH `/{id}/status`,
PATCH `/{id}/role`, DELETE `/{id}`), `AdminService`, `AdminSeeder`, DTO
records (`AdminUserResponse`, `UpdateStatusRequest{enabled}`,
`UpdateRoleRequest{role}`), and `AdminException` (`SelfAction` → 400
"Cannot modify own account", `UserNotFound` → 404 "User not found") via
`ApiExceptionHandler`. The self-guard is uniform across all three
mutations (wayfinder ratifies 400-or-403; 400 chosen as the domain
rejection) and compares the target's username to
`Authentication.getName()` — the principal's id isn't on the token.

Session invalidation (ticket-02 mechanism, `findByPrincipalName` +
`deleteById`) runs wherever a live session would otherwise out-privilege
the account: disable, delete, and ADMIN→USER demotion — a stored
SecurityContext keeps its original authorities for the session's
lifetime, so a demoted admin would stay admin without it. Promotion and
re-enable need none (the session can only hold less privilege; new powers
arrive at next login). All three mutations are `@Transactional`; delete
removes the target's `password_reset_tokens` first (`deleteByUser` — the
FK would otherwise reject the row delete), then sessions, then the user.

`AdminSeeder` is an `ApplicationRunner`: `existsByRole(ADMIN)` gates the
seed (idempotent across restarts and keyed on the role, not the
configured username); blank `app.admin.username`/`password` means
"not configured" → WARN + stand down; a username or email already taken
by a non-admin row also warns and skips rather than dying on the unique
constraint (both `users.username` and `users.email` are unique + non-null,
so `existsByUsername`/`existsByEmail` get symmetric pre-INSERT guards).
A configured password shorter than `app.password-min-length` logs a WARN
but still seeds — the registration-time rule doesn't bind the bootstrap
credential, and fail-fast hardening is ticket 14's. Seeded account: role
ADMIN, enabled, BCrypt hash,
`createdAt` from the `Clock` bean. Config plumbing: `AppProperties.Admin`
group + `app.admin.*` bound from `APP_ADMIN_USERNAME`/`APP_ADMIN_PASSWORD`/
`APP_ADMIN_EMAIL` env vars — blank defaults in `application.yml`,
documented dev defaults (`admin`/`admin-local-dev-password`/
`admin@localhost`) in `application-dev.yml`. Prod fail-fast arrives with
ticket 14: `application-prod.yml` will bind the env vars with no
fallback, so a missing config fails at startup before the seeder runs.

Test seams used: primary HTTP seam (`AdminApiTests` — `MockMvc` +
`@SpringBootTest` on H2, real `GET /api/auth/csrf` → `X-XSRF-TOKEN`
flow, users/tokens seeded via repositories, `ipThrottle.clear()` in
`@BeforeEach`). Session invalidation is proven by replayed-cookie → 401
on `/me` and `/api/admin/users`; promotion/demotion is proven by a fresh
login reaching/being refused the admin prefix. Seeder seam per ticket
05 ("admin seed tested via the config-driven seeder"): a dedicated
`@SpringBootTest(properties={app.admin.*})` context whose first ordered
test observes the startup-seeded row end-to-end; later tests invoke
`seedAdminIfAbsent()`/`run(null)` directly for restart-idempotence,
existing-admin, blank-creds, and username-/email-collision cases.

Frontend: `api.ts` gained `AdminUser` + `listAdminUsers`/
`setUserEnabled`/`setUserRole`/`deleteAdminUser` (CSRF header attached
automatically by `apiFetch`). `AdminPage` (`/admin`) renders the table
with per-row Enable/Disable, Make/Revoke admin, and Delete (confirm)
controls; the acting admin's own row is disabled client-side as UX (the
server guard is authoritative). `RequireAdmin` in `App.tsx` bounces
anonymous → `/login` and non-admins → `/`; HelloPage shows an "Admin
panel" link to ADMIN users. (`design-taste-frontend` isn't installed in
this workspace — the page follows the established Card/Button design
language.)

Traps worth noting: (a) login clears the `XSRF-TOKEN` cookie — live
curl checks must re-bootstrap before any subsequent mutation, same as
the SPA does; (b) the users schema requires a unique non-null email, so
the seeded admin carries `app.admin.email` (default `admin@localhost`);
(c) shared in-memory H2 (`jdbc:h2:mem:helloauth`) persists across cached
test contexts — the seeding test's ordered first case is the only place
startup seeding is observable, and `@AfterEach` cleanup keeps the row
from leaking into other classes.

## Comments

**Verification (do-work-min, 2026-09-16):**

- Implemented: admin list/status/role/delete endpoints under the
  ADMIN-gated `/api/admin/**` prefix; uniform self-guard (400
  "Cannot modify own account"); session invalidation on disable,
  demote, and delete; token cleanup on delete; config-driven
  `ApplicationRunner` seeder with env-var creds + documented dev
  defaults; SPA admin panel behind `RequireAdmin`.
- Verification steps: `mvn test` — `AdminApiTests` 14/14,
  `AdminSeedingTests` 5/5, full suite green (all classes);
  `npm run build` + `oxlint` clean (2 pre-existing warnings only);
  live curl on port 8083 (dev profile): admin seeded at startup,
  admin login → list shows contract shape with no hashes, self-status
  PATCH → 400, USER login → 403 on GET/PATCH, disable → target's
  session 401 + relogin 401, re-enable/promote/demote → 200 with
  updated DTO, delete → 204 and account gone.
- Not yet (later tickets): audit logging, prod profile + fail-fast
  admin-cred enforcement, token housekeeping, config-invariant guard
  (all ticket 14).

**Correction pass (review follow-up, 2026-09-16):**

- Session invalidation deduplicated: the `findByPrincipalName` +
  `deleteById` purge now lives once in
  `com.example.helloauth.session.SessionInvalidationService`
  (`invalidateAllFor(username)`); both `AdminService` (disable/demote/
  delete) and `PasswordResetService` (confirm) call it — one
  implementation of the fail-closed helper, no verbatim copies.
- `RequireAdmin` in `App.tsx` now composes `RequireAuth` (which owns the
  loading screen + anonymous → `/login`) with an inner `AdminOnly` role
  check (non-admin → `/`) instead of copy-pasting the guard body; the
  "Restoring session…" markup is a shared `SessionRestoring` component
  used by all three guards. Behavior unchanged.
- `api.ts`: PATCH calls now go through a generalized `jsonRequest` with
  `postJson`/`patchJson` delegates (matching the file's convention), and
  `Principal.role` + `AdminUser.role` + `setUserRole` share one exported
  `Role = 'USER' | 'ADMIN'` union instead of `string` vs an inline
  union.
- Re-verified: `mvn test` 117/117 green;
  `npm run build` + `npx oxlint` clean (same 2 pre-existing
  fast-refresh warnings).

**Verification (do-work-min, 2026-09-16):**

- Reviewer loop: 1 must-fix (seeder guarded username collision but not
  email collision — a taken email crashed startup via
  DataIntegrityViolationException) → fixed with a symmetric
  `existsByEmail` WARN+stand-down guard + regression test; re-review
  clean (Must-fix=0).
- Gate round 1: thermo-nuclear CHANGES REQUESTED (verbatim session-purge
  duplication; `RequireAdmin` copy-paste) → both resolved by extraction
  (`SessionInvalidationService`, guard composition); thermo-nuclear
  re-check PASS.
- Final gate: all four checks PASS — semgrep 0 findings;
  spring-security/spring-web PASS; thermo-nuclear PASS post-fix.
  Aggregate **PASS**; report
  `artifacts/code-reviewer/13-admin-module-bootstrap-seeding-compliance.html`.
- Mutation gate: 98.9% killed (89/90, 98% line coverage) — zero
  Critical/High/Medium survivors, no test changes needed; sole survivor
  is the ticket-12 confirmed-equivalent `StringBuilder` capacity mutant;
  report `artifacts/mutation-testing/13-admin-module-mutation.md`.
- All acceptance-criteria checkboxes ticked. Commits: `47832e0` (feature
  incl. both correction passes).
