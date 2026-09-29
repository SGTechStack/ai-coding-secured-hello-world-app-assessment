# 08 — Authorization matrix

Type: grilling
Status: resolved
Blocked by: 01, 03
Map: [Secured Login App](../map.md)

## Question

Which roles may call which endpoints, and how is that enforced?

Answer `Q19` (the role-to-endpoint authorization matrix), `Q20` (endpoints requiring authentication but no specific role) and `Q21` (publicly accessible endpoints) of the standard's question set, against [`Common_Role-Based_Access_Control_Configuration.md`](../../../App-Standards/Appfw-User-Standards/Shared_Recipes/Common_Role-Based_Access_Control_Configuration.md).

Produce the full matrix — every endpoint the PRD's 12 stories imply, each with method, path, and the role(s) permitted:

- Public (`Q21`): register, login, password-reset-request, password-reset-confirm. Each is public *and* state-changing, which collides with the CSRF requirement — flag anything that needs 09 to resolve.
- Authenticated, no specific role (`Q20`): `GET /api/hello`, logout, and the self-read `/me` endpoint if 10 rules it in.
- Role-gated (`Q19`): the admin endpoints of Stories 8–11, plus admin unlock if 07 adds it.

Settle enforcement mechanism too: URL-path rules in the `SecurityFilterChain` versus method-level annotations, and whether the standard's config-owned model permits a mix. The standard is explicit that authorization is enforced by roles, not privileges (`Questions.md:471`).

Two acceptance criteria must fall out of this matrix directly: a `USER` calling any `/api/admin/**` endpoint gets **403** (Story 8), and an unauthenticated call to `GET /api/hello` gets **401** (Story 5) — not 403, and not a redirect to a login page, which is what Spring Security's defaults would do for a browser client.

Blocked on 01 (path shape) and 03 (role names).

**Amended by [04 — Password policy and history](04-password-policy-and-history.md).** 04 ruled the **forced password change** in scope, which inserts a second authorization mechanism in front of the matrix this ticket defines. The matrix must account for it explicitly rather than describing role guards alone.

- **The `PasswordChangeFilter` runs after authentication and before the matrix.** A user with `requirePasswordChange=true` is rejected `403` on every path except four, regardless of role: `GET /api/v1/csrf`, `GET /api/v1/currentUser`, `PATCH /api/v1/currentUser/changePassword`, `POST /api/v1/auth/logout`. A `USER_MANAGER` in that state has no more access than a `USER` in that state. The matrix therefore has a **precondition column or a stated precedence rule**, not just role-per-method-per-path.
- **The four allowlisted paths need their own rows**, since they are the only ones reachable in that state.
- **The public rows need no filter entry** — the filter's `auth != null` guard skips unauthenticated requests. This is deliberate and 04 relies on it: it is what lets a flagged user reach `POST /api/v1/auth/password-reset/confirm`, which clears the flag.
- **Password-policy rejections may carry a specific error.** 04 settled an apparent conflict this ticket would otherwise have to re-open: `Standalone_User_Access_Control_Application_Standard.md:110` requires "a specific error … indicating which rule was violated", while `:125`/`:247` require identical bodies, statuses and timing. `:247` enumerates its own scope — "invalid credentials, locked account, non-existent user, disabled account", all *authentication* outcomes — and a policy violation is not among them and reveals nothing about who exists. Treat this as settled.
- 04 leaves this ticket the status code and body for a **failed current-password check** on `PATCH /currentUser/changePassword`. Note it is not an enumeration surface: the caller is already authenticated and the account is their own.

## Answer

Resolved by grilling, two rounds, seventeen questions, all agreed. Five facts were verified against the jars 15's pin resolves to rather than asserted from the documents; three of them changed a decision, and one conflict I expected to find turned out not to exist.

Short forms below: `Std` = `Standalone_User_Access_Control_Application_Standard.md`, `Q` = its question set, `RBAC` = `Common_Role-Based_Access_Control_Configuration.md`, `Priv` = `Standalone_Privileged_User_Administration_and_Password_Reset.md`, `SelfSvc` = `Standalone_Self-Service_Password_and_History_Management.md`, `SelfRead` = `Common_Secure_Self-Read_User_Endpoint.md`, `Bootstrap` = `Standalone_Session_Login_with_CSRF_Bootstrap.md`.

