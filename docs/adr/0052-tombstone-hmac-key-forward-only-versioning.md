---
status: accepted
---

# ADR-052: Tombstone HMAC key: forward-only versioning, old versions never retire

The tombstone's email HMAC (ADR-044) is keyed by `app.security.hmac.tombstone.key`. The key is versioned **forward
only**. A new version can be minted, and new tombstones are written under the newest version. Old versions **never
retire**, because retiring one would need the plaintext emails it was computed from, and those are deliberately not
held. "Rotate every key" is the standing policy, and this key cannot follow it. The exception is justified below.

## Context

- A deleted user's email is kept only as an HMAC. An HMAC cannot be recomputed under a new key without the input, so
  tombstones written under a version stay under that version for as long as they exist, which is indefinitely.
- ASVS 5.0 11.2.2 (L2) expects keys to be replaceable. IM8's crypto controls expect a rotation capability, and a review
  reads an unexplained exception as an omission.
- The TOTP seed key (ADR-022) rotates yearly by design, and the log-field HMAC key rotates freely.
- A missing or wrong tombstone key does not fail loudly. Reuse blocking just stops matching, so registration starts
  accepting deleted users' addresses and nothing looks wrong.

## Decision

- **Forward-only versioning.** `app.security.hmac.tombstone.version` names the current version. New tombstones use it.
  A registration or admin-create checks the candidate's canonical email (ADR-045) against **every retained version**.
  The matched version is never reported.
- **No key-version column on `deleted_users`.** An HMAC used for equality search is not one used for decryption. The
  TOTP tables need `key_version`, because you cannot decrypt without knowing which key to use. The tombstone check
  computes one HMAC per retained version and looks each up in `ux_deleted_users_email_hmac`, so a stored version
  would be write-only information. The unique index is unaffected: an address can be tombstoned only once.
- **A distinct key.** Never shared with the TOTP key or the log key. One key, one purpose (NIST SP 800-57 Part 1 Rev 5
  §5.2). Sharing it with the TOTP key would drag that key into the same non-rotatability. Sharing it with the log key
  fails because one key cannot rotate for the log stream and stay frozen for tombstones.
- **Handling.** Exactly 32 decoded bytes. Supplied from a mounted secret file or the environment, never from a
  committed file, never logged. Absence or malformation refuses startup during refresh, because the failure mode is
  silent fail-open. Generate-on-first-run is rejected for the same reason: a regenerated key looks correct everywhere
  except its fingerprint.

## Considered options

- **Full rotation.** Impossible without the plaintext.
- **No versioning, one frozen key forever.** Rejected. Versioning gives a leaked key a **forward response**: new
  tombstones stop depending on it. That moves ASVS 11.2.2 (L2) from a clean failure to a partial. Declining a control
  that converts a failure into a partial, because it is not the whole control, is the wrong trade.
- **Derive this key and the TOTP key from one root via HKDF.** Rejected. It does not restore rotation: the email subkey
  would have to stay pinned to root version 1 forever, which means keeping root version 1 alive forever.
- **Deterministic AEAD (AES-SIV, AES-GCM-SIV)**, which would allow re-keying. Rejected. ASVS Appendix C's approved AEAD
  list contains no SIV mode, so it would fail 11.3.2 (L1). It would also bring a second crypto library onto a path
  deliberately kept inside Spring Security.

## Consequences

- **Why the exception is acceptable.** This is a **blinding key** for a pseudonymised reuse index, not a
  confidentiality key protecting a secret at rest. Its compromise discloses only that a given address once held an
  account, and only to someone who already holds the tombstone table: one bit per address. Rotation exists to bound
  the damage of compromise over time, and here that damage is small. This matches accepted blind-index practice. A keyed
  password pepper, which could not retire either, resolves the other way for the same reason in reverse: it would
  protect a credential, where unbounded-in-time compromise is intolerable (ADR-004).
- **Its compromise is unbounded in time**, so its handling carries more weight than a rotatable key's.
- Each registration costs one extra HMAC and index lookup per retained version. Today there is one version.
- ASVS 11.2.2 (L2) and 13.3.4 (L3) are graded partial (R-CFG-014).
- **Reopening trigger:** if tombstone retention ever becomes bounded, old versions become retirable at the end of the
  window, which converts forward-only versioning into full rotation (R-CFG-003).

## Sources

- OWASP ASVS 5.0: 11.2.2 (L2), 11.3.2 (L1), 13.3.4 (L3); Appendix C (approved algorithms).
- NIST SP 800-57 Part 1 Rev 5 §5.2 (key usage).
- RFC 2104 (HMAC); RFC 5869 (HKDF).
