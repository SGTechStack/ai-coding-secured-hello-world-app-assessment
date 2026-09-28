# 44 — Decide where the 30-day forced-change expiry is checked, given its audit reason confirms a correct password

Type: grilling
Status: resolved
Blocked by: —
Blocks: 18

## Question

The lazy 30-day expiry on operator-set, forced-change credentials is checked through `isCredentialsNonExpired()`.
Spring Security evaluates that in `DefaultPostAuthenticationChecks`, which runs only after the password matched. So
the `CredentialsExpiredException` and its audit reason fire **if and only if the submitted password was correct**.
Failure rows carry `user.id` when the account resolves, so a log reader holds a password-confirmation oracle on
every account whose forced-change credential is older than 30 days. The wire stays uniform; the leak is the audit
stream.

Where should the check run, or what should its audit reason say, so that no audit row confirms a guessed password?
Candidates the sources already name: move the check to `preAuthenticationChecks` (the rule 11:797–799 states for
any later status check), or emit a reason indistinguishable from a wrong-password failure. Whichever is chosen,
decide what the user sees on a correct-password, expired-credential login, and whether the lazy expiry survives.

## Why this is a ticket

It was filed as a **reopen trigger** on ticket 11 (11:782–799, from 09 §R at 09:1187–1192, echoed at 13:763–777).
The trigger's condition is already true of the design, and no ticket discharged it. Ticket 33 found it three times
while building the register (stages B, C and D1) and recorded it as an open defect rather than a trigger:
**R-AUD-018** in [`docs/register/register.md`](../../../docs/register/register.md), verdict `fail`,
`application / unmitigated / required`. Resolving this ticket regrades that row by ID.

## Inputs

- 11:782–799 (the oracle and the pre-authentication rule); 13:763–777 (the catalogue rule for failure reasons);
  09:1187–1192.
- ADR-046 (the lazy 30-day expiry) in [`docs/adr/`](../../../docs/adr/); T-ADM-015 in the test plan.
- The cap's refusal already sits in `preAuthenticationChecks` for the same reason (09 §R.4), so the placement
  precedent exists.

## Done when

The check's placement and its audit reason are decided, R-AUD-018 is regraded by ID, ADR-046 is amended by ID if the
decision changes it, and the test plan gains or amends a row asserting that an expired forced-change credential
yields the same audit reason for a correct and a wrong password.

## Answer

**The expiry check moves to `preAuthenticationChecks` and throws Spring's `CredentialsExpiredException`.
`isCredentialsNonExpired()` is hard-wired `true`. The wire stays the uniform `401 AUTHENTICATION_FAILED`, and the
lazy 30-day expiry survives unchanged.** R-AUD-018 is regraded from `fail` to `pass`.

### Premises checked first

- **The oracle is real.** `DefaultPostAuthenticationChecks` is reached only after `additionalAuthenticationChecks`
  matched (verification asset §18).
- **A pre-check closes the oracle completely, reason and timing both.** On 7.1.x, `performPreCheck` catches the
  pre-check exception. With `alwaysPerformAdditionalChecksOnUser = true` it still runs `matches()` against the
  real hash, swallows that outcome ("preserve the original failed check") and rethrows the pre-check exception
  (verification asset §14). So a correct and a wrong password produce the same exception, the same event and one
  BCrypt-12 verify each. T-AUTH-003 is unaffected.
- **ADR-046 did not yet exist.** It was `reserved` and owned by ticket 41, so "amend by ID" meant amending its
  routing row and index title and briefing ticket 41.
- **R-LCK-001 depended on the old placement.** It assumed that only a correct password reaches the expiry refusal,
  so it had to be rewritten as well.

### Decisions

1. **Placement: pre-authentication.** This follows the 09 §R.4 rule already applied to the NIST cap. Two
   alternatives were rejected. The first was emitting a reason indistinguishable from a wrong password. That
   falsifies the audit log for operators. It also leaks again through the failure counter, because a correct
   guess would not increment it, so the lock would arrive one attempt later where a log reader can see it. The
   second was dropping `user.id` from the row, which a unique reason plus the submitted username defeats.
2. **Exception: Spring's `CredentialsExpiredException`, not a custom subclass.** It is thrown from our checker
   composed into `preAuthenticationChecks` and audited on the same path as `LockedException` and
   `DisabledException`. `grace-expired` now joins the reasons that reveal account state, which is where ticket 13's
   argument had put it. The cap needed a custom exception to avoid colliding with admin disable, and nothing
   similar applies here. The order within the pre-checks is: Spring defaults (locked, disabled), then the NIST cap,
   then the forced-change expiry. No order reveals a password.
3. **The post-auth slot is closed.** The application's `UserDetails.isCredentialsNonExpired()` always returns
   `true`, so a later "fix" cannot bring the reason back after the password check. T-ADM-030 asserts this.
4. **Wire: unchanged uniform 401.** A distinct code would no longer reveal the password, but it would still reveal
   that the account exists and was admin-provisioned. Ticket 11's reason stands.
5. **The lazy expiry survives.** The defect was in the placement, not in the laziness. A reaper stays out of scope.
6. **Counters: guesses on an expired credential do not count.** Neither `failed_login_attempts` nor
   `consecutive_failures_since_success` moves for any password, because `CredentialsExpiredException` is not
   `AuthenticationFailureBadCredentialsEvent`. Counting wrong guesses would defend nothing, since the credential
   cannot authenticate until an admin reissues it, and it would reopen the oracle (see 1).

### Amended by ID

- **R-AUD-018** in `docs/register/register.md`: verdict `fail` → `pass`, responsibility `application` → `none`,
  status `unmitigated` → `asserted-by-test`, priority `required` removed. The residual is now account state only.
- **R-LCK-001**: rewritten so that any password on an expired credential leaves both counters unchanged. It stays
  `pass-with-note`.
- **T-ADM-015** in `docs/test-plan/test-plan.md`: amended so that, past 30 days, a correct and a wrong password
  yield the same audit reason and `user.id`, the same 401, unchanged counters and one `matches()` call each.
- **T-ADM-030**: new row asserting that `isCredentialsNonExpired()` is always `true` (level U).
- **ADR-046**: routing row in `adr-routing/routing.md` and index title in `docs/adr/README.md` amended. Ticket 41
  is briefed to write the pre-auth placement into the decision.

### Handover items (ticket 25)

None. Nothing here asks a deployer or operator to act.
