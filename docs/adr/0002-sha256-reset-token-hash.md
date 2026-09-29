# SHA-256 for reset-token storage, BCrypt for passwords

Password-reset tokens are stored as a **SHA-256** hex digest, not a BCrypt hash. Passwords remain BCrypt. This looks inconsistent, so we record why.

## Context

The password-reset confirm endpoint receives a plaintext token and must find the matching stored record. BCrypt (and any salted password hash) uses a random per-hash salt, so the same input produces a different digest each time — you can only *verify* a known candidate, not *look one up*. Finding the row would require loading every token and calling `matches()` against each, which does not scale and leaks timing.

The reset token is not a user-chosen secret: it is 32 bytes (256 bits) from `SecureRandom`, Base64URL-encoded. Its entropy is astronomically higher than any password, so the reasons BCrypt exists — slowing down brute force of low-entropy human input — do not apply.

## Decision

- **Reset tokens:** SHA-256 hex digest, deterministic and unsalted, enabling O(1) lookup by `token_hash`. Only the digest is persisted; the plaintext is emailed once and never stored.
- **Passwords:** unchanged — `BCryptPasswordEncoder`, salted and slow, appropriate for low-entropy human-chosen secrets.

## Consequences

- Token lookup is a single indexed query (`findByTokenHash`).
- A database leak exposes only SHA-256 digests of 256-bit random values — not reversible or brute-forceable in practice.
- The asymmetry (fast hash for tokens, slow hash for passwords) is deliberate and keyed to the entropy of the secret being protected, not an oversight.
