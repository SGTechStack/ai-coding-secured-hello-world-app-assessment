# 01 — Context, topology and API surface

Type: grilling
Status: resolved
Blocked by: —
Map: [Secured Login App](../map.md)

## Question

What is this application's deployment context and topology, and what is its API surface?

Answer `Q1` (deployment context and topology), `Q31` (API base path and version) and `Q32` (login endpoint request format) of [`Standalone_User_Access_Control_Application_Standard_Questions.md`](../../../App-Standards/Appfw-User-Standards/User_Standalone/Standalone_User_Access_Control_Application_Standard_Questions.md).

Reconcile with the PRD, which fixes some of this already and leaves the rest open:

- Frontend on its own origin (`localhost:3000`), backend on its own origin (`localhost:8080`), REST API, CORS with credentials (`prd/assessment-prd.md:10`).
- The PRD names exactly one protected endpoint, `GET /api/hello` (Story 5), and an admin prefix `/api/admin/**` (Stories 8–11).
- The standard's examples use unversioned, unprefixed paths (`GET /users`, `POST /users` — `Questions.md:496`), which does not match the PRD's `/api/...`.

So the real question is whether the API base path follows the PRD's `/api` (and whether it carries a version segment), and what the login request/response contract is — form-encoded Spring Security default versus a JSON body, given the React SPA is the only client.

This ticket gates most of the map: the authorization matrix (08), HTTP security (09), audit/logging field values (12) and module structure (15) all need the path shape settled first.

## Answer

Resolved by grilling, three rounds. `Q9` and `Q10` were decided against the recommendation on this ticket — noted inline below.

### `Q1` — Deployment context and topology

- **Deployment context: Internal enterprise application.** Chosen because it is the only one of `Q1`'s three contexts whose lockout/timeout profile matches the recipe's own defaults (5 attempts, 20-minute lockout, 15-minute idle — `Standalone_Session_Login_with_CSRF_Bootstrap.md:45-51`), so tickets 07 and 13 inherit a consistent baseline. **Recorded deviation:** the PRD's self-service registration (Story 1) does not fit this context, whose accounts would normally be provisioned. Ticket 10 owns the lifecycle consequences; it is a known deviation, not an oversight.
- **Deployment topology: single instance.** The PRD excludes hosting infrastructure entirely, so claiming multi-instance would be fiction.
- **Sessions are nonetheless persisted to a JDBC store**, decoupled from topology, because the standard's Required Runtime Configuration mandates a session-state persistence backend regardless (`Standalone_User_Access_Control_Application_Standard.md:604`), and because it is what makes the PRD's "reset invalidates all sessions" criterion (`prd/assessment-prd.md:79`) assertable at all. **This pre-decides half of ticket 02** — 02 should record it rather than re-argue it.

### `Q31` — API base path and version

- **Base path: `/api/v1`. Version: `v1`.** Externalized as the `api.base-path` application property, as required by `Standalone_User_Access_Control_Application_Standard.md:597`.
- The standard requires only that the base path be *externalized*; `/api/v1` is given as an example (`:597`), so the version segment is a judgement call, not the authority order forcing it. Taken because the SPA is the only client and is built in this same effort (so the rewrite is free today but buys a versioning seam later), and because every recipe whose configuration is copied already assumes the segment — choosing `/api` would mean hand-editing each one.
- **Consequence:** every PRD acceptance-criterion path is rewritten. `GET /api/hello` becomes `GET /api/v1/hello`; `/api/admin/**` disappears entirely (see the `Q19` shape below).

### `Q32` — Login endpoint request format

**JSON only**, at `POST /api/v1/auth/login`.

- Request: `Content-Type: application/json`, body `{"username": "...", "password": "..."}`.
- Success: **`200 OK` with an empty body.** The SPA then calls `GET /api/v1/currentUser` for its identity and role. Chosen over returning the profile inline because the self-read endpoint is binding and must exist anyway, so an inline copy would be a second place every future field has to land.
- Failure: `401` with the PRD's generic message, so account existence cannot be inferred (`prd/assessment-prd.md:74`).
- Per-account rate-limit breach: `429 Too Many Requests` with `Retry-After` (`Standalone_User_Access_Control_Application_Standard.md:378`).
- The concrete error *body* shape is **not** settled here — it is the standard's §3.2 error contract and remains fog pending ticket 08.

