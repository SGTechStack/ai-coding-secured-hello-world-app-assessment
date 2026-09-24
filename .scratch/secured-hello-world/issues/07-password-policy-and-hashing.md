# 07 — Decide the password policy and hashing parameters

Type: grilling
Status: open
Blocked by: 01, 02, 19

## Question

What are the exact password rules, and exactly how is a password hashed, given BCrypt is already the
chosen algorithm?

## Settled going in

BCrypt, by user decision. The PRD mandates it and the App Standard permits it ("BCrypt is acceptable
for existing systems") even while preferring Argon2id. The deviation from the standard's preference
needs an ADR, not a re-litigation.

## What to decide

**Hashing parameters.**

- BCrypt work factor. Check current OWASP guidance via "Verify the App Standard's controls are still
  current practice". Note the direct trade-off with the timing-attack mitigation in "Decide the API
  error envelope": a higher cost widens the gap between a fast unknown-user rejection and a slow
  password verification.
- **The 72-byte truncation problem.** BCrypt silently ignores input past 72 bytes. The PRD sets a
  12-character minimum and no maximum, so a long passphrase is silently truncated — two different
  passphrases sharing a 72-byte prefix become the same password. Decide: enforce a maximum length,
  pre-hash before BCrypt (and accept the known password-shucking caveat), or document the limit.
  A decision is required; leaving it implicit is the bug.
- Use `DelegatingPasswordEncoder` with an `{bcrypt}` prefix so the stored format is
  self-describing and a future algorithm migration is possible? Recommended, but confirm.

**Strength policy.**

- Minimum length: PRD says 12. Confirm against IM8 (via "Extract the IM8 and ARC controls") — IM8
  may demand more.
- Composition rules: the standard imposes them on *admin-generated* passwords (12 chars with
  lower, upper, digit, special). Do they also apply to user-chosen passwords? Current NIST guidance
  discourages composition rules — reconcile using the currency research.
- Breached-password screening: Spring Security's `CompromisedPasswordChecker` uses the Pwned
  Passwords k-anonymity API. NIST recommends screening; the standard omits it. Decide whether to
  include it, and whether an outbound call to a third-party API is acceptable under IM8. If it isn't,
  decide whether a bundled offline list is worth the weight.
- Maximum length, permitted character set, Unicode normalisation, and whether leading/trailing
  whitespace is trimmed or preserved. Small decisions that cause real bugs when left unstated.

**Password history.** The standard mandates 3. The currency research may report that NIST has moved
away from history requirements. If they conflict, the standard wins per the map's conflict rule, but
the ADR should record the tension. Decide what is stored (BCrypt hashes of prior passwords), how
comparison works (`matches()` against each historical hash — note this means N BCrypt verifications
per change, a deliberate cost), and the eviction rule.

**Where the policy lives.** One shared validator applied identically by registration, reset
redemption, and self-service change, so the three paths cannot drift.

## Done when

Work factor, truncation handling, strength rules, breached-password decision, and history mechanics
are all written down as values an implementer can configure.
