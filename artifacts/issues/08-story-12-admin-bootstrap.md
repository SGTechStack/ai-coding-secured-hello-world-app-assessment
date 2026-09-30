# 12: As an operator deploying the app for the first time, I want an initial admin account to be created automatically, so that there's a way into the admin module without manual database edits.

`feature` · wave 3

| Effort | Float |
| --- | --- |
| 1.0 days | 1.0 days |

## Acceptance criteria

- Given no `ADMIN` user exists in the database, when the application starts, then one is seeded using credentials supplied via configuration (e.g. `app.admin.username`, `app.admin.password`), with the password hashed identically to any other account.
- Given an `ADMIN` user already exists, when the application restarts, then no duplicate seed account is created.

## Dev tasks

1. `be_user_admin_config` (0.25 d) — `AdminBootstrapProperties` (`app.admin.username/email/password`), validated.
2. `be_user_admin_bootstrap_job` (0.5 d) — `AdminBootstrap` `ApplicationRunner`: skip when an ADMIN exists, fail fast when properties are missing, hash via the shared `PasswordEncoder`.

## Test seams

- `AdminBootstrapTest` (unit) — seeds when absent, no-ops when present, rejects blank password.

## Dependencies

- Blocked by: 1
- Unblocks: 8
