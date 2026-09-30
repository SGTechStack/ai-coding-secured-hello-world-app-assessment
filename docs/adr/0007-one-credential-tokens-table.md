---
status: accepted
---

# ADR-007: One `credential_tokens` table, domain-separated SHA-256, no HMAC, no Spring OTT

Activation, invite and password-reset tokens share one `credential_tokens` table with a type column. Each token is
stored as a SHA-256 over its type label and the token, never in plaintext. The PRD's data model names a
`password_reset_tokens` table, and Spring Security ships one-time-token support that looks like the obvious
framework swap. A maintainer would plausibly reach for either.

## Context

- The PRD's data model names `password_reset_tokens`. Self-registration now needs an activation token (ADR-032) and
  admin creation an invite token (ADR-006), so there are at least two token kinds.
- **There is no token machinery in the corpus.** No recipe defines a token entity, an expiry, a single-use flag, a
  `SecureRandom` call or a token hash. Everything here is built from zero.
- **SHA-256 for tokens is suggested, not mandated.** The governing standard's only mention is an italic Spring Boot
  note under §2 Happy Path step 11 (use `SecureRandom`, store a SHA-256 hash, because the token is already high
  entropy). The Questions file (Q22) states it flatly for activation tokens. Recorded as a standards defect
  (R-STD-021).
- **No ASVS requirement sets an entropy floor for reset tokens.** 6.6.3 (L2)'s 64-bit figure is a "consider", and
  its binding half is rate limiting. 6.5.2 (L2) says a standard hash suffices for a secret of 112 bits or more, but it
  is scoped to lookup secrets. It is cited here by analogy for the hash choice only.
- **One table carries a specific risk.** The only thing between an activation token and a reset token would be a
  `type` term in a `WHERE` clause. One dropped condition becomes an account takeover.

## Considered options

- **Separate tables per token kind, as the PRD names one.** Clearer constraints, but two expiry, single-use and
  invalidation code paths that can drift.
- **One table, hashing `SHA-256(token)`.** Cross-type safety rests on every query being written correctly.
- **HMAC instead of a plain hash.** The key facility exists (ADR-022), so it would be cheap. At 256 bits there is
  nothing to brute-force from a stolen table, so it adds nothing.
- **Spring Security one-time tokens** (`oneTimeTokenLogin()`). Declined on behaviour, not storage. OTT is a login
  mechanism: redeeming a token authenticates the user and establishes a session, and Spring Security 7 grants the
  `FACTOR_OTT` authority on success. Redemption here must never mint a session (sessions end, ADR-035), and a
  `FACTOR_OTT` authority would collide with the factor model (ADR-021).
- **One table, domain-separated SHA-256 (chosen).**

## Decision

- **Table:** `credential_tokens(id, user_id, type, token_hash, expires_at, used_at, created_at)`. `type` is stored as
  a string behind a named check constraint over `ACTIVATION` and `PASSWORD_RESET`. `user_id` has `ON DELETE CASCADE`.
  The DDL carries a comment that the table **stores only a hash**, because it is the column most likely to gain a
  plaintext sibling "for debugging".
- **Generation:** 256 bits from `SecureRandom`, Base64url without padding (43 characters). 256 bits is a
  first-principles choice, not a standards-derived one.
- **Storage:** `token_hash` is lowercase hex of `SHA-256(type_label || ":" || token)`, in `VARCHAR(64)`, with a unique
  index on `token_hash` alone. Cross-type redemption then fails cryptographically ("token not found") rather than
  because a query was written correctly. The Base64url alphabet has no `:`, so a crafted token cannot spoof the
  separator. Lowercase is part of the contract: an equality lookup that misses on case fails open on the reset path.
- **Lifetimes:** `PASSWORD_RESET` 30 minutes. `ACTIVATION` 24 hours, which meets ASVS 6.4.1 (L1)'s short-lifetime
  limb.
- **Single use:** one conditional update,
  `UPDATE credential_tokens SET used_at = :now WHERE token_hash = ? AND type = ? AND used_at IS NULL AND expires_at > :now`,
  proceeding only if exactly one row changed. It is atomic in one statement, so token rows never enter the row-lock
  ordering. The repository method needs `@Modifying(clearAutomatically = true, flushAutomatically = true)`, or a
  native query with no entity loaded on that path, because a bulk update bypasses the persistence context.
