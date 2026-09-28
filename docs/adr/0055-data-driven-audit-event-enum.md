---
status: accepted
---

# ADR-055: One data-driven `AuditEvent` enum and a single `emit`, not one method per event

Every audit row is described by a member of one closed `AuditEvent` enum, and one method, `emit(event, context)`,
writes every row. There is no `logLoginSuccess`, `logLockout` and so on. A maintainer reading the typed-module
logging recipe would refactor back to one method per event, because the recipe calls that its key decision. Five
constraints below exist so that the data-driven shape keeps the protections typed methods gave for free.

## Context

- The recipe "Centralising Audit Logging with a Typed Module" defines an `AuditLogger` with one method per event
  (§4). Its §7 names "One method per event type" as a key decision, and its §8 says "Typed methods over generic event
  objects". It is a recipe, not an Enforced Constraint of the logging standard.
- The catalogue has more than forty rows. One method each means that many near-identical builder chains, and each is
  a place where a mandatory field can be forgotten.
- What must survive is set by the standards, not the recipe: a dedicated appender and destination for audit logs
  (logging standard §4), and the field rules of §3.3 and §3.4 of both standards.
- A generic emitter reopens three holes that typed methods closed by shape. A map-typed context invites domain
  objects, which the logging standard's §4 lists as an anti-pattern. A required-field check alone still lets a call
  site add `user.email`. And a throwable parameter leaks: Spring Boot's ECS formatter writes `error.type`,
  `error.message` and `error.stack_trace` whenever the event carries a throwable, so an exception whose message holds
  request content reaches the audit stream with no call site naming it.
- ASVS 5.0 16.1.1 (L2) asks for a log inventory, and 16.3.3 (L2) asks that the documented events are logged. A
  catalogue kept by hand drifts from the code.

## Considered options

- **One typed method per event (the recipe).** Structurally safe, but it multiplies the builder chains and gives no
  single place to check completeness.
- **A generic `emit(String action, Map<String, Object> fields)`.** Short, and it throws away every structural
  protection.
- **A closed enum of row definitions plus one `emit`, with typed context records (chosen).**

## Decision

`AuditEvent` holds each row's constants: action, type, level, severity, reason family, and the required and allowed
keys. `emit(event, context)` is the only writer on the `audit` logger. Five constraints:

1. **The context is never a `Map<String, Object>`.** Each family has a context record carrying only UUIDs, enums and
   primitives.
2. **Unknown keys are rejected, not only missing ones.** The allowed-key list derived from the definition enforces
   the negative list. The required-key check only enforces completeness.
3. **`emit` has no throwable parameter**, and no main code calls `setCause` on the `audit` logger. Record `error.type`
   where the class matters; throwables stay on the application logger.
4. **Fail soft at runtime, hard at test time.** Rows are written after commit (R-AUD-009), so throwing from `emit`
   could undo nothing and could turn a committed operation into a 500. A bad call emits a degraded row plus an ERROR
   alert instead. The tests make that path unreachable.
5. **Reasons are bound by type and serialised by code.** A sealed `AuditReason` hierarchy ties each event to its
   reason family, so a mismatch fails to compile. Reasons serialise through an explicit `code()`, not `name()`,
   because the values are saved-query targets and a rename would silently break every dashboard.

## Consequences

- Adding an event means adding an enum member and, if needed, a context record. The tests then cover it with no new
  test code.
- T-AUD-007 walks every member: full field set, path and method on request-scoped rows, reason in family, degraded
  path never reached. T-AUD-014 maps every required audit event to at least one member, with the three N/A events
  (security-header configuration change, critical configuration change, bulk export) as explicit negative entries.
  The list is the union of the governing standard's §3.3 and the logging standard's §3.4. T-AUD-016 pins constraint 3. T-AUD-017 generates the
  ASVS 16.1.1 inventory from the enum as a snapshot, so the catalogue document cannot drift.
- The degraded row is one leg of in-process audit-failure detection (R-OBS-003).
- Two constraints have no test yet. Constraint 2 needs a negative test: `emit` with a key outside the event's allowed
  set must produce the degraded row, not the key. Constraint 5's `code()` rule needs a test that the serialised
  reason values match a committed list, so a renamed enum constant fails.

## Sources

- Centralising Audit Logging with a Typed Module §4, §7 ("One method per event type"), §8.
- Structured Logging Application Standard §3.3, §3.4, §4 (dedicated appender and destination; domain objects as log
  arguments listed as an anti-pattern).
- Standalone User Access Control Application Standard §3.3 Audit Contract, §3.4 Logging Contract.
- OWASP ASVS 5.0: 16.1.1 (L2), 16.3.3 (L2).
- Spring Boot 4 source: `ElasticCommonSchemaStructuredLogFormatter` (writes `error.type`, `error.message` and
  `error.stack_trace` when a throwable is present).
