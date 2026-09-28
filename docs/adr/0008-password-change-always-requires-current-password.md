---
status: accepted
---

# ADR-008: A self-service password-change endpoint exists and always requires the current password

One endpoint, `PATCH /api/profile/password`, serves both self-service change and completion of a forced change. It
always requires the current password, with no exemption for forced change, and it always writes history. It checks
the current password directly, so a wrong guess never counts toward the lockout. The governing standard's
forced-change flow submits only a new password, and a maintainer following it would drop the check there. Another
would route the check through the `AuthenticationManager` because that is the idiomatic place to check a password.

## Context

- **The standard provides for a self-service endpoint.** §2 Happy Path step 13 describes a dedicated self-service
  endpoint taking the current and the new password. §2 Decision Logic and §3.1 Inputs / Outputs require the current
  password "even when the caller has an active session". §5 Self-Service Password Change Tests test it. ASVS 5.0
  **6.2.3 (L1)** requires both passwords on a change.
  - Two clauses read as if they forbid it and do not. §2 Decision Logic says password changes must use a dedicated,
    separate flow, "never through generic user profile updates". §4's Enforced Constraint names two *reset*
    endpoints. A dedicated change endpoint satisfies the first, and a change is not a reset.
- **The standard's forced-change flow omits the current password.** Its sequence diagram shows the client
  submitting only a new password to the change endpoint. §3.1 grants no such exemption. The standard contradicts
  itself (R-STD-025).
- **The recipe implements none of it.** In Standalone Self-Service Password and History Management,
  `ChangeCurrentUserPasswordCommand` holds only the new password. The current-password check is a disconnected
  snippet, no strength validation runs, and `revokeOtherSessions` is never called (R-STD-023).
- **Lockout is counted from authentication failure events** (ADR-010). Anything routed through the
  `AuthenticationManager` publishes them.

## Considered options

- **A separate forced-change endpoint with no current-password check, as the diagram shows.** A second endpoint and
  a second code path. Anyone holding a hijacked session of a flagged account could set a password without knowing
  the current one.
- **Verify the current password through the `AuthenticationManager`.** Every wrong guess would count toward the
  victim's lockout. Anyone holding a hijacked session could lock the owner out of their own account, turning a
  session compromise into a denial of service.
- **One endpoint, current password always, checked directly (chosen).**

## Decision

- `PATCH /api/profile/password` takes the current password and the new one, for both self-service change and
  forced-change completion.
- The current password is checked with `passwordEncoder.matches()` directly, not through the
  `AuthenticationManager`. The endpoint publishes no authentication events and does not feed the lockout counter.
  Failures are logged at `WARN`. The guessing risk is bounded by the per-source budget (ADR-010) and by the attacker
  needing a live session first.
- The check sits outside the password-setting component, which owns setting a password, not checking one. The flow
  is:
  1. verify the current password;
  2. set the new one through the password-setting component, which validates it and writes history (ADR-005);
  3. invalidate pending reset tokens (ADR-007);
  4. end every **other** session of the account (ADR-035);
  5. rotate the surviving session id without re-stamping the auth instant (ADR-038);
  6. notify the owner after commit.
- **No forced-change exemption.** The remaining forced-change cases are the bootstrap seed (operator-configured) and
  re-enable (the user's own prior password), so the caller demonstrably holds the current credential in both
  (ADR-046, ADR-047). Removing the exemption removes a code path, and history is written because the
  password-setting component always inserts.

## Consequences

- A change runs five BCrypt operations at cost 12, about 1.04 s on the reference measurement (ADR-001).
- The forced-change deviation from the standard's diagram is recorded in the deferral register (R-STD-025).
- A session holder who does not know the current password cannot change it, and cannot lock the owner out by trying.
- Tests: T-CRED-008 (a wrong current password changes nothing, invalidates no session, and does not move the failure
  counter); T-SES-012 and T-SES-013 (other sessions end, the acting one survives with a new id).

## Sources

- Standalone User Access Control Application Standard §2 Happy Path step 13, §2 Decision Logic, §2 Sequence Diagram
  (forced password change), §3.1 Inputs / Outputs, §4 Authentication and Security Design and Separation of Concerns,
  §5 Self-Service Password Change Tests.
- Recipe: Standalone Self-Service Password and History Management (`ChangeCurrentUserPasswordCommand`,
  `revokeOtherSessions`).
- OWASP ASVS 5.0, V6.2: 6.2.3 (L1).
- OWASP Cross-Site Request Forgery Prevention Cheat Sheet (re-authentication for security-critical operations).