### The matrix (`Q19`, `Q20`, `Q21`)

One flat, ordered list, read top to bottom, **first match wins**.

**Tier 0 — the `PasswordChangeFilter`, which is not a row.** It runs before the matrix (`Priv:611`: `http.addFilterBefore(passwordChangeFilter, AuthorizationFilter.class)`). Allowlist is 04's four paths: `GET /csrf`, `GET /currentUser`, `PATCH /currentUser/changePassword`, `POST /auth/logout`. Anything else from a flagged user is `403 PASSWORD_CHANGE_REQUIRED` and **no matrix row is consulted**.

| # | Method | Path | Access | Source / note |
|---|---|---|---|---|
| 1 | `GET` | `/api/v1/csrf` | `permitAll` | `Q21`; also a tier-0 allowlist entry — not redundant, see below |
| 2 | `POST` | `/api/v1/auth/login` | `permitAll` | `Q21`; CSRF collision → **09** |
| 3 | `POST` | `/api/v1/auth/register` | `permitAll` | `Q21`; CSRF collision → **09** |
| 4 | `POST` | `/api/v1/auth/password-reset/request` | `permitAll` | `Q21`; CSRF collision → **09** |
| 5 | `POST` | `/api/v1/auth/password-reset/confirm` | `permitAll` | `Q21`; CSRF collision → **09**; clears the flag (04) |
| 6 | *any* | `/error` | `permitAll` | backstop only; see "The `/error` dispatch" |
| 7 | `GET` | `/api/v1/roles` | `hasRole('USER_MANAGER')` | `Q19`; added by 03 (`Std:14`, `:118`) |
| 8 | `PATCH` | `/api/v1/users/batchResetPassword` | `hasRole('USER_MANAGER')` | `Q19`; literal segment ordered before the capture |
| 9 | `GET` | `/api/v1/users` | `hasRole('USER_MANAGER')` | `Q19`; Story 8's `403` test lands here |
| 10 | `POST` | `/api/v1/users` | `hasRole('USER_MANAGER')` | `Q19`; in scope per 01's `Q10` |
| 11 | `GET` | `/api/v1/users/{userId}` | `hasRole('USER_MANAGER')` | `Q19`; **added by this ticket** |
| 12 | `PATCH` | `/api/v1/users/{userId}/status` | `hasRole('USER_MANAGER')` | `Q19`; self-action → `403` (03) |
| 13 | `PATCH` | `/api/v1/users/{userId}/role` | `hasRole('USER_MANAGER')` | `Q19`; self → `403`; last manager → `409` (03) |
| 14 | `DELETE` | `/api/v1/users/{userId}` | `hasRole('USER_MANAGER')` | `Q19`; self → `403` (03, `Std:474`) |
| 15 | `PATCH` | `/api/v1/users/{userId}/resetPassword` | `hasRole('USER_MANAGER')` | `Q19`; sets the flag (04) |
| 16 | *any* | `/api/v1/users/**` | `hasRole('USER_MANAGER')` | **backstop**; see "Pattern granularity" |
| 17 | `GET` | `/api/v1/currentUser` | `authenticated` | `Q20` |
| 18 | `PATCH` | `/api/v1/currentUser/changePassword` | `authenticated` | `Q20` |
| 19 | `POST` | `/api/v1/auth/logout` | `authenticated` | `Q20` |
| 20 | `GET` | `/api/v1/hello` | `hasRole('USER')` | `Q20` → `Q19`; the one hierarchy-bearing row |
| 21 | *any* | *any* | `denyAll()` | closes `/actuator/**`, Swagger, and everything unlisted |

Both of the ticket's stated acceptance criteria fall out directly: a plain `USER` on row 9 gets `403` (Story 8, as 01 restated `prd/assessment-prd.md:160`), and an unauthenticated call to row 20 gets `401` — but only because of the entry-point ruling below, which is the single most consequential finding here.

### Enforcement mechanism: the URL matrix is the only authorization mechanism

**No `@EnableMethodSecurity`, and every recipe `@PreAuthorize` is deleted rather than rewritten.**

