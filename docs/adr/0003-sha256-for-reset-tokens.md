# 3. Hash reset tokens with SHA-256, not BCrypt

Date: 2026-09-29
Status: Accepted

## Context

Both passwords and password-reset tokens are stored as hashes, never plaintext.
It is tempting to use the same hashing scheme (BCrypt) for both. But they have
different threat profiles:

- **Passwords** are low-entropy, human-chosen, and reused. They need a slow,
  salted, adaptive hash (BCrypt) to resist offline brute force.
- **Reset tokens** are 256-bit cryptographically-random values. Brute-forcing
  a 256-bit space is infeasible regardless of hash speed, so an adaptive slow
  hash buys no security and adds latency to the reset path.

## Decision

Hash **passwords with BCrypt** (`BCryptPasswordEncoder`) and **reset tokens
with SHA-256**. Store only the hash for both. Reset tokens are 32-byte random,
Base64URL-encoded, single-use, 30-minute expiry.

## Consequences

- Correct tool per threat model: adaptive hashing where entropy is low, fast
  hashing where entropy is already high.
- Token verification is a constant-time comparison of SHA-256 digests.
- A future reader who expects "hash everything with BCrypt" will find this
  divergence documented here rather than looking like an oversight.
