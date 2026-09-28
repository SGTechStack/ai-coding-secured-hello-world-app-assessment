---
status: accepted
---

# ADR-046: Forced-change credentials expire lazily at login after 30 days, refused before the password check, not by a reaper

A credential issued with a forced-change obligation stops working 30 days after issue. Nothing runs on a schedule.
The check happens at login: if the account is flagged for a forced change and its `credential_issued_at` is more than
30 days old, the login is refused. The refusal is made **before the password is checked**, by a checker composed into
Spring Security's `preAuthenticationChecks`, and it throws Spring's `CredentialsExpiredException`. The application's
`UserDetails.isCredentialsNonExpired()` always returns `true`. A maintainer would plausibly undo this twice over. A
scheduled reaper is the expected shape for an expiry. And `isCredentialsNonExpired()` is Spring's idiomatic home for
credential expiry, but that is the post-authentication slot, where the refusal's audit reason would confirm a guessed
password.

## Context

- The governing standard pairs a forced password change with automatic disable after a grace period of 30 days (§2
  Happy Path step 7; Decision Logic; §3.5; §5 Account Hygiene Tests). §2 Failure Path 4 says a login on such an
  account is rejected with the same generic 401 as every other login failure.
- Scheduled account-hygiene jobs are not built (R-ADM-011), so a reaper would be the only scheduler in the system.
- `AbstractUserDetailsAuthenticationProvider` in Spring Security 7.1.x runs `DefaultPreAuthenticationChecks`
  (`isAccountNonLocked`, `isEnabled`, `isAccountNonExpired`) before the password comparison, and
  `DefaultPostAuthenticationChecks` (`isCredentialsNonExpired`) only after the password matched. A refusal from the
  post-authentication slot therefore fires **if and only if the submitted password was correct**.
- With `alwaysPerformAdditionalChecksOnUser = true`, the default and required here (T-AUTH-005), a failing pre-check
  still runs `PasswordEncoder.matches()` against the real stored hash, discards the result and rethrows the pre-check
  exception. So a correct and a wrong password produce the same exception, the same event and one BCrypt verify each.
- The internal failure reason goes to the audit stream, and failure rows carry `user.id` when the account resolves
  (REJ-042). The audit stream is a channel the uniform wire response does not protect.
- The standard itself warns against `isCredentialsNonExpired()` for the forced-change **flag**, because Spring rejects
  the credentials before a session exists, which blocks the change endpoint (§2 Happy Path step 7, Spring Boot note).
  That warning is about the flag. This ADR is about the deadline, where refusing to create a session is the point.

## Decision

- **Where the clock starts.** `credential_issued_at` is stamped, together with `force_password_change`, on exactly
  these forced-change issuances:
  - the bootstrap admin seed (ADR-047);
  - an admin re-enabling a disabled account, per the standard's Decision Logic;
  - a single run of the offline recovery runner, which sets an operator-typed password (ADR-073).
- **Where it is not stamped:**
  - admin-created users and admin password resets. Both issue single-use tokens (ADR-006), the user sets their own
    password at redemption, and a reset token's own 30-minute expiry is its deadline;
  - a tier-2 TOTP disable (ADR-027). It sets the forced-change **flag**, so the user must rebind their password at
    their next login, but it does not start the 30-day clock. If it did, the clock would run while the admin waited
    for another admin to re-enrol their factor, and a long wait would add a generic 401 on top of the disable.
- **Placement: pre-authentication.** A checker composed into `preAuthenticationChecks` refuses the login. The order in
  the pre-checks is the Spring defaults (locked, disabled), then the NIST failure cap (ADR-013), then this expiry. No
  order reveals the password.
- **Exception: Spring's `CredentialsExpiredException`.** It is audited on the same path as `LockedException` and
  `DisabledException`, with the reason `grace-expired`. The failure cap needed a custom exception so that it would not
  collide with admin disable. Nothing similar applies here, so no subclass is added.
