# 17 — Produce the deferral register and ADR set

Type: task
Status: open
Blocked by: 12, 13, 14, 15, 16, 19, 20, 21

## Question

What are we deliberately *not* doing, why, and which decisions need an ADR so a future reader
understands them?

This ticket makes the gaps visible. An assessor reading the PRD and the App Standard will notice
every omission; the difference between a considered plan and a careless one is whether the omissions
are named and justified.

## Deliverable 1 — the deferral register

One row per deviation: what was required, which document required it, what we did instead, why, and
the residual risk. Known entries going in, to be completed from how the tickets actually resolved:

**Deviations from the App Standard:**

- Account hygiene scheduled jobs — 90-day inactivity disablement, 180-day role revocation, ShedLock
  serialisation. Deferred as operational lifecycle controls orthogonal to the PRD's auth scope. The
  largest single deferral.
- BCrypt instead of the preferred Argon2id or scrypt.
- Password history, composition rules, or lockout duration, if the currency research found the
  standard behind current guidance and we followed current guidance instead.
- Role-based authorization matrix, if "Decide the admin module" deferred it.
- Durable 90-day audit retention, if "Build the audit event catalogue" concluded log lines cannot
  satisfy it without infrastructure.
- OWASP Dependency-Check as a *CI* gate — implemented as a Maven verify-phase gate instead, since no
  pipeline is in scope.
- TLS on the login endpoint, for local development.

**Deviations from the PRD:**

- Registration no longer returns a clear username/email conflict error, so **Story 1's third
  acceptance criterion is not met**. State this in exactly those terms — it is the most visible
  deviation and must not look like an oversight.
- Lockout duration changed from 15 to 20 minutes, if that is how it resolved.
- Additions beyond PRD scope that we chose to build: concurrent session limit, idle and absolute
  timeouts, password history, self-service password change, email verification, soft-delete
  tombstones, admin unlock endpoint.

**Accepted risks from the threat model**, carried over from "Run the threat model against the design".

## Deliverable 2 — the ADR set

`docs/adr/` in the target repo, using the `domain-modeling` skill's ADR format. Only for decisions
that are hard to reverse, surprising without context, *and* the result of a real trade-off. Expected
set, to be confirmed:

1. Session cookies over JWT — the PRD chose this; the ADR records why, including that JWT logout
   needs a blacklist which reintroduces the statefulness JWT was meant to avoid.
2. Spring Session JDBC — driven by the requirement to invalidate all of a user's sessions.
3. BCrypt over Argon2id — a deviation from the standard's preference, so it needs the trade-off
   written down.
4. Dual rate limiting — resolving a direct contradiction between the PRD and the standard, where the
   standard also contradicts itself.
5. Enumeration-resistant registration at the cost of a PRD acceptance criterion.
6. Flyway over `ddl-auto`, with vendor-neutral SQL against H2.
7. Session-bound synchronizer CSRF token, and the same-site deployment constraint it imposes.

Reject any candidate that fails the three-part test rather than padding the set.

## Deliverable 3 — `CONTEXT.md`

The glossary built up across the map: managed user, current user, role definition, authorization
matrix, tombstone, failed login counter, account lockout, absolute session timeout, unverified vs
disabled, and whatever else got pinned down. Glossary only — no implementation detail.

## Done when

The register accounts for every known deviation, the ADRs exist and each passes the three-part test,
`CONTEXT.md` is written, and nothing in the register is phrased so vaguely that a reader could not
tell whether it was a decision or an accident.
