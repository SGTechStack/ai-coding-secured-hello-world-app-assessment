# 39 — Write the authentication ADRs: lockout, throttling and source keying

Type: task
Status: resolved
Blocked by: 34
Blocks: 18

## Question

Write the eleven ADRs that [ticket 34](34-adr-candidate-list.md) routed to this ticket. They go to `docs/adr/`, in
the `domain-modeling` skill's ADR format: **ADR-010 to ADR-020**. That covers the following:

- dual rate limiting;
- the lockout ladder and the observation window;
- the NIST §3.2.2 cap and the multi-authenticator reading;
- the progressive-delay decline and the cardinality axis;
- miss-metered budgeting and the pre-CSRF rejection decline;
- the audit emitter bound;
- the /64 source key.

*Split from [35](35-adrs-authentication.md) by ticket 34.* It is owner 35's work, by 17 Answer §2's source split.

The ID, title, merged sources, attached amendments and filter answer for each ADR are in
[`adr-routing/routing.md`](../adr-routing/routing.md) §2.

## Rules

Same as [35](35-adrs-authentication.md) §Rules.

## Done when

- ADR-010 to ADR-020 exist, and their `docs/adr/README.md` index rows move from `reserved`.
- The grep in 35's Done-when returns nothing for these files.

## Answer

**ADR-010 to ADR-020 are written in `docs/adr/` and marked `accepted` in the index, as routed, with no regrouping.**
Each ADR folds in its attached amendments: 31's fencepost (840 / 260 / 580) and startup floor in ADR-011, reading (B)
with the first-insertion expiry pin and `k = 5` in ADR-015, and source-key units in ADR-017 and ADR-019. The Done-when
grep returns nothing for the eleven files. The only edits to the shared files were one row replacement per ID in
`docs/adr/README.md`.

### Premises checked at source

- **The routing filter sentences held.** The ADR-010 sentence was checked against the standard itself. §2 Decision
  Logic says per account "not per IP", the §2 sequence diagram checks "per IP", and Questions Q16 says not to limit by
  IP behind NAT. So the standard contradicts itself as ticket 17 said, and Q16 is the pull toward dropping a limiter.
- **The OWASP citation for the observation window was wrong in its source.** Ticket 09 attributes the three lockout
  parameters to "OWASP". They are in the **Authentication Cheat Sheet**, "Account Lockout" (fetched), not in the
  Blocking Brute Force Attacks page. ADR-012 cites the cheat sheet.
- **Q15's "Permanent (admin unlock required)" option** is at Questions L390. Cited by question number in ADR-011.
- **This session's own draft was wrong once.** The ADR-013 draft said a second admin cannot clear a cap because the
  reset token has no delivery channel. That is false. R-ADM-019 and 30's routes make in-app reset, issued by another
  authenticable admin, the route while one exists. The runner is the route only when none does. Corrected before
  writing. The no-channel fact is true of the *self-service* reset request only.
- ADR-016 is consistent with ADR-027, which ticket 40 wrote this session. The runner's `totp` scope was added as a
  second way to clear tier 2 before the deferred forced change can fire.

### Finding for ticket 18

- **No test-plan row asserts that a tier-2 factor disable sets `force_password_change` and ends sessions.** ADR-016,
  ADR-027 and R-LCK-008 all state it. T-MFA-021 covers the disable, and T-ADM-005 covers the flag's effect. Nothing
  joins the two. ADR-016's Consequences say so. The test plan was left unedited because ticket 38 is working on that
  file.

### Handover items (ticket 25)

None new. The ADRs cite existing rows (R-OPS-006, R-OBS-013, R-AUD-027, R-RUN-003) and do not add obligations.

Status: resolved.
