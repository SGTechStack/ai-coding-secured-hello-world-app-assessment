---
status: accepted
---

# ADR-057: Reset links are logged only in `dev`, by a non-audit logger with three enforcement controls

The stubbed `EmailService` writes the password-reset link to a dedicated application logger, never to the audit
stream, and that logger emits only under the `dev` profile. Three controls refuse it everywhere else. PRD Story 6
says the stub "logs the link instead of sending mail", with no profile condition, so a maintainer would plausibly
turn it on in every environment. A reset link is a bearer credential: whoever reads that log can take over the
account.

## Context

- PRD Story 6, second criterion: `EmailService.sendPasswordResetEmail(...)` is called, and the stub implementation
  logs the link instead of sending mail. Real mail delivery is out of the PRD's scope.
- The governing standard's §3.4 lists password reset token plaintext, and its hash, under what not to log. Its §5
  logging tests say logs never contain password reset tokens, and its test data guidance says never to log plaintext
  reset tokens "unless explicitly redacted and short-lived". The PRD and the standard conflict.
- The logging standard's §6 says not to use `/actuator/loggers` to change levels in production. Its §3.3 says level
  changes must not suppress required audit events. The same lever can switch a disabled logger on.
- A logging level can arrive by several paths that no configuration validator sees. Spring Boot binds `logging.level.*`
  from any property source, including environment variables and `SPRING_APPLICATION_JSON`. The environment-variable
  form is lowercased by relaxed binding, so it reaches only loggers whose names are all lowercase.
- Spring Boot's `LoggingSystem.getLoggerConfiguration(String)` returns a `LoggerConfiguration` whose
  `getEffectiveLevel()` reflects the level actually in force, whatever its source. It is Boot's public API, not a
  Logback internal.

## Considered options

- **Log the link in every profile (the PRD read literally).** Log read access becomes account takeover in production.
- **Do not log the link at all.** Fails the PRD, and leaves `dev` with no way to exercise the reset flow end to end.
- **Log it only in `dev`, on a separate non-audit logger, and refuse that logger elsewhere three ways (chosen).**

## Decision

- **The audit stream never carries the token or its hash.** Reset rows record issuance and redemption only.
- **The link goes to a dedicated application logger** that emits only under `dev`.
- **Control 1: a prohibited-configuration entry.** The refresh-phase validator refuses to start when that logger's
  property is set in a non-`dev` profile.
- **Control 2: an `ApplicationReadyEvent` check.** It reads the logger's effective level through
  `LoggingSystem.getLoggerConfiguration(...)` and refuses to run outside `dev` if the logger would emit. This catches
  every path control 1 cannot see. It deliberately avoids Logback's `LoggerContext`, to add no second non-public
  coupling that must be retested on every Boot upgrade.
- **Control 3: `/actuator/loggers` is absent or read-only outside `dev`**, so the level cannot be changed at runtime.
  ADR-061 already exposes only `health`. Boot's `management.endpoint.loggers.access=read-only` is the read-only form.
- The mandated startup row records which audit-relevant loggers were in force. It is evidence, not a control.

## Consequences

- **In `dev`, anyone reading the logs can take over any activated account** (R-CRED-020). For administrators the
  takeover is partial, because admin routes also need the TOTP factor authority. The deployment must attest that no
  real account ever exists under `dev`.
- **Outside `dev`, a reset request produces nothing deliverable** (R-CRED-021). Recovery there is an admin-issued
  reset token or the offline recovery runner (ADR-072). The PRD's goal for Story 6 holds in `dev` only.
- Tests: T-AUD-030 (the real stub's link reaches only the dev-only logger), T-CFG-009 (control 1), T-CFG-010
  (control 2, with an environment-variable override), T-CFG-011 (control 3), T-AUD-019 (no token or its hash in any
  appender across the reset flow).
- T-CFG-010 only bites if its override actually reaches the logger. The logger therefore needs an all-lowercase name,
  not a class name, or the test must set the level through `SPRING_APPLICATION_JSON` instead. Otherwise the check
  passes against a level that was never applied.

## Sources

- PRD, Story 6 (second acceptance criterion) and Out of scope (stubbed `EmailService`).
- Standalone User Access Control Application Standard §3.4 Logging Contract (What NOT to Log), §5 Logging and Audit
  Tests, §5 Test Data Guidance.
- Structured Logging Application Standard §3.3 Logging Contract, §6 Operational Runbook (runtime configuration note).
- Spring Boot reference, Logging, "Log Levels" (environment variables and relaxed binding; `SPRING_APPLICATION_JSON`
  for individual classes).
- Spring Boot reference, Actuator, Endpoints, "Controlling Access to Endpoints" (`management.endpoint.loggers.access`).
- Spring Boot API: `LoggingSystem#getLoggerConfiguration(String)`, `LoggerConfiguration#getEffectiveLevel()`.
