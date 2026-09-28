---
status: accepted
---

# ADR-022: The TOTP seed encryption key lives outside the database

TOTP seeds are encrypted at rest with AES-256-GCM through Spring Security's `AesGcmBytesEncryptor.withSecretKey(...)`.
The key is supplied by the deployment environment, is absent from every committed file, and is **never stored in
the database**. Each ciphertext row carries a key-version column, so the yearly rotation can run one row at a time.
The MFA_Core standard says the opposite: the key "is stored in the database", tagged as an enforced constraint. A
maintainer holding the standard would follow it.

## Context

- **MFA_Core §3.4 (Encryption Key Rotation)**, enforced constraint: "The encryption key used to protect TOTP secrets
  is stored in the database and MUST be rotated at minimum once yearly. Rotation requires re-encrypting all existing
  TOTP secret records under the new key." §4.1's external assumptions repeat it ("including retrieval of the
  encryption key").
- A key stored beside its ciphertext buys almost nothing against the control's main threat. A database compromise
  yields both.
- **The standard contradicts itself on whether this is a mandate.** Its Questions file, Q13, asks the integrator
  whether the key is provisioned "in the application database, in a secrets manager (e.g., Vault), or via another
  mechanism". §3.4 tags the same choice as enforced. The same split affects the 10-failure threshold and
  lock-until-review (Q15, Q16), and is recorded as a standards defect (R-STD-049).
- **The yearly rotation MUST cannot be carried out as written.** Re-encrypting under a new key requires knowing which
  key each row used. The standard's §4.2 schema has no key-version column (R-STD-039).
- The corpus names an `EncryptionService` with one `byte[] decrypt(byte[])` call and nothing else: no interface, no
  algorithm, no mode, no IV handling and no key source. Its shape matches Spring's `BytesEncryptor`, so our choice
  drops in with no wrapper and conflicts with no prescribed code.
- **CVE-2026-47842.** `AesBytesEncryptor` built with its two-argument constructor, or with a null IV generator in
  CBC mode, encrypts under an all-zero IV. The fix deprecates `AesBytesEncryptor` and the `Encryptors` factory
  methods, and adds `AesCbcBytesEncryptor` and `AesGcmBytesEncryptor`. `Encryptors.stronger()` uses GCM with a
  random IV and is **not** exposed, but it derives its key from a password, and it is deprecated with the rest.

## Decision

- **Primitive:** `AesGcmBytesEncryptor.withSecretKey(...)`: AES-256/GCM/NoPadding, a random 16-byte IV and a
  128-bit tag. The `withSecretKey` builder, not `withPassword`, so there is no key derivation or salt to manage.
- **Prohibited:** the two-argument `AesBytesEncryptor` constructor, a null IV generator in CBC mode, and hand-built
  `Cipher` code. `Encryptors.stronger()` is not a vulnerability, but it is deprecated and password-derived, so it is
  not used either.
- **Key source:** property `app.mfa.totp.encryption.key`, bound through `@Validated @ConfigurationProperties`
  (ADR-062) from a mounted secret file or an environment variable. It is absent from every committed profile.
  - Padded Base64, exactly 32 decoded bytes. Values that decode to all-printable ASCII or all-identical bytes are
    refused, and validation runs in the `@Bean` factory so a failure report cannot echo the rejected value
    (T-CFG-022).
  - Absence refuses startup during context refresh (T-CFG-023).
  - The key is **distinct material** from every other key. The tombstone HMAC key can never rotate (ADR-052), so a
    shared key would inherit that and make this one non-rotatable too.
- **Key version:** the current version is its own required property (`app.mfa.totp.encryption.key-version`), so
  "newest key" is never implied by ordering or naming. Every ciphertext row stores the version it was written under.
  The version is also bound inside the ciphertext (ADR-028).
- **Rotation stays yearly**, as the standard requires, and runs incrementally: decrypt under the row's version,
  re-encrypt under the current one. Writing the procedure is an operator obligation (R-CFG-005).

## Considered options

- **Key in the database, as MFA_Core §3.4 prescribes.** Rejected: key and ciphertext share one failure.
- **Vault, cloud KMS or HSM.** Out of scope, because hosting infrastructure is outside the PRD. Recorded as not
  built (R-CFG-013).
- **`Encryptors.stronger()` or `AesBytesEncryptor` with a GCM algorithm argument.** Rejected: deprecated, and the
  first is password-derived.
- **Hand-rolled `Cipher`.** Rejected: it returns to the code shape CVE-2026-47842 came from.

## Consequences

- **ASVS 13.3.1 (L2) stays failed.** Its list of what belongs in a vault includes keys and seeds for time-based
  tokens, so the seeds themselves are in scope, not only the key wrapping them. The compensating controls are a
  key held outside the database and out of every committed file, validated at startup, with a secret scan (R-CFG-013).
- **NIST SP 800-38D is satisfied, not deviated from** (R-MFA-016). The 96-bit IV in §5.2.1.1 is a non-normative
  interoperability recommendation. A fully random 128-bit IV is inside §8.2.2's RBG-based construction, and is the
  shape §8.2.2 recommends. The one "shall" in play is §8.3's limit of 2³² invocations per key. We perform about one
  encryption per enrolment against a yearly-rotated key, many orders of magnitude inside it.
- A mounted file is the source of record, but Spring Boot lets an environment variable override it. A leftover
  variable silently wins over a freshly rotated file and surfaces as decryption failures. The handover carries
  that sentence.
- The key-version property keeps a dashed leaf, so its environment spelling is
  `APP_MFA_TOTP_ENCRYPTION_KEYVERSION`. A rename is recommended and not yet confirmed (R-CFG-009).
- Tests: T-CFG-022, T-CFG-023, T-MFA-004 (key-version range), T-MFA-017 (provisioning encrypts under the configured
  key), T-MFA-022 (an encryption failure is an internal error).

## Sources

- Unified MFA Application Standard (`Appfw-Mfa-Standards/MFA_Core`) §3.4 Encryption Key Rotation, §4.1 External
  Assumptions, §4.2 Entity Schema; Base Standalone Application Standard Questions, Q13 and Q14.
- IM8 as-8 (secrets supplied from the environment).
- OWASP ASVS 5.0: 11.3.3 (L2), 13.3.1 (L2).
- NIST SP 800-38D §5.2.1.1, §8.2.2, §8.3.
- CVE-2026-47842; HeroDevs, "CVE-2026-47842 Migration Guide" (NES for Spring Security).
- Spring Security 7.1.x source and javadoc: `AesGcmBytesEncryptor` (16-byte IV, 128-bit tag, `withSecretKey`),
  `BytesEncryptor`.
