# 10: As an admin, I want to change another user's role between USER and ADMIN, so that I can grant or revoke admin privileges.

`feature` · wave 5

| Effort | Float |
| --- | --- |
| 1.0 days | 0.3 days |

## Acceptance criteria

- Given an admin targets another user's account, when they call the role-change endpoint with a valid role, then the account's role is updated.
- Given an admin targets their own account via the role-change endpoint, when the request is made, then it is rejected — an admin cannot demote themselves.

## Dev tasks

1. `be_user_role_service` (0.5 d) — `AdminUserService.changeRole` with self-guard; `PATCH /api/admin/users/{id}/role`.
2. `fe_user_role_widget` (0.25 d) — role select per row.

## Test seams

- `AdminUserServiceTest` — self-target rejected; role updated for others.
- `AdminUserIntegrationTest` — self role change → 400.

## Dependencies

- Blocked by: 8
- Unblocks: —
