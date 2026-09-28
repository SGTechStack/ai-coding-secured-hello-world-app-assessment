---
status: accepted
---

# ADR-035: Self-service credential change terminates the user's other sessions, not the current one

When users change their own password, through the self-service endpoint or by completing a forced change, every
**other** session of that account ends. The session they made the change in survives, with a new session id. The
governing standard says "invalidates all active sessions" for both flows. A maintainer reading it would make the
change log the user out of the tab they just used.

## Context

- The standard's §2 Happy Path step 13 (self-service change), its sequence diagram (forced change) and §5
  Self-Service Password Change Tests all say "all sessions".
- The recipe contradicts itself here, which is the best available evidence of what its authors meant. Standalone
  Self-Service Password and History Management declares `revokeOtherSessions(String username)` and then deletes
  every session `findByPrincipalName` returns, the caller's included. The method name states an intent its body does
  not implement.
- OWASP ASVS 5.0 **7.4.3 (L2)** asks for the option to terminate all *other* active sessions after a change of any
  authentication factor. ASVS **7.2.4 (L1)** requires a new session token on re-authentication.
- The distinction only matters where the actor and the subject are the same principal. When an administrator acts
  on another account, "all" and "all others" are the same set. The standard forbids the self-acting admin cases
  that would blur this (self-demotion, self-deletion, self-unlock).

## Decision

| Trigger | Sessions ended |
| --- | --- |
| Self-service password change | all others; the current one survives with a rotated id |
| Forced-change completion | all others; the current one survives with a rotated id |
| Password reset redemption | all (the redeemer is anonymous and holds none) |

Every other trigger is in ADR-037. The surviving session's id is rotated with the minimal strategy, which does not
re-stamp the auth instant (ADR-038).

The kill is automatic rather than offered to the user. ASVS 7.4.3 would be satisfied by an offer. The automatic kill
is stronger, and it is what the standard's "all sessions" is trying to achieve.

## Consequences

- A user who changes their password in one browser stays signed in there and is signed out everywhere else. With one
  session per account and the newest login winning, other sessions are usually already displaced. The rule still
  matters for displaced sessions whose rows have not yet been removed (eviction is lazy), and for a login that races
  the change.
- The absolute lifetime keeps running from the original sign-in. Changing a password does not extend it.
- The deviation from the standard's wording is recorded in the deferral register.
- Tests: T-SES-012 and T-SES-013 capture both sessions, make the change on one, and assert that the other's replayed
  cookie returns 401 while the acting session survives with a new id. T-SES-014 covers reset redemption ending all.

## Sources

- Standalone User Access Control Application Standard §2 Happy Path step 13, Sequence Diagram (forced password
  change), §5 Self-Service Password Change Tests.
- Recipe: Standalone Self-Service Password and History Management, `revokeOtherSessions`.
- OWASP ASVS 5.0, V7.2 and V7.4: 7.2.4 (L1), 7.4.3 (L2).
