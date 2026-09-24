# 04 — Inventory the prescribed recipes: what must be followed rather than designed

Type: research
Status: resolved
Blocked by: —

## Question

Which parts of this app are already prescribed as implementation recipes we must follow, and which
are genuinely ours to design?

If a recipe dictates the shape of something, the corresponding design ticket shrinks to "adopt the
recipe and record the parameters" instead of deliberating. Finding this out early is what stops the
map re-deciding things the organisation has already decided.

## What to find

Read every recipe under:

`App-Standards/Appfw-User-Standards/Shared_Recipes/`
- `Common_Security_Headers_and_SPA_CSRF_Configuration.md` — expected to largely settle
  "Decide session management and the CSRF contract"
- `Common_Role-Based_Access_Control_Configuration.md` — expected to settle the role definition file
  and authorization matrix format
- `Common_Secure_Self-Read_User_Endpoint.md` — the `/api/me`-shaped endpoint the SPA needs for auth
  state discovery
- `Common_Automatic_Database_Role_Synchronization_and_Deleted_Role_Backups.md`

`App-Standards/Appfw-User-Standards/User_Standalone/Standalone_User_Access_Control_Recipes/`
- `Standalone_Session_Login_with_CSRF_Bootstrap.md` — the CSRF bootstrap sequence for the SPA
- `Standalone_Privileged_User_Administration_and_Password_Reset.md` — the admin module and reset flow
- `Standalone_Self-Service_Password_and_History_Management.md` — self-service change and password
  history
- `Standalone_Scheduled_Account_Hygiene_Jobs.md` — read it, but hygiene jobs are Out of scope; the
  goal is only to confirm nothing non-hygiene is buried in it

Also read `App-Standards/Appfw-User-Standards/User_Standalone/Standalone_User_Access_Control_Application_Standard_Questions.md`
— a questions file usually encodes the decisions the standard expects an implementer to make, which
may map directly onto this map's remaining tickets or reveal ones we have missed.

## Report

For each recipe:

1. Is it **prescriptive** (follow verbatim) or **illustrative** (a worked example)?
2. Which design ticket it collapses or constrains, and what specifically it decides.
3. Named dependencies and versions it assumes — flag anything that assumes Spring Boot 3.x, since
   we are on 4.1 with Spring Security 7, where config surfaces differ.
4. Any parameter the recipe leaves open for us.

Then list any decision the `_Questions.md` file expects that is **not** currently a ticket on this
map. Those are gaps in the chart.

## Done when

Every recipe is classified prescriptive or illustrative, each design ticket knows what it inherits,
and any missing tickets are named so the map can be amended.

## Answer

Nine documents read in full: the four `Shared_Recipes/`, the four
`User_Standalone/Standalone_User_Access_Control_Recipes/`, and
`Standalone_User_Access_Control_Application_Standard_Questions.md`. Sections §3.1–§3.5 and §2
(Standard Flow) of `Standalone_User_Access_Control_Application_Standard.md` were also read, because
the Questions file cites "§3.5" as the source of its defaults and the recipes are only intelligible
against it.

### Headline findings

**1. No recipe assumes Spring Boot 3.x or Spring Security 6.x.** All eight state
`Spring Boot 4.x` + `Spring Security 7.x` under their own "Prerequisites" heading (the hygiene recipe
says `Spring Boot 4.x` and omits a Security version, as it needs none). The expected version-flag
finding does not exist at the framework level. One dated reference survives: the Standard's
§3.4 Logging Contract mandates the "SLF4J 2.0+ / Spring Boot 3.4+ fluent API" — a floor, not a
ceiling, so `log.atInfo()...log()` is fine on 4.1.

