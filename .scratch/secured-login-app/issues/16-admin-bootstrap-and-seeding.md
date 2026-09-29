# 16 — Admin bootstrap and seeding

Type: grilling
Status: resolved
Blocked by: 03
Map: [Secured Login App](../map.md)
Validated: approved — [validation record](../handoff/validation.md)

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

## Answer

Resolved by grilling, one round. Decisions delegated by the user to the orchestrating session after the evidence was presented.

**A profile-independent `ApplicationRunner`, not a Liquibase changeset — and the standards bless that explicitly. Existence check is role-based on `USER_MANAGER`, any state. `APP_ADMIN_PASSWORD` is validated against 04's policy and a missing or weak value fails startup. `Q2` is vacuous and recorded as such.**

### `Q2` and `Q3`

- **`Q2` — Greenfield** (`Q:36`, "New application with no existing users — skip migration details below"). Vacuous, and recorded rather than left blank, as the brief asks. Every downstream branch of `Q2` and `Q3` concerning legacy admin mapping, emergency admins and pre-configured mapping tables is inapplicable. Note that **`Q3`'s only `(recommended)` markers sit on the migration arms** — `Q:78`'s "Migration: Both" and `Q:72`/`:79`'s mapping table — and `Q:83`'s recommendation line is explicitly scoped "For migrations". **The greenfield arm carries no recommendation at all**, so `Q3` offers less guidance here than its shape suggests.
- **`Q3` — Greenfield: new admin, seeded from configuration at startup.** `Q:69` prescribes the implementation as "Liquibase changeset (production), profile-gated seed (dev/local only)". **We take the runtime seed for all profiles and decline the changeset**, for the reason below.

### The changeset arm is unusable, and the standards say so themselves

02 named the hazard and this ticket confirms it: **a Liquibase changeset cannot call `PasswordEncoder`**, so the BCrypt hash would have to be a literal in the changelog — a live credential in version control, which 15 rules out (`APP_ADMIN_PASSWORD` with no committed default) and which this ticket's own brief rules out. 04 compounds it: the seed must also write the first `password_history` row, so a changeset would have to embed the same literal hash **twice**.

**This is not a deviation from `Q:69` so much as a route it already provides.** `Common_Automatic_Database_Role_Synchronization_and_Deleted_Role_Backups.md:274-276` draws the line directly:

> - **Use Flyway or Liquibase for Schema (DDL)** … Always manage the creation of the `roles` and `deleted_roles` tables through versioned database migration scripts.
> - **Limit Startup Runners to Seed Data (DML):** Use startup event listeners (`ApplicationReadyEvent` or `CommandLineRunner`) strictly for configuration-bound reference data or **bootstrap user seeding**.