The binding set contradicts itself and the clause list wins. On the config side: `Std:599` makes a "role-based authorization matrix source (maps roles to allowed HTTP methods and paths)" a *Required Runtime Configuration* item; `Std:413` is a Design Choice that "HTTP methods and resource paths define the authorization contract boundary"; `Std:427` is a test clause that each HTTP method is independently authorized; and `RBAC` is entirely `authorizeHttpRequests` configuration. On the annotation side there is exactly one source, `Std:59` — an italic *Spring Boot:* implementation note, not a clause. The map's standing rule from 05 (*the recipes are guides; the standard is the clause list*) settles it.

**The decisive fact is that the recipes' annotations are unusable, not merely optional.** Five annotations across three binding recipes name authorities that do not exist in this application:

| Recipe | Annotation |
|---|---|
| `Priv:124` | `@PreAuthorize("hasRole('USERS_CREATE')")` |
| `Priv:196`, `:392`, `:400` | `@PreAuthorize("hasRole('USERS_UPDATE')")` |
| `Priv:296` | `@PreAuthorize("hasRole('USERS_DELETE')")` |
| `SelfSvc:57` | `@PreAuthorize("hasRole('CURRENT_USER_UPDATE')")` |
| `SelfRead:80` | `@PreAuthorize("hasAuthority('SELF_READ')")` |

03 fixed the role set at exactly `USER` / `USER_MANAGER` with **no privilege layer** (`Q:482` sanctions this outright). So `ROLE_USERS_CREATE`, `ROLE_CURRENT_USER_UPDATE` and `SELF_READ` are unresolvable, and copying any of them yields `403` for every caller, silently. These annotations smuggle back the privilege layer 03 deliberately deleted — they are not a second opinion about mechanism, they are a leak from a different role model.

The relationship rules stay where 03 put them: **service-layer domain invariants**, not authorization. The self-action `403`, the last-manager `409`, and the self-read scoping by principal are all decisions about the actor-target relationship, which `@PreAuthorize` could not express against a path variable without re-deriving the caller anyway.

**The cost, accepted:** one mechanism means a controller reachable by a path with no matrix row is unguarded by omission. It fails *closed* (`denyAll`), so it is a functional break rather than a security hole, and the ArchUnit row below catches it before a test ever runs.

### Row classes: the shared endpoints split in two

`Q:510-517` posits a class of endpoints requiring authentication but no specific role. 03 instead ruled that these guard on `ROLE_USER`, with `USER_MANAGER` reaching them through `role-hierarchy: "ROLE_USER_MANAGER > ROLE_USER"` — and that was 03's entire justification for the hierarchy existing.

**A consequence 03 did not see: if every shared row is `authenticated()`, the role hierarchy becomes dead configuration.** No path in the inventory would then be guarded on `ROLE_USER` while being needed by a `USER_MANAGER`, so nothing inherits anything and the hierarchy bean is inert.

Resolved by splitting the class, which is a refinement of 03 rather than a reversal:

- **Platform and self endpoints — rows 17, 18, 19 — are `authenticated()`.** `GET /currentUser` is the endpoint through which a principal discovers *what it is*; it is step 3 of 01's bootstrap. Gating self-discovery on holding a particular role is a category error, and it would mean any future role not granted `ROLE_USER` could not bootstrap the SPA at all. The same reasoning covers `/csrf` and `/logout`: platform mechanics, not business resources.
- **The one business endpoint in the class, `GET /api/v1/hello` (row 20), is `hasRole('USER')`.** This keeps the hierarchy load-bearing on exactly one row rather than zero, which gives `RBAC:114`'s own verification step ("verify an `ADMIN` can access `USER_READ` endpoints even if not explicitly assigned the role") a real case, and gives 14 a test that fails if the hierarchy is ever dropped.

03's stated consequence — "`authenticated` rows guard on `ROLE_USER`" — therefore holds for `/hello` and is narrowed for the other three.

### `401` for unauthenticated: the chain as specified returns `403`, and this ticket's acceptance criterion would have failed

**Verified, not inferred.** `ExceptionHandlingConfigurer.createDefaultEntryPoint` in `spring-security-config-7.0.6`:

```
1: getfield  defaultEntryPoint:...DelegatingAuthenticationEntryPoint$Builder;
4: ifnonnull 15
7: new       org/springframework/security/web/authentication/Http403ForbiddenEntryPoint
```

`defaultEntryPoint` is populated only by `formLogin` / `httpBasic` — and **01 removed `formLogin()`** in favour of the custom JSON `AuthenticationFilter`, which registers no entry point. So the field is null, the default is `Http403ForbiddenEntryPoint`, and every unauthenticated request returns `403`. No recipe in the binding set configures `exceptionHandling` at all (`Bootstrap:64-66` and `RBAC:75-94` both omit it), so nothing in the sources would have caught this.

