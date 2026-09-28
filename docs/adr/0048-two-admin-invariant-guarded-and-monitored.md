---
status: accepted
---

# ADR-048: Two-admin invariant: guarded on mutation paths, monitored everywhere

At least two enabled, activated, TOTP-enrolled administrators must remain. One central `AdminActionGuard` enforces this
on the admin mutations that can remove an admin: **disable, demote and delete**. It runs under a pessimistic row lock
and counts in application code. The guard cannot see every way the count falls. A cascade, the failure cap and the
out-of-band recovery channels all change it without passing a mutation path. So the invariant is **guarded on
mutation paths and monitored everywhere**, through one `authenticable` predicate that the guard and the monitoring
signal both read. Admin recovery follows three routes keyed on that predicate. A maintainer would plausibly simplify
any part of this: drop the lock, count admin rows, or treat the guard as covering everything.

## Context

- TOTP is required on the whole admin surface (ADR-023), and there are no recovery codes (ADR-024). Another enrolled
  admin able to act independently is the in-app recovery route for a lost factor or forgotten password.
- PRD Stories 9–11 bar an admin from disabling, demoting or deleting themselves. The standard's Decision Logic bars
  self-directed create, update and delete through the admin update endpoint. The admin recipe's `updateUser` permits
  self-disable anyway (R-STD-030).
- Two admins demoting two *different* admins concurrently each read "two remain" and both commit, leaving zero. So a
  count read without a lock is a stated intention, not an invariant.
- H2's `SELECT … FOR UPDATE` is not allowed in queries with non-window aggregates, `GROUP BY`, `HAVING` or `DISTINCT`.
  Only the selected rows are locked, and uncommitted new rows from other transactions are neither visible nor
  lockable. H2 has no gap or predicate locking.
- TOTP enrolment is the existence of a `totp_user_details` row (ADR-053), and deleting a user cascades that row away.
  So deletion changes the enrolled-admin count through a table the delete statement never names.
- Admins created by invitation are unactivated and unenrolled until they redeem and enrol (ADR-006).

## Decision

### The guard

- **One guard, one call site.** Every admin mutation routes through a single service method that invokes
  `AdminActionGuard`. Not an annotation and not a filter: both are opt-in, and the next endpoint forgets them. An
  architecture test forbids admin controllers from reaching a repository directly (T-ADM-014).
- **Check 1: actor ≠ subject** for disable, demote, delete, unlock and factor reset (T-ADM-003). Admin password reset
  deliberately has no such rule, because resetting one's own password is legitimate.
- **Check 2: the invariant**, evaluated against the proposed post-change state, on **disable, demote and delete**.
  Factor reset is exempt from this count (ADR-049). A refusal is 409 (REJ-050).
- **What is counted.** Admins that are enabled, activated (`activated_at IS NOT NULL`) and have a `totp_user_details`
  row. A pending invite fails "activated", so one real admin plus one invite reads as one, not two (T-ADM-010).
- **Concurrency.** One `@Transactional` unit: select the admin rows `FOR UPDATE` on `users`, then the matching
  `totp_user_details` rows `FOR UPDATE`, count in Java, then mutate. The same lock set is taken on every guarded path,
  including those whose statement never names the TOTP table, because the delete path's cascade proves a per-path lock
  set is wrong on whichever path is added next. Lock order across the codebase is `users` → `totp_user_details` →
  session rows.
- **Decrement-safe, not phantom-safe.** An unenrolled admin has no TOTP row, and H2 cannot lock rows that do not exist
  yet. The invariant survives because an insert can only **raise** the count, while every change that can lower it
  (un-enrol, delete, demote, disable) acts on existing rows, which get locked. Anything built later on the assumption
  that the guard is phantom-safe is built on a property it does not have (R-ADM-015).

### One predicate, two readers

`authenticable` is defined once, over two tables: enabled ∧ activated ∧ `password_disabled_at IS NULL` ∧ TOTP row
present ∧ not tier-2 disabled. It has two readers:

- the guard, whose count uses the enrolment terms above;
- the **authenticable-admins gauge**, which evaluates all five terms. The deployer alerts when it drops below two
  (R-OBS-007).

