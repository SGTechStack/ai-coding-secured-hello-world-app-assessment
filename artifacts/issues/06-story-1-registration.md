# 1: As a visitor, I want to register an account with a username, email, and password, so that I can log in and access the protected app.

`feature` · wave 2 · **critical path**

| Effort | Float |
| --- | --- |
| 3.6 days | Critical |

## Acceptance criteria

- Given a visitor submits a unique username, unique email, and a password meeting the minimum strength policy (length ≥ 12), when they submit registration, then an account is created with role `USER`, `enabled = true`, and the password stored as a BCrypt hash.
- Given a visitor submits a username or email that's already registered, when they submit registration, then the request is rejected with a clear validation error (username/email conflict), and no account is created.
- Given a visitor submits a password that fails the strength policy, when they submit registration, then the request is rejected with a validation error and no account is created.
- Given any registration attempt, when handled, then the plaintext password is never logged or stored.

## Dev tasks

1. `be_user_db_schema` (0.5 d) — `User` entity + `Role` enum (fields per PRD data model).
2. `be_user_data_access` (0.5 d) — `UserRepository` with username/email lookups and existence checks.
3. `be_user_registration_service` (1 d) — `RegistrationService`: uniqueness, `PasswordPolicy`, BCrypt, audit log (username only).
4. `be_user_registration_routes` (0.25 d) — `POST /api/auth/register` with `@Valid RegisterRequest`.
5. `fe_register_form` (0.5 d) — `RegisterPage` with client-side length hint and server error mapping.

## Test seams

- `RegistrationServiceTest` (unit, Mockito) — happy path, duplicate username/email, weak password.
- `RegistrationIntegrationTest` (`@SpringBootTest` + MockMvc) — 201 / 409 / 400 and hash stored.

## Dependencies

- Blocked by: INFRA-BE-01, INFRA-BE-02, INFRA-FE-01, INFRA-FE-02
- Unblocks: 2, 6, 12