**Resolved: configure it explicitly.**

```java
.exceptionHandling(e -> e
    .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
    .accessDeniedHandler(jsonAccessDeniedHandler))
```

This is uncovered ground in the same sense 01's JSON filter is — a required behaviour with no recipe behind it — so 14 owes it an explicit test rather than inheriting one.

The split it encodes is consistent across the standard: **`401` means no valid session** (`Std:82`, `:258`, `:259`, `:438`), **`403` means authenticated but denied** (`Std:58`, `:88`, `:244`, `:429`). One setting fixes the whole unauthenticated class rather than just `/hello`, because `denyAll()` throws `AccessDeniedException` and `ExceptionTranslationFilter` routes that to the *entry point* rather than to a 403 whenever the authentication is anonymous.

**The `401` body is deliberately empty.** `HttpStatusEntryPoint` writes a status and nothing else, which is the maximally generic response `Std:247` asks for — there is no error code to leak and nothing to distinguish "no session" from "expired session" from "unknown path".

### The `/error` dispatch, and why the filter must not call `sendError`

**Verified.** `AuthorizationFilter`'s constructor in `spring-security-web-7.0.6` sets `filterErrorDispatch = true`:

```
27: aload_0
28: iconst_1
29: putfield  filterErrorDispatch:Z
```

and `skipDispatch` skips an `ERROR` dispatch only when that flag is false. So Spring Boot's internal forward to `/error` **is** authorized like any other request. Under `anyRequest().denyAll()`, `/error` matches no row, the denial fires, and a genuine `400` or `500` reaches the client as a `403`.

This lands on 04's filter too. `Priv:599` closes the forced-change block with `response.sendError(SC_FORBIDDEN, "Password must be changed on first login")` — and `sendError` *is* an error dispatch, so the recipe's own filter routes straight into the trap. Independently, `sendError` cannot carry the machine-readable code that `Std:51` and `:165` require and that the SPA must distinguish from an ordinary authorization denial.

**Resolved, in two parts:**

1. **Row 6 exists**, `permitAll` on `/error`, recorded as a deliberate row rather than an oversight. It is unreachable as a direct request because nothing routes there, and the global exception handler that `Std:326` already mandates handles the normal path — `/error` is a backstop. Set `server.error.whitelabel.enabled=false` alongside it.
2. **The `PasswordChangeFilter` never calls `sendError`.** It writes its status and JSON body directly, carrying `PASSWORD_CHANGE_REQUIRED`. A recorded deviation from `Priv:599`, compelled by `Std:51`/`:165`.

Rejected: `filterAllDispatcherTypes(false)`, which disables error-dispatch authorization globally — a broad change to fix a narrow problem.

**A third defect closed by the same fix, found while checking the CSRF path.** The fallback `AccessDeniedHandlerImpl` also calls `response.sendError(...)`, so *not* setting a custom handler would have routed every `403` — authorization and CSRF alike — into this same trap. The entry-point ruling above and this one are one fix, not two.

### Pattern granularity: the recipe's `*` is wrong, and worse than it looks

