# ADR-0001: Stub `EmailService` logs the plaintext reset link/token

## Status

Accepted

## Context

`assessment-prd.md` Story 6 requires the password-reset-request flow to call
`EmailService.sendPasswordResetEmail(...)`, with a stub implementation that
"logs the link instead of sending mail" (real SMTP is explicitly out of
scope for this build).

`LoggingEmailService` (`backend/src/main/java/com/example/auth/passwordreset/LoggingEmailService.java`)
implements this by writing the full reset link — including the raw,
single-use reset token as a query parameter — to the application log at
`INFO` level:

```java
log.info("Password reset link for {}: {}", toEmail, resetLink);
```

This is the only place the raw token exists outside memory: `PasswordResetService`
itself stores only a SHA-256 hash of the token in `password_reset_tokens.token_hash`
(see `PasswordResetService.issueResetToken`/`hash`), never the plaintext value.

Anyone who can read the application log can therefore recover a live,
usable reset link for any account that requests a reset, for the lifetime
of the token (currently 30 minutes, `app.security.password-reset.token-ttl`).

## Decision

Accept plaintext-token logging in `LoggingEmailService` as the intentional
behavior of the stub, scoped strictly to local/demo use where there is no
real email transport and no untrusted log reader. The alternative — masking
or omitting the link — would make the stub unable to serve its purpose
(letting a developer/tester complete the reset flow end-to-end without
SMTP).

This decision does **not** extend to any environment with untrusted or
broadly-shared log access, or to the point at which `LoggingEmailService`
is replaced by a real email transport.

## Consequences

- Enables full end-to-end testing/demo of the password-reset flow (Stories
  6–7) without standing up SMTP, matching the PRD's explicit scope.
- The reset token's confidentiality is only as strong as the application
  log's access control for as long as this stub is in use. This must not be
  treated as acceptable outside local dev/demo.
- **Hard follow-up, not optional cleanup:** when `LoggingEmailService` is
  replaced by a real `EmailService` implementation, the plaintext-link log
  line must be removed (or reduced to a non-identifying debug signal) as
  part of that change, not left behind as dead-but-harmless code.
- **Latent timing side-channel:** the registered-email branch of
  `PasswordResetService.requestReset` does strictly more work (token
  generation, hashing, a DB insert, and the `sendPasswordResetEmail` call)
  than the unregistered-email branch. Today the stub's extra cost is
  negligible (a single log call), so response timing doesn't leak account
  existence. Once real (network-bound) email sending replaces the stub,
  this gap becomes an observable timing oracle and should be re-evaluated —
  e.g. by making the unregistered-email path do equivalent dummy work, or
  by sending the real email asynchronously off the request path.
