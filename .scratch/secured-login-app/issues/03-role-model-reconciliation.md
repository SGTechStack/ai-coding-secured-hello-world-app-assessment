# 03 — Role model reconciliation

Type: grilling
Status: resolved
Blocked by: —
Map: [Secured Login App](../map.md)

## Question

What is this application's role model — and what happens to the PRD's `ADMIN` role?

This is a **forced reconciliation**: the map's authority order puts the Appfw standard above the PRD wherever both speak, and both speak here.

- PRD: roles are an enum of `USER` and `ADMIN` (`prd/assessment-prd.md` data model), with `ADMIN` gating `/api/admin/**`.
- Standard: the default roles are `USER` and **`USER_MANAGER`** (`Questions.md:421`), and role definitions are **configuration-owned** — "Role definition configuration is the source of truth. Roles cannot be created or modified through API endpoints" (`Questions.md:431`).

Answer `Q17` (roles beyond the defaults), `Q17a` (can a user hold multiple roles simultaneously — one-to-one versus one-to-many, which changes the data model and every authorization check) and `Q17b` (default role assignment and modification strategy) against [`Common_Role-Based_Access_Control_Configuration.md`](../../../App-Standards/Appfw-User-Standards/Shared_Recipes/Common_Role-Based_Access_Control_Configuration.md).

Then settle the rename explicitly, because it ripples: the `users.role` column, the admin endpoints, the seeded bootstrap account (16), Spring Security authority strings, every admin story's acceptance criteria, and the test assertions.

Registration must still produce a baseline `USER` (Story 1), so `Q17b`'s "no default role" options conflict with the PRD's self-service registration — resolve that here too.

Confirm the recorded nuance holds: defining roles is config-owned, but *assigning* a role to a user via the admin endpoint (Story 10) remains allowed.

This ticket gates the authorization matrix (08), account lifecycle (10) and admin bootstrap (16).

## Answer

Resolved by grilling, two rounds, nine questions, all agreed. Two decisions here were **not** anticipated by the ticket and were surfaced while reading the standard: the role-definition storage question (`Q4` below) and the missing role-read endpoint (`Q6`).

### `Q17` — The role set: `USER` and `USER_MANAGER`, nothing more

`ADMIN` → **`USER_MANAGER`**, and `Q17`'s additional-roles table is **empty**.

The map's authority order forces the rename: the standard's defaults are `USER` and `USER_MANAGER` (`Standalone_User_Access_Control_Application_Standard_Questions.md:421`) against the PRD's `USER`/`ADMIN` enum (`prd/assessment-prd.md:135`). It is safe to force because it is a **pure rename, not a semantic change** — the PRD's own description of the role, "can manage other accounts" (`prd/assessment-prd.md:29`), is precisely `USER_MANAGER`'s job description. No third role, because all 12 PRD stories are served by "account holder" and "manages other accounts".

**Ripples, so nobody re-derives them:** `users.role` column values; the Spring authority string `ROLE_USER_MANAGER`; the seeded bootstrap account (ticket 16); the acceptance criteria of Stories 8–11; and `prd/assessment-prd.md:160`'s test, already restated by ticket 01 as "a plain `USER` receives 403 on `GET /api/v1/users`".

### `Q17a` — Exactly one role per user; scalar column

**No** — a user holds exactly one role. `users.role` stays a single non-null column; there is no `user_roles` join table.

The PRD assumes this (`role` is a scalar enum, `:135`; Story 10 is a *change between* values, `:95`, not a grant/revoke), and `Q17a` offers single-role as a first-class choice, so this is a selection rather than a deviation — notwithstanding the standard's consistently plural phrasing (`Standalone_User_Access_Control_Application_Standard.md:149`, `:77`), which belongs to the general case.

The decisive argument is **representational**: with a closed two-role set, a join table's only reachable states are `{USER}`, `{USER_MANAGER}` and `{USER, USER_MANAGER}` — and that third state is exactly what the role hierarchy (`Q7`) declares in configuration. Multi-role would let one fact live in two places, the database and the hierarchy, where they can disagree. Single-role also makes Story 10's `PATCH /users/{userId}/role` a total assignment, so "an admin cannot demote themselves" (`prd/assessment-prd.md:98`) is one comparison, not a set-difference.

**Recorded one-way door:** a third role needing *combinations* would require a migration to a join table. Accepted deliberately, not overlooked.

### `Q17b` — Default to `USER`, modifiable anytime after creation — read per creation path

`Q17b`'s *recommended* option ("Admin selects during creation, required field") **cannot be chosen**: it presumes admin-provisioned accounts and cannot express self-service registration (Story 1), where no admin is in the loop. Selected instead: **"Default to `USER` role, modifiable anytime after creation"**, which `Q17b` offers explicitly — a selection, not a deviation.