- **The post-authentication slot is closed.** The application's `isCredentialsNonExpired()` is hard-wired `true`, so a
  later change cannot move the refusal back after the password check (T-ADM-030).
- **Wire: the uniform `401 AUTHENTICATION_FAILED`.** A distinct code would no longer reveal the password, but it would
  still reveal that the account exists and was admin-provisioned.
- **Counters do not move.** Neither `failed_login_attempts` nor `consecutive_failures_since_success` changes on an
  expired credential, whatever the password (R-LCK-001). The counters listen for bad-credentials failures, and this
  refusal is a different event.
- **Recovery** is an admin reissue: a password reset (ADR-006), or re-enable for the re-enable case.

## Considered options

- **A scheduled reaper that disables expired accounts**, as the standard describes. Rejected. The outcome is the same:
  after 30 days the credential cannot be used. A reaper adds the only scheduler in the system and conflicts with the
  hygiene-jobs deferral. The deviation (refused lazily, not disabled) is recorded with R-ADM-011.
- **`isCredentialsNonExpired()`, the post-authentication slot.** Rejected. Its refusal fires only on a correct
  password, and with `user.id` on the failure row, a log reader gets a password-confirmation oracle on every account
  whose credential is past 30 days (R-AUD-018).
- **Keep the post-authentication slot and log a reason indistinguishable from a wrong password.** Rejected. It
  falsifies the audit log for operators. It also leaks through the failure counter: a correct guess would not
  increment it, so the lock would arrive one attempt later, where a log reader can see it.
- **Keep the post-authentication slot and drop `user.id` from the row.** Rejected. A unique reason plus the submitted
  username defeats it.
- **Count wrong guesses on an expired credential.** Rejected. It defends nothing, because the credential cannot
  authenticate until an admin reissues it, and it would reopen the oracle through the counter.

## Consequences

- A standing, admin-known or operator-known credential of unlimited life becomes one with a real 30-day expiry. That
  includes the bootstrap admin's seed password.
- `grace-expired` joins the audit reasons that reveal account state (locked, disabled, capped) but never password
  correctness. That enumeration trade is already accepted for those reasons (REJ-042).
- **Setting a user-chosen password clears `credential_issued_at` to null**: completing a forced change, and redeeming
  a token (which also clears the flag, ADR-009). The user has just chosen their own password, so no issued credential
  remains to expire. The clear sits in the one password-setting seam, beside the flag. If the column kept its old value, a later tier-2 disable would set the flag
  on an account whose issue time is already past 30 days, and the next login would be refused at once, which is the
  outcome the no-stamp rule above exists to prevent. The expiry check treats a null issue time as not expired
  (T-ADM-031).
- For a tier-2-disabled admin, the forced change is **deferred, not proportionate**. They cannot complete the factor
  stage, so the forced change fires only once another admin has re-enrolled them. It must not be described as relief
  for someone already locked out.
- Tests: T-ADM-015 asserts that, past 30 days, a correct and a wrong password yield the same audit reason and
  `user.id`, the same 401, unchanged counters and one `matches()` call each. T-ADM-030 asserts the post-authentication
  slot stays closed. T-ADM-031 asserts completion clears the issue time. T-AUTH-005 pins `alwaysPerformAdditionalChecksOnUser`.

## Sources

- Standalone User Access Control Application Standard §2 Happy Path step 7 (and its Spring Boot note), Failure Path 4
  and Decision Logic; §3.5 (grace period 30 days); §5 User Administration and Account Hygiene Tests.
- Spring Security 7.1.x, `AbstractUserDetailsAuthenticationProvider` (`preAuthenticationChecks`,
  `postAuthenticationChecks`, `performPreCheck`, `alwaysPerformAdditionalChecksOnUser`) and
  `DefaultAuthenticationEventPublisher`.
- CVE-2026-22746 (Spring Security advisory; fixed in 7.0.5; the flag above is its fix).