`RBAC:43` configures `PATCH: /api/v1/users/*`. Run against the real parser (`spring-web-7.0.8`, matching 15's Spring Framework 7 pin):

```
/api/v1/users/**  vs /api/v1/users                     -> true
/api/v1/users/**  vs /api/v1/users/abc/status          -> true
/api/v1/users/*   vs /api/v1/users/abc                 -> true
/api/v1/users/*   vs /api/v1/users/abc/status          -> false
/api/v1/users/*   vs /api/v1/users/batchResetPassword  -> true
/api/v1/users     vs /api/v1/users/                    -> false
```

`PathPattern`'s `*` matches within a **single** segment. So the recipe's pattern grants a manager the *batch password reset* endpoint while denying `/{userId}/status`, `/{userId}/role` and `/{userId}/resetPassword` — three of the five sub-resource rows. Copied verbatim, Story 9 and Story 10 return `403` to the very role that owns them.

**Resolved: explicit rows plus a backstop.**

- Explicit per-method, per-path rows (7–15), which is what `Std:427` asks for and what keeps the matrix one-to-one with 12's audit events.
- A terminal `/api/v1/users/**` row (16) before `denyAll()`. Verified above to cover `/api/v1/users` itself, so it needs no companion. It costs nothing and guarantees that no future `/users/**` sub-resource is ever reachable by a plain `USER` through a forgotten row; a forgotten row still fails closed for managers, which 14 catches.
- **A standing rule: no bare `*` segments anywhere in the matrix.** Named captures like `{userId}` are single-segment by construction and are what the rows use. The ban is on the shape that fails silently, not on path variables.

**Trailing slashes and undeclared methods both fail closed, and both are accepted.** `GET /api/v1/users/` misses row 9 and falls to the backstop, so managers are unaffected and a `USER` still gets `403`. `GET /api/v1/hello/` matches nothing and returns `401`/`403` instead of reaching the endpoint — a client-facing nuisance whose correct fix is on the client, since the SPA's axios base URL emits canonical paths. `DELETE /api/v1/hello` returns `403` rather than `405`, which is a *feature* under `Std:247`'s enumeration-resistance posture: `405` tells a prober which methods exist on a path. Rejected: `/**`-suffixed variants of every row (doubles the matrix to paper over a client bug) and re-enabling trailing-slash matching globally (deprecated in Spring Framework 6+, and it would silently widen every row).

### `GET /api/v1/users/{userId}` is added to 01's inventory

The same shape of gap 03 found with `GET /roles`. `Q:498` lists `GET /users/{id}` — "View user details" — inside `Q19`'s own example matrix, and `Std:414` is a Design Choice that "administrators access full user records; regular users access only their own record through a dedicated endpoint". No PRD story asks for it, so 01's inventory, built from the story list, has no such row.

Honest counter-case, recorded: `Q:498` is an *example*, not a clause, and `GET /users` arguably already discharges `:414`.

**Resolved: in scope, guarded `USER_MANAGER` (row 11).** Two reasons beyond conformance. A manager about to `PATCH /users/{userId}/status` or `/role` needs to read that user's current state, and the alternative — `GET /users` returning every field of every user — is a *worse* disclosure posture than a list projection plus a detail read. And it is consistent with 01's `Q10` ruling, which ruled `POST /users` in on the same standing preference for the fuller standards scope.

**The disclosure consequence, stated so it is not softened later:** `Std:429` is explicit that "all user management endpoints reject non-administrators with `403 Forbidden`, **including requests on their own account**". A `USER` calling `GET /users/{ownId}` gets `403`. This row is not a self-read shortcut and must never become a second one; the self-read path stays `/currentUser`.

### Config shape: one ordered list, not the recipe's map

`authorizeHttpRequests` is first-match-wins, and the matrix now has ordered tiers, so order is load-bearing. The recipe's shape cannot express it: `RBAC:38-43` is `url-guards: {AUTHORITY: [{METHOD: path}]}`, a map keyed by authority whose row order is an accident of YAML key order, and `RBAC:87` applies the whitelist **after** the guards, so any whitelisted path that also matches a guard is denied rather than permitted. Both are ordering bugs waiting to happen.

**Resolved: `app.security.authorization-matrix`, a flat sequence of `{method, path, access}` rows read top-to-bottom into the chain in file order**, where `access` is one of `permitAll` / `authenticated` / `hasRole:USER` / `hasRole:USER_MANAGER`. This is literally what `Std:599` describes, it makes precedence visible in the file instead of emergent from map iteration, and it makes the table above and the YAML the same artifact. `role-mappings` and `role-hierarchy` stay exactly as 03 wrote them.

```yaml
app:
  security:
    role-mappings:
      USER_MANAGER: [ROLE_USER_MANAGER]
      USER: [ROLE_USER]
    role-hierarchy: "ROLE_USER_MANAGER > ROLE_USER"
    authorization-matrix:
      - { method: GET,   path: "${api.base-path}/csrf",  access: permitAll }
      # ... rows 2-19 in the order of the table above ...
      - { method: GET,   path: "${api.base-path}/hello", access: "hasRole:USER" }
    forced-change-allowlist:
      - { method: GET,   path: "${api.base-path}/csrf" }
      - { method: GET,   path: "${api.base-path}/currentUser" }
      - { method: PATCH, path: "${api.base-path}/currentUser/changePassword" }
      - { method: POST,  path: "${api.base-path}/auth/logout" }
```

Recorded as a deviation from `RBAC:38-43` and `:87`, on the ground that the recipe's shape cannot express order.

**Property naming: `pathsForAuthenticatedUsers` and `pathsForWhitelisting` are both dropped.** `Q:517` and `Q:529` name them and `Priv:548`/`:560` implements the second, but under a flat list they are redundant — a public row *is* `access: permitAll`.

**The filter gets its own list, deliberately not derived from the matrix.** `Priv:560-562` builds the filter's allowlist *from* `pathsForWhitelisting` plus two constants. That coupling is wrong here twice over. 04's allowlist and the matrix's public set are different sets by design — the filter's `auth != null` guard already skips unauthenticated requests, so the public rows need no entry, while `POST /auth/logout` needs one and is not public. And coupling them would mean that adding a public endpoint silently widens the forced-change hole. Two lists, because they answer two questions.

**`Priv:561-562`'s `DEFAULT_SWAGGER_WHITELIST` and `DEFAULT_AUTH_WHITELIST` are not adopted.** There is no OpenAPI dependency in 15's set; inheriting a whitelist constant for paths that do not exist is how a matrix acquires holes.

### Precedence: the filter is tier 0, and the matrix never runs behind it

This is the "precondition column or stated precedence rule" 04 demanded. `Priv:611` settles the mechanism but not the policy: `addFilterBefore(passwordChangeFilter, AuthorizationFilter.class)` puts the filter ahead of the matrix unconditionally. So a flagged `USER` calling `GET /api/v1/users` is rejected by the *filter* with `PASSWORD_CHANGE_REQUIRED`; the matrix is never consulted and the authorization denial never happens.

**Resolved: a stated precedence rule, no precondition column.** The matrix reads *tier 0 first; if it rejects, no row is consulted*. What that discloses is the flag state, which the caller already knows from `GET /currentUser`, while withholding the authorization outcome — strictly less leakage than the alternative. Rejected: running the matrix first, which would need the filter moved after `AuthorizationFilter`, where it protects nothing the matrix has not already denied and directly contradicts `Priv:611`.

**A hole in the audit trail, named here rather than left for 14 to discover.** Because tier 0 preempts, a flagged account probing admin paths produces `password-change-enforcement` / `failure` lines (`Priv:595-596`) and **no `403 Forbidden` authorization-failure lines at all** — so `Std:325`'s authorization-failure logging and 18's authorisation-denial `source.ip` class do not fire for flagged accounts. 12 owns the consequence.

**Row 1 and the tier-0 allowlist are not redundant.** `GET /csrf` is `permitAll` in the matrix *and* an allowlist entry, because the filter only ever sees it when the caller is already authenticated — which is exactly the flagged user's case.

### Status codes and the error-code vocabulary

**The failed current-password check on `PATCH /currentUser/changePassword` is `400 Bad Request`** — the question 04 explicitly left open.

The decisive constraint is downstream, not in the standard's status-code table. `Std:437` requires the SPA to run a global `401`/`403` interceptor that clears local state and redirects to login, and 15 chose axios specifically to have it. So returning `401` or `403` for a mistyped current password would **log the user out on a typo** — and since a successful change already kills every session (04's step 6), the user could not tell the two outcomes apart. `400` says what is actually true: the session is valid, the caller is authorized, and a field in the request body is wrong. It also sits inside the class 04 settled as permitted to carry a specific error, since `Std:247` scopes itself to authentication outcomes and this is not one.

