# ADR-0001: Dev-only stub `EmailService` logs the reset link

## Status

Accepted

## Context

PRD Story 6 requires the reset-request flow to call
`EmailService.sendPasswordResetEmail(toEmail, resetLink)`, with a stub that "logs the link
instead of sending mail". Real SMTP is out of scope. The raw single-use token exists only in that
link, because `PasswordResetService` stores just its SHA-256 hash. So anyone who can read the log
can use the link until it expires (30 min, `app.security.password-reset.token-ttl`).

## Decision

- `LoggingEmailService` is the only `EmailService`, and it is `@Profile("dev")`. It logs one ECS
  line, `DEV ONLY - password reset link`, with the link in `labels.reset_link`. It does **not**
  log the recipient address (App-Standards LOG forbids emails in logs).
- There is deliberately no fallback bean. Under `prod` the application fails to start until a
  real mail transport is implemented, so a reset link can never be logged in production.
- `PasswordResetService` publishes an event, and `PasswordResetEmailSender` sends the email with
  `@Async @TransactionalEventListener`, after commit and off the request thread. Sending never
  delays the response, so response timing doesn't reveal whether the address is registered.

## Consequences

- The full reset flow (Stories 6–7) can be exercised locally without SMTP: copy the link from the
  backend console.
- Dev logs contain live reset links and must be treated as confidential.
- Production readiness requires a real `EmailService`. Its implementation must not log the link
  or the address. Failures are logged by `PasswordResetEmailSender` without either.
