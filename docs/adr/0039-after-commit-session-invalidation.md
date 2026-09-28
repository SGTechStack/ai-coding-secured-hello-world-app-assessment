---
status: accepted
---

# ADR-039: Session invalidation is dispatched after commit, backed by an idempotent reconciliation sweep

When a state change must end sessions (ADR-037), the state change commits first and the sessions are ended
afterwards, outside any row lock. A reconciliation sweep at startup ends the sessions of every account whose state
should already have ended them. A maintainer would move the kill inside the transaction "for atomicity". That cannot
work here, and trying it adds a pool-exhaustion failure under attack.

## Context

- Spring Session JDBC 4.1.x builds its `TransactionTemplate` with `PROPAGATION_REQUIRES_NEW`, and
  `JdbcIndexedSessionRepository` runs `findById`, `deleteById` and `findByIndexNameAndIndexValue` through it. Every
  session write suspends the caller's transaction and commits on its own. No surrounding `@Transactional` can roll a
  session deletion back, so a state change and its session kill can never be atomic.
- Several triggers hold a pessimistic row lock while they change state: failure counting, lockout, the
  authenticator cap, and the two-admin guard. A lock is released only at commit or rollback, and savepoints do not
  release it. So "lock, write, release, then kill sessions" has no implementation. The only choices are *before
  commit, inside the lock* and *after commit, outside it*.
- Killing sessions inside the lock needs a second pooled connection while the first still holds the lock. Under a
  many-accounts attack the connection pool then joins the lock graph, and threads wait on the pool's own timeout,
  which H2's one-second `LOCK_TIMEOUT` does not bound. That is thread-pool exhaustion, the failure mode that also
  rules out sleep-based backoff (ADR-014). The counting path fails open on counting, so the visible symptom would be
  lost failure counts under load.

## Decision

> **User rows, then TOTP rows, inside the transaction. Session rows only after commit, never inside the lock.**

- `SessionTerminationService` is called from an after-commit hook, for all triggers in ADR-037.
- A failure to end sessions after commit never undoes the committed state change. The sweep is the repair.
- **Reconciliation sweep.** At startup, before traffic is served, the sweep ends the sessions of every account
  whose durable state says they should have none. It is idempotent and indexed. It needs no new scheduler.

## Consequences

- **The residual is a crash between commit and dispatch.** Transactions cannot close it, and the sweep repairs it at
  the next start. A dispatch that fails without a crash is not repaired until the next start. Running the sweep on
  Spring Session's existing cleanup schedule as well would close that gap without a new scheduler, if that scheduler
  can host it. That has not been verified.
- **What the sweep can see.** It can only reconcile triggers that leave durable state: the authenticator cap (a set
  `password_disabled_at`), admin disable, deletion (a session whose principal no longer exists), and a lock still in
  force. A role change, a credential change or a factor reset leaves nothing to reconcile against. If the process
  dies between commit and dispatch on one of those, the old session lives until idle or absolute expiry.
- ASVS 5.0 **2.3.3 (L2)** asks that a business-logic operation either succeeds entirely or rolls back. The session
  half of these operations cannot, so the sweep is the recovery path instead. This is an L2 item against the L1
  target, and the register grades it.
- **Lock order** follows from the rule: user rows before TOTP rows, and session rows never while either is held.
- The rule is short enough to follow without knowing what `REQUIRES_NEW` is, which is the point of stating it as a
  rule.
- Tests: T-SES-022 (the sweep ends the sessions of a capped account whose kill was never dispatched, and a second
  run changes nothing). The other durable-state triggers need sweep cases of the same shape. T-SES-021 covers
  after-commit dispatch for the cap.

## Sources

- Spring Session 4.1.x source: `JdbcHttpSessionConfiguration#createTransactionTemplate`
  (`PROPAGATION_REQUIRES_NEW`), `JdbcIndexedSessionRepository`.
- Spring Framework reference, Transaction Management, transaction propagation (`REQUIRES_NEW` suspends the outer
  transaction).
- OWASP ASVS 5.0, V7.4: 7.4.2 (L1). A disabled account, or a disabled authenticator, must not keep the session it
  minted. V2.3: 2.3.3 (L2).
