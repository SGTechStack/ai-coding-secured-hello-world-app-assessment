# Independent review: tickets 11 to 13 (`7c075e9..da2a3aa`)

**Scope:** 10 commits, 145 files. Ticket 11 (rate limiter core), ticket 12 (lockout, NIST cap, cardinality) and ticket 13 (PasswordService, self-service change, SPA page). `da2a3aa` is a merge whose conflicts were resolved by hand.

**Method:** the `code-review` skill ran two sub-agents in parallel, one for Standards and one for Spec. In addition, I read the security-critical code directly at `da2a3aa`: the limiter, filters, converter, lockout counter and recorder, cardinality, PasswordPolicy and PasswordService, PasswordChange, SessionTerminationService, the ArchUnit rules and the SPA page. I also checked Spring Security 7.1.1's `AbstractUserDetailsAuthenticationProvider` source. Each finding says whether it still applies at HEAD (`18a7654`).

**Counts:** Critical 0, High 1, Medium 4, Low 12.

---

## Merge resolution (`da2a3aa`): correct

- **`JsonCredentialsConverter`:** the order is right. It parses the body, charges the per-username budget, builds the source-key details, checks cardinality, then normalises the password to NFC and sets the details on the token. Ticket 12's details and cardinality survived, and so did ticket 13's `PasswordPolicy.normalise`. T-CRED-006 is proven properly: the byte-level check shows one literal in NFD and the other in NFC, in both directions.
- **`UserAccount`:** the resolution is a clean union. It keeps `getLockoutState`/`setLockoutState` and adds `replacePasswordHash`.
- **ArchUnit rules, self-tests and fixtures:** a union with every import present. `NO_ACCOUNT_LOOKUP_BEFORE_AUTHENTICATION` still holds after the merge: the converter's new dependency is on `password.PasswordPolicy`, not on a repository.

I found no semantic conflict between the three branches.

---

## High

### H1. Sub-threshold pacing reaches the NIST cap with no lockout, defeating the ADR-011 floor and the ADR-015 cardinality axis

**Where:**
- `backend/src/main/java/sg/securedhello/security/lockout/LockoutCounter.java:51-61`
- `LockoutRecorder.java:76-79`
- `LockoutLadder.java:13-18` (floor arithmetic)

**Still applies at HEAD:** yes. `LockoutCounter`, `LockoutLadder` and `LockoutCardinality` are unchanged.

**Why it happens:**
- The windowed counter restarts whenever the previous failure is at least 20 minutes old (ADR-012).
- The cap counter `consecutive_failures_since_success` increments on every counted failure, with no window.
- An attacker therefore sends 4 wrong passwords, waits 20 minutes, and repeats. The windowed counter never reaches 5, so the account never locks, but the cap counter gains 4 per cycle.

**What it gives the attacker:** the cap of 100 is reached in about 25 × 20 = **500 minutes**, and the alert at 50 fires at about 250 minutes. The spec's guarantees are:
- ADR-011 and the startup floor: "time to the permanent disable = 840 minutes", with 580 minutes of warning.
- ADR-015 and R-LCK-005: "no account is disabled in under about 14 hours".

Under this pacing, both become 500 minutes to disable and 250 minutes of warning.

**Why it is worse than one account:**
- ADR-015's cardinality axis is recorded only on a lockout transition (`LockoutRecorder.java:76`). This attack never produces one.
- T-RL-007 explicitly guarantees that "attempting many accounts without locking any is never refused".
- So a single source is bounded only by its login budget: 60 burst, then 1 per second, about 3,600 an hour. At 12 requests per account per hour, one source can drive about 300 accounts in parallel to a permanent password disable in about 8.3 hours.
- It writes no `LOCKOUT_TRIGGERED` rows, only the per-account alert and disable rows. ADR-015 calls itself "the only width bound on the mass-disable" and assumes k ≈ 5 chains per source; that bound does not hold.

**Why the tests miss it:** T-LCK-020 proves the floor only for the steady "5 then wait out the lock" attack, and `requireFloor()` models only that attack.

**Nature of the defect:** the code faithfully implements the ADR mechanics. The flaw is that the ADR-011 floor arithmetic ignores failures that are not in a locking cycle.

**Fix options:**
- Count sub-threshold failures toward the ladder: derive the lock rung and the floor from time rather than lock count.
- Or make the cardinality axis also count sources that drive an account's cap counter past some mark, for example the alert threshold.
- Or rate-limit the cap counter's growth per account, for example at most t failures per window counting toward the cap.

