# ADR 0006 — Logs and audit records identify accounts by a keyed pseudonym

- **Status:** Accepted
- **Date:** 2026-09-24
- **Context:** logging, privacy

## Decision

Audit lines and audit rows carry `userRef=u_<12 hex>` — an HMAC-SHA256 of the
lower-cased username under `app.logging.pseudonym-salt` — instead of the username
itself. `UserPseudonym` is the only place this is derived.

When the salt is unset, a random key is generated per process.

## Why

The audit lines are necessary: "who locked this account and when" is a question an
operator has to be able to answer. But they named the account directly, and the
`users` table ties every username to an email address. That made the log a second,
unbounded copy of a personal-data linkage — read it and you learn when an
identified individual signs in, how often they fail, and what hours they work —
held in the least-governed store in most deployments.

Three options were considered:

1. **Drop the identifier.** Strongest privacy answer, wrong security answer. "Five
   failed logins" is not actionable without knowing whether it was five attempts on
   one account or one attempt on five.
2. **Log the account UUID.** Stable and not a name, but it is also the primary key
   the API uses in URLs, so it is not really a pseudonym — anyone with database or
   API access resolves it directly, and it appears in admin request paths.
3. **Keyed pseudonym.** Keeps the only property an investigation needs — two lines
   about the same account share a reference — and removes the one privacy objects
   to.

A plain digest was rejected. The input space is small and guessable, so
`SHA-256("alice")` is reversed by hashing a wordlist, and the pseudonym would be
decorative. The key is what makes the mapping one-way in practice.

## Why the default is an unkeyed, per-boot random

A fixed fallback key committed to this repository would make every pseudonym in
every deployment reversible by anyone who can read the source. That is strictly
worse than logging the username plainly, because it would *look* protected. A
per-boot random key is the only honest default.

## Consequences

- **References do not survive a restart** unless the salt is configured. An
  investigation spanning one cannot join across it. Deployments that need that must
  supply the salt from a secret manager; the application logs a line at startup
  saying which mode it is in.
- **Resolving a reference to a person requires the key and a deliberate act.** That
  is the point, and it is also a cost: an operator holding a support ticket for
  "alice" has to derive the reference rather than grep for the name.
- **Two identifiers are still logged in the clear, both deliberately.** The
  bootstrap runner logs the configured admin *username* — an operator credential,
  not a data subject's, and the operator needs to know which account was seeded.
  And `LoginFailureHandler` logs a reference derived from an unvalidated,
  caller-supplied username; `LogSafe` is the backstop that keeps the non-reference
  form from forging log records.
- Pinned by `UserPseudonymTest`, including that the reference does not contain the
  username and that two keys produce different references for the same input — the
  most likely wrong implementation is one that lightly encodes the input and would
  pass every other assertion.

## Related

- Threat model TM-15, TM-35, TM-36, TM-27
- ADR 0005 (lawful basis and retention)
- ADR 0007 (append-only audit log)
