# ADR 0007 — Irreversible actions are recorded in an append-only audit table

- **Status:** Accepted
- **Date:** 2026-09-24
- **Context:** accountability

## Decision

Consequential actions write an `AuditEvent` row to `admin_audit_log` **in the same
transaction as the action itself**. The set is closed
(`AuditAction`): enable/disable, role change, account deletion, email reveal,
self-service export, self-service erasure.

Append-only is enforced at three levels, with one deliberately left to deployment:

1. **The entity.** No setters, no public no-arg constructor, every field
   `@Column(updatable = false)` — so Hibernate will not emit an `UPDATE` even if
   something tried.
2. **The repository.** `AuditEventRepository extends Repository`, the bare marker
   interface, not `JpaRepository`. Spring Data generates an implementation for
   exactly the declared methods, so the absence of a delete method is the absence
   of a delete capability.
3. **The schema.** No foreign key from `actor_id` or `target_id` to `users`.
4. **The database grant — not done, and not doable from here.** The migration runs
   as the schema owner, so it cannot restrict itself. This belongs in deployment
   provisioning, with the application connecting as a role distinct from the
   migration owner:

   ```sql
   REVOKE UPDATE, DELETE, TRUNCATE ON admin_audit_log FROM <application_role>;
   GRANT INSERT, SELECT ON admin_audit_log TO <application_role>;
   ```

## Why the transaction, specifically

The finding was that destructive admin actions left nothing behind but an
unstructured INFO line. The log line is a side channel: the mutation commits, the
line is written, and if the process dies between them — or the transaction rolls
back after it — the two disagree, with no way afterwards to tell which happened. A
record that can disagree with the thing it describes is not evidence.

`AuditService.record` is annotated `Propagation.MANDATORY`. It does not start a
transaction; it refuses to run outside one. A future caller who audits from a
non-transactional context fails loudly at that call, rather than quietly writing a
record that commits independently — which is the failure mode that would
reintroduce the original problem while looking like a fix.

## Why no foreign key

This is the design decision the deletion case forced. A foreign key would have to
either cascade the audit row away with the account — destroying the evidence that
the deletion happened — or block the deletion outright. The record has to outlive
its subject, so `actor_id` and `target_id` are plain UUID columns and the
pseudonymous references are copied in at write time rather than derived on read.

Asserted by `AdminAuditTrailTest.theAuditRowSurvivesTheAccountItDescribes` and
`FlywayMigrationTest.theAuditTableHasNoForeignKeyToUsers`.

## Consequences

- **Application-level immutability only, until the grant above is made.** Anyone
  with the application's database credentials can still rewrite history. Stated
  plainly rather than implied to be covered.
- **The table grows without bound.** Retention is stated as 1 year in ADR 0005 and
  is not yet enforced — and enforcing it needs care, because a delete job on an
  append-only table is precisely the capability the design removes. It should run
  as a separate role, not as the application.
- **The log line is still emitted** alongside the row, carrying the same reference
  and correlation id. The table is the authoritative record; the line is what gets
  shipped to an aggregator and alerted on. Neither is a substitute for the other.
- Ordinary reads are not audited. An audit table that records everything is one
  nobody reads.

## Related

- Threat model TM-39, TM-27, TM-34
- ADR 0006 (pseudonymous identifiers)
- ADR 0004 (the migration, and what it cannot grant)