Record the chosen trade-off in ADR-011 and ADR-015 and add a paced-attack test.

---

## Medium

### M1. A lockout or cap-disable does not end the account's sessions (ADR-037)

**Where:** `LockoutRecorder.java:44-45, 66-81` and `SessionTerminationService.java:13-14`.

**Still applies at HEAD:** no. HEAD adds `endSessionsIfRestricted` and `sessions.endAll(username)`.

At `da2a3aa`:
- ADR-037's table requires "Account lockout | all | added" and "Password authenticator disabled by the failure cap | all | added" (ASVS 7.4.2 L1). ADR-034 also relies on it: "Lockout does end the account's sessions".
- `LockoutRecorder` never calls `SessionTerminationService`, which has no `endAll` caller at all in this range.
- Both Javadocs describe the behaviour as if it exists.
- T-SES-017 and T-SES-021 were on the pending ledger, so this was a known deferral, not an oversight. Ticket 12 still shipped as "done" with an L1 control absent.

### M2. The cardinality axis fails open when the source details are missing

**Where:** `LockoutRecorder.java:76-79`.

**Still applies at HEAD:** yes, at HEAD `LockoutRecorder.java:~82-84`.

- ADR-015 says: "**Fails closed if the details are missing.** If the converter stops setting the details, every lockout is attributed to one key. The set fills at once, and the axis 429s everyone."
- The code does the opposite. `if (... getDetails() instanceof SourceKeyAuthenticationDetails details) cardinality.recordLockout(...)` silently skips the recording, and the control switches off with no signal.
- T-RL-005 pins the converter, but nothing tests the listener's fallback.
- Fix: attribute a missing or foreign details object to a fixed sentinel key, for example `SourceKey.UNPARSEABLE`, or throw.

### M3. The breach slice is a 35-entry hand-picked seed, so the `BLOCKLISTED` rule is nominal

**Where:** `backend/src/main/resources/password/breach-slice.txt:1-8`.

**Still applies at HEAD:** yes, the file is unchanged.

- Ticket 13 and the spec ask for "a version-pinned breach slice (REJ-004; R-CRED-006)".
- The file's own header says: "This version is a seed … It is not yet an extract of a full corpus."
- NIST SP 800-63B's blocklist requirement is therefore met in form only. zxcvbn's score gate catches most of these entries anyway, which is why the rule rarely fires.
- The ticket is marked done, and the gap appears only in the file header. Either track it as an open deferral with an owner, or ship a real pinned extract (the ≥15-character entries of a current HIBP or SecLists slice).

### M4. The password policy properties have no validation or floor

**Where:** `backend/src/main/java/sg/securedhello/security/PasswordProperties.java:14-16` and `PasswordService.java:82`.

**Still applies at HEAD:** yes. There is still no `@Validated` and no constraints.

The lockout ladder gets a startup floor (ADR-011); the password policy gets nothing. Each of these misconfigurations is silent or fails only at runtime:
- **`history-length: 0`:** `retained.stream().skip(historyLength - 1L)` becomes `skip(-1)`, which throws `IllegalArgumentException`. Every password set then returns a 500.
- **`max-bytes` above 72:** passwords of 73 or more bytes pass the policy and reach `BCryptPasswordEncoder.encode`, which throws on more than 72 bytes. That is the 500 that T-CRED-001 forbids.
- **`min-length` below 15 or `min-strength-score` below 3:** weakens ADR-002 and ADR-005 without any warning.

Fix: add `@Validated` with bounds, and ideally refuse at startup any value weaker than the ADR values, mirroring the lockout floor.

---

## Low

- **L1. Timing difference between a real and an unknown username on the failure path.**
  - Where: `LockoutRecorder.java:67-81`. Still applies at HEAD.
  - A wrong password for an existing account costs `SELECT … FOR UPDATE`, an `UPDATE`, a commit and possibly audit writes. An unknown username costs only an empty `SELECT … FOR UPDATE`.
  - The delta is a few milliseconds against BCrypt's roughly 250 ms plus jitter, and the per-username budget throttles sampling, so the risk is small. It is still an existence-dependent branch in the synchronous request path (ADR-001 and ADR-033 timing uniformity). Consider noting it in the threat model.
- **L2. Evicting a limiter bucket resets it to full.**
  - Where: `AuthRateLimiter.java:39-40, 59, 142` and `LockoutCardinality.java:49-50`. Still applies at HEAD.
  - The Javadoc says eviction "is not a bypass", which is true only of the audit bound, not the budget.
  - `LockoutCardinality.sources` reuses `MAXIMUM_KEYS` (10,000), while ADR-015 says that cache must be sized "so that it does not evict within a window".
  - Practical risk is low: Caffeine's TinyLFU admission favours hot keys, and the lockout still binds. The comment should say what eviction actually gives an attacker.
