# 10 — Account lifecycle and delete semantics

Type: grilling
Status: resolved
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

## Answer

Resolved by grilling, one round. Decisions delegated by the user to the orchestrating session after the evidence was presented.

**Delete is a hard row delete plus a `deleted_users` tombstone row, retained indefinitely. Self-read is `/currentUser` at `Q23a`'s Moderate visibility. Self-service profile update and bulk operations are both ruled out. The self-action guard returns `403` with a code — and that forces a refinement of 08's interceptor constraint.**

### The standard's word and the standard's mechanism disagree

`Std:408` is an `[Enforced Constraint]`: "Deleted users are retained in the database through **soft-delete** to maintain the audit trail", echoed at `:475` ("soft-delete records with tombstone markers"). "Soft-delete" normally means an in-place flag on the live row.

**The recipe does the opposite.** `Standalone_Privileged_User_Administration_and_Password_Reset.md:316-345` copies the user into a separate `DELETED_USERS` table and then **hard-deletes the live row**:

```java
@Transactional   // tombstone save + user delete must be atomic
...
deletedUsers.save(tombstone);
users.delete(existing);
```

`PRIV:728` confirms the intent: "delete writes a tombstone record **before the user row is removed**."

**The glossary settles it for the recipe.** `Std:31` defines the term the constraint relies on: "`Deleted-user tombstone`: A **read-only archive record** of a deleted user account, retained to support audit traceability." An archive record is a separate row in a separate table — which is exactly what the recipe builds. So `:408`'s "soft-delete" is loose phrasing for "the data is retained somewhere", not a prescription of an in-place flag, and the enforced property — *the audit trail survives deletion* — is satisfied either way.

**Adopted: tombstone table plus hard delete.** Beyond being the only executable specification in the standards, it makes Story 8 correct by construction: with the row gone from `users`, the admin list needs no `WHERE deleted_at IS NULL` filter, and no future query can forget one. An in-place flag would put a soft-delete predicate on every read path in the application forever, and the first one that omits it is a data leak.

**PRD Story 11's "the account is removed" survives intact** — the row genuinely is removed. The reconciliation this ticket was created for turns out to cost nothing at the PRD's level of description.

### The tombstone

Its own Liquibase changelog file per 02's convention; `deleted_users`:

| Column | Notes |
|---|---|
| `id` | `UUID`, the **same id** the live row had — this is what keeps audit lines' `user.id` resolvable |
| `username` | permanently blocks reuse (`Std:95`) |
| `email` | see the asymmetry fix below |
| `role` | the role held at deletion; `PRIV:302-313` omits it, we keep it for audit |
| `enabled` | last known state |
| `deleted_at` | `TIMESTAMP WITH TIME ZONE`, UTC, from the injectable `Clock` |
| `deleted_by_user_id` | the acting administrator |

**No `password_hash`, ever.** `PRIV:302-313` omits it and that omission is correct: the tombstone exists for audit traceability (`Std:31`), and a credential is not an audit fact.

**`deleted_by_user_id` closes a gap the recipe admits to.** `PRIV:356` — "**Implementation gap:** `deletedById` is not yet in the starter's `DeletedUser` entity. Add the column and populate it in `handleUserDeletion` — without it, the deleting user's identity is only recoverable from logs." `Std:287` requires the tombstone to be attributable, so the gap is a defect, not an option.

**Retention: indefinite (`Q29`).** `Q:708` marks "**Indefinitely** (recommended — complete audit trail, minimal storage cost)" and it is the only option that is *coherent here*: every finite option needs a purge job, scheduled hygiene jobs are out of scope, and 15's ArchUnit row bans `@Scheduled` outright — so a 90-day retention would simply never be enforced, and we would be recording a policy we cannot execute. Worse, a finite retention makes `Std:95`'s permanent username block **silently expire**: purge the tombstone and the username becomes re-registrable, which is the exact identity collision the clause exists to prevent.

**So purging has no owner, and that is the deliberate answer** rather than an omission — this ticket's brief asked for it to be stated rather than assumed.

### A recipe bug: tombstoned emails are reusable

`PRIV:139-148` checks the tombstone table for **username** only:

```java
if (users.existsByUsername(request.username()) || deletedUsers.existsByUsername(request.username()))
```