Two copies of the predicate would drift, and the monitoring would silently stop matching the guard. Neither lock is a
term: a locked-out admin is still `authenticable`.

### Channels that change the count outside a guarded statement

The monitoring reader must observe all three, or it stops matching the guard in exactly the incident it exists for:

1. **`ON DELETE CASCADE`** removing a `totp_user_details` row. The delete path is guarded, but its statement never
   names the TOTP table. The uniform lock set is what covers it.
2. **The NIST failure cap** (ADR-013) arrives through the authentication path, which no guard observes. Two capped
   admins still count as enrolled while neither can log in. This channel is **monitored, not enforced**, and it is why
   the gauge reads `authenticable` rather than enrolment.
3. **The out-of-band channel**: the offline recovery runner at scope `totp` or `both` (ADR-072), which is today's
   only break-glass route (ADR-070). It runs with the application stopped, outside any transaction the guard could
   join, and it bypasses the guard on purpose, because break-glass must be able to reach zero admins. Also **monitored, not enforced**: their audit rows record the
   enrolled-admin count before and after, and a transition to zero raises an alert.

### Recovery routes

Keyed on `authenticable`, which each route points at rather than paraphrasing:

1. **Lockout, on either axis: auto-expiry.** The password ladder is 20/40/60 minutes (ADR-011). TOTP tier 1 lifts
   after 20 minutes (ADR-027).
2. **Forgotten password, single-account cap, lost phone or tier-2 TOTP disable, when another `authenticable` admin
   exists: in-app.** An admin password reset, whose redemption clears the cap (ADR-009), or a factor reset (ADR-049).
   If the other admin is `authenticable` but locked out, **route 2 waits on route 1**. It does not fall through to
   route 3, or an operator takes an outage for something that clears itself within the hour (R-ADM-019).
3. **No `authenticable` admin other than the subject** (sole admin, a targeted cap on every admin, zero admins): the
   offline recovery runner, as a planned outage (ADR-072). It is pass-with-note pending the first rehearsal
   (R-RUN-002).

## Considered options

- **A guard with no lock.** Rejected: the concurrent-demotion race above.
- **`SELECT COUNT(*) … FOR UPDATE`.** Not valid on H2 (see Context).
- **Serialisable isolation with retry.** Rejected. A retry loop around a security invariant is a correctness risk
  under load.
- **Locking only the rows each path names.** Rejected: the delete path changes the count through a cascade.
- **Counting admin rows.** Rejected: pending invites would count.
- **Guarding the full `authenticable` predicate.** Rejected. The cap arrives through authentication, which no guard
  observes, so a guard over five terms would still miss the case it was widened for.
- **Making the invariant a bootstrap precondition.** Rejected in ADR-047.
- **Annotations or a filter per endpoint.** Rejected: opt-in, and the recipe already shows the forgotten call.

## Consequences

- **Promote before demote.** At exactly two enrolled admins, neither can be disabled, demoted or deleted until a third
  is enrolled. Offboarding a leaver hits this. It is the invariant working (R-ADM-018).
- **Admin-on-admin takeover is not prevented** (TM-08). The flat admin role has no separation of duties, and factor
  reset is exempt from the count, so at exactly two admins A can take B over completely. At three or more the path was
  always open. Detection is the admin-reset and TOTP-removal audit events, whose durability fails ASVS 16.4.2 (L2)
  (R-ADM-009).
- The invariant goes beyond the PRD and is registered as a deviation (R-ADM-008).
- The gauge exists in the application, but with metrics export off, nothing reads it. Alerting is the deployer's
  (R-OBS-007).

## Sources

- PRD Stories 9, 10 and 11.
- Standalone User Access Control Application Standard §2 Decision Logic.
- H2 2.x SQL grammar, `SELECT` (`FOR UPDATE` restrictions, row locking, visibility of uncommitted rows).
- NIST SP 800-63B-4 §3.2.2 (the failure cap behind channel 2).
- OWASP ASVS 5.0: 6.1.1 (L1), 16.4.2 (L2).
- IM8 ac-2 (privileged access requires MFA, which is why the second admin must be enrolled).
