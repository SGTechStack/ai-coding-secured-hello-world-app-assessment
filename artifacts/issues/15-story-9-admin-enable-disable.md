# 9: As an admin, I want to enable or disable another user's account, so that I can suspend access without deleting their data.

`feature` · wave 5 · **critical path**

| Effort | Float |
| --- | --- |
| 1.0 days | Critical |

## Acceptance criteria

- Given an admin targets another user's account, when they call the status-toggle endpoint, then the account's `enabled` flag is updated accordingly, and a disabled user can no longer log in.
- Given an admin targets their own account via the status-toggle endpoint, when the request is made, then it is rejected — an admin cannot disable themselves.

## Dev tasks

1. `be_user_status_service` (0.5 d) — `AdminUserService.setEnabled` with `SelfActionException` guard; `PATCH /api/admin/users/{id}/status`.
2. `fe_user_status_widget` (0.25 d) — enable/disable button per row.

## Test seams

- `AdminUserServiceTest` — self-target rejected; other user updated.
- `AdminUserIntegrationTest` — disabled user cannot log in (401).

## Dependencies

- Blocked by: 8
- Unblocks: —
