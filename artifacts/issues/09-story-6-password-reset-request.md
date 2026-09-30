# 6: As a user who forgot their password, I want to request a password reset via my registered email, so that I can regain access without contacting an admin.

`feature` · wave 3

| Effort | Float |
| --- | --- |
| 3.9 days | 2.0 days |

## Acceptance criteria

- Given a request with an email address, when submitted to the password-reset-request endpoint, then the response is a generic success message regardless of whether the email is registered — so account existence cannot be inferred.
- Given the email matches a registered user, when the request is processed, then a single-use reset token is generated, its hash (not the plaintext token) is stored with a short expiry (15–30 min), and `EmailService.sendPasswordResetEmail(...)` is called (stub implementation logs the link instead of sending mail).

## Dev tasks

1. `be_password_reset_db_schema` (0.5 d) — `PasswordResetToken` entity.
2. `be_password_reset_data_access` (0.5 d) — `PasswordResetTokenRepository` (find by hash, delete by user).
3. `be_notification_integration` (0.25 d) — `EmailService` interface + `LoggingEmailService`.
4. `be_password_reset_request_service` (1 d) — `PasswordResetService.requestReset`: SecureRandom token, SHA-256 hash, expiry from config, audit log.
5. `be_password_reset_routes` (0.25 d) — `POST /api/auth/password-reset/request`.
6. `fe_password_reset_request_form` (0.5 d) — `ForgotPasswordPage` always showing the generic confirmation.

## Test seams

- `PasswordResetServiceTest` (unit) — unknown email is a silent no-op; known email stores hash (not token) and calls the email stub.
- `PasswordResetIntegrationTest` — both known and unknown email return 200 with the same body.

## Dependencies

- Blocked by: 1
- Unblocks: 7
