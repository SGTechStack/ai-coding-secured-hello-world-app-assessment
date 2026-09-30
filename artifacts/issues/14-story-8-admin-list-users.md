# 8: As an admin, I want to see a list of all registered users, so that I can review who has access to the system.

`feature` · wave 4 · **critical path**

| Effort | Float |
| --- | --- |
| 2.3 days | Critical |

## Acceptance criteria

- Given an authenticated admin, when they call `GET /api/admin/users`, then the response lists each user's username, email, role, enabled status, and created-at date — never password hashes.
- Given an authenticated non-admin user, when they call `GET /api/admin/users`, then the response is forbidden (403).

## Dev tasks

1. `be_user_admin_service` (0.5 d) — `AdminUserService.listUsers` → `UserSummary` DTOs.
2. `be_user_admin_routes` (0.25 d) — `AdminUserController` under `/api/admin/users`, `@PreAuthorize("hasRole('ADMIN')")` plus filter-chain rule.
3. `fe_user_admin_list` (0.5 d) — `UserTable` component.
4. `fe_user_admin_page` (0.5 d) — `AdminUsersPage` behind `RequireAdmin`.

## Test seams

- `AdminUserIntegrationTest` — admin gets 200 without `passwordHash` field; USER gets 403 on every `/api/admin/**` method.

## Dependencies

- Blocked by: 2, 12
- Unblocks: 9, 10, 11
