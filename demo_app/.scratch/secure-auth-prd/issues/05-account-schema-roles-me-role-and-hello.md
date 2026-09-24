# 05: Account schema, roles, `/me` role and `/hello`

**What to build:** Accounts gain the fields the rest of the PRD needs: email, role, enabled, the lockout fields and `created_at`. The signed-in user carries their role, `/me` reports it, and the PRD's protected-content endpoint `GET /api/v1/hello` exists. See spec §Data model, §Backend modules › Account module and Protected content.

**Blocked by:** 01

**Status:** resolved

- [x] A new Flyway migration adds `email` (unique), `role` (`USER`/`ADMIN`, default `USER`), `enabled` (default true), `failed_login_attempts` (default 0), `locked_until` (nullable) and `created_at` to `user_account`. It lowercases existing usernames and backfills `created_at`. There is no H2-only syntax in the versioned migration.
- [x] The migration creates the `password_reset_token` table (unused until ticket 09) as the spec describes, with `ON DELETE CASCADE`.
- [x] The dev seed `johndoe` gains `johndoe@example.com` and role `USER`.
- [x] Username lookups are case-insensitive: logging in as `JohnDoe` works.
- [x] The principal carries `ROLE_USER` or `ROLE_ADMIN` and the `enabled` flag, and stays `Serializable`.
- [x] `GET /api/v1/auth/me` returns `role`, and the frontend `UserProfile` type includes it. MSW fixtures are updated.
- [x] `GET /api/v1/hello` → `200 {"message": "Hello, <username>"}` as JSON when signed in, and `401` when anonymous.
- [x] New password hashes use BCrypt cost 12, and the existing cost-10 seed still verifies.
- [x] The existing tests stay green. `mvn verify` and `npm run check` pass.

## Comments

- **Migration.** `V2__account_roles_lockout_and_reset_tokens.sql` is portable SQL (one `ALTER TABLE` per change, no `MERGE`/`IF NOT EXISTS`). It lowercases usernames first, then adds `email` nullable, backfills `johndoe@example.com`, and then sets it `NOT NULL` + unique. Only the seed ever created accounts before this feature, so any other pre-existing row makes the migration fail rather than get an invented email. `role` has a `CHECK (role IN ('USER','ADMIN'))`. `created_at` has `DEFAULT CURRENT_TIMESTAMP`, which backfills existing rows. New code should still set it from the `Clock` (ticket 07). `password_reset_token` has an index on `user_id` and no JPA entity yet (ticket 09). Hibernate `validate` accepts `@Enumerated(STRING)` against `VARCHAR(10)` and `Instant` against `TIMESTAMP WITH TIME ZONE`.
- **Seed.** The repeatable `R__seed_demo_user.sql` keeps its H2 `MERGE … KEY (username)`. It now also sets `email` and `role`, which it has to because it runs after V2 and `email` is `NOT NULL`. The hash stays at cost 10 and still verifies under the cost-12 encoder.
- **Case-insensitive usernames.** `UserAccount.normaliseUsername` (lowercase, `Locale.ROOT`) is applied in `AccountUserDetailsService` before `findByUsername`. The principal and `/me` return the stored lowercase name, so logging in as `JohnDoe` gives `johndoe`, and so do the audit `LOGIN_SUCCESS` actor and `/hello`. `LOGIN_FAILURE` still logs the name as submitted. Registration (07) must store `normaliseUsername(...)`. Email normalisation and the email/role lookups are left to 07 and 10.
- **Principal.** `AccountUserDetails` passes `enabled` through to `User`, with the other three flags `true`. It has a single `ROLE_USER`/`ROLE_ADMIN` authority and a `getRole()`. Heads-up for 08: because `enabled` is now real, `DaoAuthenticationProvider`'s default pre-checks would reject a disabled account with `DisabledException` *before* the password check. No account can be disabled yet. 08 must move that check after the password check. Lock state isn't mapped to `accountNonLocked`, which is also 08's call.
- **`/me` and `/hello`.** `UserProfile` is now `(username, firstName, role)`, with `role` serialised as `"USER"`/`"ADMIN"`. `HelloController` (new `hello` package) needs no security-config change, because `anyRequest().authenticated()` already covers it. It is `produces = application/json`, so `Accept: text/html, */*` still gets JSON.
- **Tests.** `SpaAuthFlow` and `SpaAuthFlow.logIn` are now `public`, so tests outside the `auth` package (`hello`, and later `admin`) can log in. `AccountSchemaTest` runs the real Flyway migrations up to V1, inserts a `JohnDoe` row, migrates to latest on a separate in-memory DB and checks the upgrade path. The other tests check defaults, email uniqueness, the role check and the reset-token cascade on the shared test DB, using unique `schema-*` usernames.
- **Frontend.** `Role = "USER" | "ADMIN"` and `UserProfile.role` are in `src/api/auth.ts`. The MSW `demoUser` is typed as `UserProfile`, and the two inline `janedoe` fixtures gained `role`.
