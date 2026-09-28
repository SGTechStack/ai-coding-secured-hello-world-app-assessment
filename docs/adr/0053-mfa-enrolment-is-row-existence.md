---
status: accepted
---

# ADR-053: MFA enrolment is row existence on shared-key tables, not a flag

A user is TOTP-enrolled if and only if a `totp_user_details` row exists for them. There is no `totp_enrolled` flag on
`users`. Both MFA tables, `totp_user_details` and `pending_totp`, use the user's id as their own primary key, so the
key is also the one-row-per-user constraint. `im8-review` greps the user entity for an MFA flag, so a maintainer
chasing a green scan would add one. That would create a second stored source of truth for one fact.

## Context

- The MFA standard normalises enrolment state into its own table, rather than a column on the user.
- The same shape is already used for lockout: `isAccountNonLocked()` is derived from `locked_until`, not stored,
  because two sources of truth for one fact drift.
- The two-admin invariant counts enrolled admins under a row lock (ADR-048). Deleting a user cascades the TOTP row
  away, so deletion changes the count through a table the delete never names.
- The `im8-review` MFA-state check reads only the user entity.

## Decision

- **Enrolled ⇔ a `totp_user_details` row exists.** No flag, no `enrolled_at` column, since nothing reads it.
- **Shared primary keys.** `totp_user_details.user_id` and `pending_totp.user_id` are each the table's primary key and
  a foreign key to `users.id` with `ON DELETE CASCADE`. The primary key is the uniqueness constraint, so no further
  unique index is needed, and "is enrolled" is a primary-key lookup.
- **Keyed on the user id, not the username** as the recipes' `findByUsername` does. The user is already loaded on
  every path that touches these rows, and keying on the username would copy an identifier into two more tables.
- **Both tables are created in one migration** with identical 69-byte ciphertext widths, because enrolment
  confirmation copies the pending blob verbatim.

## Considered options

- **A `totp_enrolled` flag on `users`.** Rejected. It must be kept in step with the row on enrolment, factor reset,
  tier-2 handling and cascade delete, and the one place it would drift is the invariant's lock set. It would also add
  a column the guard must lock and read for no gain.
- **A surrogate key on the MFA tables plus a unique index on `user_id`.** Rejected: one more column and index for the
  same constraint.

## Consequences

- `im8-review`'s MFA-flag check reports a missing flag against a compliant design. That is a documented false negative
  (R-MFA-005).
- The invariant reads two tables, so the guard locks both, in the order `users` → `totp_user_details` (ADR-048).
- An unenrolled admin has no row to lock. That is why the guard is decrement-safe but not phantom-safe (R-ADM-015).

## Sources

- MFA standard (`MFA_Core`) data model for TOTP user details.
- IM8 ac-2; the `im8-review` MFA-state check.
