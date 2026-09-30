# INFRA-FE-03: Users can log in via the frontend

`infrastructure` · wave 2 · **critical path**

| Effort | Float |
| --- | --- |
| 0.7 days | Critical |

## Acceptance criteria

- The current session is loaded once from `GET /api/auth/me` and shared through an `AuthProvider`.
- `RequireAuth` redirects anonymous visitors to `/login`; `RequireAdmin` sends non-admins to `/`.
- Session state survives a page refresh (cookie-backed, re-queried on load).

## Dev tasks

1. `auth_ui` (0.5 d) — `useSession` query, `AuthProvider`, guards, header with user info.

## Dependencies

- Blocked by: INFRA-FE-01, INFRA-FE-02
- Unblocks: 2, 4, 5, 8
