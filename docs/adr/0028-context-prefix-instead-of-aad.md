---
status: accepted
---

# ADR-028: A context prefix inside the plaintext substitutes for AES-GCM AAD

Each TOTP seed is encrypted together with a fixed-width prefix naming its owner and key version:
`userUuid ‖ keyVersion ‖ secret`. After decryption the prefix is compared with the row it came from, and a mismatch
is a security event. The textbook way to bind that context is GCM's additional authenticated data (AAD). A
maintainer who sees context inside the plaintext would move it to AAD. `AesGcmBytesEncryptor` cannot take AAD, so
that move means abandoning the encryptor chosen for being safe (ADR-022).

## Context

- Without bound context, a valid ciphertext can be copied from one user's row to another's and decrypts cleanly. A
  row re-encrypted under one key version could also be presented as another.
- `AesGcmBytesEncryptor` implements `BytesEncryptor`, whose whole surface is `encrypt(byte[])` and
  `decrypt(byte[])`. There is no AAD parameter, and its `Cipher` instances are private. Reaching AAD means building
  `Cipher` by hand and calling `updateAAD`, which is the code shape CVE-2026-47842 came from.
- ASVS 5.0 imposes no AAD requirement. 11.3.3 (L2) asks for authenticated encryption, which GCM already is.
- `AesGcmBytesEncryptor` uses a **16-byte** IV and a 16-byte tag, per its javadoc. An earlier sizing assumed a
  12-byte IV and a 48-byte envelope. That column would truncate.

## Decision

- **Plaintext layout, 37 bytes, fixed width:**
  - `userUuid`: 16 raw bytes, not the 36-character string form;
  - `keyVersion`: 1 byte;
  - `secret`: 20 raw bytes, not the 32-character Base32 form.
- **Stored envelope, 69 bytes:** 16-byte IV, 37 bytes of ciphertext, 16-byte tag. Both TOTP tables hold the same
  format, because enrolment confirmation copies the pending blob verbatim with no re-encryption.
- **Verification is slice-and-compare** on fixed offsets, not delimiter parsing. The prefix must equal the row's
  user id and stored key version.
- **A prefix mismatch is a security event, not a decrypt error.** It is audited at ERROR, and it trips only when
  rows have been moved. Its alarm and response are an operator obligation (R-MFA-020).
- **The schema pins the widths**, because Hibernate's `validate` mode does not check column length:
  - `totp_key VARBINARY(69)` with a named check `OCTET_LENGTH(totp_key) = 69` on both tables, never `BINARY`,
    which zero-pads;
  - `key_version SMALLINT` with a named check `BETWEEN 0 AND 255`, because the version is copied into a one-byte
    field and a larger value would truncate there silently. Storing the version twice is deliberate.
- One code comment says why AAD is absent, so a reviewer reading for AAD knows it was considered.

## Considered options

- **GCM AAD through a hand-built `Cipher`.** Rejected: it discards the CVE-safe implementation for no assurance
  gain.
- **No context binding.** Rejected: row swaps and key-version replay become silent.
- **A delimited or variable-width prefix.** Rejected: parsing adds failure modes. Getting the UUID or secret width
  wrong yields 81 or 105 bytes instead of 69.
- **Fixed-width prefix inside the plaintext (chosen).** The same row-swap and version-replay resistance as AAD, at
  no cryptographic cost.

## Consequences

- The prefix is confidential as well as authenticated, which AAD would not be. Nothing depends on that.
- The widths are three separate facts (16, 1, 20), and changing any one of them changes the column check.
- Key rotation re-encrypts the whole plaintext, prefix included, under the new version (R-CFG-005).
- Tests: T-MFA-003 (both width checks reject 48-byte and 81-byte values and accept 69), T-MFA-004 (the key-version
  range), T-MFA-017 (the pending row decrypts to the user id, key version and the shown secret).

## Sources

- Spring Security 7.1.x source and javadoc: `AesGcmBytesEncryptor` (16-byte IV, 128-bit tag, prepended IV),
  `BytesEncryptor`.
- CVE-2026-47842.
- OWASP ASVS 5.0, 11.3.3 (L2).
- NIST SP 800-38D (GCM; AAD as optional input).
- Unified MFA Application Standard (`Appfw-Mfa-Standards/MFA_Core`) §4.2 Entity Schema (`TOTP_USER_DETAILS`,
  `PENDING_TOTP`).
- H2, PostgreSQL and MySQL documentation for `OCTET_LENGTH`.
