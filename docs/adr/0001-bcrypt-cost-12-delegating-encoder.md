---
status: accepted
---

# ADR-001: BCrypt at cost 12 behind `DelegatingPasswordEncoder`, not Argon2id

Passwords are hashed with BCrypt at cost 12, through Spring Security's `DelegatingPasswordEncoder` with
`idForEncode = "bcrypt"`. The governing standard prefers Argon2id or scrypt, and the admin recipe hard-codes
Argon2id. A maintainer holding either would switch. The reason we did not is the PRD's BCrypt mandate, not a
security argument, and this ADR says so rather than implying the two are equivalent.

## Context

- **The PRD mandates BCrypt** (Story 1, first acceptance criterion).
- **The governing standard prefers the alternatives.** §3.5 Password Policy says to prefer Argon2id or scrypt, and
  that BCrypt is acceptable "for existing systems". This is a new system, so the carve-out does not cover us. The
  Questions file (Q12) sets the floor we adopt: BCrypt "with cost factor ≥12".
- **The recipes disagree with each other.** Standalone Privileged User Administration and Password Reset hard-codes
  `new Argon2PasswordEncoder(16, 32, 1, 19456, 2)` and calls a single encoder mandatory. Standalone Self-Service
  Password and History Management says the default encoder is BCrypt, then recommends Argon2id.
- **OWASP ranks BCrypt as the legacy option.** The Password Storage Cheat Sheet puts Argon2id first (minimum 19 MiB,
  t=2, p=1), then scrypt, and says bcrypt should only be used in legacy systems where Argon2 and scrypt are
  unavailable, with a work factor of at least 10 and a 72-byte input limit. Its budget is under one second per hash.
- **NIST SP 800-63B-4 §3.1.1.2 names no algorithm.** It requires a salted password hashing scheme with a salt of at
  least 32 bits. The cost factor SHOULD be as high as practical without hurting verifier performance and SHOULD rise
  over time. A reference to the scheme and cost factor SHOULD be stored with each password.
- **The costs are concrete.** BCrypt's working state is about 4 KiB. It does not penalise GPU or ASIC attackers the
  way Argon2id's 19 MiB floor does, so cracking a stolen hash file is materially cheaper at equal verification
  latency. The 72-byte input ceiling is a BCrypt artefact, and it is what forces ADR-003's restriction on users.

## Considered options

- **Argon2id, as the standard and the admin recipe prescribe.** The better algorithm. Declined only because the PRD
  mandates BCrypt.
- **scrypt.** Declined for the same reason.
- **PBKDF2.** OWASP's choice when FIPS-140 validation is required. It is not required here.
- **A bare `BCryptPasswordEncoder`.** Stores no scheme reference, so a later algorithm change becomes a data
  migration. The IM8 as-6 check names `DelegatingPasswordEncoder` among its acceptable encoder beans.
- **Cost 10 (OWASP's floor).** Below the standard's Q12 floor of 12.
- **BCrypt at cost 12 behind `DelegatingPasswordEncoder` (chosen).**

## Decision

- `DelegatingPasswordEncoder` with `idForEncode = "bcrypt"`. Stored hashes take the form `{bcrypt}$2a$12$…`, which
  satisfies NIST's scheme-and-cost reference. Moving to Argon2id later is a code change that re-hashes on the next
  successful login, with no data migration. The credential column is `VARCHAR(255)`, which leaves room for an
  Argon2id hash.
- Cost 12, bound from `app.security.password.bcrypt-strength`.
- The dummy hash behind the framework's timing mitigation carries the `{bcrypt}` prefix at the **same cost** as live
  hashes. A cheaper dummy reopens the timing gap described in CVE-2025-22234.
- `matches(raw, null)` is guarded. Spring's encoder throws `IllegalArgumentException` on a null stored hash rather
  than returning false.
- Encoding input over 72 bytes throws. That behaviour is the fix for CVE-2025-22228, where `matches` returned true
  for passwords over 72 characters whenever the first 72 matched. CVE-2025-22234 is the consequence of that fix: the
  early exit broke the timing mitigation in `DaoAuthenticationProvider`. So length is validated at set time, before
  the encoder, and never on the login path (ADR-003).

## Measurement

The work factor is only meaningful with the machine it was measured on.

| Cost | Hash (median) | Verify (median) |
| --- | --- | --- |
| 10 | 52 ms | 52 ms |
| 11 | 104 ms | 104 ms |
| 12 | 208 ms | 207 ms |
| 13 | 416 ms | 418 ms |

Measured September 2026 on an Intel Core i9-12900H laptop (14 cores, 64 GB RAM), Windows 11 Pro, Temurin
21.0.6, `spring-security-crypto` 7.0.6 (the `BCrypt` class), single-threaded, 15 runs per cost after warm-up
(7 at cost 13).

The number that binds is not a single verify. The costliest request is a self-service password change: one verify
of the current password, three history verifies and one encode, so five operations, about **1.04 s** at cost 12.
Activation and reset redemption run four (about 0.83 s). Login runs one. Cost 13 would take the change request to
about 2.1 s.

This is a developer laptop, not the target hardware. The figures are a lower bound on latency for a typical server
core and must be taken again on the deployment host.

## Consequences

- The deviation from the standard's preference is recorded in the deferral register (R-CRED-017).
- The 72-byte ceiling (ADR-003) and the declined pepper (ADR-004) follow from this choice.
- Five BCrypt operations per change, and four per redemption, make these the most expensive authenticated and
  anonymous requests in the application. The per-source limits (ADR-010) run before the password pipeline for that
  reason.
- Tests: T-CRED-025 binds the production encoder to cost 12 from configuration. T-AUTH-003 counts `matches()` calls
  to prove the timing mitigation runs on every provider path.
- **Reopening triggers:** the PRD's BCrypt mandate is lifted (switch `idForEncode` to Argon2id, and raise ADR-003's
  ceiling); or a measurement on the deployment host puts a single verify above one second or the change request well
  above two seconds.

## Sources

- PRD Story 1, first acceptance criterion.
- Standalone User Access Control Application Standard §3.5 Password Policy; §6 Operational Runbook, password policy
  configuration.
- Standalone User Access Control Application Standard Questions, Q12.
- Recipes: Standalone Privileged User Administration and Password Reset (§3 step 1); Standalone Self-Service Password
  and History Management.
- NIST SP 800-63B-4 §3.1.1.2 Password Verifiers.
- OWASP Password Storage Cheat Sheet: algorithm ranking, bcrypt work factor and input limit, upgrading the work
  factor.
- Spring Security reference, Password Storage (`DelegatingPasswordEncoder` storage format).
- CVE-2025-22228 and CVE-2025-22234, Spring Security advisories.
- IM8 as-6, via the `im8-review` control catalogue.