**2. The version risk is one layer down, in libraries and starter-internal classes, not in Spring
itself.** Five snippets cannot be compiled verbatim on our stack (details in "Version and
buildability risk register" below). The most consequential: the rate-limiter snippet's API does not
exist in any published Bucket4j release, and roughly a third of the admin recipe is written against
an internal "starter" (`UserManagementRepositoryHandler`, `CommandExecutor`, `PasswordUtil`,
`CustomUserDetails`, `DataRestAutoConfigurationProperties`) that we do not have and that is not a
public Spring API.

**3. The two recipes expected to settle ticket 08 settle most of it, but leave the hardest part
unresolved and get one thing wrong by omission.** They do fully specify the CSRF bootstrap
(`GET ${api.base-path}/csrf`, `permitAll`, session-bound, JSON body, no cookie) and they prohibit
the cookie/double-submit alternative outright. But:
   - **`SameSite=Lax` is mandated and is incompatible with our cross-origin SPA.** Standard §3.5
     ("Session Management") requires session and CSRF cookies be configured `SameSite=Lax`, and both
     recipes hard-code `same-site: lax` in `application.yml`, calling it the "Standard SPA posture".
     A `Lax` cookie is not sent on cross-site `fetch`/XHR at all, so a SPA on its own origin cannot
     authenticate. Meanwhile Q27 of the Questions file explicitly offers "CORS enabled, credentials
     allowed — required for cookie-based session auth from different origin", and the security
     headers recipe §Step 4 configures exactly that. **The standard contradicts itself and ticket 08
     must resolve it**, in one of two directions: put SPA and API on the same registrable site (a
     reverse proxy or shared parent domain, keeping `Lax`), or use `SameSite=None; Secure`, which is
     a documented deviation from §3.5 requiring an ADR. This is the single most load-bearing thing
     found.
   - **Neither recipe mentions that the bootstrap token must be re-fetched after login and after
     logout.** Spring Security's `CsrfAuthenticationStrategy` and `CsrfLogoutHandler` clear the
     existing token on authentication and logout success; Spring's own SPA guidance therefore
     requires refreshing it ([spring-security issue #13424 quoting the official
     guide](https://github.com/spring-projects/spring-security/issues/13424)). A SPA that follows
     the recipe literally — fetch `/csrf`, POST `/login`, then POST anything — gets a 403 on the
     third call. Ticket 08 must write this into the sequence.
   - Also unstated: `GET /csrf` while anonymous **creates a session** for every unauthenticated
     visitor (the token is session-bound and must be persisted to be verifiable). With Spring Session
     JDBC that is a database write per visitor and a denial-of-service surface. Ticket 08 and ticket
     09 own the mitigation; no recipe addresses it.

**4. `Common_Role-Based_Access_Control_Configuration.md` settles the role format and the matrix
format, and — read together with Q17b — resolves the admin-changes-another-user's-role question:
yes, an admin may, subject to a choice we still have to make.** The recipe's "Immutable Security
State" principle and its `ImmutableSecurityHandler` block runtime mutation of **role definitions**
(which roles exist, and which authorities each maps to), not **role assignments** to users. The
admin recipe confirms this by permitting `existing.setRoles(request.roles())` in `updateUser` and
blocking only *self*-role-change. Standard §3.5 ("Data Access Control") agrees: roles are managed by
role administrators; users cannot assign roles to themselves *or others*. Q17b then asks us to pick
between seven default-role/mutability strategies, including "modifiable only during creation".
So: the *capability* is prescribed as allowed; the *policy* is an open parameter for ticket 11.

**5. There is no recipe at all for password reset by token.** Standard §2 Happy Path step 11 and §3.1
require a single-use token with 30-minute expiry, token *hash* stored against the account, plaintext
returned once. Q14 and Q22 add the security requirements (`SecureRandom`, SHA-256 hash at rest,
token excluded from logs, issuing a new token invalidates the prior one). But
`Standalone_Privileged_User_Administration_and_Password_Reset.md` §3 step 7 implements something
different: it generates a **random plaintext password**, writes it to the account, and returns it in
`NewPasswordDTO.newPassword`. No token, no expiry, no hash, no redemption endpoint. **The standard
contradicts itself here too** — §3.5 ("Password Policy") says "Administrative password reset must
generate a 12-character random password", while §3.1 says it "must return the newly generated
plaintext token exactly once". Ticket 10 inherits a contradiction rather than a recipe, and our
hashed-single-use-token design matches §2/§3.1/Q14/Q22 but *not* §3.5 or the only recipe that
exists. Record as an ADR.

### Classification

| Recipe | Prescriptive or illustrative | Basis for the call |
|---|---|---|
| `Shared_Recipes/Common_Security_Headers_and_SPA_CSRF_Configuration.md` | **Prescriptive**, with illustrative code | Contains a "Strictly Prohibited: Cookie-Based CSRF" section and "You must configure a strict CORS whitelist"; Standard §3.1 marks the same rule `*[Enforced Constraint]*`. The Java is a faithful, compilable expression of it except for the H2 item. |
| `Shared_Recipes/Common_Role-Based_Access_Control_Configuration.md` | **Prescriptive on shape, illustrative on content** | The YAML *structure* (role→authority map, hierarchy string, method+path guard matrix, whitelist) and deny-by-default are prescribed; role names and paths are examples. Standard §3.5 makes config-as-source-of-truth binding. |
| `Shared_Recipes/Common_Secure_Self-Read_User_Endpoint.md` | **Prescriptive on the contract, illustrative on implementation** | The contract (principal-only lookup, DTO/projection whitelist, read-only, audited) is required by Standard §3.5 "Data Access Control". The implementation assumes Spring Data REST (`@Projection`, `@RepositoryEventHandler`), which we are not using. |
| `Shared_Recipes/Common_Automatic_Database_Role_Synchronization_and_Deleted_Role_Backups.md` | **Illustrative** | Its own §4 walks back the code ("The temporary password in the code snippet is for illustration"; use Flyway/Liquibase not `ddl-auto`), and it is written for the SSO namespace (`spring.security.sso.*`). The `DeletedRole` archive concept is the prescriptive residue. |
| `Recipes/Standalone_Session_Login_with_CSRF_Bootstrap.md` | **Prescriptive on parameters, illustrative on code** | Every number in it restates a Standard §3.5 default (15m/480m/5/20m/10-per-minute/max 1 session). The rate-limiter and absolute-timeout code is sketch-quality. |
| `Recipes/Standalone_Privileged_User_Administration_and_Password_Reset.md` | **Mixed; largely a description of an internal starter** | Its business rules are prescriptive and testable (they reappear verbatim in Standard §2 Failure Paths 12–16 and its own §5 Verification list). Its code narrates a starter we do not have, and it even flags its own defect: "`deletedById` is not yet in the starter's `DeletedUser` entity". |
| `Recipes/Standalone_Self-Service_Password_and_History_Management.md` | **Illustrative, and partly wrong** | The history-check *rule* is prescriptive (§3.5 default 3). The code uses `@Autowired` fields on an object built with `new` (they will be null), omits password-policy validation, and puts the mandatory current-password check in a "Best Practices" appendix even though Standard §3.5 and Failure Path 17 make it mandatory. |
| `Recipes/Standalone_Scheduled_Account_Hygiene_Jobs.md` | **Illustrative**; out of scope, and **nothing non-hygiene is hidden in it** | Confirmed as instructed. Three non-hygiene dependencies leak out of it, listed below. |
| `Standalone_User_Access_Control_Application_Standard_Questions.md` | **Neither — it is the decision inventory** | 32 questions (plus Q17a, Q17b, Q23a). Several carry non-negotiables in a "Standard Requirement" callout: Q12 ("This is not a decision point"), Q17 (roles not creatable via API), Q22 (activation token hashing). |

### What each design ticket inherits

**06 error envelope and enumeration contract — constrained, not collapsed; and inherits a
contradiction.** Standard §3.2 names the exact message strings and statuses (`user exist` 400,
`username change not allowed` 400, `account locked` 401, `account cannot authenticate` 401,
`too many requests` 429 with `Retry-After`, `password reset token expired or invalid` 400,
`role <operation> not allowed` 403) and requires identical status, body **and timing** across
login/reset/registration outcomes. But the recipes give two incompatible envelopes: the CSRF/session
recipes deliberately fall through to Spring Boot's `BasicErrorController` default body
(`response.sendError(...)`), while the self-service recipe's §4 example returns `{"error": "..."}`.
Boot's default body is not the "stable, machine-readable error body" §3.2 demands. Ticket 06 has to
choose, and the Standard's §3.2 strings are the input.
Also note §3.2 says authorization failures map to **403** while §3.5 relies on Spring defaults that
return **401** for unauthenticated — the recipes' `sendError(401)` on auth failure is consistent, but
the `PasswordChangeFilter` returns **403** for "password must be changed", which an SPA will
otherwise confuse with an authorization failure.

**07 password policy and hashing — largely collapsed, but the recipes disagree with each other on
the algorithm.** Prescribed: adaptive hash mandatory, "not a decision point" (Q12); history default
3 (§3.5, Q13); min 12 / max to prevent DoS; admin-generated initial password must be 12 chars with
one lower, one upper, one digit, one special (§3.5); no periodic expiry (hygiene recipe's NIST note).
The clash: the admin recipe §3 step 1 hard-codes `new Argon2PasswordEncoder(16, 32, 1, 19456, 2)` and
calls a single encoder mandatory ("We use a single encoder implementation"), while the self-service
recipe says "Password Encoder: Default configured to **BCrypt**" and its §5.3 then recommends
Argon2id anyway. Our settled baseline is BCrypt, which §3.5 permits ("BCrypt is acceptable for
existing systems") and Q12 permits at "cost factor ≥12" — so ticket 07 should cite Q12's ≥12 as the
floor for the work-factor decision, and record the deviation from the admin recipe's Argon2id.
Open to us: exact min/max length, whether to keep composition rules (the admin recipe's regex
requires four classes from the set `@#$%^&+=`, which is *narrower* than §3.5's permitted special-char
set and would reject a password §3.5 explicitly allows — a real defect if copied verbatim),
breach-list screening (`HIBP` is commented out in the recipe; Q12 offers it as a checkbox).

**08 session and CSRF contract — mostly collapsed; see headline findings 3.** Inherited verbatim:
session-bound synchronizer tokens only, `HttpSessionCsrfTokenRepository` + XOR handler by default,
`CookieCsrfTokenRepository` strictly prohibited, bootstrap endpoint at `/csrf` whitelisted and
non-cacheable, `HttpOnly`/`Secure` cookies, idle 15m, absolute 8h, max 1 concurrent session, session
ID rotation on login, session and CSRF cookies cleared on logout plus server-side invalidation. Three
things the recipes do **not** give you and ticket 08 must still decide: the `SameSite` resolution, the
post-login/post-logout token refresh, and anonymous-session creation on `/csrf`. Two more inherited
from elsewhere: Standard §3.5 requires `Clear-Site-Data: "cache","cookies","storage"` on logout (no
recipe implements it), and Q7 requires `SpringSessionBackedSessionRegistry` rather than
`SessionRegistryImpl` when on Spring Session JDBC — the session-login recipe's
`.sessionRegistry(sessionRegistry())` is silent about which, and with `maximumSessions(1)` the wrong
choice silently fails to enforce the limit across instances.

**09 lockout and dual rate limiting — parameters collapsed, mechanism not.** Inherited: 5 consecutive
failures, 20-minute auto-lift, counter resets only on success, 10 login attempts per account per
minute, 429 + `Retry-After`, rate limiting also required on both password-reset token endpoints
(§3.5), admin unlock permitted and no email-based self-service unlock. The `AccountLockoutService`
sketch in the session-login recipe shows the DB-backed counter. Not inherited: **no recipe implements
per-IP limiting**, and Q16 actively argues against it for internal apps behind corporate NAT
("Do NOT implement if: Internal behind corporate proxy..."). Our PRD mandates dual limiting, so
ticket 09 inherits a justification burden, not a recipe — and an ADR, since we are adding a control
the standard's decision guidance steers away from. Also open: how 429 is actually produced (the
recipe asserts it in Verification but no code emits it), the `Retry-After` value, whether the
limiter is in-memory (the sketch's `ConcurrentHashMap` is per-instance and does not hold across the
load-balanced topology Q1 contemplates), and Q15's choice of auto-unlock vs admin unlock vs admin
unlock with mandatory reason.

**10 credential flows — least served by the recipes; inherits the reset contradiction.** See
headline finding 5. What *is* inherited: single-use, 30-minute expiry, hash-at-rest, plaintext
returned once and never logged, new token invalidates the prior token, successful self-service change
invalidates pending tokens, successful reset invalidates all sessions for the account (all §2/§3.1
plus Failure Paths 16, 18–21), current password required on self-service change even with an active
session, and `requirePasswordChange=true` forced after an admin reset with lock state cleared
(`failedLoginAttempts=0`, `accountNonLocked=true`). The session-invalidation mechanism is prescribed
concretely and is reusable: `FindByIndexNameSessionRepository.findByPrincipalName(username)` →
`deleteById`, **saving the credential before invalidating** so a failed invalidation cannot lock the
user out. Open to us: token format (Q14 offers 32-char alphanumeric / 64-hex / UUID), delivery
(Q14's six options; "admin-generated, display once" needs no SMTP and matches our scope), and
whether `/password-reset/request` and `/password-reset/confirm` are the endpoint names (Q14 names
them in its "Why this matters" note).

**11 admin module, role model and bootstrap — heavily collapsed.** Inherited as a ready-made
acceptance list from the admin recipe §5 and Standard §2 Failure Paths 12–16: reject duplicate
username *and* duplicate username against tombstones, reject duplicate email, `username` and `email`
are `@Column(updatable = false)`, no password change through the generic update path, no locking
through the generic update path, no self-role-change, no self-delete, no self-unlock, tombstone
written before the row is deleted (atomically, `@Transactional`), tombstone records `deletedById`,
batch payload capped (`spring.user-management.batch-payload-max-size`, default 5000) and rejected
before any work is done, **sessions terminated whenever roles or `enabled` change**, and
`requirePasswordChange` enforced by a filter registered `addFilterBefore(..., AuthorizationFilter.class)`
that permits only csrf + current-user read + change-password + the public whitelist. Role model
inherited from the RBAC recipe: roles in configuration, authorities checked in code, hierarchy via
`RoleHierarchyImpl.fromHierarchy(...)`, deny-by-default terminal `anyRequest().denyAll()`,
runtime role-definition mutation blocked. Open to us: the actual role and privilege names (Q17/Q18
are empty tables; Q17 says "beyond the default `USER_MANAGER` and `USER` roles" — note that name,
`USER_MANAGER`, differs from the `ADMIN`/`MANAGER`/`USER` used in every recipe example), multi-role
vs single-role (Q17a), default-role and mutability strategy (Q17b), the matrix content (Q19), the
authenticated-but-unroled path list (Q20, property `pathsForAuthenticatedUsers`), the public
whitelist (Q21, property `pathsForWhitelisting`), and admin unlock semantics (Q15). One conflict to
resolve: Q3's bootstrap options all say **Liquibase changeset** for the production admin, while our
settled baseline is Flyway — same idea, different tool, worth an explicit line in the ADR rather
than a silent substitution.

**12 data model — substantially prescribed, but from three mutually inconsistent sources.** The
admin recipe §3 step 2 is the richest and should be the base: `UserAccount` with `id` (UUID,
`GenerationType.UUID`), `username`/`email` non-updatable, `fullName`, `roles` (`@ManyToMany`, EAGER),
`createdAt`, `lastLoginAt`, `disabledAt`, `enabled`, `accountNonLocked`, `accountNonExpired`,
`credentialsNonExpired`, `passwordHash`, `requirePasswordChange`, `firstLogin`, `passwordResetAt`,
`failedLoginAttempts`, `passwordHistory` (`@ElementCollection` of `PasswordHistoryEntry`), plus
`authenticationModes`/`sourceMode` and the SSO-only `ssoId`/`uuid`. Table names `USERS` and
`DELETED_USERS`; `DeletedUser` and `DeletedRole` archives; `deleted_roles` retained **indefinitely**
as audit trail with no cascade. Drift ticket 12 must reconcile: the session-login recipe uses
`failedAttempts` and `lockedUntil` where the admin recipe uses `failedLoginAttempts` +
`accountNonLocked` (two different lockout representations — a boolean plus a separate cooldown
instant are not interchangeable, and the 20-minute auto-lift needs the instant);
the role-sync recipe's `Role` has only `id` and `name` while the RBAC recipe implies authorities;
`user.setPassword(...)` vs `user.setPasswordHash(...)`; `roleRepository.findByName` returns
`Optional<Role>` in the role-sync recipe but is null-compared in the admin recipe. Our stack adds
one: `ddl-auto: validate` + Flyway means the recipes' `spring.session.jdbc.initialize-schema: always`
must become `never`, with Spring Session's DDL vendored into a Flyway migration — the admin recipe
says as much parenthetically ("use 'never' and apply the schema manually in production").
`@ElementCollection` for password history also needs an explicit ordering column to make "most
recent N" deterministic; the recipe relies on `List` index order, which JPA does not guarantee
without `@OrderColumn`.

**13 audit event catalogue — collapsed onto Standard §3.3/§3.4, which the recipes then demonstrate.**
Required events: successful/failed login, logout, lockout transitions both ways, user create/unlock/
update/delete attributed to the acting admin, reset token issuance **and** redemption, password
changes (admin and self-service), role assignments and changes, security header configuration
changes, admin deletion with tombstone. Every event carries timestamp, principal, outcome, request
path, method, correlation ID. Field names are fixed by ECS: `event.action`, `event.outcome`,
`user.id` (UUID only — raw usernames and emails must not be logged in cleartext), plus `trace.id`
and a hashed session id. Never log: raw passwords, reset token plaintext, **reset token hashes**,
CSRF tokens, raw session ids, OTPs. Levels are prescribed per event class. The recipes supply a
working `event.action` vocabulary to adopt: `user-authentication`, `access-control`,
`user-administration`, `user-provisioning`, `password-reset`, `password-change-enforcement`,
`profile-read`, `application-startup`, plus batch fields `batch.job.name`, `record.success`. Open:
retention (Q28, 90-day floor) and destination (local DB vs shipped out). One tension for ticket 13:
§3.4 forbids cleartext usernames, but the login *failure* handler only has a username, and the
session invalidation API is keyed by principal *name* — §3.4 answers it (omit identity at auth entry
points, correlate by `session.hash` and `trace.id`), and ticket 13 should write that down.

**14 frontend architecture — inherits a concrete API surface and two naming collisions.**
Endpoints named across the recipes: `GET ${api.base-path}/csrf`, `POST ${api.base-path}/login`
(form-urlencoded by default), `POST ${api.base-path}/logout`, `GET ${api.base-path}/currentUser`,
`PATCH ${api.base-path}/currentUser/changePassword`, `/users` CRUD,
`PATCH /users/{id}/resetPassword`, `PATCH /users/batchResetPassword`. Collision one: the shared
self-read recipe calls the same endpoint `GET /api/v1/profile` guarded by authority `SELF_READ`,
while the standalone recipes call it `/currentUser` guarded by `CURRENT_USER_UPDATE`/`USERS_UPDATE`;
Q23a calls it `/currentUser`. Pick one and record it. Collision two: the SPA must handle a 403 from
the `PasswordChangeFilter` as "go change your password", distinct from a 403 authorization failure —
the session-login recipe's inline comment already tells the SPA to own the 401/403 handling
("The SPA handles the 401/403 (clears state, redirects to login)") so no error page is shown, and
logout on an expired session legitimately returns 403 because the CSRF filter runs first.
Prescribed CSRF header name: `X-CSRF-TOKEN` (the CORS `allowedHeaders` list in the headers recipe).

**16 test plan — inherits a ready-made checklist.** Each recipe's "Verification" section is
directly liftable: header presence via `curl -I` for all five headers; `GET /csrf` returns JSON
**and** sets no `XSRF-TOKEN` cookie; new `JSESSIONID` after login; 5 failures → 401 + 20-minute DB
lockout; high-frequency → 429; absolute invalidation at 8h; role hierarchy inheritance; YAML-only
role change with no rebuild; unconfigured endpoint → 403; IDOR attempt bounded to self; no
`password`/`passwordHistory` in the profile JSON; the admin recipe's fourteen-item rule list
including "the returned plaintext password is NOT present in any log line"; history rotation proving
the N+1-th password becomes reusable. Two are hard and ticket 16 should say how: proving **identical
response timing** across auth outcomes, and proving a log line *absence*.

**17 deferral register and ADRs — inherits five new entries** beyond hygiene jobs: the `SameSite`
deviation (or the topology change that avoids it), BCrypt over the admin recipe's Argon2id, reset-by-
token over §3.5's reset-by-generated-password, adding per-IP limiting against Q16's guidance, and
Flyway where Q3 says Liquibase.

**Confirmed clean: `Standalone_Scheduled_Account_Hygiene_Jobs.md` hides no non-hygiene requirements.**
Three dependencies nonetheless leak out of it into in-scope tickets: (a) `lastLoginAt` must be
maintained on every successful login — a login-handler and data-model obligation (tickets 10/12) even
though nothing in scope reads it; (b) its `invalidateUserSessions` helper is the canonical
multi-principal session-termination routine and shows that principal name may be `username`, `uuid`,
or `ssoId` (ticket 08 can simplify to `username` only and should say so); (c) Standard Failure Path
22 — a session belonging to an account disabled by a job must be rejected on its next request — is
the same "state change invalidates sessions" rule tickets 08 and 11 already need.

### Version and buildability risk register

Five items will not compile or run verbatim on Spring Boot 4.1 / Spring Security 7, none of them
caused by a 3.x assumption:

1. **`PathRequest.toH2Console()`** in the headers recipe's dev filter chain. Spring Boot 4 split
   `spring-boot-autoconfigure` into focused modules, and the H2 console now needs an explicit
   `spring-boot-h2console` dependency; without it the console (and the matcher's backing properties)
   is simply absent. See [Spring Boot 4 modularization](https://www.danvega.dev/blog/spring-boot-4-modularization)
   and this [Boot 4.0.0 H2 console write-up](https://qiita.com/zatton27/items/3abaec3924e35f34640c).
   Verify the class's new package before copying. *Content was rephrased for compliance with
   licensing restrictions.*
2. **The rate limiter API does not exist.** The session-login recipe's
   `Bucket.builder().addLimit(Limit.of(10, Refill.intervally(10, Duration.ofMinutes(1))))` matches
   neither the current Bucket4j form —
   `addLimit(limit -> limit.capacity(..).refillIntervally(..))`, per the
   [Bucket4j 8.20 documentation](https://bucket4j.com/8.20.0/toc.html) — nor the older
   `Bandwidth.classic(capacity, Refill.intervally(...))`. There is no `Limit` type. The library is
   never named and no version is pinned anywhere in the recipe set. Treat the whole limiter as
   pseudo-code and pin the dependency ourselves.
3. **Starter-internal types presented as if they were API.** `UserManagementRepositoryHandler`,
   `CommandExecutor`/`UserActionCommand`, `PasswordUtil.generateRandomPassword()`,
   `CustomUserDetails`, `SecurityFilterProperties`, `DataRestAutoConfigurationProperties`,
   `WebSecurityConfiguration.DEFAULT_AUTH_WHITELIST`. We have none of these. The behaviour is
   prescriptive; the code is not adoptable.
4. **`hasRole` vs `hasAuthority` mismatch.** The admin and self-service recipes guard with
   `@PreAuthorize("hasRole('USERS_UPDATE')")` / `hasRole('CURRENT_USER_UPDATE')`, but `hasRole(X)`
   checks for the authority `ROLE_X`, while the RBAC recipe grants bare authorities (`USER_READ`,
   `USER_WRITE`) and guards with `hasAuthority`. Copied verbatim these deny every request. Q18
   compounds it by stating authorization is enforced "by roles, not individual privileges", which is
   the opposite of the RBAC recipe's "code checks for fine-grained *Authorities*". Ticket 11 must
   pick one convention and apply it everywhere.
5. **Field injection into a manually constructed object.** The self-service recipe's
   `ChangeCurrentUserPasswordCommand` is created with `new` in the controller but declares
   `@Autowired`/`@Value` fields; they will be null at runtime.

Two more that are not compile errors but will bite:

6. **Configuration namespace is incoherent across the set.** `app.security.*` (RBAC, self-read,
   session-login), `spring.security.sso.*` (role sync), `spring.password.sso.*` (self-service),
   `spring.user-management.*` (admin), `app.batch.*` (hygiene). Two of those squat inside Spring
   Boot's own `spring.*` namespace, and the role→authority map appears under two different keys
   (`app.security.role-mappings` and `spring.security.sso.predefined-roles-and-privileges`). Ticket
   12 or 11 should declare a single prefix and note the deviation.
7. **Two incompatible `PasswordChangeFilter` implementations** for one requirement. The admin recipe
   extends `OncePerRequestFilter`, allows csrf + currentUser + changePassword + whitelist, and is
   registered before `AuthorizationFilter`. The self-service recipe extends `GenericFilter`, compares
   full URI equality, and allows only changePassword + logout — which would block the `/csrf` call
   the SPA needs in order to *make* the change-password request, deadlocking the flow. Adopt the
   admin recipe's version.

`Argon2PasswordEncoder(16, 32, 1, 19456, 2)` was checked and is fine: the five-arg constructor is
still public and undeprecated in the current
[Spring Security API](https://docs.spring.io/spring-security/site/docs/current/api/org/springframework/security/crypto/argon2/Argon2PasswordEncoder.html).
`RoleHierarchyImpl.fromHierarchy(...)`, `Customizer.withDefaults()`, `permissionsPolicy`,
`contentSecurityPolicy`, and the `authorizeHttpRequests` lambda DSL are all current 7.x API.

### Parameters the recipes explicitly leave to us

CSP directive list beyond `default-src 'self'; object-src 'none'` and any external domains (Q26);
CORS allowed origins per environment (Q27); `api.base-path` (Q31); role and privilege names and the
full guard matrix (Q17–Q21); BCrypt work factor; password min/max length and whether composition
rules survive; breach-list screening; password history depth (default 3); token format (Q14); batch
payload cap (default 5000); audit retention (default 90 days); tombstone retention (Q29); notification
channel (Q30); `Retry-After` value; login request content type (Q32).

### Decisions the Questions file expects that are NOT on our map

Ten gaps. The first four are the valuable ones — each is security-load-bearing and none of tickets
06–16 currently owns it.

1. **Q32 — login request content type.** Form-urlencoded, form + JSON, or JSON only. The Standard's
   §2 step 4 and the session-login recipe both use `formLogin` with form parameters, and both note
   that accepting a JSON body "requires a custom `AuthenticationFilter`". A React SPA posting JSON is
   the natural design and would put us off the recipe path for the single most security-sensitive
   endpoint in the app — including the `failureHandler`'s `request.getParameter("username")`, which
   returns null for a JSON body and silently breaks lockout counting. This decision gates tickets
   08, 09, 10 and 14 and is currently unmade anywhere.
2. **Q23a — how much account state `/currentUser` may disclose.** Minimal / moderate / detailed, with
   "account locked status and unlock time" and "failed login attempt count" only in the detailed
   tier. This directly opposes ticket 06's anti-enumeration rule (§3.2 requires identical responses
   regardless of account state) and shapes the ticket 14 auth-state payload and the ticket 12
   projection. Neither 06 nor 14 currently frames it as a decision.
3. **Q22 — activation workflow for admin-created accounts.** Immediately active with an
   admin-provided password, versus pending activation with a 24-hour / 7-day / 30-day token, versus
   pending email verification. Q22 attaches real requirements to the token path (`SecureRandom`,
   SHA-256 hash at rest, excluded from logs, one-time, issuing a new one invalidates the prior). Our
   map implicitly assumes "immediately active" via ticket 11's bootstrap, but never decides it, and
   the alternative would add a whole second token lifecycle beside ticket 10's reset token.
4. **Q4 — which identifier users actually log in with, and its constraints.** Username-only,
   email-only, flexible (either field accepts both), or employee-number. Plus allowed character set,
   min/max length, and case-insensitive lookup. Q4 notes this "Determines login form field label,
   `UserDetailsService` lookup logic, and required database fields/constraints" — and the flexible
   option changes the uniqueness constraints and the enumeration surface. Ticket 12 covers *fields*;
   nothing covers the *login identity* decision.

5. **Q23 — self-service profile update scope.** Which fields a user may change themselves, and
   whether email and phone changes require verification. Q23 warns that unverified email change is
   dangerous when email is used for password reset. No ticket owns self-service profile mutation;
   ticket 10 is credentials only and ticket 11 is admin-side.
6. **Q24 — bulk operations scope.** Bulk create, bulk role assignment, bulk enable/disable, bulk
   reset. The Standard mandates batch-reset limit behaviour (Failure Path 15, §3.2's
   `request body with list of more than {max} entries is not allowed`) and the admin recipe implements
   `batchResetPassword`, so *something* batch-shaped is prescribed — but the map never decides whether
   we build it.
7. **Q10 — Remember Me.** Q10 asks it directly and lists requirements if enabled (DB-stored token
   with expiry, invalidated on password change and logout, separate cookie). Almost certainly "no"
   for us, but it is an unrecorded decision with a security rationale, and our absolute-timeout and
   single-session decisions assume the answer.
8. **Q29 — deleted-user tombstone retention.** Indefinite / match audit retention / 1 year / custom.
   Ticket 12 creates the tombstone and ticket 13 covers audit retention (Q28), but neither decides how
   long tombstones live — and the answer interacts with ticket 11's rule that a tombstoned username
   can never be re-registered.
9. **Q31 — API base path and version.** Every recipe is written against `${api.base-path}` and Q31
   asks for it explicitly. Trivial to answer, but it is a real unmade decision that every endpoint
   string in every downstream ticket depends on.
10. **Q11 — login and logout redirect behaviour.** Home page vs last-accessed page vs role-specific
    landing; post-logout destination. Q11 flags the open-redirect risk in the last-accessed-page
    option ("better UX but requires URL validation"). Ticket 14 is the natural home but does not
    currently name it, and the open-redirect validation is a security control, not styling.

Two further Questions items are already charted but as prose rather than tickets, and should either
graduate or be pointed at explicitly: **Q30 notification strategy** (map: "Owner notification
mechanics" under *Not yet specified*) and **Q1's deployment context**, whose answer is what justifies
or undermines Q16's IP-limiting guidance that ticket 09 now has to argue against. Two are already
settled by the stack baseline and need no ticket: Q6 database (H2) and Q7 session backend (Spring
Session JDBC — though Q7's `SpringSessionBackedSessionRegistry` requirement must land in ticket 08).
Q2 migration is greenfield and Q8 batch jobs is the deferral already recorded as Out of scope.

### Net effect on the map

Collapsed or near-collapsed to "adopt and record parameters": **08** (minus the `SameSite`,
token-refresh and anonymous-session questions), **11**, **13**, **16**.
Constrained but still real design work: **06** (inherits a contradiction and no envelope), **07**
(inherits an algorithm conflict), **09** (no recipe for per-IP or for emitting 429), **12** (three
inconsistent models to reconcile), **14** (two endpoint-naming collisions).
Barely served: **10** — the one flow the PRD cares most about has no recipe and a contradiction
inside the standard itself.
The *Not yet specified* entry "**Authorization matrix file format**" can be struck: the RBAC recipe
prescribes it (`app.security.url-guards`, authority → list of `METHOD: path` entries, plus
`role-mappings`, `role-hierarchy`, `whitelist`), so it collapses into ticket 11 as predicted.

### Addendum — CSP on a two-origin deployment (the standard does not cover it)

Re-verified against the primary sources. Short answer: **no, `Common_Security_Headers_and_SPA_CSRF_Configuration.md`
does not address CSP for the SPA origin at all.** Every CSP obligation in the standard set is written
for the API's own responses, and the whole document set appears to assume one origin serves both the
API and the frontend assets. On our topology that assumption is false, and the obligation lands
somewhere no recipe reaches.

**What the recipe actually does.** §3 Step 2 item 3 sets the policy inside the Spring Security
filter chain:

```java
.contentSecurityPolicy(csp -> csp
    .policyDirectives("default-src 'self'; object-src 'none';"))
```

That emits `Content-Security-Policy` on responses from the Spring Boot app. Its §4 Verification item 1
checks exactly that and nothing more — `curl -I` for the five headers against the API. A CSP header is
enforced by the browser against **the document it was delivered with**. Our API returns JSON, never a
document, so this header governs no browsing context. `default-src 'self'` scoped to the API origin
also means `'self'` resolves to the *API's* origin, which is not where the SPA's scripts live.
The SPA's `index.html` is served by whatever fronts the static bundle, and that response carries
no CSP unless we configure it there.

**The rest of the set agrees, and none of it closes the gap.**
- `Standalone_User_Access_Control_Application_Standard.md` §3.5, "HTTP Security" bullet 1, requires
  headers "on every response at the application level using the framework's built-in security
  configuration", giving `Content-Security-Policy: default-src 'self'` as the example, and adds that
  infrastructure-level injection is complementary but not a substitute. Read literally on our
  topology, that sentence points the control at the one origin where it does nothing and rules out
  the one place it would work.
- §3.5 "Configuration Parameters" (HTTP Security and CORS) lists the same four headers as app config.
- §5 "Security Headers and CORS Tests" asserts only presence "on all responses" — satisfiable by the
  API while the SPA origin ships bare.
- **Q26** is the only question touching CSP, and it asks solely for a list of external domains to
  whitelist (its blanks are prompted with font and analytics CDN examples). It never asks which origin
  the policy protects. So the standard's own decision inventory has no slot for this.

**Consequence for our design.** This is a real hole in the inherited controls, not a parameter:

1. The CSP that actually mitigates XSS has to be emitted by the SPA's own host (static file server,
   CDN, or reverse proxy). That host is outside every recipe's scope, so **ticket 14 has to own an
   SPA-origin CSP** and the deployment config that delivers it. Nothing in tickets 06–16 currently
   does.
2. Keeping the API-side CSP is still worth it — cheap, and it hardens the one document the API can
   serve (Boot's `/error` page) plus any accidental HTML — but we should record that it is
   defence-in-depth, not the XSS control, so a later reviewer does not read the green `curl -I` check
   as the obligation being met.
3. The SPA-origin policy needs directives the recipe's two-directive string does not have, precisely
   because of the split: `connect-src` must name the API origin (under `default-src 'self'` alone the
   SPA cannot call the API at all), and `form-action`, `frame-ancestors`, `base-uri` and
   `script-src`/`style-src` need to be set for the document context. `frame-ancestors` matters
   independently because `X-Frame-Options` is set on the API, not on the framed document.
4. If ticket 08 resolves `SameSite` by putting both behind one reverse proxy — the option that keeps
   §3.5's `Lax` mandate intact — then origin becomes single, the recipe's CSP lands on the SPA
   document as written, and this gap closes along with the cookie problem. **That is a second,
   independent argument for the shared-origin topology**, and ticket 08 should weigh it alongside the
   cookie argument rather than treating them separately.

Net: add "CSP for the SPA origin" to ticket 14 (or a new deployment-topology ticket shared with 08),
and add it to ticket 16's test plan as a check against the **SPA** host, since the recipe's
verification step only ever probes the API.

### Verification pass on this report

The nine documents plus `Standalone_User_Access_Control_Application_Standard.md` were re-read from
`c:\Users\zlimweil\Desktop\SGTechStack\App-Standards\Appfw-User-Standards\`. Confirmed directly:
all four `Shared_Recipes/` and three of four standalone recipes state `Spring Boot 4.x` +
`Spring Security 7.x` under "Prerequisites"; `Standalone_Scheduled_Account_Hygiene_Jobs.md` line 14
says `Spring Boot 4.x with Spring Scheduling or Spring Batch` and names no Security version.
**A full-text search for "Spring Boot 3" and "Spring Security 6" across both recipe directories
returns nothing** — the headline "no 3.x assumption" finding holds.
`same-site: lax` confirmed in two places: the headers recipe §3 Step 2 item 2 (commented "Standard
SPA posture") and `Standalone_Session_Login_with_CSRF_Bootstrap.md` line 40.
The RBAC reading is confirmed: `ImmutableSecurityHandler` guards a `SecurityResource` argument, so it
blocks mutation of role *definitions*, not role *assignments* to users — an admin changing another
user's role is untouched by it.

One small defect in the RBAC recipe not noted above: in §3 Step 3 the filter chain registers
`url-guards` **before** the `whitelist`, and `authorizeHttpRequests` is first-match-wins. The example
paths do not overlap so it works as printed, but any whitelist entry that also matches a guard
pattern would be silently guarded instead of public. Ticket 11 should register the whitelist first.
