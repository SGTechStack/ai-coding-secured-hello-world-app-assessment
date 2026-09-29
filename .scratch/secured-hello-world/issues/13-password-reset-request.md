# 13: Password reset request

**What to build:** A user who forgot their password submits their email address and a reset link
is generated for them — without the response ever revealing whether that email is registered.
The response is a generic success either way. Real mail delivery is out of scope: a stubbed
email service logs the link instead of sending it.

Brings the `password_reset_tokens` table with it. Covers PRD Story 6.

**Blocked by:** 04.

**Status:** ready-for-agent

**IM8 controls:** `as-4` Authentication Mechanism Rate-Limiting; `as-14` Secure Cryptographic
Libraries; `lm-19` Log Sanitisation; `as-1` Input Validation. *ASVS: V2.5 Credential Recovery,
V6 Stored Cryptography, V8 Data Protection, V7 Logging.*

- [ ] The `password_reset_tokens` table exists with the fields the PRD's data model specifies:
      identifier, user reference, token hash, expiry timestamp, nullable used-at timestamp
- [ ] The response is a **generic success message regardless** of whether the email is
      registered, so account existence cannot be inferred
- [ ] Response timing does not leak registration status either — the unregistered path must not
      return conspicuously faster than the path that generates and stores a token
- [ ] The submitted email is validated for **shape** before any lookup is attempted, so a
      malformed address is never used to query the user store
- [ ] A malformed email receives the same generic success response as everything else —
      validation must not become the leak that the generic response was written to close
- [ ] Where the email matches a registered user, a single-use token is generated using a
      **cryptographically secure** random source with sufficient entropy to resist guessing
- [ ] The token comes specifically from `java.security.SecureRandom` with at least **256 bits**
      of entropy, encoded URL-safely so it survives being placed in a link
- [ ] `java.util.Random`, `Math.random()`, timestamps, and UUID v1 or v4 are **prohibited** as
      the token source — a v4 UUID carries only 122 bits and is not specified to come from a
      CSPRNG at all, so it is not a security token however random it looks
- [ ] Only the token's **hash** is stored; the plaintext token exists solely in the emailed link
      and is never persisted
- [ ] The token expiry is short — the PRD specifies 15 to 30 minutes — and comes from
      configuration
- [ ] The stubbed email service is called with the reset link and logs it rather than sending
      mail; the logged link is acknowledged as a dev-only affordance in ticket 27's deviation
      register
- [ ] That logged link carries the **live plaintext token** — a working credential written to a
      log file, which is exactly what `lm-19` exists to prevent, so the emission is fenced: only
      under the **dev profile**, only at **DEBUG** level, and only through a dedicated logger
      that is **not** the audit logger
- [ ] Under the prod profile the link emission is **unreachable**, not merely quieted by a log
      level someone could turn back up
- [ ] The stub is recorded in ticket 27 as a **deployment-blocking** deviation, because a real
      deployment has to replace it with SMTP before this control is satisfied
- [ ] The plaintext token does not reach the audit log; the reset-requested audit event records
      that a reset was requested for an account without embedding the token
- [ ] The reset-requested audit event records **that** a reset was requested and **for whom**,
      and never the token or the link itself
- [ ] This endpoint is itself rate-limited, so it cannot be used to spray mail or to probe for
      registered addresses at volume
- [ ] The throttle is **per-email and per-IP**: password reset is a credential-recovery
      authentication mechanism, and `as-4` covers all of them, not only login — left unthrottled
      it is simultaneously an enumeration probe and a mail-flood vector
- [ ] The throttled response is **identical** to the un-throttled one in status and body, so the
      throttle does not turn into the enumeration oracle the generic response was meant to deny
- [ ] The frontend has a forgot-password form that shows the generic confirmation
- [ ] Test: a registered and an unregistered email produce identical client responses
- [ ] Test: the throttled and un-throttled responses are byte-identical in body and status
- [ ] Test: under the **prod profile** no reset link is emitted at any log level
- [ ] Test: a registered email persists a token row whose stored value is a hash, not the token
      handed to the email service
- [ ] Test: the stored expiry is within the configured window
