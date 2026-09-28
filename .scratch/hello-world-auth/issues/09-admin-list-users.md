# 09: Admin — list all users

**What to build:** An authenticated admin views a list of all registered users (username, email, role, enabled, created-at — never password hashes). A non-admin calling the endpoint is forbidden. This slice establishes the admin route-guard that later admin slices reuse.

**Blocked by:** 03 (authenticated sessions), 06 optional but 03 is the real gate.

**Status:** ready-for-agent

- [ ] `GET /api/admin/users` for an authenticated admin returns each user's username, email, role, enabled status, and created-at — never password hashes.
- [ ] A `USER` (non-admin) calling any `/api/admin/**` endpoint receives 403. (IM8 ac-1, as-7)
- [ ] Role check is enforced server-side via Spring Security method/URL security, never trusted from client state. (least privilege)
- [ ] Admin React view lists users; sensitive fields never sent to the client.
- [ ] Integration test: a `USER` calling an `/api/admin/**` endpoint receives 403. (Testing Requirements, Story 8)
