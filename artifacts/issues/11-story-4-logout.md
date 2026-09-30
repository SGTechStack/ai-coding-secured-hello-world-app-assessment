# 4: As a logged-in user, I want to log out, so that my session is fully ended and cannot be reused.

`feature` · wave 4

| Effort | Float |
| --- | --- |
| 0.7 days | 3.0 days |

## Acceptance criteria

- Given an active session, when the user calls logout, then the server-side session is invalidated and the session cookie is cleared.
- Given a session cookie captured before logout, when it is replayed after logout, then the server rejects it as unauthenticated.

## Dev tasks

1. `be_auth_logout_routes` (0.25 d) — Spring Security logout on `POST /api/auth/logout` (invalidate session, delete cookie, clear context, 204).
2. `fe_logout_widget` (0.25 d) — header logout button; clears query cache and CSRF token.

## Test seams

- `LogoutIntegrationTest` — after logout the old cookie yields 401 on `/api/hello`.

## Dependencies

- Blocked by: 2
- Unblocks: —
