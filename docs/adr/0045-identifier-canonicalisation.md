---
status: accepted
---

# ADR-045: Identifier canonicalisation: NFC, trim, lowercase, no dot or tag folding; the canonical value replaces the stored one

Usernames and email addresses are canonicalised by one shared function: **Unicode NFC normalisation, then trim, then
lowercase**. Nothing else. No Gmail-style dot folding, no `+tag` stripping. The canonical value **is** the stored
value; there is no second column holding what the user typed. The two identifiers then diverge: a username that
canonicalisation would change is **rejected**, while an email is **transformed**. Two "fixes" are likely. One is
provider-style folding to catch more duplicates. The other is preserving the submitted case for display or delivery.
Both are rejected below.

## Context

- Identifier uniqueness is enforced across two tables, live `users` and the `deleted_users` tombstone (ADR-044). The
  tombstone holds only an HMAC of the email, so the comparison is only as good as the canonical form fed into the
  HMAC.
- RFC 5321 §2.4 says the local part of a mailbox MUST be treated as case sensitive, and that mailbox domains are not
  case sensitive. It also discourages exploiting local-part case sensitivity. §2.3.11 says the local part is
  interpreted only by the host named in the domain.
- A case-insensitive unique index is not available on this stack. H2's `CREATE INDEX` takes columns, not
  expressions, so `CREATE UNIQUE INDEX … ON users (LOWER(email))` is not expressible. H2's `VARCHAR_IGNORECASE` is
  proprietary, would break the vendor-neutral DDL, and would change behaviour silently on PostgreSQL.
- The username format is `[a-z0-9._-]{3,32}`, so `@` is rejected (REJ-027). A valid username is already in canonical
  form.

## Decision

- **One canonicaliser, two identifiers, both tables.** The same implementation feeds the live unique indexes and the
  tombstone HMAC. The steps are NFC, trim and lowercase, in that order.
- **The canonical value replaces the stored value.** One `username` column and one `email` column, each holding the
  canonical form. So `ux_users_email` sits on the value that uniqueness is actually about, not on a raw address.
- **Username: reject.** If canonicalisation would change a submitted username, the username is invalid. Silent
  transformation would give the account a name the user did not type, and it is the plaintext username that the
  tombstone keeps forever.
- **Email: transform.** The submitted address is canonicalised and the canonical form is stored and used.
- **Never applied to passwords.** A password with surrounding spaces and mixed case authenticates only as submitted
  (T-CRED-007). The password policy does its own NFC normalisation at set and verify, and nothing else (T-CRED-006).

## Considered options

- **Provider-specific folding** (dots, `+tags`). Rejected. RFC 5321 leaves local-part semantics to the receiving host,
  so folding one provider's conventions onto every provider rejects addresses that are genuinely distinct.
- **Preserving the submitted case** (store as typed; compare canonically; or keep a second column). Rejected. The
  only consumer of the original capitalisation would be mail delivery, and a second column means every read must know
  which value it holds. Comparison on a derived value also needs the expression index this stack lacks.
- **A case-insensitive index or column type.** Not available portably (see Context).
- **Case-folding only the domain**, which is all RFC 5321 guarantees. Rejected. `Alice@example.com` and
  `alice@example.com` would then be two accounts, and one of them could be a deleted user's recovery address. The
  whole-address lowercasing is a registered deviation from RFC 5321 §2.4 (R-CRED-014).

## Consequences

- **The failure mode of lowercasing the email:** a user whose provider really does treat the local part as case
  sensitive cannot receive reset mail. Reset is the email's only consumer, so it is the only thing that can break.
  Accepted and registered (R-CRED-014).
- **The canonicalisation contract is portable only on H2 and PostgreSQL.** MySQL's default collation,
  `utf8mb4_0900_ai_ci`, is accent-insensitive as well as case-insensitive, and NFC does not fold accents. So under
  MySQL `josé@…` and `jose@…` would collide where on H2 they do not, a folding this decision explicitly declines,
  reintroduced by a default nobody wrote. It is recorded in the seam register (R-DATA-009) and becomes a functional
  difference if a MySQL runtime ever appears (R-DATA-012).
- Users cannot log in with their email address, because usernames cannot contain `@` (R-CRED-015).

## Sources

- RFC 5321 §2.3.11 and §2.4.
- Unicode Standard Annex #15, Unicode Normalization Forms (NFC).
- H2 2.x SQL grammar, `CREATE INDEX` (column list only) and data types (`VARCHAR_IGNORECASE`).
- MySQL 8.0 reference, character sets and collations (`utf8mb4_0900_ai_ci` default).