**Generalised as a constraint on the error contract:** no non-authentication rejection on an authenticated path may use `401` or `403`, because those two codes are reserved as the interceptor's logout signal.

**08 fixes the codes; the envelope stays fog.** Four stable machine-readable codes per `Std:244`:

| Code | Status | Raised by |
|---|---|---|
| `PASSWORD_CHANGE_REQUIRED` | `403` | tier-0 filter (`Std:51`, `:165`) |
| `ACCESS_DENIED` | `403` | the `accessDeniedHandler` (`Std:244`, `:428`, `:429`) |
| `LAST_USER_MANAGER` | `409` | service layer (03's invariant) |
| `CURRENT_PASSWORD_INVALID` | `400` | service layer (this ticket) |

The split is deliberate: `Std:51`/`:165` require the forced-change code *specifically so the SPA can tell it from an ordinary `403`*, and that is discharged by the code existing, not by the envelope's shape. Had the codes waited on the fog, the tier-0 filter would have been unimplementable. The **Error contract shape** fog patch keeps the envelope (`ProblemDetail` versus a custom body) and the `ERROR`-vs-`WARN` contradiction 05 left it; it graduates to a ticket on this closure.

### CSRF: a conflict I expected to find, and did not

Making the entry point return `401` looked like it would break `Std:87` ("Login or any state-changing request without a valid CSRF token is rejected with `403 Forbidden`") and `Std:445`, because `ExceptionTranslationFilter` converts an `AccessDeniedException` into an entry-point call whenever the authentication is anonymous — and an unauthenticated `POST /auth/login` with a bad token is exactly that case.

**Verified: it does not arise.** `CsrfFilter` in `spring-security-web-7.0.6` holds its own `AccessDeniedHandler` field and invokes it directly:

```
232: invokeinterface AccessDeniedHandler.handle(HttpServletRequest, HttpServletResponse, AccessDeniedException)
```

It never throws to `ExceptionTranslationFilter`, so a CSRF failure returns `403` even for an anonymous caller. `Std:87` and `:445` hold by construction.

**And the two handlers are one handler.** `CsrfConfigurer.getDefaultAccessDeniedHandler` pulls `ExceptionHandlingConfigurer.getAccessDeniedHandler(...)`, wrapping it in a `DelegatingAccessDeniedHandler` / `CompositeAccessDeniedHandler`, so the single JSON handler configured above serves the authorization path and the CSRF path alike. One note for 13: that delegation inserts an `InvalidSessionAccessDeniedHandler` ahead of ours **if** an `InvalidSessionStrategy` is configured, in which case a CSRF failure on an expired session bypasses the JSON handler entirely.

**Logout needs no special rule.** Row 19 under `authenticated` plus the `401` entry point produces exactly the `401` on an expired session that `Std:438` predicts — and forbids "fixing" by exempting logout from CSRF. The clause is satisfied by construction.

### What is closed by `denyAll()` rather than by a row

**`/actuator/**` has no row, and that is a positive answer.** 15 put Actuator on the classpath purely as Micrometer Tracing's carrier (`15:139`, `:149`) and it offers this application no feature. `denyAll()` is terminal, so no row means closed to everything including `USER_MANAGER`. Recorded explicitly so 09 cannot read the silence as undecided; 09 retains only `management.endpoints.web.exposure.*`.

**Role mutation gets its `403` for free.** 03 established there is no role-write endpoint, so `Std:250` and `:425`'s required `403` on attempted role mutation falls out of row 21. It is a 14 test row, not a handler.

### One property recorded rather than guarded

With a flat two-role model, manager A can create or promote B to `USER_MANAGER` (row 10, row 13 — 03 ruled a manager may select the role), after which B can demote or disable A. No matrix row prevents this and no clause asks for one: it is inherent to 03's closed two-role set, and the last-manager `409` is the only floor under it. Named here so a reviewer does not read it as an omission in the matrix.

### What this ticket hands to its dependents

- **[15 — Tech baseline and module structure](15-tech-baseline-and-module-structure.md)** — a fifth ArchUnit row. With `@PreAuthorize` gone, a request-mapped method with no matrix row is the new failure mode, and only a static check catches the endpoint that has *no* row at all.
- **[10 — Account lifecycle and delete semantics](10-account-lifecycle-and-delete-semantics.md)** — owns `GET /users/{userId}`'s payload, under one constraint from here.
- **[12 — Audit and logging contract](12-audit-and-logging-contract.md)** — the tier-0 preemption hole, and the four error codes as audit field values.
- **[14 — Test and validation plan](14-test-and-validation-plan.md)** — a matrix-walking test table, plus explicit tests for the two recipe-free components.
- **[09 — HTTP security, CSRF, CORS, headers](09-http-security-csrf-cors-headers.md)** — four CSRF-colliding public rows; `/actuator/**` needs no row.
- **[07 — Account lockout and IP throttling](07-lockout-and-ip-throttling.md)** — the conditional unlock row, pre-specified so 07 closes without reopening this ticket.
- **[13 — Session policy](13-session-policy.md)** — the `InvalidSessionAccessDeniedHandler` interaction.
- **New: [21 — Error contract shape](21-error-contract-shape.md)** — graduated from the map's fog by this closure.