- **L3. The ArchUnit credential-write rule only sees direct `UserAccount` code units.**
  - Where: `ArchitectureRules.java` (`credentialWrite()`). Still applies at HEAD.
  - It inspects only the callee's own field accesses. It misses:
    - a `UserAccount` method that sets `passwordHash` through a private helper;
    - a JPQL `@Query("update … set passwordHash")` or `@Modifying` repository method;
    - a `JdbcTemplate` `UPDATE users SET password_hash`.
  - Note that the `encode()` rule does have a non-vacuity guard (`ArchitectureTest.passwordServiceIsTheSoleCallerOfEncode`). The Spec sub-agent's claim that it could pass vacuously is wrong.
- **L4. The T-SES-012 and T-SES-013 replay assertion does not discriminate.**
  - Where: `PasswordChangeSessionsPortTest.java:90-103`.
  - The "other" session was already displaced by the one-session-per-account rule at the second sign-in, so replaying it returns 401 with or without the change.
  - The row check (the row exists before, is gone after) does tie the result to the change, so the T-IDs are still proven, but the replay half adds nothing.
  - A stronger test would create the other session in a way that is not displaced, or assert that the row is still present immediately before the PATCH (already done) and drop the replay as evidence.
- **L5. There is no row lock across the password change.**
  - Where: `PasswordChange.java:55-62` and `PasswordService.java:69-83`. Still applies at HEAD.
  - Two concurrent changes on one account both read the same history and current hash. That allows reuse or history-trim races and makes the last writer win.
  - Use `findForUpdateById` in `PasswordService`.
- **L6. The SPA maps every `VALIDATION_FAILED` to "The current password is not correct."**
  - Where: `frontend/src/pages/ChangePasswordPage.tsx:101-102`. Still applies at HEAD (`:88-89`).
  - The server also sends that code for a malformed or missing-field body and for the body cap.
  - Separately, if the PATCH succeeds but `refreshCsrfToken()` fails (`session.ts`), the page says "The password could not be changed", which is false.
- **L7. The forced-change expiry pre-authentication check is a documented no-op in this range.**
  - Where: `PreAuthenticationChecks.java:14-16`. Filled in at HEAD.
  - Ticket 12 claims the three-step order, but only two steps existed.
- **L8. T-CRED-004's binding is not proven.** `PasswordPolicyTest` hard-codes `new Limits(15, 72, 3)`, so reading the threshold from `app.security.password.min-strength-score` is untested.
- **L9. `CONTEXT_TERM` skips terms under 4 folded characters.**
  - Where: `PasswordPolicy.java:40`. Still applies at HEAD.
  - This floor is not in ADR-005. Usernames can be 3 characters (`[a-z0-9._-]{3,32}`), and a 3-character username is never checked by that rule; it is only fed to zxcvbn as a user input.
  - The floor is reasonable, but it should be recorded in the ADR or the spec.
- **L10. Audit keying relies on hard-coded field-name strings.**
  - Where: `AuditRowDefinition.java:91,93`, `AuditKeying.java:75-77,93` and `AuditEmitter.java:110`.
  - If a key field is renamed, `record()` finds no key and writes the row unkeyed. That silently removes the ADR-019 volume bound, and no test fails.
- **L11. `TruncationContext.truncatedRows` is a `List<String>`.** ADR-055 constraint 1 allows only UUIDs, enums and primitives on context records. Use `List<AuditEvent>` or an `EnumSet`.
- **L12. The SPA mirrors policy constants by hand.**
  - Where: `frontend/src/lib/password/policy.ts:8-22`.
  - `MIN_LENGTH`, `MAX_BYTES` and the rule list duplicate the server's configuration and `PasswordRule`. A deployer who raises `min-length` gets a client that lets short passwords through to a server 400.
  - Acceptable as indicative UX, but fragile.

---

## Standards axis (sub-agent report, lightly edited)

**Hard or documented-standard breaches:**
- ADR-037 (see M1).
- ADR-055 constraint 1 (see L11).

**Judgement calls:**
- **ADR-065's fixed context list was not amended.** `CtxBudgetTest` adds a fifth context, `ctx-budget`, with three `@MockitoSpyBean`s.
- **CONTEXT.md vocabulary drift:**
  - "Tier 1/2/3" is used unqualified in `AuditRowDefinition`, `AuditEvent`, `AuditProperties` and `AuditEmitter`. CONTEXT.md warns against that, and "tier-1" also names the factor lock.
  - `failedLoginAttempts` is on CONTEXT.md's avoid list.