**Implementation shape.** `Standalone_User_Access_Control_Application_Standard.md:44` states JSON support requires a custom `AuthenticationFilter`, and no recipe supplies one, so this is uncovered ground:

- **Subclass `UsernamePasswordAuthenticationFilter`**, overriding `attemptAuthentication` to parse the JSON body. Retain the recipe's `successHandler`, `failureHandler` and `SessionAuthenticationStrategy` unchanged.
- Register via `addFilterAt(..., UsernamePasswordAuthenticationFilter.class)` with `setFilterProcessesUrl("/api/v1/auth/login")`; `formLogin()` leaves the chain.
- Rejected: a plain `@RestController` calling `AuthenticationManager` directly. It reimplements rather than inherits session-ID rotation on authentication (`Standalone_Session_Login_with_CSRF_Bootstrap.md:32`), the failure handler that feeds the lockout counter (`:68`), and correct ordering relative to `CsrfFilter` — and nothing fails visibly when session-fixation protection is dropped.
- **Ticket 14 must cover this filter explicitly**: it is the one security-critical component in this build with no standards recipe behind it.

### `Q19` shape — admin endpoints are resource-oriented, not role-segmented

A forced reconciliation; the authority order resolves it **against the PRD**, since both speak.

- **Resource paths guarded by role** (`/api/v1/users/**`), as `Common_Role-Based_Access_Control_Configuration.md:40-43` literally configures — **not** the PRD's `/api/admin/**` prefix.
- **Consequence:** `prd/assessment-prd.md:160`'s test is restated as "a plain `USER` receives 403 on `GET /api/v1/users`", and ticket 08 defines the matrix per method+path rather than per prefix.

### `/auth` grouping rule: split by provenance

An `/auth` segment was added at the user's request. It diverges from the recipe wherever the standard already names a path, so the divergence is bounded by an auditable rule: **nothing the standard names was moved.**

