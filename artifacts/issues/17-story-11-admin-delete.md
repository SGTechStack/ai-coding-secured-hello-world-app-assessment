# 11: As an admin, I want to delete another user's account, so that I can remove accounts that should no longer exist.

`feature` · wave 5

| Effort | Float |
| --- | --- |
| 1.3 days | 0.0 days |

## Acceptance criteria

- Given an admin targets another user's account, when they call the delete endpoint, then the account is removed.
- Given an admin targets their own account via the delete endpoint, when the request is made, then it is rejected — an admin cannot delete themselves.

## Dev tasks

1. `be_user_delete_service` (0.5 d) — `AdminUserService.deleteUser` with self-guard, deletes reset tokens and sessions; `DELETE /api/admin/users/{id}`.
2. `fe_user_delete_modal` (0.5 d) — confirm dialog before delete.

## Test seams

- `AdminUserServiceTest` — self-target rejected; other user removed with tokens.
- `AdminUserIntegrationTest` — self delete → 400; other → 204 then gone from list.

## Dependencies

- Blocked by: 8
- Unblocks: —
