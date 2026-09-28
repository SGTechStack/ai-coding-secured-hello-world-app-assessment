---
status: accepted
---

# ADR-044: Deletion leaves a tombstone: HMAC of the email, plaintext username, indefinite retention

Deleting a user removes the `users` row and, in the same transaction, writes a row to `deleted_users`: the deleted
user's id, the plaintext username, a keyed HMAC of the canonical email, the deletion time and the deleting admin's
id. The tombstone is kept indefinitely and blocks reuse of **both** identifiers. PRD Story 11 says the account "is
removed", so a maintainer holding the PRD would delete the tombstone table as dead weight, or keep it and drop the
email check. Either reopens a recovery-channel hijack.

## Context

- The governing standard's §4 Enforced Constraint requires deleted users to be retained through soft delete, to keep
  the audit trail. §3.3 requires administrative deletion to preserve a tombstone with the deleted user's details and
  the deletion time. §5 requires user creation to fail when the username exists in a tombstone.
- The admin recipe checks tombstones for the username but **not for the email** (R-STD-028). Email is the
  password-reset identifier. An unblocked address lets a new account claim a deleted user's recovery channel.
- Singapore's PDPA Retention Limitation Obligation applies to personal data regardless of what IM8 says. The IM8
  controls that bind this application contain no retention control, which is a compliance observation, not a privacy
  argument.
- The live `users` row is genuinely deleted, and every child table cascades from it (REJ-034).

## Decision

- **Columns:** `user_id`, `username`, `email_hmac`, `deleted_at`, `deleted_by_id`. Unique indexes on `username` and
  on `email_hmac`, because a duplicate tombstone would mean two accounts had claimed one identifier.
- **Written in the delete's transaction.** A failure after the tombstone write commits neither (T-ADM-004).
- **Both identifiers are blocked for reuse** (T-ADM-002), each compared in its canonical form (ADR-045). Without a
  single canonical form the HMAC silently reintroduces the hijack: `HMAC("Alice@Example.com")` differs from
  `HMAC("alice@example.com")`.
- **The email is a keyed HMAC; the username is plaintext.** The email is the higher-value identifier and the recovery
  channel. The username is a handle every admin already sees in the user list. The key is ADR-052's.
- **Retention is indefinite.** No purge column and no purge job.
- **Neither `user_id` nor `deleted_by_id` is a foreign key.** A foreign key on `user_id` is violated the moment the row
  is written, because the parent is being deleted. One on `deleted_by_id` would block, or cascade away, the
  accountability record as soon as the deleting admin is deleted in turn. Both columns carry a DDL comment saying so,
  or a reviewer files the absent constraints as a defect. A test fails if a foreign key is added (T-ADM-021).
- **What the tombstone does not hold:** no password hash (history rows purge by cascade, REJ-035) and no role.
- **Enumeration.** A tombstone hit on self-registration takes the same path as a live duplicate: the uniform response,
  nothing created, no token minted (ADR-032). Admin-initiated creation returns the specific `USER_EXISTS`, because
  that caller is privileged and factor-verified. No notification is possible on a tombstone hit, since no address is
  held.

## Considered options

- **Hard delete, as PRD Story 11 reads.** Rejected: the standard's §4 Enforced Constraint. The deviation from the
  PRD's wording is registered (R-ADM-002).
- **A plaintext email in the tombstone.** Rejected on PDPA grounds: an indefinitely retained readable address of a
  person who asked to leave.
- **No email in the tombstone, as the recipe does.** Rejected: the recovery-channel hijack.
- **An HMAC of the username as well.** Rejected. The username is not a secret from any admin, and §3.3 asks the
  tombstone to keep the deleted user's details for audit, which a hash of the handle would not.
- **A role column**, so that the permanent record says whether the deleted account was privileged. Rejected. It widens
  indefinite retention of personal-adjacent data, against the same minimisation argument that made the email an HMAC.
  The residual is registered (R-ADM-010).
- **Bounded retention.** Not adopted. Indefinite retention is the option the standard's questionnaire (Q29)
  recommends, and a window would re-open both identifiers for reuse when it closed. Bounded retention is the
  reopening trigger for ADR-052 (R-CFG-003).

## Consequences

- **A keyed HMAC is pseudonymisation, not anonymisation.** The key holder can still confirm a guessed address. The
  design reduces exposure; it does not discharge the retention obligation the way deletion would.
- A deleted user can never re-register with the same username or email. Their only route back is a different address.
- Reuse blocking depends on the tombstone key being present and correct. A missing key would fail open silently, so its
  absence refuses startup (ADR-052).
- Blocking is enforced by two unique indexes that cannot see each other, so a registration interleaved with a delete
  of the same identifier can slip through. That race is accepted, with a reopening trigger (REJ-036, R-DATA-011).
- Past the audit retention horizon, the permanent record says an account existed and who deleted it, but not whether
  it was an admin (R-ADM-010, TM-09).

## Sources

- PRD Story 11.
- Standalone User Access Control Application Standard §1 Definitions (deleted-user tombstone); §2 Failure Paths
  (username in tombstones); §3.3 Audit Contract; §4 Data Persistence (Enforced Constraint); §5 User Administration
  Tests.
- Standalone User Access Control Application Standard Questions, Q29 (tombstone retention).
- Personal Data Protection Act 2012 (Singapore), Retention Limitation Obligation; PDPC Advisory Guidelines on Key
  Concepts, chapter 18.
