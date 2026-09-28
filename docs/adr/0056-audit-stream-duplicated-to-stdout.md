---
status: accepted
---

# ADR-056: The audit stream is duplicated to stdout, against the separate-destination constraint

Audit rows go to their own rolling file **and** to stdout, in every profile. The logging standard's §4 makes it an
Enforced Constraint to route audit logs to a dedicated appender and destination, so that access control, retention
and alerting can be applied to them independently. The stdout copy mixes audit rows into the general stream, so a
maintainer holding that constraint would remove it. It is kept on purpose, and the deviation is documented rather
than hidden.

## Context

- The logging standard's §4 Enforced Constraint: "Separate critical audit logs from standard application logs by
  routing them to a dedicated appender and log destination". Its stated purpose is to let retention, access controls
  and alerting rules apply to audit events independently. Its §5 test asks that audit events are present in the
  dedicated destination, "not only in the standard application log".
- The same standard's §3.1 log emission requirements say to emit newline-delimited JSON to standard output, one event
  per line. That clause is a requirement, but it does not carry the Enforced Constraint label. The two clauses pull in
  opposite directions for audit rows.
- §3.5 names the route the standard prefers for shipping: a durable local rolling JSON file read by a forwarding
  agent. Its §6 runbook says the platform handles forwarding, retention and encryption. This build has no agent and no
  collector.
- ASVS 5.0 16.2.3 (L2) asks only that logs go to files and services documented in the log inventory. It does not ask
  for exclusive destinations. ASVS 5.0 16.4.3 (L2) asks for transmission to a logically separate system.
- In Logback, a logger's events reach its own appenders and every ancestor's appenders unless additivity is set to
  false. Removing the copy is therefore a one-line change that no test of the file destination would notice.

## Considered options

- **Dedicated file only.** Meets §4 literally. Audit rows then reach a platform only through a forwarding agent on
  the file, which the deployer must add, and they miss the stdout route that §3.1 asks every event to take.
- **stdout only.** Fails §4 and its §5 test outright.
- **Dedicated file plus a stdout copy, both documented (chosen).**

## Decision

- The `audit` logger writes to a dedicated rolling file appender: daily rollover, 90 archives, and deliberately no
  total size cap (REJ-045). This is the dedicated destination §4 asks for, and the durable local buffer §3.5 names.
- The same rows are also written to stdout in every profile, as ECS NDJSON, one event per line.
- The generated log inventory records both destinations, with who can read each. That is how ASVS 16.2.3 (L2) is
  met: by documentation, not by exclusivity.
- The deviation from §4 is limited to the copy. The dedicated file still exists and holds every audit row.

## Consequences

- **The copy has the access control of stdout, not of the audit file.** Anyone who can read the application's
  terminal or container log can read audit rows. R-AUD-012 records that the audit log is neither access-controlled
  nor tamper-proof (ASVS 16.4.2 (L2), fail).
- **stdout is a route, not a separate system.** Nothing consumes it in this build, so ASVS 16.4.3 (L2) stays a fail
  with a deployer obligation to forward it to a collector on separate infrastructure (R-AUD-013).
- **The two appenders fail independently**, so one failing leaves the other reporting (R-OBS-003).
- Retention past the host belongs to the platform (R-AUD-022). The offline runner's stdout joins the documented
  destinations on the same 16.2.3 basis (R-AUD-034). 16.2.3 is one of the documentation-conformance requirements
  gated for drift (R-BLD-006).
- Tests: T-AUD-005 pins the dedicated file ("not only to stdout"), T-AUD-017 generates the inventory with its
  destination column, and T-AUD-011 pins the file's rollover settings.
- Nothing yet fails if the stdout copy is removed. It needs a test of its own: an audit row emitted on the `audit`
  logger must appear on stdout as well as in the audit file.

## Sources

- Structured Logging Application Standard §3.1 Inputs / Outputs (log emission requirements), §3.5 Security Contract
  (local log buffering; log inventory), §4 Architectural Design (Enforced Constraint: separate critical audit logs),
  §5 Test & Validation Standard (audit events in the dedicated destination), §6 Operational Runbook.
- OWASP ASVS 5.0: 16.2.3 (L2), 16.4.2 (L2), 16.4.3 (L2), 16.1.1 (L2).
- Logback manual, Architecture chapter, "Appenders and Layouts" (appender additivity; additivity true by default).
