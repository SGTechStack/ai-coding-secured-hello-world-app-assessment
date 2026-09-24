# 12: Admin account actions, end to end

**What to build:** An admin can disable and re-enable, promote and demote, and delete any account except their own. Each change takes effect at once, because the target's existing sessions are ended. The server rejects self-actions even if the UI is bypassed. See spec §Backend modules › Admin module, §Frontend modules › Admin users page, and Acceptance scenarios › Story 7.

**Blocked by:** 09, 11

**Status:** ready-for-agent

- [ ] `PATCH /api/v1/admin/users/{id}/status` with `{enabled}` → `200` with the updated row. Disabling expires the target's sessions: their live session gets `401` and they can't log in. Re-enabling lets them log in again.
- [ ] `PATCH /api/v1/admin/users/{id}/role` with `{role: "USER" | "ADMIN"}` → `200` with the updated row. An unknown role → `400 VALIDATION_FAILED`. A change expires the target's sessions, and the new role applies after they log in again.
- [ ] `DELETE /api/v1/admin/users/{id}` → `204`. It expires the target's sessions, their reset tokens cascade, and they can't log in.
- [ ] Self disable, self demote and self delete → `409 SELF_ACTION_NOT_ALLOWED`, with state unchanged. An unknown id → `404 USER_NOT_FOUND`. A non-admin → `403`. Anonymous → `401`. Every mutation without CSRF → `403`.
- [ ] Audit events `USER_ENABLED`, `USER_DISABLED`, `USER_ROLE_CHANGED` (old and new role), `USER_DELETED` and `ADMIN_SELF_ACTION_REJECTED`, each with actor, target and IP.
- [ ] The frontend admin API has `setUserEnabled`, `setUserRole` and `deleteUser`, each invalidating the user list on success.
- [ ] Each row has an enable/disable toggle, a role control and a delete button behind a confirmation `AlertDialog`. A failed action shows `ErrorAlert`.
- [ ] Frontend tests: each action sends the right request and refreshes the list, delete needs confirmation, and a failure shows the alert.
- [ ] e2e Story 7 scenarios 2, 3 and 4 pass under the CSP fixture, using freshly registered users.