while the email check one line later (`PRIV:149`) is `users.existsByEmail(...)` — live users only. So under the recipe as written, a deleted user's **email** can be re-registered while their **username** cannot.

`Std:96` states email uniqueness with no tombstone qualifier, and `Std:471` folds both into one sentence. **Both are checked against tombstones.** The asymmetry is a recipe oversight, and it matters here specifically because the PRD makes email the password-reset identifier (`prd:133`) — re-registering a deleted user's email means reset traffic for a former account lands on a new one.

Consequence recorded honestly: **deleting an account permanently burns both its username and its email.** For an assessment application that is right; at real scale it is the kind of thing that needs a support process, which is an integrator concern.

### `password_history` rows are deleted with the user

No clause governs this — confirmed by exhaustive search — so it is this ticket's call, as its brief says.

The recipes cannot help: `PRIV:76-77` models history as an `@ElementCollection` **on `UserAccount`**, so JPA cascades it away on delete and the question never surfaces. 04 instead made it a separate table with a `user_id` FK, so we must choose.

**Deleted.** Retaining them keeps a set of BCrypt hashes of a real person's passwords, attached to an account that no longer exists, for an indefinite retention period — a standing liability with no offsetting audit value, since `Std:31` scopes the tombstone to *traceability* and the tombstone already carries every audit fact. The counter-argument in this ticket's brief — that a re-registered username would start with a clean history — **is moot**: the username can never be re-registered (`Std:95`), and the email now cannot either.

FK is `ON DELETE CASCADE` so this cannot be forgotten in application code.

### Self-read: `/currentUser`, at Moderate visibility (`Q23a`)

**The path contradiction resolves 3-to-1 for `/currentUser`.** `Common_Secure_Self-Read_User_Endpoint.md` mounts its controller at `/api/v1/profile` (`:45`, `:115`, `:122`) — but `SelfRead:5` describes the path as an example in its own opening sentence ("e.g., `/profile` or `/me`"), and three files use `/currentUser` normatively:

- `Std:264` — the standard body's only mention: "Attempts to mutate `currentUser` through create, update, or delete endpoints must fail with `403 Forbidden`."
- `PRIV:536-537` and `PRIV:579-584` — the `PasswordChangeFilter` allowlist, built **in code** from `Paths.get(baseUrl, "/currentUser")`.
- `SELFPW:47`, `:185` — `@RequestMapping("/currentUser")` and `PATCH /api/v1/currentUser/changePassword`.

So `GET ${api.base-path}/currentUser`, with `PATCH ${api.base-path}/currentUser/changePassword` hanging off it per 04. 01's provisional choice stands; 08's rows 17–18 and 04's two forced-change allowlist entries need no edit.

**Payload: `Q23a`'s Moderate (`Q:611`, the marked recommendation), not `SelfRead:29-36`'s projection.** The recipe's five-field view (`username`, `firstName`, `lastName`, `email`, `roles`) predates the questionnaire's tiering and carries no operational state at all. Moderate is a superset, and one of its fields is load-bearing for this application:

```json
{
  "username": "...",
  "email": "...",
  "role": "USER_MANAGER",
  "requirePasswordChange": false,
  "createdAt": "...",
  "lastLoginAt": "...",
  "lastPasswordChangeAt": "..."
}
```

- **`requirePasswordChange` is `Q23a`'s "Pending mandatory password change flag"** and the SPA cannot function without it — 04's forced-change state must gate every route, and 08 put the filter *ahead* of the matrix, so a flagged user's only way to learn why everything is `403` is this field plus 21's `code`.
- **`role` is singular**, per 03's one-role-per-user model — not `SelfRead:33`'s `Set<Role>`.
- **`lastPasswordChangeAt` costs nothing** — it is the newest `password_history` row's timestamp, which 04 already writes.
- **`lastLoginAt` adds one column** to `users`, in this ticket's changelog. Ruled in rather than dropped: it is a named Moderate field, it is the only self-service signal a user has that someone else used their account, and one nullable timestamp is a low price for it.
- **`firstName` / `lastName` do not exist** — the PRD's data model has no name fields and no story needs them. Recorded as absent, not omitted.

**Detailed (`Q:614-615`) is refused: no lock status, no failed-attempt count.** `Q23a:603` says exactly why — "exposing lockout timing or failed attempt counts can help attackers optimize brute force attacks" — and this application has three counters (07) that such a field would expose the state of.