- **Redemption:** in one transaction, consume first, then set the password through the single password-setting
  component. A rejected password rolls back the consume, so it never burns the token. The token is still checked
  before password quality, so a strength error cannot confirm a token was valid.
- **Pending tokens are invalidated by:** any successful password set (reset tokens); a new issuance of the same type;
  admin disable (both types); and re-registration against an unactivated record (its activation token). Deletion
  needs no trigger, because the cascade removes the rows. A surviving token after deletion would be a live
  redemption path for a deleted account, so the database guarantees it rather than a remembered call.
- No constant-time comparison: the lookup is hash equality in SQL against a full-entropy value. No per-token attempt
  limit: at 256 bits, the per-source budget (ADR-010) is enough.

## Consequences

- The PRD data-model deviation is recorded in the deferral register (R-DATA-002).
- **A third type is designed but not built.** Break-glass recovery uses an issued recovery code (ADR-070). When mail
  transport exists it joins this table under its own type, with its own 24-hour email lifetime and its own
  throttling. It reuses the same hashing and single-use path. Until then the check constraint admits two values and
  the recovery routes are structurally absent.
- Tests: T-CRED-011 (cross-type redemption fails and consumes nothing); T-CRED-012 (the stored hash is the
  domain-separated digest and no column holds the plaintext); T-CRED-013 (30-minute expiry); T-CRED-014 (concurrent
  redemption, exactly one succeeds); T-CRED-015 (a rejected password does not burn the token); T-CRED-019 (pending-token
  invalidation); T-CRED-020 (the type check constraint); T-CRED-023 (recovery routes absent).

## Amendment (2026-09-30): admin-issued tokens carry an explicit marker, and self-service leaves them alone

**The defects.**
- **An invite was told apart from a self-registration by its role** (review of tickets 14 to 16, M1). Once
  `USER`-role invites existed (ticket 23), a stranger who knew an invitee's address could register it again under a
  username of their choosing: the repeated registration renamed the invited account and, through the new issuance,
  cancelled the activation token the administrator had handed on.
- **A self-service reset request cancelled a pending admin-issued reset token.** "A new issuance of the same type"
  invalidates the pending token, so anyone who knew the address could cancel an administrator's reset. Outside `dev`
  the replacement is undeliverable (ADR-057), so the user was left with no working token at all.

