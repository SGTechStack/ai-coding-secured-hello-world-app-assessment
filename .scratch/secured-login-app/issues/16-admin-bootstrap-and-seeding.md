# 16 — Admin bootstrap and seeding

Type: grilling
Status: open
Blocked by: 03
Map: [Secured Login App](../map.md)

## Question

How does the first privileged account come into existence?

Answer `Q3` (initial administrator account bootstrap strategy, `Questions.md:61`) and `Q2` (user migration strategy, `Questions.md:34`) of the standard's question set. `Q2` is likely vacuous for a greenfield app — record that rather than leaving it blank.

PRD Story 12 sets the requirement: if no privileged user exists at startup, seed one from configuration (`app.admin.username`, `app.admin.password`), hashed identically to any other account; if one already exists, do not create a duplicate. Reconcile against the standard's `Q3` strategy options, which may prescribe something different — the standard wins where it does.

Settle:

- The existence check: role-based ("no user holds the privileged role") versus username-based, and what happens if the configured admin exists but has been disabled or tombstoned by 10's rules.
- Where the seeding runs (`ApplicationRunner`, `@PostConstruct`, a migration) and how it behaves under 02's schema-management choice.
- How the bootstrap password is supplied and what happens if it is absent — fail fast, or start without an admin. A default fallback password would be a live credential in version control, so rule that out explicitly.
- Whether first login forces a password change, and whether the seed event is audited (12's event list may need a row).
- The role name the seed assigns, which is whatever 03 decided — the PRD's `ADMIN` string will not survive that ticket unchanged.

Blocked on 03 (the role model decides what is being seeded).

**Amended by [02 — Persistence and session backend](02-persistence-and-session-backend.md).** This ticket's brief asks "where the seeding runs … and how it behaves under 02's schema-management choice." That choice is now **Liquibase** with `ddl-auto: validate`, which changes what the options mean:

- **`Q3`'s Liquibase-changeset options are now live, not hypothetical.** `Questions.md:65,69,74,83` all express admin bootstrap as a Liquibase changeset, and 02 chose Liquibase partly *because* of that. So the `ApplicationRunner`-versus-migration question this ticket poses is now a genuine fork with both arms available, and `Questions.md:69`'s own split — "Liquibase changeset (production), profile-gated seed (dev/local only)" — is directly implementable.
- **The seeding changeset gets its own file** under the convention 02 locked: `db/changelog/db.changelog-master.yaml` including one versioned file per logical change, this ticket owning its own. Note the ordering constraint if the seed is a changeset: it must run after 03's `roles` seed.
- **Story 12's idempotence is now observable in dev.** 02 chose a **file-based** `dev` H2 specifically for this: under an in-memory database every boot starts empty, so the "if a privileged user already exists, do not create a duplicate" branch never executes. It now does. [14](14-test-and-validation-plan.md) has been handed the matching assertion.
- **A hazard worth naming if the seed is a Liquibase changeset rather than runtime code:** a changeset cannot call `PasswordEncoder`, so the hash would have to be literal in the changelog — which is the version-controlled live credential this ticket's brief already rules out. That argues for the profile-gated runtime seed, or for a changeset that only runs in environments where the value comes from a parameter. Settle it explicitly.
- Any timestamp the seed writes follows 02: UTC `Instant` in a `TIMESTAMP WITH TIME ZONE` column, from the injectable `Clock`.

**Amended by [15 — Tech baseline and module structure](15-tech-baseline-and-module-structure.md).** Two constraints on the seeding mechanics. **`app.admin.password` resolves from `${APP_ADMIN_PASSWORD}` with no fallback default in any committed file**, so a missing variable fails context startup rather than seeding a guessable admin — a default in `application.yaml` is a published credential, and this ticket must not add one "for convenience" in the `dev` profile. (`dev` is otherwise the one profile needing no `DB_PASSWORD`, since file-based H2 has none.) **The `dev` H2 URL must be pinned absolute-from-project-root, not left relative**: 02's `jdbc:h2:file:./data/app` resolves against the *working directory*, so `mvn spring-boot:run` from `backend/` and a run from the repo root produce two different databases — and Story 12's "no duplicate admin on restart" branch would appear to fail for a reason that has nothing to do with the seeding logic.

**Amended by [04 — Password policy and history](04-password-policy-and-history.md).** This ticket's brief asks "whether first login forces a password change". 04 answered it, and the answer is a constraint here rather than an option.

- **The seeded admin is flagged `requirePasswordChange=true`.** 04 ruled the forced-change flag and its filter in scope, and put the bootstrap admin in the flag's lifecycle table. The reasoning is worth keeping because the clause does *not* cover this case on its face: `Standalone_User_Access_Control_Application_Standard.md:122` scopes the mandate to "all users created by administrators", and the bootstrap admin is created by configuration at startup, not by an administrator. It is flagged anyway because its password arrives as `APP_ADMIN_PASSWORD` (15) and is known to whoever ran the deployment — precisely the exposure the flag closes. **This may not be dropped for demo convenience**, which is the obvious temptation given Story 12 is demonstrated by restarting the app.
- **The bootstrap password must satisfy 04's policy**, or the seed fails: min 12, max 72, printable ASCII, not on the bundled denylist, not containing the admin username. Combined with 15's no-committed-default rule, a deployer supplying a weak `APP_ADMIN_PASSWORD` should fail startup with a clear policy error rather than seeding a weak admin. Decide whether that validation runs in the seed path.
- **The seed writes the first `password_history` row.** 04's table holds the current hash as its newest row, so the seed must insert one alongside the user — matching what `Privileged:165` does for admin-created users. If the seed ends up as a Liquibase changeset rather than runtime code, this is a second row the changeset has to write, and it compounds the hazard 02 already named: a changeset cannot call `PasswordEncoder`, so the hash would have to be literal in the changelog. That argues further for the profile-gated runtime seed.
- **Demo consequence to design around, not away.** On first boot the seeded admin logs in and immediately hits the `PasswordChangeFilter`, reaching only `GET /api/v1/csrf`, `GET /api/v1/currentUser`, `PATCH /api/v1/currentUser/changePassword` and `POST /api/v1/auth/logout`. Any walkthrough of Stories 8–11 therefore starts with the admin changing their password — and since 04 ruled that a successful change invalidates **all** sessions including the caller's, it starts with a second login too. Worth stating in whatever run instructions 17 hands over, so it reads as designed rather than broken.
- **The grace period does not apply.** 04 ruled the 30-day auto-disable (`Standard:50`, `:372`) out of scope with the hygiene jobs, so a seeded admin who never changes their password is never disabled. The flag persists indefinitely, which for a single-admin bootstrap is the safe direction.