**`SelfRead`'s IDOR defence is kept in full and is not negotiable**: the record is looked up **only** by `authentication.getName()` (`SelfRead:41`, `:62`), never by a client-supplied id. As 08 ruled, `SelfRead:80`'s `@PreAuthorize("hasAuthority('SELF_READ')")` is **deleted** — `SELF_READ` names an authority that does not exist under 03's role model — and `SelfRead:83`/`:86`'s `@PostAuthorize`/`@PostFilter` go with it, since they reference `uuid` and `ssoId` fields that are SSO-only. **None of those annotations was doing the IDOR work**; the principal-only lookup was.

`SelfRead:96-103`'s mutation block is likewise unnecessary: we expose one `@GetMapping`, not a Spring Data REST repository, so there is no `POST`/`PATCH`/`DELETE` to block. 08's matrix denies them anyway. `SelfRead:12`'s `Cache-Control: no-cache, no-store, max-age=0, must-revalidate` comes free from Spring Security's defaults and is kept.

### `GET /users/{userId}`: a third projection, deliberately

Three distinct types, never shared:

1. **List** (`GET /users`, Story 8) — `username`, `email`, `role`, `enabled`, `createdAt`. Exactly `prd:87`, which also says "never password hashes".
2. **Detail** (`GET /users/{userId}`, 08 row 11) — the list fields plus `locked_until`, `failed_login_attempts`, `requirePasswordChange`, `lastLoginAt`. A superset of the list, per 08's constraint, and the lock fields that are *refused* on `/currentUser` are *correct* here — an administrator diagnosing a locked-out user is the reason `Std:414` grants "full user records".
3. **Self-read** (`/currentUser`) — as above.

Two and three must be **different Java types**, per 08: sharing a DTO is how `Std:414`'s two halves erode into one, and the erosion runs toward disclosure. A `USER` calling `GET /users/{ownId}` gets `403` (`Std:429`), not a self-read shortcut.

### The self-action guard, and a refinement to 08

PRD Stories 9, 10 and 11 each require it; `Std:266` ("Self-delete and self-unlock through the admin flow must fail"), `:100`, `:101`, `:105` and `:120` make it standards-mandated; 07 added a fourth endpoint (unlock).

**One `SelfActionGuard` component, called by all four** write endpoints on `/users/{userId}` — status, role, delete, unlock. Not four inline checks, which is how three of them stay right and one drifts.

**It returns `403` with code `SELF_ACTION_NOT_ALLOWED`** — and this is where a conflict inside the map surfaces:

- `Std:474` is explicit: "User deletion fails with **`403 Forbidden`** when user attempts to delete their own account." 03 independently ruled all self-actions `403`.
- But `PRIV:321-330` implements it as `BadRequestException("current user deletion not allowed")` — a **`400`**, contradicting the standard it implements.
- And **08 imposed a blanket rule**: "no non-authentication rejection on an authenticated path may use `401` or `403`, because `Std:437`'s global SPA interceptor treats both as a logout signal." Taken literally, that rule forbids the very status `Std:474` mandates.

**Resolution: `403` stands, and 08's constraint is refined rather than obeyed literally.** The correct rule is *no non-authentication rejection may use `401`/`403` **without a `code` the interceptor can recognise***. 21's `ProblemDetail` envelope is exactly what makes that possible, and this is the first place its `code` field earns its keep beyond documentation: the SPA's axios interceptor inspects `code` before logging out, and treats `SELF_ACTION_NOT_ALLOWED` (and `PASSWORD_CHANGE_REQUIRED`) as in-app errors rather than session death.

The recipe's `400` is rejected — `Std:474` is a clause and `PRIV:329` is a recipe contradicting it, the same precedence 04, 08 and 19 all applied.

**`SELF_ACTION_NOT_ALLOWED` is a fifth error code**, joining 08's four. 08 said it fixed four codes; this is an addition, not a renumbering.

### `Q22`, `Q23`, `Q24`

