# 7: As a user with a valid reset token, I want to set a new password, so that I can regain access to my account.

`feature` · wave 4

| Effort | Float |
| --- | --- |
| 2.0 days | 2.0 days |

## Acceptance criteria

- Given a valid, unexpired, unused reset token and a new password meeting the strength policy, when submitted to the password-reset-confirm endpoint, then the password is updated, the token is marked used, and all existing sessions for that user are invalidated.
- Given an expired token, when submitted, then the request is rejected and the password is not changed.
- Given a token that has already been used once, when submitted again, then the request is rejected (single-use enforcement).

## Dev tasks

1. `be_password_reset_confirm_service` (1 d) — `PasswordResetService.confirmReset`: hash lookup, expiry + used checks, policy, BCrypt, mark used, `SessionInvalidationService.invalidateAllForUser`, audit log.
2. `fe_password_reset_confirm_form` (0.5 d) — `ResetPasswordPage` reading `?token=` and posting the new password.

## Test seams

- `PasswordResetServiceTest` — expired, used and unknown tokens rejected; valid token updates hash and marks used.
- `PasswordResetIntegrationTest` — confirm invalidates an existing session (old cookie → 401).

## Dependencies

- Blocked by: 6, INFRA-BE-02
- Unblocks: —
