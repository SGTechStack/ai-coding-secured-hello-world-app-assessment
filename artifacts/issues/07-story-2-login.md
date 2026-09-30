# 2: As a registered user, I want to log in with my username and password, so that I can access my session and the protected app content.

`feature` · wave 3 · **critical path**

| Effort | Float |
| --- | --- |
| 2.3 days | Critical |

## Acceptance criteria

- Given a registered, enabled, non-locked account with correct credentials, when the user submits login, then a server-side session is created, a secure session cookie is set, and `failed_login_attempts` resets to 0.
- Given incorrect credentials, when the user submits login, then the request is rejected with a generic error message that does not reveal whether the username exists, and `failed_login_attempts` increments.
- Given an account that is currently locked (`locked_until` in the future), when the user submits login with correct credentials, then the request is still rejected until the lockout expires.

## Dev tasks

1. `be_auth_login_service` (1 d) — `LoginService` using `AuthenticationManager` (DaoAuthenticationProvider over `AppUserDetailsService`), `SessionAuthenticationStrategy` (change session id + CSRF token rotation), `SecurityContextRepository.saveContext`, success/failure hooks.
2. `be_auth_login_routes` (0.25 d) — `POST /api/auth/login`, `GET /api/auth/me`, `GET /api/auth/csrf`.
3. `fe_login_form` (0.5 d) — `LoginPage`, generic error display, redirect to `/` on success.

## Test seams

- `LoginIntegrationTest` — success sets cookie + resets counter; wrong password and unknown user produce identical 401 bodies; locked account with correct password → 401.

## Dependencies

- Blocked by: 1, INFRA-FE-03
- Unblocks: 3, 4, 5, 8