- **`PasswordProperties` is the only property record without `@Validated`** (see M4).

**Smells (judgement calls):**
- **Duplicated Code:**
  - `ClientIpConfig` re-binds `app.audit.keying.window` with its own `15m` default, duplicating `AuditProperties`.
  - `SourceKeyAuthenticationDetailsSource` re-checks the request attribute that `SourceKeyResolver.resolve` already caches.
  - `LoginThrottledException` and `LockoutCardinalityException` are near-identical.
  - `LockoutProperties.valid()` re-implements the bean-validation checks.
- **Repeated Switches:**
  - The `Keying`→`TruncationReason` ternary appears in both `AuditKeying` and `TruncationContext`.
  - `SignIn.loginFailed` is an `instanceof` chain.
- **Divergent Change:** `JsonCredentialsConverter` now parses, meters the username, checks cardinality, normalises and builds details.
- **Middle Man / Data Clumps:**
  - `SignIn` holds the limiter, details source and cardinality only to build the converter.
  - `SecurityConfig.securityFilterChain` now takes 15 parameters.
- **Feature Envy:** `LockoutCardinality` reaches into `AuthRateLimiter` for `Refusal`, `MAXIMUM_KEYS` and `seconds()`. `Refusal` should be top-level.
- **Speculative Generality:**
  - `RateLimit.Axis.IDENTIFIER` and `Route.identifier` have no row in this range.
  - `SignedInUser.of(UserAccount)` has no callers left.
  - `AuditRowDefinition` has a legacy 10-argument constructor that only one test uses.

The sub-agent found no other documented-rule breach. All error bodies go through `ProblemDetailWriter` (ADR-031). New tests carry `@Proves`, and no slices or sleeps were added.

## Spec axis (sub-agent report, lightly edited, with my corrections)

**Missing or partial:**
- Session termination on lockout and cap (M1).
- The breach slice is a seed (M3).
- The forced-change expiry check is a no-op (L7).
- T-CRED-004's binding is unproven (L8).

**Not asked for:**
- The `MIN_TERM_LENGTH = 4` floor (L9).
- CORS headers on the early 429s (a reasonable addition).
- A computed cardinality `Retry-After`.
- Unpinned `^` ranges on `@zxcvbn-ts/*` in `frontend/package.json`.

**Implemented but looks wrong:**
- Cardinality fails open (M2).
- Eviction resets a bucket (L2).
- The T-SES-012 and T-SES-013 replay does not discriminate (L4).
- The sub-agent claimed T-CRED-005 could pass vacuously. **This is incorrect:** the test asserts that `PasswordService` calls `encode`. The column rule's blind spots are real (L3).
- The rule-order test lacks a candidate that fails both `MAX_BYTES` and `BLOCKLISTED` (a nit).

**Verified correct:**
- **Password rule order:** MIN_LENGTH, MAX_BYTES, BLOCKLISTED, CONTEXT_TERM, TOO_WEAK, then HISTORY_REUSE last.
- **History of 3:** the current password plus two priors, trimmed correctly.
- **Lockout window and ladder:** the arithmetic matches ADR-011 and ADR-012, attempts during a lock are not counted, and rungs must be at least the window.
- **Pre-authentication checks:** they still cost one `matches()`. Spring Security 7.1.1's `performPreCheck` runs `additionalAuthenticationChecks` when `alwaysPerformAdditionalChecksOnUser` is set, so a locked or capped account is timing-uniform.
- **Per-username 429:** refused before any lookup.
- **Body cap:** counted in bytes while reading, after the source filter and before the converter.
- **ADR-008 steps 1 to 5:** in order, with other sessions ended in an `afterCommit` hook and the acting session's id rotated after the commit.
- **Sound tests:** T-AUTH-003, T-LCK-001/002/003/004/006/009/011/020, T-RL-001/002/003/006/007/017/030/031, T-CRED-001/002/006/008/021.

## Summary

- **Standards:** 2 hard breaches (ADR-037 and ADR-055) and about 12 smell-level judgement calls. The worst is the ADR-037 gap, M1, which is fixed at HEAD.
- **Spec and security:** 1 High, 4 Medium and 12 Low findings in total. The worst is H1: pacing below the lockout threshold reaches the NIST cap in about 500 minutes with no lockout, bypassing the 840/580 floor and the cardinality width bound. It still applies at HEAD.
