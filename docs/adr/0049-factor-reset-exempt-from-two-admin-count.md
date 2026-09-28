---
status: accepted
---

# ADR-049: Factor reset is exempt from the two-admin count

`DELETE /api/admin/users/{uuid}/totp`, an admin resetting another admin's TOTP factor, is **not** subject to the
two-admin invariant (ADR-048). The `actor ≠ subject` rule still applies. Disable, demote and delete keep the invariant
unchanged. Exempting one admin mutation from the invariant looks like a hole, and a maintainer tidying the guard would
put it back. The reason it is not a hole is who can restore the count afterwards.

## Context

- The invariant exists so that a second admin can always act independently of the first. It is the compensating
  control for having no recovery codes (ADR-024).
- The guard evaluates the invariant against the proposed post-change state. At exactly two enrolled admins, A
  resetting B's factor would leave one, so the reset was refused. That is the lost-phone recovery the invariant was
  meant to compensate with, refused at the minimum population it enforces. Lost-phone recovery at two admins was
  therefore runner-only: a planned outage (ADR-072).
- After a factor reset, the subject's sessions are ended (ADR-037) and their pending enrolment row is deleted too, so
  an attacker's half-finished secret cannot survive the reset. The subject then logs in with their password alone,
  gets 422 `FACTOR_ENROLMENT_REQUIRED`, and the SPA routes them to enrolment.
- In-app, a tier-2 TOTP disable (ADR-027) is cleared only by a factor reset. The only other route is the offline
  recovery runner.

## Decision

- **The factor-reset path is exempt from check 2** (the two-admin count). Check 1 (`actor ≠ subject`) stays, so an
  admin cannot reset their own factor.
- **Zero is unreachable on this path anyway.** The endpoint needs the TOTP factor and refuses a self-reset, so the
  actor is always an enrolled admin other than the subject.
- **Why only this path.** Reversibility is not the distinction: disable and demote are reversible too (re-enable,
  promote), and only delete is permanent. The distinction is **who can restore the count**:
  - after disable or demote, only another enrolled admin can restore it. At two admins, that is the admin who just
    removed B;
  - after a factor reset, **B restores it alone**, by logging in and re-enrolling.

  What the invariant protects, a second admin able to act independently, survives a factor reset and does not survive
  the other three mutations.

## Considered options

- **Keep the count on factor reset.** Rejected: at two admins it refuses the lost-phone recovery the invariant exists
  to support, and sends an everyday case to a planned outage.
- **Go live with three admins**, so the count never blocks a reset. Rejected on cost, not security. At three or more
  admins the guard bounds nothing on this path either, so it buys the same security as the exemption at a higher
  staffing cost.
- **Exempt disable or demote as well.** Rejected: only another admin can reverse those.

## Consequences

- At two admins, a lost phone or a tier-2 disable is recovered in-app, not by an outage.
- **TM-08 widens at exactly two admins.** Admin A can now take admin B over completely: an admin password reset
  (no `actor ≠ subject` rule, by design) plus a factor reset, and since A holds B's reset token, A can be the one who
  re-enrols as B. Before, A got B's password but not B's factor. At three or more nothing changes; the path was
  already open. Detection is the admin-reset audit event with reason `ADMIN_RESET` and the TOTP-removal event
  (R-ADM-009).
- **Partial compensation, only if the deployer enables metrics export:** the authenticable-admins gauge drops below
  two during the takeover step (R-OBS-007). It also fires on every legitimate factor reset at two admins, so it says
  *that* something happened, not whether it was a takeover. At three or more it does not fire at all.
- Tests: at two enrolled admins A can reset B's factor (T-ADM-027) and still cannot disable, demote or delete B
  (T-ADM-028); A cannot reset A's own factor, and after the reset B re-enrols unaided and the `authenticable` count
  returns to two (T-ADM-029).

## Sources

- IM8 ac-2 (MFA for privileged access, the reason the second admin must be enrolled).
- OWASP ASVS 5.0: 6.1.1 (L1) (anti-lockout behaviour documented and operable); 16.4.2 (L2).
