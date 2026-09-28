---
status: accepted
---

# ADR-003: Reject passwords over 72 UTF-8 bytes rather than pre-hash

A password longer than 72 UTF-8 bytes, measured after NFC normalisation, is rejected with a specific error. It is
never truncated and never pre-hashed. A maintainer who sees the byte cap will want to lift it with a pre-hash. The
only safe pre-hash is a peppered one, which ADR-004 declines, and the unsafe ones reopen known BCrypt pitfalls.

## Context

- **BCrypt consumes at most 72 bytes** (ADR-001). Input past that is ignored by the algorithm.
- **NIST SP 800-63B-4 §3.1.1.2:** verifiers "SHALL verify the entire submitted password (e.g., not truncate it)".
  ASVS 5.0 **6.2.8 (L1)** requires verifying the password exactly as received, without truncation.
- **Spring Security throws on over-long input at encode time.** That is the CVE-2025-22228 fix, which stopped
  `matches` accepting two passwords that shared a 72-character prefix. Without our own check, an over-long password
  surfaces as a framework exception rather than our error envelope.
- **The ceiling costs something.** §3.1.1.2 says verifiers SHOULD permit at least 64 characters. ASVS 5.0 **6.2.9
  (L2)** requires that passwords of at least 64 characters be permitted. Both count characters. At three bytes per
  character, 72 bytes is 24 characters.

## Considered options

- **Truncate silently.** Violates the NIST `SHALL` and ASVS 6.2.8. Two passphrases sharing a 72-byte prefix become
  the same password.
- **Unkeyed pre-hash, `bcrypt(base64(sha(password)))`.** The OWASP Password Storage Cheat Sheet calls this
  dangerous. An attacker holding a leaked unsalted hash of the same password elsewhere can "shuck" the bcrypt layer
  and attack the fast hash instead. Without the base64 step, a NUL byte in the digest truncates the input.
- **Keyed pre-hash, `bcrypt(base64(hmac-sha384(password, pepper)))`.** The construction OWASP sanctions. It answers
  both objections: shucking needs the inner hash, which the key denies, and base64 removes NUL bytes. It is a
  pepper, and ADR-004 declines peppers because a keyed hash cannot be re-derived under a new key.
- **Change algorithm.** Argon2id has no such ceiling. The PRD mandates BCrypt (ADR-001).
- **Reject explicitly (chosen).**

## Decision

- `app.security.password.max-bytes: 72`, checked in UTF-8 bytes after NFC normalisation, by our own validator inside
  the single password-setting component, before the encoder ever sees the input.
- A rejection returns `PASSWORD_REJECTED` with rule `MAX_BYTES`. The limit is expressed and surfaced in **bytes**,
  and the SPA counts the same way.
- **Checked when a password is set, never on the login path.** At login, `matches()` runs on whatever was submitted.
  A length check before authentication would be a fast exit that skips the hash, which is the timing leak
  CVE-2025-22234 describes.

## Consequences

- **ASVS 6.2.9 (L2) is knowingly failed**, and the NIST 64-character SHOULD is not met, for multi-byte input. 64
  ASCII characters fit. 64 CJK characters (about 192 bytes) do not, nor does a 40-character accented-Latin
  passphrase. The affected population is non-Latin-script users. Recorded in the deferral register (R-CRED-007) so
  the limit is owned rather than discovered.
- Two upstream causes: the PRD's BCrypt mandate (ADR-001) and the declined pepper (ADR-004).
- The permitted band is 15 code points (ADR-002) to 72 bytes, and it narrows under non-ASCII input.
- Tests: T-CRED-001 (an over-72-byte password, including a multi-byte string under 72 characters, is rejected by our
  validator with `MAX_BYTES`, never a 500 from the encoder); T-FE-019 (the client counts bytes after NFC).
- **Reopening trigger:** an algorithm change away from BCrypt. The ceiling should then be raised, not kept.

## Sources

- NIST SP 800-63B-4 §3.1.1.2 Password Verifiers.
- OWASP ASVS 5.0, V6.2: 6.2.8 (L1), 6.2.9 (L2).
- OWASP Password Storage Cheat Sheet: input limits of bcrypt, pre-hashing passwords with bcrypt.
- CVE-2025-22228 and CVE-2025-22234, Spring Security advisories.
