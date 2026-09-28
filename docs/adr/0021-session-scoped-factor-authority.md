---
status: accepted
---

# ADR-021: Session-scoped factor authority instead of per-request possession proof, with bounded validity

A successful TOTP check grants a timestamped `FACTOR_TOTP` authority that lives in the session. Admin requests are
authorised against that authority. They are not re-proved with a fresh code on each request, which is what the
MFA_Core standard prescribes. Two rules bound the authority: admin **mutations** accept it for 10 minutes, and admin
**reads** accept it for the absolute session lifetime. The read rule's duration is **not a time bound**. It is a
fail-closed type guard that can never fire in normal operation. A reviewer seeing `validDuration(8h)` would grade it
as an 8-hour factor bound, and a maintainer would either delete it as pointless or shorten it as a fix. Both are
wrong.

## Context

- **IM8 ac-2** requires MFA for privileged access, with no N/A branch. It names two halves: privileged account
  logins, and privileged actions, including changing account credentials or MFA settings.
- **MFA_Core §4.1 (Provider Dispatch)** is an enforced constraint: "On each request the enforcement layer iterates
  through all present providers and calls `authenticate()` on each in sequence." That is a per-request
  possession-proof model. Every protected request carries a code.
- The corpus's only concrete per-request mechanism is the `MFA_Critical_Transaction` pattern: an AOP aspect and an
  `X-TOTP` header on each business request, with `lastUsedCounter` replay rejection. It does not fit here, for three
  reasons:
  - The MFA router (`Appfw-Mfa-Standards/index.md`) sends "MFA TOTP Standalone on Login" through `MFA_Core` and
    `MFA_Frontend/Standalone` only. `MFA_Critical_Transaction` is a different feature row, and `MFA_Core` contains no
    AOP or annotation-interception requirement. So we are not rejecting a prescribed enforcement layer. We are
    building login-path enforcement that the router prescribes and the corpus never specifies.
  - Replay rejection allows one success per 30-second time step. Paging through an admin list would exhaust codes
    during ordinary work.
  - A per-transaction check closes only the privileged-action half of ac-2, and it has no verification endpoint to
    show a separate second login step.
- **Spring Security 7.1 has first-class MFA.** `FactorGrantedAuthority` carries `getIssuedAt()`, and
  `AllRequiredFactorsAuthorizationManager` checks each `RequiredFactor` by authority and optional `validDuration`.
  Spring Security has no TOTP provider, so the provider, token and processing filter are ours.
- The corpus's own implementation notes release us from its request-context and command patterns ("Implementors may
  use any equivalent mechanism"). So re-homing verification into a Spring `AuthenticationProvider` is licensed, not a
  deviation. One trap: the corpus's `MultiFactorAuthenticationProvider` is a different, undefined type with a
  no-argument `void authenticate()`. It is **not** `org.springframework.security.authentication.AuthenticationProvider`,
  despite the name, and nothing here conforms to it.
- From the 7.1.x source of `AllRequiredFactorsAuthorizationManager`:
  - when `validDuration` is `null`, a factor is granted as soon as any authority's **string** matches;
  - the private `getFactorGrantedAuthorities` returns **all** of the principal's authorities, not only
    `FactorGrantedAuthority` instances, despite its javadoc;
  - when `validDuration` is non-null and the matching authority is not a `FactorGrantedAuthority`, the result is
    `RequiredFactorError.createExpired(...)`.

  So an unbounded rule is satisfied by a plain `SimpleGrantedAuthority("FACTOR_TOTP")`, for example one that
  survived a degraded serialisation round trip. A bounded rule is not.

## Decision

- **Per-session, not per-request.** The TOTP verification filter grants `FACTOR_TOTP` into the session. The grant
  replaces the `Authentication`, because the rule reads the first matching authority (ADR-038).
- **Admin mutations** (`/api/admin/**`, any method other than `GET`) require `FACTOR_PASSWORD` and a `FACTOR_TOTP`
  issued within **10 minutes**. A burst of mutations runs on one verification, and a stale session re-verifies. This
  is the compensating bound on the half of the surface that changes state.
- **Admin reads** (`GET /api/admin/**`) require both factors with `validDuration` **equal to the absolute session
  lifetime**. It is there to make a degraded authority fail closed, like the mutation rule does. It cannot fire in
  normal operation: the factor is always granted after `AUTH_INSTANT`, so it always expires after the session does.
  The real bounds on reads are the session's 15-minute idle and 8-hour absolute limits.
- **A startup assertion** refuses a read duration shorter than the absolute session lifetime (T-CFG-019). If the
  session lifetime were ever raised past it, admins would be bounced to the challenge mid-session with no code
  change to blame.
- Both rules read the **same `Clock` bean** that drives the TOTP time-step counter (T-MFA-008).
- The rules themselves are hand-composed, role first (ADR-026).

## Considered options

- **Per-request codes, as MFA_Core §4.1 prescribes.** Rejected. A fresh six-digit code for every page of an admin
  list is unusable, and replay rejection makes it unworkable within one time step.
- **Unbounded read rule (`validDuration` null).** The original design. Rejected, because it fails **open** when a
  plain authority carries the factor string, while the 10-minute rule fails closed.
- **A real time bound on reads, shorter than the session.** Rejected. It re-prompts during read-only work and adds
  no assurance that the session's own limits do not already give.
- **`MFA_Critical_Transaction` enforcement (AOP aspect plus `X-TOTP`).** Not on our router path, and rejected on
  the code-exhaustion and ac-2 grounds above.

## Consequences

- **A recorded deviation from MFA_Core §4.1** (R-MFA-012). A session that has proved the factor once can read the
  admin surface without a code for the rest of that session. The 10-minute rule limits the exposure on mutations.
- An unenrolled or unverified admin cannot see the admin surface at all. That is as close to login-level
  enforcement as a cookie-session SPA gets, and it gives `im8-review` a genuine two-step admin login to find.
- Because every factor response goes to a session that already holds the password factor, and is about that
  session's own account, factor responses may be specific without opening an enumeration channel (ADR-033).
- The corpus's success signal ("did not throw") and exception types do not carry over. Its
  `MFAAuthenticationException` is not an `AuthenticationException` and would never reach Spring Security's failure
  handling. The success path, the authority grant and the error translation are all ours.
- The privileged-action scope reading of ac-2 is recorded separately (R-MFA-009).
- Tests: T-MFA-002 (the factor matrix over every admin route), T-MFA-005 (the authority survives the JDBC session
  as a `FactorGrantedAuthority`, and a plain `SimpleGrantedAuthority("FACTOR_TOTP")` is denied on both rules),
  T-MFA-008, T-CFG-019.

## Sources

- IM8 ac-2 (MFA for privileged account logins and privileged actions).
- Unified MFA Application Standard (`Appfw-Mfa-Standards/MFA_Core`) §4.1 Provider Dispatch; the MFA recipes'
  Implementation Notes (request context and command patterns not mandated); `Appfw-Mfa-Standards/index.md` (feature
  routing).
- Spring Security 7.1.x source: `AllRequiredFactorsAuthorizationManager` (`requiredFactorError`,
  `getFactorGrantedAuthorities`), `RequiredFactor` (`validDuration`), `FactorGrantedAuthority` (`getIssuedAt()`).
- Spring Security reference, Multi-Factor Authentication (servlet); spring.io blog, "Multi-Factor Authentication in
  Spring Security 7" (21 October 2025).
- RFC 6238 §5.2 (one-time use of a verified value).
