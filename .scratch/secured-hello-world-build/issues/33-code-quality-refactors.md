# 33: Code-quality refactors (thermo-nuclear and review leftovers)

**What to build:** Behaviour-preserving refactors only, from the should-fix items the maintainability reviews
recorded for tickets 19–31. Every existing test keeps its intent (tests may move or be renamed, never weakened), the
strict traceability gate stays green, and the audit inventory and error contract regenerate with no change.

- **Recovery runner (27):** split `RecoveryRunner` into planning, printing and applying pieces, with the ADR-072/074
  behaviour, audit rows and digest unchanged.
- **TOTP tier-1 window (19):** the factor lockout uses the password lockout's chained observation-window rule through
  one shared, named rule; the verification outcome types are flattened.
- **Reason enums (28/30):** reason enums that mean the same thing are consolidated; wire and audit values unchanged.
- **Role request (22):** the role is an enum bound by Jackson and Bean Validation, with the same 400
  `VALIDATION_FAILED` for bad input.
- **Row 10 (thermo-nuclear, 28/30):** the displaced session is audited explicitly rather than inferred from side
  effects in `SignIn`.
- **Other pure-refactor should-fix items** from `artifacts/code-reviewer/*-compliance.html` for 19–31: the ones taken
  and the ones left are listed in the closeout.

**Blocked by:** 19, 22, 27, 28, 30

**Status:** done

- [x] `RecoveryRunner` is split into cohesive planning, printing and applying classes; `RecoveryRunnerTest`,
      `RecoveryRunnerProcessIT` and `RecoveryPlanTest` pass unchanged; audit rows and the digest are identical.
- [x] The TOTP tier-1 window and the password windowed counter share one named rule; the TOTP verification outcome
      types are flattened; `TotpLockoutTest`, `TotpVerificationTest`, `LockoutCounterTest` pass unchanged.
- [x] Duplicated reason enums that mean the same thing are consolidated; the audit inventory and error contract
      regenerate with no diff; `AuditReasonCodesTest` passes unchanged.
- [x] The role-change request binds a role enum; every bad body is still 400 `VALIDATION_FAILED`.
- [x] Row 10 is written explicitly for each displaced session, with no behaviour change (`SessionEndRowsTest`).
- [x] Other taken pure-refactor should-fix items are done, and the left ones are listed with reasons.
- [x] One full `verify` passes (traceability gate strict, audit-inventory and error-contract drift gates unchanged);
      PIT on the changed gate-list classes does not drop.

## Closeout: should-fix items taken and left

Taken (behaviour-preserving):
- 19: TOTP tier 1 re-implemented the windowed counter; the LOCKING lock end was recomputed; Outcome/Failure/Checked
  duplicated; FactorLockedException took a nullable lock end. All four are closed by `ObservationWindow` and
  `TotpOutcome`.
- 22/23: stringly typed role in `RoleRequest`, now the `admin.Role` enum.
- 27: `RecoveryRunner` split into `RecoveryTargets`, `RecoveryApplier` and `RecoveryReport`.
- 28/30: `SignIn` row 10 inferred from side effects, now written on each expiry through a `Displaced` wrapper.
- 30: the signed-in user id extraction copied 4+ times, now `SignedInUser.from/current`.
- fix-c: 10 test classes with local copies of the wrong password, now `Accounts.WRONG_PASSWORD`.
- 28/30 reason enums: `TotpFactorEntryPoint.Reason` and `FactorRequiredReason` merged. No other reason enums mean
  the same thing: each family types one audit row, and `UnlockReason` is an admin-stated reason, not a lockout clear.

Left, with reasons:
- 23/28/29/30 `Registration` and `AdminInvitations` audit scattering and identifier-race translation: ticket 32's
  area, running in parallel.
- 28 `LockoutRecorder` deferral duplication: already folded into `keepingDeferred` before this ticket.
- 22 `AdminUserDetailPage` duplication and 21 `StepUpDialog`/`TotpCodeForm` duplication: frontend refactors that the
  specs would need to re-prove; left for a frontend pass.
- 25 `ReconciliationTrigger` lock-in-force rule duplicated with `PasswordLockoutState`; 26 `RowCount` pass-through and
  `RequiredPropertiesPostProcessor`; 30 duplicate-cookie request attribute, `CredentialTokens` Optional<Boolean>, and
  duplicate H2 URL parsing: Low/Medium, outside the named scope; left as follow-ups.
- Role literals `"ADMIN"`/`"USER"` elsewhere (`UserAccount`, `AdminBootstrap`, `AuthenticableAdmins`,
  `TotpFactorStatus`, demo accounts): a `user.Role` owner is a follow-up, since the column and the ArchUnit
  `setRole(String)` rule would move with it.
- The per-enum `code` field boilerplate across the reason families: kept, since `code()` is deliberately decoupled from the
  constant name, and `FactorRequiredReason` now relies on that.
- Recovery wiring: `RecoveryTargets` and `RecoveryApplier` are built inside the runner, not beans, so
  `RecoveryRunnerTest` can still `createBean` the runner on the shared web context unchanged. `BATCH_CAP` stays on
  the runner for the same reason.

PIT (same tests, before -> after): TotpUserDetails 20/22 -> 17/19, TotpVerification 18/19 -> 19/20, LockoutCounter
22/22 -> 19/19, ObservationWindow (new) 4/4. The moved logic's mutants went to ObservationWindow; the survivors are
the same three as before, so the combined score is unchanged at 95%.
