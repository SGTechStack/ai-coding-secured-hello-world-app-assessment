# 10 — Account lifecycle and delete semantics

Type: grilling
Status: open
Blocked by: 03, 04
Map: [Secured Login App](../map.md)

## Question

What is an account's full lifecycle, and — the forced reconciliation here — what does "delete" mean?

**Delete is a conflict the authority order resolves against the PRD.** Story 11's criterion is that "the account is removed", but the standard requires **tombstones** with a retention period (`Q29`, `Questions.md:704`). Settle the tombstone model: which columns mark it, the retention period, whether a tombstoned username/email is reusable (this interacts with Story 1's uniqueness criterion), whether tombstoned rows are excluded from the admin list of Story 8, and what eventually purges them — given scheduled hygiene jobs are out of scope, purging may have no owner, which needs stating rather than assuming.

Then answer, from the same question set:

- `Q23` (self-service profile update capability) — no PRD story; rule in or out.
- `Q23a` (account status visibility via a `/currentUser` endpoint, `Questions.md:601`) against [`Common_Secure_Self-Read_User_Endpoint.md`](../../../App-Standards/Appfw-User-Standards/Shared_Recipes/Common_Secure_Self-Read_User_Endpoint.md) — this recipe is binding. Decide the path (`/me`, `/profile`, `/currentUser`), and what it exposes. The React SPA currently has no way to learn its own role except by provoking a 403, so this likely earns its place.
- `Q24` (bulk user operations) — no PRD story; rule in or out.

Also settle the **admin self-action guards** the PRD requires in Stories 9, 10 and 11 (an admin may not disable, demote or delete their own account): where the check lives so all three endpoints share it, and what status and error code it returns under 08's error contract.

**Amended by [01 — Context, topology and API surface](01-context-topology-and-api-surface.md).** `POST /api/v1/users` (admin creates a user) was ruled **in scope** there, which makes the previous instruction here — "admin-created-user activation (`Q22`) is out of scope, do not reopen it" — wrong as written. The activation *workflow* (token email, pending state) remains out of scope; what 10 must now settle is the stance that replaces it: `Q22`'s **"Immediately active — user can login with admin-provided password"**. Confirm that stance, or say what else makes an admin-created account usable without reintroducing the out-of-scope workflow.

01 also hands 10 two further items:

- **The self-read path string.** 01's inventory uses `/api/v1/currentUser` *provisionally*. The standards contradict themselves — `Standalone_Privileged_User_Administration_and_Password_Reset.md:536` says `/currentUser`, `Common_Secure_Self-Read_User_Endpoint.md:53` mounts the controller at `/api/v1/profile`. Resolve the contradiction as part of `Q23a`.
- **`PATCH /api/v1/users/batchResetPassword`** (`Standalone_Privileged_User_Administration_and_Password_Reset.md:399`) is the concrete bulk operation `Q24` must rule in or out.

Blocked on 03 (role model) and 04 (which self-service surface exists at all).

**Amended by [02 — Persistence and session backend](02-persistence-and-session-backend.md).** Schema mechanics are settled, so this ticket's schema extension has a prescribed home and fixed column conventions:

- **Liquibase**, with `spring.jpa.hibernate.ddl-auto: validate` — so the changelog is the source of truth and any drift from the entities fails at startup.
- **This ticket owns its own changelog file** under `db/changelog/db.changelog-master.yaml`, one versioned file per logical change. 02 locked that convention because several tickets add schema concurrently and a single growing changelog would guarantee conflicts.
- **Column conventions are not this ticket's to choose:** primary keys are `UUID`, declared as `java.sql.Types.UUID` so Liquibase resolves the vendor type; every timestamp is `TIMESTAMP WITH TIME ZONE` mapped to `java.time.Instant` and stored UTC, read through an injectable `Clock`.
- 02's table inventory already **reserves the slot** for what this ticket adds; only the columns are open.
- Whatever DDL this ticket writes must hold on **both** H2 and PostgreSQL — 02 answered `Q6` as PostgreSQL-declared-target, and [14](14-test-and-validation-plan.md) now runs the security-critical suite against real PostgreSQL via Testcontainers, so vendor-specific DDL will fail visibly rather than silently.
