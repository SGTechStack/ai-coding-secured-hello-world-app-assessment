---
status: accepted
---

# ADR-034: A failed login does not invalidate an existing session

A failed login returns the uniform `401` and leaves any session the account already holds untouched. The governing
standard's Failure Path 1 says that a login with invalid credentials is rejected "and any existing session is
invalidated". A compliance reviewer will look for that behaviour and find it missing on purpose.

## Context

- Read literally, Failure Path 1 lets anyone who knows a username end that user's live session by posting one wrong
  password. That is an unauthenticated denial of service, and it sits beside the standard's own concern, in Failure
  Path 2, about lockout being used for mass denial of service.
- No other clause supports it. Happy Path step 5 describes the invalid-credential branch as "the login fails, the
  failed-login counter increments, and a generic error is returned", with no invalidation, and ties invalidating a
  previous session to the **valid** branch. Failure Path 8 ("a second login by the same user invalidates the earlier
  session") and §5's session tests say the same.
- The failure also does not reliably belong to the session's owner. The login request is anonymous. Nothing
  connects the person who typed the wrong password to the person holding the live session.

## Decision

Invalidate earlier sessions only on successful authentication, through the concurrent-session control (one session
per account, the new login wins). A failed login increments the failure counter and may lock the account. It never
touches existing sessions.

## Consequences

- **The session-ending power moves to lockout, at five times the price.** Lockout does end the account's sessions
  (ADR-037), so an attacker who knows a username can still end a live session by driving the account into lockout:
  five consecutive failures inside the observation window (ADR-012) rather than one request. That path is
  per-account throttled, audited as a lockout, and already present under the standard's own lockout rule, which this
  ADR does not change. What this ADR removes is the one-request kill.
- The deviation is recorded in the deferral register as a defect in the standard.
- Reopening trigger: a change that lets a failed login be attributed to the session owner, such as a failure
  submitted from inside an authenticated session. That case would need its own rule.

## Sources

- Standalone User Access Control Application Standard §2 Happy Path step 5, Failure Paths 1, 2 and 8; §5
  Authentication and Session Tests.