Liquibase owns DDL (02's ruling, unchanged); a startup runner owns this DML, named in as many words. `Q:69`'s changeset suggestion is the outlier, and it is the arm that cannot be implemented without publishing a credential.

**One departure from `Q:69` remains and is deliberate: the runner is not `dev`-only.** `Q:69` scopes the runtime seed to "dev/local only". Making it profile-independent is what lets PRD Story 12 be true in every environment, and the property that made the changeset attractive — no runtime code touching credentials — is not available anyway. The seed is safe in `prod` precisely because of 15's no-default rule: with no `APP_ADMIN_PASSWORD`, the application does not start.

**Mechanism:** an `ApplicationRunner` (or `ApplicationReadyEvent` listener) that runs **after** Liquibase has applied the schema and after 03's `roles` seed — ordering matters, since `users.role` references `roles`. Idempotent by the check below; `@Transactional`; timestamps from 02's injectable `Clock`.

`Common…:245-252`'s `syncDBUsersBasedOnDefinedUsers()` — which does `userRepository.deleteAll(); initialiseUsers();` — is **not adopted** in any form. It is a destructive dev-reset path from a recipe the map already ruled out of scope, and it would silently delete every registered user on a config flag.

### The existence check: role-based, any state

**PRD Story 12 gets this right and the recipe gets it wrong.** `prd:109` keys on "no `ADMIN` user exists in the database" — role-based. `Common…:144-146` instead uses `isFirstRun()`:

```java
return userRepository.count() == 0 && roleRepository.count() == 0;
```

A count across both tables is wrong here for a concrete reason: 03's `roles` table is **seeded from config at startup**, so `roleRepository.count()` is never zero after the first boot — and once any ordinary user self-registers, `userRepository.count()` is non-zero too. Under `isFirstRun()` the admin seed would never fire again even if no administrator existed.

**The check is: does any user hold `USER_MANAGER`?** Under 03's model that is the role name — the PRD's `ADMIN` string did not survive that ticket, exactly as this brief predicted.

**"Any state", not "any enabled".** Checking for an *enabled* manager looks safer and is a bug: a disabled sole administrator would fail the check, the runner would try to seed, and the insert would collide with the existing username. It also cannot happen by accident — 03's invariant already forbids disabling the last enabled `USER_MANAGER` (`409 LAST_USER_MANAGER`). So the weaker check is both correct and sufficient.

**The edge 10 creates, named rather than discovered later:** if the configured admin username has been **tombstoned**, 10's rules block it permanently (`Std:95`) and both username and email are burned. The seed then cannot insert, and the correct behaviour is to **fail startup with an explicit message naming the tombstone** — not to skip silently, which would leave a running application with no route into the admin module. Deleting the last administrator is already blocked by 03's invariant, so reaching this state requires deleting an admin *after* promoting another; the failure message should say so.

### Secrets, and validating before seeding

`app.admin.username` and `app.admin.password: ${APP_ADMIN_PASSWORD}` — **no `:default`**, per 15, so a missing variable fails context startup rather than seeding a guessable admin. `Common…:278` says the same in the standards' own words: "**Never Hardcode Default Passwords** … loaded from secure environment variables or a secrets manager."

**04's policy is validated in the seed path, before any write.** Min 12 / max 72 / printable ASCII / not on the bundled denylist / does not contain the username. A deployer who supplies a weak `APP_ADMIN_PASSWORD` gets a **startup failure naming the violated rule**, not a weak administrator. This ticket's brief asked whether that validation runs here; it does, and it is the one place where failing loudly is unambiguously right — there is no user to inconvenience and no request to fail.

**No standard clause mandates fail-fast on a missing admin secret** — searched for, and the only fail-fast clause in the Standalone standard is `Std:424` (duplicate role definitions). So this is 15's rule, adopted here, with the nearest standards precedent being the SSO recipe's "configuration validation fails fast on missing or invalid JWK material" (`SSO_MCC_Provider_Hint_and_Private_Key_JWT.md:427`). Recorded as ours rather than inherited.

The `dev` H2 URL is pinned **absolute from the project root**, per 15. 02's relative `jdbc:h2:file:./data/app` resolves against the working directory, so `mvn spring-boot:run` from `backend/` and a run from the repo root produce two different databases — and Story 12's no-duplicate branch would appear to fail for a reason having nothing to do with seeding.

### What the seed writes

One `users` row: `username`, `email`, BCrypt(`APP_ADMIN_PASSWORD`) at cost 12 (04), `role = USER_MANAGER`, `enabled = true`, `failed_login_attempts = 0`, `locked_until = null`, `created_at = clock.instant()`, **`require_password_change = true`**.

Plus **one `password_history` row** carrying the same hash — 04's table holds the current hash as its newest row, matching what `Priv:165` does for admin-created users. Both writes in one transaction.

**The forced-change flag is not dropped for demo convenience**, which 04 anticipated as the obvious temptation. The clause does not cover this case on its face — `Std:122` scopes the mandate to "all users created by administrators", and this account is created by configuration — but `Common…:280` closes exactly that gap: "**Always** flag newly seeded users with `requirePasswordChange: true` to force users to configure their own secure credentials upon first login." `Q:68` says the same for the greenfield arm ("mandatory change on first login"). The reason is that `APP_ADMIN_PASSWORD` is known to whoever ran the deployment, which is precisely the exposure the flag closes.

**The grace period does not apply.** 04 ruled the 30-day auto-disable (`Std:50`, `:372`) out of scope with the hygiene jobs, so a seeded admin who never changes their password is never disabled. For a single-admin bootstrap that is the safe direction — the alternative locks the only administrator out of the application.

### The seed is audited

`Std:282` requires "User account operations (create, unlock, update, delete) **attributable to the acting administrator**" and `Std:324` puts successful administrative user operations at `INFO`. A bootstrap seed has no acting administrator, which is a gap in the clause rather than a reason to skip the event.

One `INFO` audit line on seed: `event.action = user-administration` (the value `Priv:349` uses), `event.outcome = success`, `user.id` of the created account, and the actor recorded as the **system** rather than omitted or faked. **12 owns the field name for a non-human actor** — this is the only event in the application with no human principal, and 05 already found `event.action`'s enum is a closed set with gaps, so 12 should treat this as a third case alongside its role-change and status-change gaps.

**No line is emitted on the no-op path.** A restart that finds an existing manager logs nothing at `INFO` — an audit event per boot would be noise, and `Std:279-282`'s list is about state changes.

### The demo consequence, designed for rather than around

On first boot the seeded administrator logs in and immediately meets the `PasswordChangeFilter`, reaching only `GET /api/v1/csrf`, `GET /api/v1/currentUser`, `PATCH /api/v1/currentUser/changePassword` and `POST /api/v1/auth/logout` (08 tier 0, `Priv:535-539`). And because 04 ruled that a successful change invalidates **all** sessions including the caller's, they must then log in a second time.

So any walkthrough of Stories 8–11 begins: log in → change password → log in again. **This belongs in 17's run instructions verbatim**, or it reads as broken software on the first thing an assessor does.

**Story 12's idempotence is now genuinely observable** — 02 chose file-based `dev` H2 precisely so the second boot has state to find. 14 has the assertion.

### Amends

- **02** — no new tables; the seed is runtime DML ordered after Liquibase and after 03's role seed.
- **03** — the `roles` seed must complete before this runner; both live in the same startup ordering.
- **10** — a tombstoned admin username causes a startup failure, not a silent skip.
- **12** — one new event (bootstrap seed) with a **system actor**, a field 12 must name; it is the only actor-less audit event in the application.
- **17** — the three-step first-boot sequence goes in the run instructions.
- **14** — tests for: seeding on an empty database; **no duplicate on restart** against file-based H2 (Story 12's second criterion, and the reason 02 chose it); startup failure when `APP_ADMIN_PASSWORD` is absent; startup failure when it violates 04's policy; the seeded admin carrying `require_password_change = true` and one `password_history` row; and the full login → change → re-login sequence as an end-to-end path.