This app has two creation paths and the rule reads differently on each:

| Path | Role behaviour |
|---|---|
| `POST /api/v1/auth/register` (Story 1, public) | Hard-codes `USER`. **Does not read a role from the request body** — a client-supplied role on a public endpoint is privilege escalation. |
| `POST /api/v1/users` (standard `:619`, in scope per ticket 01 `Q10`) | A `USER_MANAGER` may select the role; defaults to `USER` when omitted. |

Both are then modifiable via Story 10's `PATCH /users/{userId}/role`.

**Consequences:** `users.role` is `NOT NULL`, so zero-role accounts are unrepresentable. Default-deny is therefore satisfied by `anyRequest().denyAll()` at the end of the filter chain (`Common_Role-Based_Access_Control_Configuration.md:83`), **not** by rolelessness.

### `Q4` (new) — Role definitions get a minimal read-only `roles` table

Not anticipated by the ticket. The standard makes it an *enforced constraint* that "user account records **and role definitions** are persisted in a relational database" (`Standalone_User_Access_Control_Application_Standard.md:407`) and that the app "creates or syncs configured roles from the role definition" on startup (`:41`) — yet the map ruled the Automatic DB Role Synchronization recipe out of scope, and that recipe is where the `roles` table, `DeletedRole` archive and sync flags live.

**Resolved: a minimal read-only `roles` table, seeded from `application.yml` at startup, with `users.role` referencing it.** This is the narrowest reading that keeps `:407` intact — `:415` requires *both* ("role provisioning is configuration-driven, while user records are database-driven"), which a config-only model cannot deliver. It also gives `:424`'s mandated test ("duplicate role definitions fail fast at startup") a real place to fail.

**The out-of-scope boundary, sharpened:** what stays out is specifically the *archive-on-removal* (`DeletedRole`) machinery and the `sync-db-users` reset flag. Those earn their keep only when roles churn, and `Q17` fixed the role set as closed. The map's Out-of-scope entry is unchanged in substance; this ticket narrows what it meant.

### `Q5` — The filter chain guards on role authorities directly; no privilege layer

`url-guards` key on **`ROLE_USER_MANAGER` / `ROLE_USER`**, not on synthesized privileges.

The RBAC recipe's example keys `url-guards` on authorities like `USER_READ`/`USER_WRITE` (`Common_Role-Based_Access_Control_Configuration.md:40-43`, called via `hasAuthority` at `:88`), but `Q18` states outright that "if your application doesn't need fine-grained privilege abstraction, you can define roles without explicit privileges" — so this is sanctioned, and consistent with the map's out-of-scope ruling on the fine-grained privileges model (`Questions.md:485`).

Rejected: a thin privilege layer purely to match the recipe's literal shape. With exactly one role holding every write, the privilege set would be a bijection with the role set — an indirection that never varies.

```yaml
app:
  security:
    role-mappings:
      USER_MANAGER: [ROLE_USER_MANAGER]
      USER: [ROLE_USER]
    role-hierarchy: "ROLE_USER_MANAGER > ROLE_USER"
```

Ticket 08 fills in the `url-guards` rows; this ticket fixes only the **string form** they use.

### `Q7` — `USER_MANAGER` inherits `USER` via role hierarchy

**`role-hierarchy: "ROLE_USER_MANAGER > ROLE_USER"`.**

This is not cosmetic. With one role per user (`Q17a`) under deny-by-default, a `USER_MANAGER` holds only `ROLE_USER_MANAGER` and would receive **403 on `GET /api/v1/hello`** — and could not read `GET /api/v1/currentUser`, breaking the login bootstrap ticket 01 fixed (`GET /csrf` → `POST /auth/login` → `GET /currentUser`) for the very account ticket 16 seeds first.

The recipe calls hierarchy a "Best Practice", supplies the bean (`:53-60`), and its own verification step is literally this case: "verify an `ADMIN` can access `USER_READ` endpoints even if not explicitly assigned the role" (`:120`).

Rejected: listing every shared endpoint under both roles in `url-guards`. That expresses the same policy by duplication, and it fails **quietly and in the dangerous direction** — someone adds an authenticated endpoint, grants it to `USER`, and admins silently lose access with no test to catch it.

**Consequence for ticket 08:** the matrix needs a third row class. `authenticated` rows (`/hello`, `/currentUser`, `/currentUser/changePassword`, `/auth/logout`) guard on `ROLE_USER`; `USER_MANAGER` reaches them **through the hierarchy**, never by enumeration.

### `Q8` — `roles` table keyed by name; `GET /roles` returns names only; `role` is a validated `String`

