# 2. Revoke all sessions on password reset

Date: 2026-09-29
Status: Accepted

## Context

Story 7 requires that a successful password reset invalidates "all existing
sessions for that user." With Spring Session, the current session is trivial to
kill, but revoking *every* session for a principal requires the indexed
repository support (`FindByIndexNameSessionRepository` /
`findByPrincipalName`), which is extra configuration and a store that supports
the principal-name index.

The cheaper alternative is single-session invalidation (kill only the session
that performed the reset), which covers the common "user has one session" case
but leaves other live sessions valid after a reset — exactly the scenario a
reset is meant to defend against (e.g. account takeover from another device).

## Decision

Implement **true all-session revocation**: on successful reset, enumerate and
delete all of the user's sessions via Spring Session's indexed repository.

## Consequences

- A password reset genuinely locks out every other device/session — the
  security-meaningful behavior the PRD asks for.
- Requires a session store and configuration that supports principal-name
  indexing; the session repository choice is constrained by this.
- Slightly more infrastructure than single-session logout, accepted as the
  cost of correct reset semantics.
