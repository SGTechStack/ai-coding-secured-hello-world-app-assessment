# 06 — Decide the API error envelope and the enumeration-safe response contract

Type: grilling
Status: open
Blocked by: 01, 02, 04

## Question

What does every error response from this API look like, and what precisely must be identical across
authentication outcomes so account existence cannot be inferred?

This is decided early because the credential flows, lockout, and admin tickets all emit errors and
must not each invent their own shape.

## What to decide

**Envelope shape.** RFC 9457 Problem Details (`application/problem+json`) or a custom envelope? The
App Standard demands a "stable, machine-readable error body" and names specific error codes —
`user exist`, `username change not allowed`, `account locked`, `account cannot authenticate`,
`too many requests`, `password reset token expired or invalid` — so the code vocabulary is partly
given. Decide whether those strings are the wire format or internal identifiers mapped to something
else, and whether the recipes already fix this (see "Inventory the prescribed recipes").

**Status code map.** The standard fixes much of it: 401 for auth failures, 403 for authorization and
CSRF failures, 400 for validation, 429 with `Retry-After` for rate limits. Confirm and fill gaps —
notably what a request against a *disabled* account returns, and what the reset-token endpoints
return.

**The enumeration contract.** The standard requires identical status codes, bodies, **and response
timing** across login, password reset, and registration regardless of account state. Decide:

- The single generic failure body for all auth failures. `account cannot authenticate` is the
  standard's catch-all — is that the wire code for wrong-password, unknown-user, locked, and
  disabled alike?
- How the internal reason is captured for operators without leaking it to the caller (log-only, per
  the standard).
- **Timing.** This is the hard part and the one most often skipped. Unknown username short-circuits
  before any hash verification, so it returns measurably faster than a wrong password. Decide the
  mitigation: verify against a dummy hash for unknown users, pad to a fixed floor, or something
  else. Note the interaction with BCrypt work factor from "Decide the password policy and hashing
  parameters" — a high work factor makes the timing gap larger, not smaller.
- Whether validation errors may be specific. The standard says yes for password strength and history
  ("a specific error must be returned indicating which rule was violated") while auth failures must
  be generic. Draw that line explicitly so it isn't decided ad hoc per endpoint.

**Known tension to resolve.** The standard's error list includes `user exist` (400) for duplicate
username or email, yet also demands enumeration-safe registration. The likely resolution is that
`user exist` belongs to the **admin-initiated** user creation flow, where the caller is trusted,
while **self-registration** must stay generic. Confirm or reject that reading — it directly
determines whether PRD Story 1's "clear validation error" survives.

## Done when

An implementer can write the error handler and the auth failure path from the answer without
guessing, the timing mitigation is chosen, and the `user exist` tension is resolved in writing.
