# ADR 0001 — Plain HTTP in local development

- **Status:** Accepted
- **Date:** 2026-09-24
- **Context:** transport security

## Decision

`app.security.require-https` defaults to `true` and is set to `false` in the `dev`
profile only. Local development therefore runs over plain HTTP, and the session
cookie is not marked `Secure` there.

## Why

Enforcing HTTPS locally means either a self-signed certificate every developer
has to trust, or a local proxy. Both are setup steps that stand between cloning
the repository and running it, and the PRD explicitly scopes local HTTPS out.

The alternative considered was making dev the default and requiring deployments to
opt in to transport security. That is the same code with the failure mode
inverted: a deployment that forgot a variable would run in the clear. Defaulting
to secure and opting out in exactly one named profile means the concession is
visible in a diff and cannot be inherited by accident.

## Consequences

- Credentials and session cookies travel in the clear on a developer's machine.
  Accepted: the only traffic is loopback, and the accounts are disposable.
- The `Secure` cookie flag cannot be exercised in dev, so
  `SessionCookieAttributesTest` asserts its *absence* there and
  `ProductionProfileTest` asserts its presence in the base profile. Neither test
  alone would catch the dev setting leaking outward.
- `server.forward-headers-strategy` remains `none`. Behind a TLS-terminating
  proxy it must be set, or `requiresSecure`, HSTS and `Secure` cookies all
  misjudge the scheme — but enabling it while the app is directly reachable lets
  any client forge `X-Forwarded-Proto: https` and defeat those same controls. The
  topology has to be declared; there is no safe default.

## Related

- Threat model TM-05, TM-19b
- ADR 0004 (H2 in dev, PostgreSQL in production) — same shape of decision
