# 13 — Build the audit event catalogue

Type: grilling
Status: open
Blocked by: 03, 09, 10, 11

## Question

What is the complete list of audit events this app emits, and for each one: the exact fields, the log
level, and which requirement it satisfies?

## Starting point

The App Standard §3.3 and §3.4 give the required events and the shape. Every audit event must carry
timestamp, principal, outcome, request path, HTTP method, and correlation ID. Required events:

- Login success, login failure, logout success
- Lockout transitions (locked, unlocked)
- User account operations — create, unlock, update, delete — attributable to the acting admin
- Password reset token issuance and redemption
- Password changes, both admin-initiated and self-service
- Role assignments and changes
- Administrative deletion, preserving a tombstone record

Levels are prescribed: INFO for successes, WARN for failed logins, lockout transitions, rate-limit
breaches and authorization failures, ERROR for authentication system failures.

## What to decide

- The catalogue itself: one row per event with `event.action`, `event.outcome`, level, fields, and
  the PRD story or standard clause it satisfies. The exact `event.action` vocabulary comes from
  "Extract the binding structured logging and audit schema" — use it rather than coining names.
- **Actor and target identification.** The PRD wants admin actions logged with actor and target. The
  logging standard bans cleartext usernames and emails and mandates UUID `user.id`. Decide the field
  names for actor and target UUIDs — ECS has `user.id` for one subject, so a second subject needs a
  deliberate choice (`user.target.id`? a custom field?). This is the sharpest open question here.
- **Events with no resolved user.** A failed login for an unknown username has no UUID. The standard
  says omit user identity and rely on `session.hash` and `trace.id`. Confirm, and make sure that
  doesn't make failed logins useless for investigation — decide what *is* recorded so a brute-force
  campaign is still reconstructable without logging the attempted username.
- Where audit emission lives: a typed audit component (the logging standard has a recipe for
  centralising it) or inline calls. A central component is testable and makes "did we log it?" a
  real assertion.
- Correlation: how `trace.id` is generated and propagated, and how `session.hash` is computed.
- **Retention.** The standard requires 90 days retained durably. The PRD says structured log lines,
  no audit table. Decide whether log lines alone can satisfy durable 90-day retention with no
  infrastructure in scope, or whether this becomes a documented deferral. Check what "Extract the IM8
  and ARC controls" found — if IM8 requires a durable audit store, log lines fail and this needs the
  user's decision.
- The negative list, as testable assertions: never log passwords, reset token plaintext, reset token
  **hashes**, CSRF tokens, or raw session IDs.

## Done when

The catalogue is complete and each row is specific enough to write a log assertion against, the
actor/target field naming is decided, and the retention question is answered rather than deferred by
omission.
