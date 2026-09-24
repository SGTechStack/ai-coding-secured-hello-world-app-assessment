# 05: Account schema, roles, `/me` role and `/hello`

**What to build:** Accounts gain the fields the rest of the PRD needs: email, role, enabled, the lockout fields and `created_at`. The signed-in user carries their role, `/me` reports it, and the PRD's protected-content endpoint `GET /api/v1/hello` exists. See spec §Data model, §Backend modules › Account module and Protected content.

**Blocked by:** 01

**Status:** ready-for-agent

- [ ] A new Flyway migration adds `email` (unique), `role` (`USER`/`ADMIN`, default `USER`), `enabled` (default true), `failed_login_attempts` (default 0), `locked_until` (nullable) and `created_at` to `user_account`. It lowercases existing usernames and backfills `created_at`. There is no H2-only syntax in the versioned migration.
- [ ] The migration creates the `password_reset_token` table (unused until ticket 09) as the spec describes, with `ON DELETE CASCADE`.
- [ ] The dev seed `johndoe` gains `johndoe@example.com` and role `USER`.
- [ ] Username lookups are case-insensitive: logging in as `JohnDoe` works.
- [ ] The principal carries `ROLE_USER` or `ROLE_ADMIN` and the `enabled` flag, and stays `Serializable`.
- [ ] `GET /api/v1/auth/me` returns `role`, and the frontend `UserProfile` type includes it. MSW fixtures are updated.
- [ ] `GET /api/v1/hello` → `200 {"message": "Hello, <username>"}` as JSON when signed in, and `401` when anonymous.
- [ ] New password hashes use BCrypt cost 12, and the existing cost-10 seed still verifies.
- [ ] The existing tests stay green. `mvn verify` and `npm run check` pass.
