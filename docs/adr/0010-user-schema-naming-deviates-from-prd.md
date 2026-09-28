# ADR-0010: `users` schema naming deviates from the PRD

## Status

Accepted

## Context

The PRD's data model names a `password_hash` column and does not include a
`first_name` column. The actual `User` entity (`backend/.../user/User.java`)
instead has:

- `password` (not `password_hash`) — it does hold a BCrypt hash, matching
  the PRD's intent, just under a different column name.
- `first_name`, `NOT NULL` — an extra required column the PRD's model
  doesn't mention, collected at registration (`RegisterRequest.firstName`)
  and surfaced nowhere security-sensitive (never returned by `/api/admin/users`
  or logged).

Neither is a functional gap: the column *is* a password hash regardless of
its name, and collecting a first name is a product-shape choice, not a
security concern.

## Decision

Keep both as-is rather than rename `password` → `password_hash` or drop
`first_name` to match the PRD literally. Schema is entirely
Hibernate-managed (`spring.jpa.hibernate.ddl-auto=update`, see ADR-0008),
so renaming now is a pure-churn schema change with no behavioral upside,
and `first_name` reflects an intentional registration-flow decision already
built into the frontend's `RegisterPage`.

## Consequences

- Anyone comparing this codebase against the PRD's data model literally will
  see a mismatch on both points; this ADR is the record of why that's
  deliberate rather than an oversight.
- If a future requirement needs the column literally named `password_hash`
  (e.g. a schema-conformance check against the PRD text), it is a
  mechanical rename with no logic change, since nothing depends on the
  column name outside `User`/Hibernate mapping.
- If `first_name` should be optional or removed, that is a product decision
  affecting `RegisterRequest`, `RegistrationService`, and the frontend's
  `RegisterPage` together, not just the entity.