**Decision (the user's).**
- **`credential_tokens.admin_issued`** (V9, `BOOLEAN NOT NULL DEFAULT FALSE`) marks a token an administrator issued:
  an invite's `ACTIVATION` token or an admin-issued `PASSWORD_RESET` token (ADR-006). Nothing reads the role for this.
- **A self-registration replaces a pending registration only if its activation token was self-issued**, used, expired
  or pending. An invite's is admin-issued, so a self-registration of an invitee's address gets the uniform 202 and
  changes nothing: no rename, no cancelled token, no email. A pending row with no activation token at all (a disabled
  self-registration, whose token a disable deleted) is not treated as self-registered either, so the check fails closed.
- **While an admin-issued reset token is pending** (unused, unexpired), a self-service reset request for the address
  mints nothing and leaves that token in place. Its response is the same empty 202 as for every other address, and it
  writes the same audit row, which names no account (REJ-002). Its timing follows the no-account path: one account
  lookup and one indexed existence check, and no hash, insert or email. Once the admin token is redeemed or expires,
  self-service issues again.
- **An admin-issued token still yields to the ordinary triggers**: a new admin issuance of the same type, a password
  set, an admin disable and deletion all invalidate it as before. A self-service issuance deletes only self-issued
  pending tokens, so even one that raced past the pending check cannot remove an administrator's. An admin disable
  expires an administrator's pending token in place instead of deleting it, so its row keeps marking the invite.
- **Re-invite (the user's decision, 2026-09-30).** `POST /api/admin/users` for a username and address that both name
  one enabled, never-activated account an administrator invited (it holds an admin-issued activation token, in any
  state) issues the invite again on that row: its outstanding token is cancelled and a new admin-issued one minted in
  the same transaction, under the account's row lock, with the same 201 as a first invite. Nothing is deleted and no
  tombstone is written, since it never activated, and username holds are untouched. The account takes the role the
  administrator asks for now: it has never held a password or a session, so no authority changes hands, and the role
  is the administrator's to choose (ADR-006). It writes its own audit row, "Invitation re-issued." on row 27's action.
  An activated or disabled account, a pending self-registration, a tombstoned identifier, or a username and address
  naming different accounts stay `USER_EXISTS`: the marker check fails closed.

**Consequences.** A stranger can no longer cancel or redirect a credential an administrator issued. A user who holds
an admin-issued reset token and asks for a self-service one gets nothing new for up to 30 minutes; the admin token is
the one to use. An invite whose token expired, or was cancelled by a disable, is recovered by inviting it again: the
invitee's own registration still leaves it alone, and a disabled invite is re-enabled first. Tests: T-CRED-019 (the triggers, admin disable and delete included); the invite and admin-reset tests
of ticket 23 (`AdminInviteTest`, `AdminReinviteTest`, `AdminPasswordResetTest`) prove both protections and the
re-invite.

## Amendment (2026-09-30, ticket 32): an expired invite lapses like a self-registration

**The defects (review of ticket 29).**
- **An expired invite held its identifiers for good.** An invite never lapsed, so once its link expired unredeemed
  its username and address were blocked until an administrator re-invited it: the invitee's own registration of the
  address created nothing, and no other address could take the username.
- **An invite could not free a lapsed self-registration.** A self-registration that had lapsed (ADR-032 amendment of
  2026-09-29) still made an invite of its username or address `USER_EXISTS`, and nothing but another registration of
  that username could remove it.

**Decision.** One lapse rule, read by both registration and invite (`PendingRegistrationLapse`):
- **An invite lapses once its admin-issued activation token has expired unredeemed**, which is 24 hours after the
  last invite, the same period a self-registration gets from its last registration. A re-invite mints a new token and
  so renews it. The admin-issued marker still decides what an invite is; the role never does.
- **A lapsed invite is deleted by the next registration that needs it**, without a tombstone and with the lapse audit
  row (row 48), exactly as a lapsed self-registration is: another address's registration of its username, or a
  registration of its own address. The address then registers as a new one, so the invitee gets a `USER`-role
  self-registration and their own emailed link; the invite's role is never inherited, because it was the
  administrator's to choose (ADR-006).
- **An invite frees a lapsed pending registration**, self-registered or invited, that holds its username or address:
  it deletes it under its row lock in the invite's transaction, audits the deletion, and proceeds. A live one still
  answers `USER_EXISTS`.
- **Unchanged:** a live invite is never replaced or renamed by a registration (the amendment above); an
  administrator's re-invite of a pending invite, expired or not, re-issues on the same row; a disabled invite never
  lapses, because its disable expired the token in place and freeing its identifiers would undo the administrator's
  decision; a pending row with no activation token at all never lapses (the check fails closed).
- **A re-enable does not renew an invite; a re-invite does.** A disable expired the invite's token in place, so an
  invite re-enabled after it has no live token and has lapsed at once. The administrator re-invites it, as the
  amendment above already says to, which mints a new token and restarts its 24 hours.

**Why this option.** It is the rule ADR-032 already applies to self-registration, for the same reason: a pending
registration that no one can activate should not squat identifiers forever. The protection this ADR's first amendment
gives an invite rests on its token being usable; once the token has expired there is nothing left to protect, and
keeping the identifiers blocked only locked the invitee out. Registration's enumeration stance is unchanged: the wire
answer is the same 202 in every email state, and the lapse deletion runs in its own short transaction, locking one
account row, like the other lapse step (T-CRED-027).

**Consequences.** An invitee who misses the 24-hour window can register themselves, or be re-invited, whichever comes
first. An administrator who wants an unredeemed invite held longer must re-invite it within the day. Tests:
T-CRED-030 (an expired invite lapses; live, disabled and re-invited ones do not), T-ADM-035 (an invite frees a lapsed
self-registration), T-CRED-031 (an invite and a registration racing on one identifier never answer 500).

## Sources

- PRD, Data model (`password_reset_tokens`).
- Standalone User Access Control Application Standard §2 Happy Path step 11 (including its Spring Boot note), §5
  Password Reset Tests.
- Standalone User Access Control Application Standard Questions, Q22.
- OWASP ASVS 5.0, V6.4, V6.5 and V6.6: 6.4.1 (L1), 6.5.1 (L2), 6.5.2 (L2), 6.5.3 (L2), 6.6.3 (L2).
- OWASP Forgot Password Cheat Sheet (token length and single use).
- Spring Security reference, One-Time Token Login and Multi-Factor Authentication (`FACTOR_OTT`).
