# 17: TOTP enrolment

**What to build:** An admin with no factor enrols a TOTP authenticator by QR code or by typing the secret (ADR-025; R-FE-005).

- **`POST /api/mfa/totp/enrolment`** (`ROLE_ADMIN`, no factor) writes a *pending enrolment* only and resets no counter. It returns `otpauthUri`, `secretBase32` and `qrPng` once, with `Cache-Control: no-store` and nothing logged. It returns 409 `FACTOR_ALREADY_ENROLLED` when a confirmed factor exists.
- **Secret storage.** Secrets are encrypted with `AesGcmBytesEncryptor` under the environment TOTP key (ADR-022). A 37-byte *context prefix* sits inside the plaintext, so the stored envelope is 69 bytes (ADR-028). A prefix mismatch is a security event (R-MFA-020).
- **`POST /api/mfa/totp/enrolment/confirmation`** checks a code and copies the pending blob verbatim into `totp_user_details`. This is *enrolment binding*, and it grants the factor (REJ-071). The code check is 6 digits, a 30-second step and ±1 step, and is a pure function in PIT scope.
- **Rate limit:** add the enrolment (10 / 1 per 6 s) and confirmation (20 / 1 per 3 s) rows.
- **SPA:** `/settings/mfa` renders the QR from the PNG through a `blob:` URL that one effect creates and revokes, plus a manual-entry secret.

**Blocked by:** 16

**Status:** ready-for-agent

- [ ] Provisioning twice replaces the pending row, and neither call resets any counter.
- [ ] The stored `totp_key` is exactly 69 bytes. A row copied under another user's context fails to decrypt and emits the mismatch event.
- [ ] A correct code confirms enrolment and rotates the session id. A wrong one does not.
- [ ] The secret response has `no-store`, and the canary scan finds no secret in the logs.
- [ ] The `blob:` URL is revoked on unmount (Vitest).
