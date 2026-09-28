---
status: accepted
---

# ADR-033: Password lockout is never a wire code; the factor lock is, behind the password

A locked, disabled or capped password account gets exactly the same `401 AUTHENTICATION_FAILED` as a wrong password or
an unknown username. There is no "account locked" response. The second factor's locks are the opposite: they are
reported specifically, as `429` for a tier-1 lock and `423 FACTOR_DISABLED` for a tier-2 disable. A maintainer could
reverse either one. They could add a "locked" response at login because the standard lists one, or harmonise the
factor responses to match the password axis. Both would be wrong, for opposite reasons.

## Context

- The governing standard's §3.2 lists `account locked` (401) and, in the same bullet, requires that response to be
  identical to an invalid-credential response. The two clauses cancel each other. The next entry,
  `account cannot authenticate`, is the catch-all for disabled or locked accounts whose reason must not be revealed.
  The list mixes wire codes with log reasons.
- An implementer reading §3.2 alone would emit `account locked` and open an account-existence channel on the
  unauthenticated login endpoint. ASVS 5.0 **6.3.8 (L3)** forbids exactly that.
- The factor challenge is different. Every factor response goes to a session that has already passed the password
  factor, and it is about that session's own account (ADR-021). A specific answer tells the caller nothing they
  have not already proved.
- The factor locks also need the client to act differently. A tier-1 lock lifts by itself, so the client should
  wait. A tier-2 disable never lifts, and only factor rebinding by another administrator clears it (ADR-027). If the
  client is not told, it keeps offering a challenge that cannot succeed.

## Decision

- **Password axis.** Wrong password, unknown user, locked, disabled, authenticator-capped, not yet activated, and a
  forced-change credential past its deadline all return one `401 AUTHENTICATION_FAILED`. `type`, `title`, `status`,
  `detail` and `code` are constants, and `instance` is set to a constant rather than left to Spring, which fills it
  from the request URI. `traceId` is the only member that varies. The internal reason goes to the audit log at WARN.
- **Timing on the password axis** relies on the framework, not a response-time floor. `DaoAuthenticationProvider`
  already runs a dummy `matches()` for unknown users. Two rules keep it intact: no lockout check may run as a
  pre-authentication branch keyed on account existence, and no `PasswordEncoder` wrapper may skip `matches()`.
- **Factor axis.** A tier-1 lock returns `429 TOO_MANY_REQUESTS` with the factor member and `reason: LOCKED`, and an
  integer `Retry-After` taken from the lock's end. A tier-2 disable returns `423 FACTOR_DISABLED` on every surface
  that could otherwise invite a challenge: the factor state on the self-read, the entry point for the admin surface,
  and the verification endpoint (REJ-049).
- **The factor axis is only reachable behind the password.** The verification endpoint refuses an anonymous caller
  before it looks at the factor, and the username comes from the security context, never the request body. So
  every factor guess costs a password first, and the specificity leaks nothing to someone who does not hold one.

## Consequences

- The per-source 429 and the factor-lock 429 share a status and must stay distinguishable. The factor member is the
  discriminator, so the source limiter's 429 must never carry it. That is a negative assertion about a different
  envelope producer, and it needs its own test.
- `LOCKED` is a new member of the factor reason vocabulary, and `FACTOR_DISABLED` a new code in the closed enum. Both
  live in the error contract.
- A user whose account is locked is not told so. The cost is support calls. The audit log carries the reason, and the
  lock lifts by itself (ADR-011).
- Rejected: an `account locked` wire code, a `Retry-After` on the password axis (it would put account state on the
  wire before authentication), and harmonising the factor locks to a generic `401`, which the SPA would read as an
  expired session and turn into a re-login loop.
- Tests: T-AUTH-006 (one literal body across every account state and all three endpoints) and T-MFA-019 (the factor
  mapping), with T-MFA-020 and T-MFA-021 for the two tiers.

## Sources

- Standalone User Access Control Application Standard §3.2 Error Contract (`account locked`,
  `account cannot authenticate`), §2 Failure Path 4.
- OWASP ASVS 5.0, V6.3, 6.3.8 (L3).
- RFC 9110 (HTTP Semantics) §10.2.3 `Retry-After`; RFC 6585 §4 `429 Too Many Requests`.
- Spring Security reference, `DaoAuthenticationProvider` (dummy-password timing mitigation for unknown users).
