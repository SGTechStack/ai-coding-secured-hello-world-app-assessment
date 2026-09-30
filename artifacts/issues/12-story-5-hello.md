# 5: As a logged-in user, I want to see a personalized greeting, so that I can confirm my authentication actually worked.

`feature` · wave 4

| Effort | Float |
| --- | --- |
| 1.0 days | 3.0 days |

## Acceptance criteria

- Given an authenticated session, when the user requests `GET /api/hello`, then the response is `"Hello, <username>"`.
- Given no session (or an invalid/expired one), when a request is made to `GET /api/hello`, then the response is unauthorized (401).

## Dev tasks

1. `be_hello_routes` (0.25 d) — `HelloController` returning `{ "message": "Hello, <username>" }`.
2. `fe_hello_page` (0.5 d) — `HomePage` behind `RequireAuth` showing the greeting.

## Test seams

- `HelloIntegrationTest` — 200 with greeting when authenticated; 401 anonymous; 401 with bogus cookie.

## Dependencies

- Blocked by: 2
- Unblocks: —
