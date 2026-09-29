# 08 — Authorization matrix

Type: grilling
Status: open
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
