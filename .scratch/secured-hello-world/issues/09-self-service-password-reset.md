# 09: Self-service password reset

**What to build:** An Account holder who forgot their password enters their email on the forgot-password screen and always sees the same message, in the same time, whether or not the email is registered. The emailed link opens the reset screen, which reads the Reset Token from the URL fragment and clears it from the address bar. They set a new password, every Session ends, and they are notified. A token works once, expires after 30 minutes, and is cancelled by a newer request or by a Password Change. See the spec's stories 48, 50–62 and 103, "Password reset service", "Rate limiters", "Schema", and the Further Notes on the stub. Use `CONTEXT.md`'s Reset Token.

**Blocked by:** 07, 08, 11 (the Disabled-Account case needs the API to disable an Account)

**Status:** resolved

- [x] A Flyway migration creates `password_reset_tokens` (id, Account FK, unique SHA-256 `token_hash`, `expires_at`, nullable `used_at`). Tokens are never deleted.
- [x] `POST /api/password-reset/request` with `{email}` always returns 202 with a generic message, before any lookup.
- [x] Token issuance and the email run on a background executor whose `TaskDecorator` copies the trace and correlation IDs into the task and clears them afterwards.
- [x] Issuance generates at least 32 random bytes from `SecureRandom`, URL-safe encoded, and stores only the SHA-256 hash with a 30-minute expiry (configurable). It cancels any earlier pending token, and issues nothing for unknown or Disabled Accounts.
- [x] `EmailService` gains a "reset link" operation and a "reset completed" operation. The emailed link is `<frontend-origin>/reset-password#token=<token>`, written in full to the stub's own file (ADR 0001).
- [x] `POST /api/password-reset/confirm` with `{token, newPassword}` returns 200. It applies the Credential policy and Password History, marks the token used, ends every Session, leaves any active lock in place, and sends the "reset completed" notification.
- [x] An expired, used or unknown token returns 400 `token_invalid` with `detail` "password reset token expired or invalid". Policy and history failures return the same errors as Password Change.
- [x] A successful Password Change cancels any pending Reset Token.
- [x] Reset requests are limited to 3 per hour per email and 10 per hour per IP. Confirmations are limited to 10 per minute per IP (ADR 0001 explains why not per Account). All return 429 in the standard format.
- [x] No log line (application, audit or anything other than the stub's own file) contains the token or its hash.
- [x] Audit events: `password-reset` issuance and completion (INFO), and failures and limit breaches (WARN).
- [x] SPA: a forgot-password screen and a reset-password screen (which reads `#token=` and then calls `history.replaceState`), both with "Confidential" labels. Success goes to the login page with a message.
- [x] Tests (Clock seam, with the token taken from the recording `EmailService` after a bounded wait) cover: the token works once; it expires after 30 minutes; a new request cancels the old token; a Password Change cancels a pending token; the reset ends existing Sessions; a lock remains after reset; a Disabled Account gets no token and no email with an identical response; the request returns 202 before the email is recorded; `token_invalid` wording; each limiter's 429; the completion notification; and no token or hash in the audit or application logs.

## Comments

### Verification (2026-09-30)

**Implemented:** `PasswordResetController`/`PasswordResetService` add `POST /api/password-reset/request`, which runs email/IP rate-limit checks synchronously (so 429 stays fast) but returns 202 with an identical generic message before any Account lookup; issuance and email dispatch are offloaded to a dedicated `passwordResetExecutor` (`PasswordResetExecutorConfig`) decorated with `MdcCopyingTaskDecorator`, which copies trace/correlation IDs into the task and clears them afterwards. Issuance (`PasswordResetToken`/`PasswordResetTokenRepository`) generates 32 `SecureRandom` bytes, URL-safe unpadded Base64-encodes them for the emailed token, and persists only the SHA-256 hash with a configurable 30-minute expiry (`app.password-reset.token-expiry`); it cancels the Account's earlier pending token via `PasswordResetTokenCanceller` and issues nothing for unknown or Disabled Accounts. `EmailService` gained `sendPasswordResetLink`/`notifyPasswordResetCompleted`; the link `<frontend-origin>/reset-password#token=<token>` is written in full only under the stub's own `email` logger (ADR 0001), confirmed absent from every other log. `POST /api/password-reset/confirm` returns 200, runs the new shared `PasswordUpdater` (Credential policy + Password History, also now used by `PasswordChangeService`), marks the token used, ends every Session via `SessionControl.endAll`, leaves `locked_until` untouched, and sends the completion email. Expired, used and unknown tokens all return 400 `token_invalid` with identical wording, so none is distinguishable from another. A successful Password Change now calls the same `PasswordResetTokenCanceller`. Three new rate limiters (3/hour per email, 10/hour per IP for request; 10/minute per IP for confirm) return 429 in the standard format. Audit emits INFO `password-reset` for issuance and completion and WARN for `token_invalid`/policy/history failures and limit breaches. The SPA adds `ForgotPasswordPage` and `ResetPasswordPage` (Confidential labels via the shared `Field` component), with the reset page reading `#token=` and clearing it via `history.replaceState` in a `useEffect`; both land on `/login` with a status message, and `LoginPage` gained a "Forgot password?" link.

**Deviations and decisions:** None beyond what's already recorded in `docs/agents/reviewer-decisions.md`; the confirm limiter stays IP-only, not per-Account, per ADR 0001.

**Known gaps:** None identified by the reviewer.

**Verification steps:** `mvn verify` passed: 262 backend tests (0 failures), including the new 22-test `PasswordResetApiTest` covering single-use, 30-minute expiry boundary (29 vs 30 min), cross-cancellation with Password Change, session-end, lock-preserved, Disabled-Account parity, the 202-before-email-recorded ordering via a deterministic block/release gate, `token_invalid` wording, all three limiters' 429s, the completion notification, and no token/hash in any captured log line. JaCoCo coverage ~96% (gate 80%). Frontend: vitest 127/127 passing, `tsc -b`, oxlint and prettier clean. The reviewer loop passed on the first round with Must-fix 0 and Human decisions 0. KB retrieval and the code-reviewer compliance gates were skipped by request; mutation testing was skipped.

**Checklist:** all acceptance-criteria boxes ticked.

Commit: `025c765 feat(auth): Add self-service password reset`