- **Natural primary key on the role name** (`roles.name VARCHAR PK`, `users.role` FK). `users.role` therefore reads as `USER_MANAGER` in the database, so audit lines (ticket 12) and manual queries stay legible without a join. The usual case for a surrogate key — names change — does not apply to a closed, config-owned set.
- **`GET /api/v1/roles` returns role names only** (`["USER","USER_MANAGER"]`), not their authority mappings. Its one live consumer is Story 10's role-change control, which needs assignable *values*; publishing the guard vocabulary to a client that cannot act on it is needless disclosure.
- **In Java, `role` is a `String` validated against the loaded role set — not a compile-time `enum`.** A Java `enum` would make code, not configuration, the source of truth (`Questions.md:431`), since adding a role would demand a rebuild. This overrides the PRD's `enum` wording at `prd/assessment-prd.md:135`.

### `Q6` (new) — `GET /api/v1/roles` is IN scope; ticket 01's inventory was missing it

Not anticipated by the ticket, and a genuine gap: the standard twice requires read access to role definitions — "expose HTTP APIs for … read-only access to role definitions" (`Standalone_User_Access_Control_Application_Standard.md:14`) and "read access to role definitions is permitted only for authorized roles" (`:118`) — but ticket 01's endpoint inventory has no such endpoint, because 01 worked from the PRD's story list and no PRD story asks for it.

**Resolved: add `GET /api/v1/roles`, guarded `USER_MANAGER` (ticket 08 confirms).** Ticket 01's inventory is amended accordingly.

Rejected: ruling it out of scope as a standard-only subsystem. That reasoning removed the *scheduled hygiene jobs* — a whole subsystem with a scheduler, batch policies and 180-day timers. This is one read-only GET over configuration already held in memory, against an explicit `:14` requirement, and it has a live consumer in Story 10's role dropdown.

**No role-write endpoint exists**, so the standard's required `403` on attempted role mutation (`:250`, `:425`) falls out of `anyRequest().denyAll()` for free. It is recorded as a **test row for ticket 14**, not as an endpoint or a handler.

### `Q9` — Self-action guard is broad; plus an explicit last-manager invariant

The standard lists three self-action rejections — unlock your own account (`:101`), change your own roles (`:102`), delete your own account (`:105`) — but items `:97`–`:104` are scoped to *"the generic admin update flow"*, i.e. a single `PATCH /users/{id}`. Ticket 01 chose dedicated sub-resource endpoints instead (`Q9`), so these rules must be **re-hosted** onto them. Note that **neither the standard nor the PRD forbids disabling your own account.**

**Resolved — broad guard:** every `/api/v1/users/{userId}/**` mutation and `DELETE /api/v1/users/{userId}` rejects `userId == caller`, **including `PATCH /status`**. The standard's silence on self-disable is a gap, not permission: a `USER_MANAGER` disabling their own account is an unrecoverable self-lockout short of a direct database edit. One comparison in a shared place, not three scattered ones.

**Separately asserted invariant: the application always retains at least one enabled `USER_MANAGER`.** The broad self-guard *implies* this for the ordinary two-manager case, but not universally — it does not cover manager A demoting the last other manager while A's own account has already been disabled by a third party. It is therefore checked directly rather than relied upon as a corollary. **Tickets 10 and 16 consume this invariant** (lifecycle transitions and bootstrap seeding respectively).

**Status codes:**

| Condition | Status | Why |
|---|---|---|
| Self-action on `/users/{userId}/**` or `DELETE` | **`403 Forbidden`** | `:243` — "authorization failures must map to `403`". This is a decision about the actor-target relationship, which is authorization. |
| Would leave zero enabled `USER_MANAGER` | **`409 Conflict`** | A state conflict, not a permission failure: the same caller succeeds once another manager exists. `403` would say "you may not" when the truth is "not yet". |

Both carry a stable machine-readable error code per `:244`. The concrete body shape remains the map's open **Error contract shape** item, owned downstream of ticket 08.

### What this ticket hands to its dependents

- **Ticket 08 (authorization matrix)** — unblocked by this closure. Guard strings are `ROLE_USER_MANAGER` / `ROLE_USER`; needs an `authenticated` row class resolved via hierarchy, plus a row for `GET /api/v1/roles`.
- **Ticket 16 (admin bootstrap)** — unblocked by this closure. Seeds a `USER_MANAGER`; must satisfy the at-least-one-enabled-manager invariant.
- **Ticket 10 (account lifecycle)** — still blocked by 04. Inherits the broad self-guard, the `409` last-manager conflict, and the tombstone interaction with `users.role`'s FK.
- **Ticket 14 (test plan)** — owes rows for: duplicate role definitions failing fast at startup (`:424`), `403` on role mutation attempts (`:425`), hierarchy inheritance (`USER_MANAGER` reaching `GET /hello`), registration ignoring a body-supplied role, and both self-action and last-manager rejections.
