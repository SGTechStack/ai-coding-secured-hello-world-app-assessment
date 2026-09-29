# 03 — Role model reconciliation

Type: grilling
Status: open
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
