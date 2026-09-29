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
