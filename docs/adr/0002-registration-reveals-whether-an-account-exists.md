# ADR 0002 — Registration reveals whether an account exists

- **Status:** Accepted
- **Date:** 2026-09-24
- **Context:** account enumeration

## Decision

`POST /api/auth/register` answers `409` with distinct messages for a username
already taken and an email already registered. Login and password reset remain
enumeration-resistant; registration does not.

## Why

The PRD scopes enumeration resistance to login and password reset. Registration
is different in kind: the caller is choosing an identifier, and the system has to
tell them the choice is unavailable or they cannot proceed. A generic "registration
failed" leaves a legitimate user with no idea which field to change.

A uniform response is achievable — accept the registration, send a verification
email, and reveal nothing — but that requires working mail delivery, which the PRD
scopes out, and would change the registration flow from synchronous to
asynchronous for the benefit of a property the PRD does not ask for.

## Consequences

- An unauthenticated caller can test whether a username or an email address is
  registered, at 10 requests per hour per IP (`app.security.rate-limits`).
- Combined with the email address being the account's only personal data, this
  means a list of addresses can be tested to learn which of those people use the
  system. Tracked as TM-32 (Identifiability) rather than dismissed.
- Revisit if a verification-email step is ever added: at that point the uniform
  response becomes free and this ADR should be superseded.

## Related

- Threat model TM-22, TM-32