- **`Q22` — "Immediately active"** (`Q:552`) confirmed. `POST /users` creates a usable account with an admin-supplied password; no activation token, no pending state. `Q:556`'s recommended 7-day activation token is the workflow the map already ruled out of scope, and nothing here reintroduces it. Per `Q:564-567` and `Std:122`, the created account **is flagged `requirePasswordChange=true`** and its initial password must satisfy 04's policy — so "immediately active" means *able to log in*, not *able to do anything*: the new user's first act is a password change.
- **`Q23` — no self-service profile updates** (`Q:577`). No PRD story, and the only field worth changing is email — which `Q:584` flags as "⚠️ not recommended if email used for password reset", precisely our case (`prd:133`). Changing it safely needs a verification workflow, which is out of scope for the same reason activation is. Username is immutable regardless (`PRIV:69`, `Std:254`, `Q:573`). **Answered as a conscious decline, not left blank.**
- **`Q24` — no bulk operations** (`Q:627`). No PRD story, and the concrete candidate 01 handed here is disqualifying on its face: `PRIV:399-407`'s `batchResetPassword` returns `List<NewPasswordForBatchDTO>` — **N plaintext passwords in a single response body**. Under 11's token model that endpoint does not even have a coherent shape. Goes to the map's **Out of scope**, not Decisions so far.

  Consequence: `Std:107` and `Std:255`'s oversized-batch rules, `PRIV:484`'s 5000-entry limit and the `request body with list of more than {max} entries is not allowed` error become **vacuous** — no batch endpoint exists to exceed a limit. Recorded so 14 does not write a test for an endpoint that isn't there, and so a reviewer sees the clause was read and ruled inapplicable.

### Disable, enable, and what dies with them

Disable is a reversible in-place flag flip — `enabled=false` plus `disabled_at` (`HYG:69-72`) — and is the *only* lifecycle operation the PRD's `enabled` column drives (`prd:136`). It is categorically not delete: the row stays, the username stays taken, the user can be restored.

- **A disabled user's sessions are killed immediately.** `PRIV:262-267` fires session invalidation whenever `enabled` or roles change; `Std:114` and `:488` make it mandatory. Same for a role change — a demoted `USER_MANAGER` must not keep an authenticated session carrying the old authority. Both use 13's repository-deletion mechanism.
- **Re-enabling sets `requirePasswordChange=true`.** `Std:130` ("**must** be flagged for a mandatory password change before the user can access the application") and `:485`. 04 recorded it; the operation lives here, so it is wired into `PATCH /users/{userId}/status` on the false→true transition only.
- **A disabled user's login returns the generic `401`** (`Std:85`, `:259`, `:247`) — never a "your account is disabled" message. Enumeration resistance applies to account state, not just existence.
- **Deleting kills sessions too** (`PRIV:342-345`), inside the same transaction as the tombstone write.

### Amends

- **01** — no new endpoints; `GET /users/{userId}` (08) and `PATCH /users/{userId}/unlock` (07) already landed. `batchResetPassword` is ruled out.
- **02** — this ticket's changelog adds `deleted_users` and one column (`users.last_login_at`), plus `disabled_at`.
- **04** — `password_history` is `ON DELETE CASCADE`; the seed/create paths write its first row unchanged.
- **07** — the unlock endpoint joins the `SelfActionGuard`'s four callers.
- **08** — the interceptor constraint is refined to "no `401`/`403` **without a recognisable `code`**"; `SELF_ACTION_NOT_ALLOWED` is a fifth code; `SelfRead:80`'s annotation deletion is confirmed and extended to `:83`/`:86`.
- **11** — the token model must work for the admin-initiated flow too; `batchResetPassword` is gone, so `Std:107`'s batch clauses do not bind there either.
- **12** — events: user created, disabled, enabled, role changed, deleted (with actor **and** target, `prd:122`), plus the tombstone write. `Std:324` puts successful admin operations at `INFO`.
- **16** — a tombstoned admin username can never be re-seeded; see that ticket's startup-failure edge.
- **14** — tests for: delete writes a tombstone before removing the row and both happen atomically; re-registering a tombstoned username **and** a tombstoned email both fail; `password_history` rows vanish on delete; self-action guard returns `403` + `SELF_ACTION_NOT_ALLOWED` on all four endpoints; re-enable sets the flag; disable and role change both kill sessions; `/currentUser` never returns a hash, a lock state or a failed-attempt count; a `USER` calling `GET /users/{ownId}` gets `403`.
- The map's **React screen and route inventory** fog gains the user-detail screen 08 implied, and the self-read payload the SPA's auth context reads.
