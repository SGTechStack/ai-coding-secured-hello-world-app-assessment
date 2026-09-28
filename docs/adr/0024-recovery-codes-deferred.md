---
status: accepted
---

# ADR-024: Recovery codes deferred; two enrolled admins and break-glass compensate

No saved recovery codes are issued at TOTP enrolment. An administrator who loses their authenticator is recovered
by **another administrator resetting their factor**, which a two-enrolled-admins invariant keeps possible. The
genuine zero-admin case goes to the offline break-glass runner. Recovery codes are the expected companion to TOTP,
and a maintainer would add them. This ADR records why they are absent, what depends on their absence, and the
branch to take when they are added.

## Context

- The corpus has no answer for a lost authenticator. There is no TOTP removal recipe, no recovery codes, and the only
  admin-unlock recipe touches the PIN table. The standard also forbids self-service re-provisioning once a key
  exists. As prescribed, a single-admin deployment that loses its phone is locked out of its own admin module.
- **Restoring access after authenticator loss is NIST SP 800-63B-4 §4.2 account recovery.** At AAL2, §4.2.2.2
  requires one of a closed list of two-element combinations. The route this plan takes is an issued recovery code
  plus the password, which needs mail transport. Until that exists, the offline runner is the only route (ADR-070).
- **Declining saved codes is not a NIST failure.** §4.2.1.1's issuance clause is a SHOULD, scoped to "a CSP that
  supports this recovery option".
- **Mail is stubbed.** The `dev` stub logs what it would send, so a log reader can already reset any password.
  Today the TOTP factor contains that leak for administrators. Any recovery credential that clears the factor and
  travels the stub channel would make the leak total for admins (ADR-070).

## Decision

- **Saved recovery codes are deferred**, not built.
- **Admin resets admin.** `DELETE /api/admin/users/{uuid}/totp` is admin-only, behind the factor, audited, deletes
  the confirmed and any pending TOTP row, and ends the target's sessions.
- **The two-enrolled-admins invariant** keeps that reset available. It guards disable, demote and delete under a
  pessimistic lock (ADR-048). **The factor-reset path is exempt from the count** (ADR-049). Without the exemption,
  the guard refused A resetting B at exactly two admins, so the compensating reset was refused at the minimum
  population it enforced. The exemption is safe because the subject restores the count alone by re-enrolling.
- **Break-glass** covers zero reachable admins: the offline same-jar runner, with deploy-level access (ADR-072).

## Considered options

- **Saved recovery codes now.** Deferred. For a single-tenant reference application they duplicate the admin-reset
  path, and they add a fourth throttled counter and an invalidate-and-reissue rule to build and test.
- **Recovery contacts, with the second admin as the contact.** Rejected. NIST §4.2.1.3 requires the subscriber to
  nominate and manage their contacts. The second admin is system-designated and has no management surface, so it
  would match the shape of the option without the class it claims (ADR-071).
- **Emailed recovery codes.** Blocked by the stub transport, for the containment reason above (ADR-070).
- **Admin reset, two-admin invariant and break-glass (chosen).**

## Consequences

- **The invariant carries more than this ADR.** It is also the reason recovery contacts were considered at all
  (ADR-071). Removing it gets correspondingly harder.
- **At exactly two admins, either one can take over the other** by resetting their factor. That widening is a
  recorded residual (R-ADM-009).
- **Every expensive branch of the break-glass design follows from this deferral**, including the distinct recovery
  address (ADR-071) and the offline runner's outage (ADR-072).
- **Reopening trigger (R-MFA-021):** reversing this deferral. The branch to take is **saved plus issued** codes,
  NIST §4.2.2.2 option 1. The saved code lives offline, not in the mailbox the password protects, so it closes the
  password/mailbox correlation and needs no second human. Design notes for that day:
  - issue at enrolment, inside the password-plus-fresh-TOTP session; §4.2.1.1 sets no channel constraint on
    issuance;
  - mint at 112 bits or more, not the 64-bit floor. That settles whether storage follows §4.2.1.1's one-way
    function or §3.1.2.2's salted scheme, and meets ASVS 6.5.2 (L2) and 6.5.4 (L2) at once;
  - saved-code verification is throttled under §3.2.2, and a used code set is invalidated and reissued.
- Tests: T-MFA-009 (admin reset removes both factor rows and audits), T-CRED-023 (recovery routes are structurally
  absent while mail is stubbed).

## Sources

- NIST SP 800-63B-4 §4.2, §4.2.1.1, §4.2.1.3, §4.2.2.2, §3.1.2.2, §3.2.2.
- OWASP ASVS 5.0: 6.5.2 (L2), 6.5.4 (L2).
- Unified MFA Application Standard (`Appfw-Mfa-Standards/MFA_Core`) §3.4 Setup self-service guard; the MFA recipes
  (no TOTP removal recipe; the admin unlock recipe covers PIN only).
