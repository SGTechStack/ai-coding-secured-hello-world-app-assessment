# 06: Admin bootstrap + user listing

**What to build:** An initial admin account is seeded automatically on first startup from configuration (`app.admin.username`, `app.admin.password`), hashed identically to any other account, with no duplicate seeded on subsequent restarts if an `ADMIN` already exists. That admin can call an endpoint listing all registered users (username, email, role, enabled status, created-at — never password hashes); non-admin users calling the same endpoint get 403.

**Blocked by:** 03 (needs role-aware session/auth infrastructure to enforce admin-only access)

**Status:** done

- [x] On startup, if no `ADMIN` user exists, one is seeded using `app.admin.username` / `app.admin.password` from configuration, password hashed the same way as any other account.
- [x] On restart with an `ADMIN` already present, no duplicate seed account is created.
- [x] `GET /api/admin/users` as an authenticated admin → lists each user's username, email, role, enabled status, created-at date; never password hashes.
- [x] `GET /api/admin/users` as an authenticated non-admin (`USER`) → 403.
- [x] Role checks enforced server-side via Spring Security, never trusted from client-supplied state.
- [x] React app has a minimal admin view rendering the user list (auth-gated).

## Implementation notes

**Backend**

- `AdminBootstrapRunner` (`ApplicationRunner`, in `user` package): checks `userRepository.existsByRole(Role.ADMIN)` before seeding — idempotent by role, not by a specific username, so it stays correct even if the admin username changes between deployments. Seeds from `app.admin.username`/`app.admin.password`, hashed with the same `PasswordEncoder` as every other account. No default outside the `dev` profile (`APP_ADMIN_USERNAME`/`APP_ADMIN_PASSWORD` env vars required elsewhere) — a predictable initial admin password must never ship to a real deployment; `dev` supplies a documented, dev-only default (`admin` / `change-this-admin-password`). The seeded account's email is a placeholder (`<username>@admin.local`), since the spec only names username/password for the seed and email has no functional role for an admin account here.
- New `admin` package: `AdminUserController` (`GET /api/admin/users`) and `UserSummaryResponse` DTO (id, username, email, role, enabled, createdAt — no password hash, matching the same pattern as `RegistrationResponse`).
- `SecurityConfig`: `/api/admin/**` requires `ROLE_ADMIN` via `.hasRole("ADMIN")`. Added `RestAccessDeniedHandler` (403 JSON, matching the existing `RestAuthenticationEntryPoint`/`RestSessionExpiredStrategy` pattern) since Spring Security's default `AccessDeniedHandler` renders an HTML whitelabel page, wrong for a JSON API.
- `LoginResponse` now also returns `role` (read straight off the already-loaded `User`, no extra query) — the frontend needs to know this to decide whether to render the admin view, and it's not privileged information (a user already knows their own role).

**Real (pre-existing, latent) bug found and fixed, unrelated to this ticket's own logic**: every test class that does `userRepository.deleteAll()` in `@BeforeEach` was fragile to test execution order. If a `PasswordResetToken` row from another test class's shared H2 context still referenced a user, the delete would fail with a foreign-key `DataIntegrityViolationException` (no cascade is configured between `User` and `PasswordResetToken`). This surfaced when adding this ticket's new test classes shifted execution order. Fixed by clearing the token table before the user table in every affected test class (5 files), matching what `PasswordResetTest` already did correctly. Not a production bug — nothing in the app currently deletes users — but a real test-isolation gap.

**Frontend**

- `AdminUserList.tsx`: a table of all users, rendered only inside `ProtectedGreeting` when `role === "ADMIN"`. `api/client.ts` gained `fetchAdminUsers()` and a shared `UserRole` type used across login/registration/admin responses.
- Role-gating here is a UI convenience only — the component simply doesn't mount for a non-admin, so no request is even made — but the real boundary is server-side, as verified below.

**Tests**

- `AdminBootstrapRunnerTest`: seeds exactly one admin when none exists; the seeded password is actually BCrypt-hashed, not stored as plaintext; running the bootstrap twice doesn't create a second admin; doesn't seed if an admin already exists under a different username. Since the Spring context (and the runner) only executes once per shared test context, idempotency is tested by re-invoking the runner directly against the real repository rather than restarting the application multiple times within one test run.
- `AdminUserControllerTest`: an admin session lists all users without password hashes in the response; a non-admin session gets 403; no session gets 401.
- `mvn clean verify`: 27/27 tests pass, BUILD SUCCESS.
- Verified live: confirmed the startup log line seeding `admin`, logged in as that seeded admin through the real UI and saw the user table render (username/email/role/enabled/created-at, no password hash field anywhere in the payload), then logged out, registered and logged in as a plain `USER`, confirmed the admin section simply doesn't render, and — the boundary that actually matters — made a direct `fetch` to `/api/admin/users` from that non-admin session and got a 403 with `{"message":"Access denied"}`.
