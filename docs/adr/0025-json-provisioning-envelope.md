---
status: accepted
---

# ADR-025: Provisioning returns a JSON envelope with a manual-entry secret, and the SPA renders the server PNG

TOTP provisioning returns one JSON body, `{ otpauthUri, secretBase32, qrPng }`, where `qrPng` is the server-rendered
QR code as base64. The SPA turns `qrPng` into a `Blob` and shows it through an object URL, and it also shows the
Base32 secret for manual entry. The standard returns bare PNG bytes. Rendering the QR code in the browser is the
obvious frontend alternative. A maintainer could plausibly move to either, and both were weighed and rejected.

## Context

- **MFA_Core §2.2**, TOTP provisioning: "Response: `200 OK` with PNG bytes in body." Nothing else is returned. The
  frontend standard's only text alternative is `alt="QR Code"`, and the `otpauth://` URI is never exposed.
- So an administrator who cannot scan a QR code cannot enrol. For a factor required of every admin (ADR-023), that
  is a functional accessibility blocker, not a conformance detail.
- The document CSP allows `img-src 'self' blob:`. `data:` is deliberately absent from the policy (ADR-060).
- A PNG success body next to `application/problem+json` errors would give the endpoint two media types, and one
  error writer would need a carve-out for it (ADR-031).
- The frontend standard's object-URL effect only revokes and never creates. Under React 19 StrictMode's
  mount/unmount/remount, and after every Fast Refresh, the first cleanup revokes a URL the second mount still
  renders, and the body has nothing to recreate it with. It also leaks on the error path and when the verified flag
  flips.

## Decision

- **Response:** `200 application/json` with `otpauthUri`, `secretBase32` and `qrPng` (base64), and
  `Cache-Control: no-store`.
- **The secret is shown exactly once.** It is never stored in plaintext, never re-fetchable and never logged. The
  `otpauthUri` is as sensitive as the Base32 string, because it embeds it, and body logging is suppressed for both.
- **The server still renders the QR code**, as the standard prescribes. The SPA decodes `qrPng` to bytes, builds a
  `Blob` of type `image/png`, and uses a `blob:` object URL. The CSP is unchanged and `data:` stays out.
- **One effect owns both halves of the object URL.** It creates the URL from the `Blob` in the effect body and
  revokes it in the cleanup, keyed on the `Blob`. The cycle is symmetric by construction, so the remount, error-path
  and verified-flag leaks are one cleanup firing, not three cases.
- **Errors are ordinary problem responses** from the one writer, with no per-route `Accept` handling.

## Considered options

- **PNG bytes, as MFA_Core §2.2 prescribes.** Rejected: no manual-entry path, and a second media type on one route.
- **`{ otpauthUri, secretBase32 }` only, with the QR rendered client-side as inline SVG.** Genuinely attractive. It
  deletes the server QR library and object URLs entirely, the StrictMode bug with them, and the need for `blob:`.
  Rejected because it moves a prescribed server responsibility to the client on the MFA happy path, which is the
  most visible place in the design to deviate. It remains the fallback if the `Blob` route ever fights the
  component library. Taking it costs this ADR, not a redesign.
- **A `data:` URI for the image.** Rejected: it would add `data:` to `img-src`.

## Consequences

- A recorded deviation from MFA_Core §2.2's response shape.
- The manual-entry secret makes enrolment possible without a camera, and with password managers and command-line
  authenticators.
- Provisioning is a POST, not the standard's GET, for CSRF reasons (REJ-069). The confirmation code arrives as a
  JSON body, not an `X-TOTP` header (REJ-070).
- Tests: T-FE-025 (the SPA renders `qrPng` through a `blob:` URL, never `data:`), T-FE-003 (one revoke per create
  across a StrictMode remount, QR replacement, unmount, the error path and the verified-flag flip, and the image
  never points at a revoked URL), T-MFA-017 (the returned PNG decodes, and the pending row decrypts to the secret
  that `secretBase32` shows).

## Sources

- Unified MFA Application Standard (`Appfw-Mfa-Standards/MFA_Core`) §2.2 Happy Path — TOTP Provisioning, §3.1
  Inputs / Outputs.
- `Appfw-Mfa-Standards/MFA_Frontend/Standalone` standard, `MFATotpForm` and `useMFATotpForm` (object-URL image,
  `alt` text).
- React 19 documentation, `StrictMode` (effects re-run in development).
- W3C Content Security Policy Level 3, `img-src`.
