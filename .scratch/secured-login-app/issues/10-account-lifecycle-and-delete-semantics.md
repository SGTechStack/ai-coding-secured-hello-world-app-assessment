# 10 — Account lifecycle and delete semantics

Type: grilling
Status: open
Blocked by: 03
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

**Amended by [04 — Password policy and history](04-password-policy-and-history.md).** 04 is resolved, so this ticket is unblocked on that edge. Three things land here.

- **The `/currentUser` path string now carries more than a self-read.** 01 deferred the literal segment to this ticket, noting the standards contradict themselves (`Standalone_Privileged_User_Administration_and_Password_Reset.md:536` says `/currentUser`, `Common_Secure_Self-Read_User_Endpoint.md:53` mounts at `/profile`). 04 hung `PATCH .../changePassword` off it, and put **two** of the `PasswordChangeFilter`'s four allowlist entries under it. Whatever this ticket rules, the filter allowlist and the change endpoint follow the segment — they are written against it, not a literal — but the decision now has a blast radius beyond one endpoint.
- **`password_history` rows need a disposition under the tombstone rule.** 04 added the table (`user_id` FK → `users.id`, `NOT NULL`) and writes a row per password ever set, including the current one. When a user is tombstoned rather than hard-deleted, decide whether the history rows are deleted, cascaded to the tombstone, or retained — and note the tension: retaining them keeps a set of BCrypt hashes for an account that no longer exists, while deleting them means a re-registered username starts with a clean history. There is no clause on this; it is this ticket's call.
- **Re-enabling a disabled account sets `requirePasswordChange=true`.** `Standalone_User_Access_Control_Application_Standard.md:130` makes this mandatory and 04 recorded it in the flag's lifecycle table, but the re-enable operation itself (`PATCH /users/{userId}/status`) is this ticket's. Wire the flag into it. Note the consequence 04 accepted: that user's next login lands them in the filter, so they can reach only CSRF, self-read, change-password and logout until they choose a new password.
- **Bulk operations.** 01 assigned `PATCH /api/v1/users/batchResetPassword` to this ticket via `Q24`. 04 fixed what a single reset does to the flag, the history table and the session store; a batch version repeats all of it per user, and `Standalone_Privileged_User_Administration_and_Password_Reset.md:427` requires the generated password to avoid the account's existing history — which under 04's four-value rule is a slightly larger set than the recipe's loop assumes.

**Amended by [08 — Authorization matrix](08-authorization-matrix.md).** One new endpoint whose payload this ticket owns, and one constraint on it.

- **`GET ${api.base-path}/users/{userId}` is now in the inventory**, guarded `USER_MANAGER` (08 row 11). 08 added it on the same reasoning 03 used for `GET /roles`: `Standalone_User_Access_Control_Application_Standard_Questions.md:498` lists it inside `Q19`'s own example matrix and `Standalone_User_Access_Control_Application_Standard.md:414` is a Design Choice that "administrators access full user records", while 01's inventory — built from the PRD story list — had no such row. 08 owns the guard; **this ticket owns the projection.**
- **The constraint: the detail projection must be a superset of the list projection and a *different type* from the self-read payload.** Sharing one DTO between `/users/{userId}` and `/currentUser` is the standard way `:414`'s two halves erode into one, and it erodes in the direction of disclosure. Two types, so widening the admin view can never widen `/currentUser`.
- **A `USER` calling `GET /users/{ownId}` gets `403`, not their own record.** `:429` is explicit — "all user management endpoints reject non-administrators with `403 Forbidden`, **including requests on their own account**". This row is not a second self-read path and must not be turned into one; the self-read path stays whatever this ticket names it.
- **The `/currentUser` path string is still this ticket's**, per 01's note on the `Privileged:536` versus `Common_Secure_Self-Read_User_Endpoint.md:53` contradiction. 08's rows 17 and 18 are written against the segment, not a literal, and follow whatever this ticket rules — as do the two forced-change allowlist entries 04 placed on it.
- **`SelfRead:80`'s `@PreAuthorize("hasAuthority('SELF_READ')")` is deleted, not rewritten.** 08 made the URL matrix the sole authorization mechanism and `SELF_READ` names an authority that does not exist under 03's role model. The recipe's IDOR defence — looking the record up *only* by `authentication.getName()` (`SelfRead:41`, `:62`) — is untouched and still mandatory; it was never the annotation doing that work.
