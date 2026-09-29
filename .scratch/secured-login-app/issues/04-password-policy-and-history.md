# 04 — Password policy and history

Type: grilling
Status: open
Blocked by: —
Map: [Secured Login App](../map.md)

## Question

What is the password strength policy, and what password history and rotation policy applies?

Answer `Q12` (minimum password strength requirements) and `Q13` (password history and rotation policy) of the standard's question set.

- PRD sets a floor only: length ≥ 12 (`prd/assessment-prd.md:36`), BCrypt via `BCryptPasswordEncoder`, no custom hashing. The standard's `Q12` asks for more dimensions than length — settle whether character-class rules, denylists or breach checks apply, and where validation lives so that registration (Story 1) and reset-confirm (Story 7) enforce it identically.
- Password history is **in scope** per the map's authority order (the PRD is silent; the standard requires a policy). Decide the retained-hash count, whether rotation/expiry is enforced, and how history rows are stored — the PRD's data model has no table for them, so this extends the schema.

**Also settle a scoping question this ticket exposes.** Password history and self-service password change ship in the *same* recipe, [`Standalone_Self-Service_Password_and_History_Management.md`](../../../App-Standards/Appfw-User-Standards/User_Standalone/Standalone_User_Access_Control_Recipes/Standalone_Self-Service_Password_and_History_Management.md). History is in scope; self-service change (a logged-in user changing their own password, distinct from the PRD's forgot-password reset) has no PRD story. Rule it in or out here, and record it on the map accordingly — if out, it belongs in **Out of scope**, not in **Decisions so far**.

This ticket gates account lifecycle (10) and the password reset flow (11).

**Amended by [01 — Context, topology and API surface](01-context-topology-and-api-surface.md).** Also rule in or out **forced password change**, a standard feature with no PRD story: `Standalone_Privileged_User_Administration_and_Password_Reset.md:531-539` describes a `requirePasswordChange` flag and a `PasswordChangeFilter` that blocks every endpoint except `GET ${api.base-path}/csrf`, `GET ${api.base-path}/currentUser` and `PATCH ${api.base-path}/currentUser/changePassword`.

This is no longer hypothetical: 01 ruled `POST /api/v1/users` (admin creates a user with an admin-supplied password) **in scope**, and that is precisely the case forced change exists for. If ruled in, settle what sets the flag (admin create? admin `resetPassword`? password age?) and confirm the filter's allowlist against 01's endpoint inventory.

**Amended by [02 — Persistence and session backend](02-persistence-and-session-backend.md).** Schema mechanics are settled, so this ticket's schema extension has a prescribed home and fixed column conventions:

- **Liquibase**, with `spring.jpa.hibernate.ddl-auto: validate` — so the changelog is the source of truth and any drift from the entities fails at startup.
- **This ticket owns its own changelog file** under `db/changelog/db.changelog-master.yaml`, one versioned file per logical change. 02 locked that convention because several tickets add schema concurrently and a single growing changelog would guarantee conflicts.
- **Column conventions are not this ticket's to choose:** primary keys are `UUID`, declared as `java.sql.Types.UUID` so Liquibase resolves the vendor type; every timestamp is `TIMESTAMP WITH TIME ZONE` mapped to `java.time.Instant` and stored UTC, read through an injectable `Clock`.
- 02's table inventory already **reserves the slot** for what this ticket adds; only the columns are open.
- Whatever DDL this ticket writes must hold on **both** H2 and PostgreSQL — 02 answered `Q6` as PostgreSQL-declared-target, and [14](14-test-and-validation-plan.md) now runs the security-critical suite against real PostgreSQL via Testcontainers, so vendor-specific DDL will fail visibly rather than silently.
