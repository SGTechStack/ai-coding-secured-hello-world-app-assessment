# 11: Admin — list users and role enforcement (Story 8)

**What to build:** An admin can review who has access to the system, and non-admins cannot. `GET /api/admin/users` returns, for an authenticated admin, each user's username, email, role, enabled status, and created-at date — never password hashes. An authenticated non-admin calling it gets 403. This slice establishes the admin module and the server-side `/api/admin/**` role guard that the remaining admin slices reuse. A React admin table renders the list for an admin.

**Blocked by:** 04, 10.

**Status:** done

- [x] `GET /api/admin/users` for an authenticated admin lists username, email, role, enabled, created-at
- [x] Password hashes are never included in the response
- [x] An authenticated non-admin (USER) calling it receives 403
- [x] The `/api/admin/**` role guard is enforced server-side via Spring Security (never trusted from client state)
- [x] Audit log line for admin user-list access (actor)
- [x] React admin table renders the user list for an admin
- [x] Integration tests: admin 200 with expected fields and no hashes; USER → 403
