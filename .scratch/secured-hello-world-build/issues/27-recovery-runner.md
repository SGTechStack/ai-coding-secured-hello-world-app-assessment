# 27: Recovery runner

**What to build:** An operator recovers when no admin can sign in (ADR-072; ADR-073; ADR-074). It runs from the same jar, with the application stopped and `web-application-type=none`, inside a planned outage.

- **Scope** is explicitly `password`, `totp` or `both`. It runs through the full context refresh with `IFEXISTS=TRUE`, and it never migrates or seeds (REJ-089).
- **Dry run is the default.** It prints a digest over the account state. A destructive run needs `--confirm=<digest>` and a reason.
- **Password.** The operator types the password on stdin or at a prompt. It goes through `PasswordService` as a forced-change credential. Batch mode mints nothing, and no secret crosses any output stream.
- **Guard.** The runner bypasses `AdminActionGuard`, and it audits the enrolled-admin count before and after.
- **Audit:** a dry-run row, an intent row and an outcome row (REJ-090), with `labels.operator_claimed_id`.

**Blocked by:** 16, 24

**Status:** done

- [x] With no flags, the runner changes nothing and prints a digest.
- [x] A stale or wrong digest is refused.
- [x] `password` sets a forced-change credential the admin can then sign in with. `totp` clears the factor so the admin re-enrols.
- [x] Against a missing database file it fails rather than creating one.
- [x] The canary scan finds no password in stdout, stderr or the logs.
- [x] The three audit rows are written with the before and after counts.
