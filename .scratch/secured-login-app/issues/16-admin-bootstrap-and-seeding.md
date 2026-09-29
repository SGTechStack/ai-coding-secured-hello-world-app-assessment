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
