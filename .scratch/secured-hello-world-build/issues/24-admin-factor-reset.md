# 24: Admin factor reset

**What to build:** An admin resets another admin's second factor so a lost phone is recoverable in the app (ADR-024; ADR-049).

`DELETE /api/admin/users/{uuid}/totp` deletes the confirmed and pending TOTP rows and ends the subject's sessions. It refuses self-action but is exempt from the two-admin count. It clears the tier-2 disable, and the subject must re-enrol at their next sign-in.

**Blocked by:** 19, 20

**Status:** ready-for-agent

- [ ] Resetting your own factor is refused.
- [ ] Resetting one of exactly two enrolled admins succeeds, so the exemption holds.
- [ ] After a reset the subject's sessions are gone. On sign-in they get `FACTOR_ENROLMENT_REQUIRED` and can re-enrol.
- [ ] A tier-2-disabled admin is recoverable by this route.
- [ ] An audit row names the actor and the subject.
