# 10: Admin bootstrap

**What to build:** On first start, an operator gets a working admin account created from configuration, so the admin module is reachable without editing the database. It is never duplicated. The app refuses to start when it would have no admin at all. No default admin credentials exist outside `dev` and `test`. See spec §Backend modules › Admin bootstrap.

**Blocked by:** 07

**Status:** resolved

- [x] An application runner starts after Flyway. If no `ADMIN` exists, it creates one from `app.admin.username`, `app.admin.email` and `app.admin.password`, using the same normalisation, password policy and encoder as registration.
- [x] If an admin already exists, it does nothing, even when the properties are set. A second context on the same database doesn't create a duplicate.
- [x] With no admin, missing properties or a password that fails the policy stop startup with a clear message that never contains the password.
- [x] `dev` and `test` provide documented dev-only values (username `admin`, a ≥12-character non-common password). The base and `prod` configs provide none and read them from the environment. The README lists the dev admin next to `johndoe`.
- [x] An `ADMIN_BOOTSTRAPPED` audit event is emitted.
- [x] Tests: with no admin and the properties set, the admin can log in and `/me` shows `role: ADMIN`. A restart doesn't duplicate it. Missing properties fail the context start. A weak password fails the context start.

## Comments

- **Where.** `admin.AdminBootstrap` (`ApplicationRunner`, so Flyway has run) + `admin.AdminBootstrapProperties` (`app.admin.{username,email,password}`, password redacted in `toString()`). Ticket 11 adds its classes to the same `admin` package. `UserAccountRepository` gained `existsByRole(Role)`.
- **Behaviour.** Any existing `ADMIN` means no-op, whatever the properties say. Otherwise: blank/missing properties, a password failing `PasswordPolicy.problem(...)` (checked first, for a precise message), or a username/email already held by a non-admin (`ACCOUNT_CONFLICT` from `AccountRegistration.register`) throw `IllegalStateException("No ADMIN account exists and the bootstrap admin cannot be created: ...")`. Boot rethrows a runner's exception unwrapped, so startup fails with it. Messages name the property/env var and the policy message, never the value. The admin is created through `AccountRegistration.register(NewAccount, Role.ADMIN)`, first name `Admin` (the spec has only three properties).
- **Audit.** `AuditLog.recordSystem(event, fields)` is new, for events outside a request: `actor=system ip=system`. The bootstrap logs `event=ADMIN_BOOTSTRAPPED actor=system ip=system outcome=success target=<admin username>`.
- **Config.** `dev` and `test`: `admin` / `admin@example.com` / `Dev-Admin-Passw0rd!` (not on the common list). The base config has only a comment; `prod` has nothing, so it reads `APP_ADMIN_USERNAME`, `APP_ADMIN_EMAIL` and `APP_ADMIN_PASSWORD`. `ProdProfileTest` now sets `app.admin.*`, and any later prod-context test must set them too. Playwright's backend runs `dev`, so it gets the admin with no config change. README lists both dev accounts in a table.
- **Tests.** `AdminBootstrapApiTest` (shared test context): the admin logs in and `/me` returns `role: ADMIN`. `AdminBootstrapStartupTest` boots the whole app (`prod`, or `test` for the johndoe conflict) on its own `jdbc:h2:mem:admin-bootstrap-<uuid>` DB through command-line args, covering: creation with normalised username/email and a cost-12 hash, plus exactly one audit line; a restart with other properties creating nothing; a restart with no properties working once an admin exists; and missing properties, a short password, a common password and a taken username each failing startup. These contexts set `org.springframework.boot.logging.LoggingSystem=none`, so Logback isn't re-initialised: this keeps the `AUDIT` `ListAppender` attached and leaves the JVM's console format alone. `SpaAuthFlow` gained `ADMIN_USERNAME`, `ADMIN_PASSWORD` and `logInAsAdmin(mvc)` for 11/12. The bootstrap admin lives in the shared test DB, so `GET /admin/users` there includes `admin` as well as `johndoe`.
- **Checks.** `mvn verify` passes (165 tests, JaCoCo met). Playwright passes 26/26 with `CI=1 BACKEND_PORT=18091 FRONTEND_PORT=13010`.
