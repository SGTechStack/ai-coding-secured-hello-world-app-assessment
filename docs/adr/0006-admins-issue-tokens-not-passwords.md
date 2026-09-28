---
status: accepted
---

# ADR-006: Admins issue credentials by single-use token, never by generated password

When an administrator creates an account or resets a password, the application mints a single-use token and returns
it to the administrator once. The user sets their own password when they redeem it. No password is generated for a
user anywhere in the build. The admin recipe generates a plaintext password, and the governing standard mandates one
in one section, so a maintainer would plausibly restore it.

## Context

**The governing standard mandates two admin-reset models that cannot both hold.**

- **Token model.** §2 Happy Path step 11: the application generates a random, single-use reset token with a
  30-minute expiry, stores its hash, and returns the plaintext to the administrator exactly once. The user submits
  the token with the new password. §4 makes the split an Enforced Constraint: "Password reset uses separate
  endpoints: one for admin-initiated token issuance and one for user-facing token redemption". §5 Password Reset
  Tests repeat it.
- **Generated-password model.** §3.5 Password Policy: "Administrative password reset must generate a 12-character
  random password" with four character classes. §6 lists the same rule. The admin recipe (Standalone Privileged User
  Administration and Password Reset) implements this one. It writes a random plaintext password to the account and
  returns it in `NewPasswordDTO.newPassword`, with no token, expiry or hash.
- The Questions file (Q22) relabels the same 12-character rule as the initial password for admin-*created* accounts,
  a third reading.

One action cannot both return a redemption token and set the credential. This is a standards defect (R-STD-020).

**Admin creation has the same shape.** §2 Decision Logic says users created by administrators must be flagged to
change their password on first login, and disabled if they do not within the grace period. §5 User Administration
Tests say the same. That presumes the administrator set a password.

**ASVS 5.0 6.4.6 (L3)** settles it: administrators can initiate a reset but must not be able to change or choose
the user's password, so they never know it. **6.4.1 (L1)** requires system-generated initial secrets and activation
codes to be random and to expire after a short period or after first use.

## Considered options

- **Generated plaintext password, as the recipe implements.** The administrator knows the user's password. Fails
  6.4.6.
- **Generated password with a forced change and a 30-day grace.** The administrator still knows it, and a relayed
  plaintext credential works for up to 30 days if the user logs in and abandons the change.
- **Token issuance for both reset and creation (chosen).** Satisfies the standard's token model and its Enforced
  Constraint, and 6.4.6.

## Decision

- **Admin reset.** `POST /api/admin/users/{uuid}/password-reset` mints a `PASSWORD_RESET` token (ADR-007). The
  plaintext is returned once, with `Cache-Control: no-store`, and is never logged or cached. It redeems at
  `/api/password-reset/confirm`, the endpoint the PRD requires anyway, so the Enforced Constraint costs one endpoint
  and no new machinery.
- **Admin create.** `POST /api/admin/users` takes no password field. It creates a pending account and mints an
  `ACTIVATION` token, returned once, which the user redeems through the same activation path as self-registration
  (ADR-032). Invite and self-registration differ only in who initiates, how the token is delivered, and whether the
  email axis is uniform.
- Both endpoints sit on the administrator surface and inherit its role and factor rules (ADR-026). The self-action
  guard does **not** apply to reset. An administrator resetting their own password is legitimate.
- Issuing a reset token does not clear a lock (REJ-016). The user's redemption does (ADR-009).

## Consequences

- **No generator exists.** The 12-character and 20-character generators are gone, and with them the only path that
  would have kept character-class quotas (ADR-005).
- **No forced-change flag is set at creation.** The user chose their own password at redemption, so forcing a change
  would make them re-enter it as the "current" one. The remaining forced-change cases are the bootstrap seed and
  re-enable (ADR-046, ADR-047). The prescribed creation test cannot pass by design (R-ADM-012).
- **After this, no system-generated secret is relayed by a third party anywhere.** 6.4.1 is met on its
  short-lifetime limb: 30 minutes for reset, 24 hours for activation.
- **A pending invite must not count as an administrator.** Otherwise one real admin plus one unredeemed invite reads
  as two, and the real admin can demote themselves to zero usable admins. The two-admin invariant counts only
  activated, TOTP-enrolled administrators (ADR-048).
- Admin reset is also a recovery route. When another usable administrator exists, it handles a forgotten password or
  a disabled password authenticator (ADR-048, ADR-013), because redemption clears both (ADR-009).
- With the stubbed transport, the administrator reads and relays the token exactly as they would have relayed a
  generated password. Nothing is lost against the old model.
- The owner notification for an admin-issued reset, which is the only detector of admin abuse, is not built while
  there is no mail transport (R-CRED-022).
- Test: T-CRED-010 (the admin-issued token is returned once, is 43 characters of Base64url, differs on each issuance,
  and redeems at the confirm endpoint).

## Sources

- Standalone User Access Control Application Standard §2 Happy Path step 11, §2 Decision Logic, §3.5 Password
  Policy, §4 Authentication and Security Design, §5 Password Reset Tests and User Administration Tests, §6
  Operational Runbook.
- Standalone User Access Control Application Standard Questions, Q22.
- Recipe: Standalone Privileged User Administration and Password Reset (§3, `NewPasswordDTO`).
- OWASP ASVS 5.0, V6.4: 6.4.1 (L1), 6.4.6 (L3).