- `/auth` holds login, logout, and the endpoints the standard has **no** path for, because it lacks the feature: self-registration (excluded by `Questions.md` `Q22`) and email-token self-service reset (the standard's reset is admin-initiated).
- Every standard-named path stays put: `/csrf`, `/currentUser`, `/currentUser/changePassword`, `/users/**`. Evidence that the standard mounts these directly under the base path: `Standalone_Privileged_User_Administration_and_Password_Reset.md:535-537` (the `PasswordChangeFilter` allowlist) and `:619,646,699` (curl examples).
- `app.security.auth.login-path` becomes `${api.base-path}/auth/login`; the `permitAll` matchers and `logoutUrl` in `Standalone_Session_Login_with_CSRF_Bootstrap.md:65,68,72` are edited accordingly. This is the full extent of the divergence.

### Endpoint inventory

Roles below are placeholders pending ticket 03; the privileged role is written `USER_MANAGER` per the map's Notes. Authorization is ticket 08's to fix — the Auth column states intent only.

| Method | Path | Source | Auth |
|---|---|---|---|
| `GET` | `/api/v1/csrf` | standard (`:535`) | public |
| `POST` | `/api/v1/auth/login` | standard (`login-path`), JSON per `Q32` | public |
| `POST` | `/api/v1/auth/register` | PRD Story 1 — no standard path | public |
| `POST` | `/api/v1/auth/password-reset/request` | PRD Story 6 — no standard path | public |
| `POST` | `/api/v1/auth/password-reset/confirm` | PRD Story 7 — no standard path | public |
| `POST` | `/api/v1/auth/logout` | standard (`logoutUrl`) | authenticated |
| `GET` | `/api/v1/currentUser` | standard (`:536`) | authenticated |
| `PATCH` | `/api/v1/currentUser/changePassword` | standard (`:537`) | authenticated |
| `GET` | `/api/v1/hello` | PRD Story 5 | authenticated |
| `GET` | `/api/v1/users` | PRD Story 8 + standard (`:699`) | `USER_MANAGER` |
| `POST` | `/api/v1/users` | standard (`:619`) — **no PRD story** | `USER_MANAGER` |
| `PATCH` | `/api/v1/users/{userId}/status` | PRD Story 9 | `USER_MANAGER` |
| `PATCH` | `/api/v1/users/{userId}/role` | PRD Story 10 | `USER_MANAGER` |
| `DELETE` | `/api/v1/users/{userId}` | PRD Story 11 (tombstone per ticket 10) | `USER_MANAGER` |
| `PATCH` | `/api/v1/users/{userId}/resetPassword` | standard (`:391`) — owner: **ticket 11** | `USER_MANAGER` |
| `PATCH` | `/api/v1/users/batchResetPassword` | standard (`:399`) — owner: **ticket 10** (`Q24` bulk ops) | `USER_MANAGER` |
| `GET` | `/api/v1/roles` | standard (`:14`, `:118`) — **added by ticket 03** | `USER_MANAGER` |

**Amendment (ticket 03).** The inventory above originally omitted `GET /api/v1/roles`. This inventory was built from the PRD's story list, and no PRD story asks for it — but the standard requires read-only access to role definitions twice (`Standalone_User_Access_Control_Application_Standard.md:14`, `:118`). [03 — Role model reconciliation](03-role-model-reconciliation.md) ruled it in scope and the row is added above; that ticket holds the reasoning and the payload shape.

Deliberately not fixed here: the `/currentUser` path string itself (`/me` vs `/profile` vs `/currentUser`) is **ticket 10's** to decide. Note for 10 — the standards contradict themselves: `Standalone_Privileged_User_Administration_and_Password_Reset.md:536` says `/currentUser`, while `Common_Secure_Self-Read_User_Endpoint.md:53` mounts the controller at `/api/v1/profile`. This inventory uses `/currentUser` provisionally.

### `Q9` — admin mutation granularity: sub-resource verbs

Decided **(b)**, matching the recommendation: `PATCH /users/{userId}/status`, `PATCH /users/{userId}/role`, `DELETE /users/{userId}`. Follows the idiom the privileged recipe itself demonstrates (`:391`), matches the PRD's "status-toggle endpoint" / "role-change endpoint" wording (`prd/assessment-prd.md:92,97`), and makes ticket 08's matrix and ticket 12's audit events one-to-one with operations — `event.action` distinguishes `user-status-change` from `user-role-change` without inspecting a request body. Rejected: a single `PATCH /users/{userId}` with a partial body, which collapses two separately-guarded, separately-audited operations behind one path.

### `Q10` — `POST /api/v1/users` is IN scope

**Decided against this ticket's recommendation.** The recommendation was to rule it out of scope under Notes authority-order #3 (standard-only surface serving no PRD story), on the grounds that its companion feature — the activation workflow that makes an admin-created account usable — is already out of scope, leaving a half-feature. The user overruled, consistent with a standing preference for fuller standards conformance.

**Consequence that must not be lost.** An admin-created account needs a defined route to being usable. Since the activation workflow stays out of scope, the only coherent stance is `Questions.md` `Q22`'s **"Immediately active — user can login with admin-provided password"**. That is the *absence* of an activation workflow rather than a reopening of it, so the map's Out-of-scope entry survives unchanged — but **ticket 10's instruction "do not reopen `Q22`" is now wrong** and has been amended: 10 must rule on immediate-activation for admin-created accounts.

### Newly surfaced

- **Forced password change is a standard feature with no PRD story.** `Standalone_Privileged_User_Administration_and_Password_Reset.md:531-539` describes a `requirePasswordChange` flag and a `PasswordChangeFilter` that blocks every endpoint except `/csrf`, `/currentUser` and `/currentUser/changePassword`. It interacts directly with `Q10`'s outcome — an admin-supplied password is exactly the case that warrants forcing a change. **Ticket 04 has been amended** to rule it in or out.
- **Ticket 14 owes tests for the custom JSON `AuthenticationFilter`** — see `Q32` above.
