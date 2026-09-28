# 20: AdminActionGuard and enable/disable

**What to build:** An admin disables or re-enables another account, with the protections that stop admins locking themselves or the system out (ADR-048).

- **One guarded service method** handles every admin mutation and calls `AdminActionGuard`. The guard's decision is a pure function in PIT scope.
  - Check 1, actor ≠ subject: a self-action gets 403 `ACCESS_DENIED` (REJ-050).
  - Check 2, the two-admin invariant: refuses disabling, demoting or deleting either of exactly two enrolled admins. It runs under a uniform pessimistic lock set, in the lock order `users`, then TOTP, then sessions after commit.
  - **Name the new closed-enum member for the invariant's 409** (spec Further Notes). The JSON Schema and the error contract pick it up automatically.
- ***Authenticable*** is defined once and read by both the guard and an authenticable-admins gauge (R-OBS-007).
- **Freshness.** Mutations need a factor issued within 10 minutes (ADR-021), otherwise 412 `MISSING_FACTOR` with reason `EXPIRED`.
- **`PUT /api/admin/users/{uuid}/enabled`:**
  - Disable ends the subject's sessions after commit.
  - Re-enable issues a forced-change credential (ADR-046).
  - Audit rows name the actor and the subject.
- **SPA:** an enable/disable control on user detail. A two-admin refusal shows the next step: invite, redeem, promote, enrol.

**Blocked by:** 18

**Status:** ready-for-agent

- [ ] Disabling yourself gets 403 `ACCESS_DENIED`.
- [ ] With exactly two enrolled admins, disabling either gets 409 with the new code. With three, it succeeds.
- [ ] A disabled user's live session is gone on its next request.
- [ ] A factor older than 10 minutes gets `MISSING_FACTOR`/`EXPIRED` on the mutation, while reads still succeed.
- [ ] The gauge reports the authenticable count from the same definition the guard uses.
- [ ] The guard meets 85% mutation score. A concurrency test shows two racing disables can't leave fewer than two enrolled admins.
