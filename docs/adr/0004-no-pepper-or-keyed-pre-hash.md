---
status: accepted
---

# ADR-004: No pepper or keyed pre-hash

Password hashes carry no pepper: no keyed pre-hash, no keyed second pass, no encryption layer. NIST SP 800-63B-4
says verifiers SHOULD add one, and OWASP recommends one as defence in depth. A maintainer would add it. We decline a
`SHOULD`, and this ADR names it as a declined `SHOULD` rather than hiding it behind the rotation argument alone.

## Context

- **NIST SP 800-63B-4 §3.1.1.2** says verifiers SHOULD perform an additional keyed hashing or encryption operation
  with a secret key known only to the verifier. If used, the key SHALL be stored separately from the hashed
  passwords, and SHOULD be stored and used within a hardware-protected area such as an HSM or a TPM-backed TEE.
- **The OWASP Password Storage Cheat Sheet** recommends considering a pepper as defence in depth. It notes that a
  pepper cannot be changed without knowing each user's password, so changing one means forcing a reset for everyone
  it protected.
- **A key facility exists.** The TOTP seed is encrypted under an environment-supplied key held outside the database
  (ADR-022). So "no secrets handling" is no longer a reason to decline.
- **We have no hardware-protected key storage.** The NIST storage SHOULD could not be met even if a pepper were
  added.
- **NIST forbids periodic password changes** (§3.1.1.2: verifiers SHALL NOT require subscribers to change passwords
  periodically), and ASVS 5.0 **6.2.10 (L2)** says the same.

## Considered options

- **Keyed pre-hash, `bcrypt(base64(hmac-sha384(password, pepper)))`.** OWASP's sanctioned pre-hash. It would also
  lift the 72-byte ceiling (ADR-003). **It cannot be rotated.** A hash computed under key *K* cannot be re-derived
  under *K′* without the plaintext, which we do not hold and must never hold. Routine rotation then means a forced
  global reset, which is a periodic change the NIST `SHALL NOT` forbids. The alternatives are a dual-key read path
  kept forever, or a key that silently never rotates under a policy that says it must. The failure mode of either
  going wrong is every account locked out at once.
- **Keyed post-hash, `HMAC(pepper, bcrypt_hash)`.** Same objection. The stored value is the HMAC, so the bcrypt hash
  it covers is not recoverable either.
- **Encrypt the bcrypt output under an environment key, as the TOTP seed is.** This one **can** rotate: decrypt
  under the old key and re-encrypt under the new one, with no user involved. The rotation argument does not reach
  it, and it is declined on weaker grounds, stated here so they can be challenged:
  - It protects only against disclosure of the database without the key (an injection flaw or a leaked backup).
    BCrypt at cost 12, a 15-character floor, a breach blocklist and a strength gate (ADR-005) already make an
    offline attack on that dump expensive.
  - Losing the key makes **every** password unverifiable at once. Outside `dev` there is no deliverable reset
    channel (R-CRED-021), so the only recovery would be the offline runner, account by account (ADR-072). The TOTP
    key's loss is bounded to administrators' factors, which an administrator can reset.
  - Without an HSM or TEE, the key sits beside the application process, the same place an attacker who reaches the
    key also reaches the running verifier.
- **No pepper (chosen).**

## Decision

- No pepper, no keyed pre-hash, no encryption layer over password hashes. No pepper key exists anywhere.
- The key-rotation policy covers **only keys that can rotate**. It states that no password-hash pepper exists and
  why, so nobody implementing rotation later goes looking for one.

## Why the tombstone HMAC key is different

The deleted-user tombstone stores an email address as a keyed HMAC (ADR-044), and that key can never retire either:
its versions accumulate forward and old ones are kept (ADR-052). The same constraint is accepted there and
disqualifying here, because of what each key protects:

- A pepper protects a **credential**. If it leaks and cannot be retired, the leak is unbounded in time.
- The tombstone key is a **blinding key for a pseudonymised reuse index**. Its compromise discloses one bit, that a
  given address once held an account, and only to someone already holding the tombstone table.

Without this distinction the two decisions read as one rule applied twice with two answers.

## Consequences

- A declined NIST `SHOULD`, recorded in the deferral register (R-CRED-004).
- The 72-byte ceiling stays (ADR-003), because the only safe pre-hash is peppered.
- **Reopening triggers:** hardware-protected key storage becomes available (the encryption variant then meets
  NIST's storage SHOULD and should be reconsidered first); or mail transport enters scope, which removes the
  runner-only recovery that makes key loss so costly.

## Sources

- NIST SP 800-63B-4 §3.1.1.2 Password Verifiers (keyed hashing or encryption pass; periodic change).
- OWASP ASVS 5.0, V6.2: 6.2.10 (L2).
- OWASP Password Storage Cheat Sheet: peppering, pre-hashing passwords with bcrypt.
