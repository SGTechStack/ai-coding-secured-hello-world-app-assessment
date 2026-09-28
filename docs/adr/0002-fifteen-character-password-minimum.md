---
status: accepted
---

# ADR-002: 15-character password minimum, overriding the PRD's 12

Every password must be at least 15 characters, for every account, under one rule. PRD Story 1 says 12. A maintainer
holding the PRD would lower it, and anyone who knows administrators use TOTP might argue for 8. Both would put us
below a NIST `SHALL`.

## Context

- **PRD Story 1, first acceptance criterion:** length of at least 12.
- **NIST SP 800-63B-4 §3.1.1.2 Password Verifiers:** verifiers "SHALL require passwords that are used as a
  single-factor authentication mechanism to be a minimum of 15 characters in length". They "MAY allow passwords that
  are only used as part of multi-factor authentication processes to be shorter", but never below 8.
  - The often-quoted "SHALL 8, SHOULD 15" is draft-era wording. In the final text 15 is a `SHALL` for single-factor
    use. The PRD's 12 was below a requirement, not below a recommendation.
  - The relaxation is narrow. It is a MAY bounded by a SHALL-eight, and it covers only passwords used *solely* inside
    multi-factor processes.
- **ASVS 5.0 6.2.1 (L1)** requires at least 8 and strongly recommends 15. The Questions file (Q12) recommends 12,
  and 15 or more for high-security systems. IM8 as-5 asks only that a minimum length exist.

## Why 15 binds, by two independent routes

1. **Regular users are password-only.** TOTP is required for administrators and offered to no one else (ADR-023).
   A regular user's password is a single-factor mechanism, so 15 is mandatory for them outright. This alone decides
   the question, before administrators are even considered. The strictest floor is forced by the *least* privileged
   population.
2. **An administrator's password is a single-factor authenticator in recovery.** Break-glass recovery pairs an
   issued recovery code with the account password (ADR-070). Once the password serves in that role, the
   single-factor floor applies to administrators as well.

## Considered options

- **12, as the PRD states.** Below a `SHALL`.
- **8 for administrators, 15 for users.** Fails route 2 above, and adds a per-role rule that buys nothing.
- **15 for everyone (chosen).**

## Decision

- `app.security.password.min-length: 15`, counted in Unicode **code points** after NFC normalisation, as §3.1.1.2
  requires.
- One rule for every account, including the bootstrap seed credential. It is enforced inside the single
  password-setting component, so no path is exempt (ADR-005).
- A rejection returns `PASSWORD_REJECTED` with rule `MIN_LENGTH`.

## Consequences

- Users whose 12- to 14-character password the PRD would accept are refused. The PRD deviation is recorded in the
  deferral register (R-CRED-023).
- 15 is a floor a verifier may raise. A higher minimum length is **not** a composition rule, which §3.1.1.2 forbids
  separately. So this floor and ADR-005's rejection of character-class quotas are consistent.
- The lower bound counts characters and the upper bound (ADR-003) counts bytes, so the permitted band narrows for
  non-ASCII input.
- **Reopening trigger:** MFA extended to every account. Even then, route 2 keeps 15 for administrators while the
  password is their recovery companion.

## Sources

- PRD Story 1, first acceptance criterion.
- NIST SP 800-63B-4 §3.1.1.2 Password Verifiers; §4.2.2.2 (recovery with a recovery code plus a single-factor
  authenticator).
- OWASP ASVS 5.0, V6.2: 6.2.1 (L1).
- Standalone User Access Control Application Standard Questions, Q12.
- IM8 as-5, via the `im8-review` control catalogue.
