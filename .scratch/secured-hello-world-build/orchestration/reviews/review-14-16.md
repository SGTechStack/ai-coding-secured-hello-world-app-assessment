# Independent review: tickets 14, 15 and 16 (`da2a3aa..18a7654`)

Repo: `ai-coding-secured-hello-world-app-assessment`, branch `zacharylim`. HEAD is `18a7654`, so every finding
below still applies at HEAD.

Scope: 12 commits, 116 files (+6024/-219): registration and activation (14), the lockout-ends-sessions carry-over
(`3380fa6`), password reset (15), and the admin bootstrap with the forced-change credential (16).

Spec sources: `.scratch/secured-hello-world-build/issues/{14,15,16}-*.md`, `docs/spec.md` (Credential flows,
Identifiers, Admin module and bootstrap), ADR-007/009/032/037/039/045/046/047/057, `docs/test-plan/test-plan.md`.

Standards sources: the repo has no CODING_STANDARDS, CONTRIBUTING, AGENTS or CLAUDE file, so the Standards axis uses
only the smell baseline.

Method: I read the whole production diff, all new backend tests, and the SPA link, register and change-password
pages. I reasoned through each security path by hand. I ran no builds or tests (the review was read-only).

Counts: **Critical 0, High 1, Medium 2, Low 8** (plus smell notes on the Standards axis).

---

## Spec

### High

**H1. The username axis leaks the email axis, so registration enumerates activated addresses.**
`backend/src/main/java/sg/securedhello/registration/Registration.java:77-100` (`reserve`). Still applies at HEAD.

`reserve` checks the username first. After that, it creates a pending registration (which holds the username) only
when the address is new or is a self-registered pending one. When the address belongs to an activated account, an
invite or a tombstone, it returns quietly and reserves nothing. So whether the username got reserved depends on the
email's state, and a second request can observe that:

1. `POST /api/register {username: "u-<random>", email: VICTIM}` returns 202 in every state.
2. `POST /api/register {username: "u-<random>", email: attacker@x}` (same username, different address):
   - **400 `USERNAME_UNAVAILABLE`** means step 1 reserved the name, so VICTIM is **new or pending**;
   - **202** means nothing was reserved, so VICTIM belongs to an **activated account**, an invite or a tombstone.

Each probe costs two requests under the per-source budget (5, then 1 per 12 s), and a fresh username keeps each
probe independent. This defeats the point of R-CRED-018, T-AUTH-014 and ADR-032 ("Email existence is account state
and is never observable"). ADR-032's accepted leak covers the *username* axis only; this one is on the email axis.

It is a design-level interaction, not a coding slip. ADR-032's rule that "a request that would collide on email
never reserves a username" is what creates the oracle. T-AUTH-006 and T-AUTH-014 both pass because they compare
single responses; neither sends a follow-up request.

Fix options for the ADR owner:
- (a) Pending registrations stop blocking the name for other addresses. Only activated accounts, invites and
  tombstones block it, and the collision is settled at activation (first to activate wins, which needs a
  unique-constraint path).
- (b) Every email state blocks the name the same way. For example, write a short-lived username hold regardless of
  the email's state.

Either way, add a test that makes the two-request probe and asserts identical outcomes for a new address and an
activated one.

### Medium

**M1. `isSelfRegisteredPending` tells invites apart by role, so a USER-role invite can be hijacked by
self-registration.** `Registration.java:106-108`. Latent: it goes live with ticket 23. Still applies at HEAD.

The javadoc says an administrator's pending invite must never be replaced, because "replacing it would let a
stranger rename an invited account". The check behind that is `account.isPending() && "USER".equals(role)`.
Ticket 23's `POST /api/admin/users` "creates a *pending registration*", including USER-role invites, and the
`users` table has no column that records how a pending row was created.

Once ticket 23 lands, anyone who knows an invited user's address can self-register it and get a 202. That:
- renames the invited account to a username the attacker picks;
- deletes the admin-issued activation token (through `mint` → `deletePending`);
- sends a new token to the invitee under the attacker's chosen username.

`RegistrationTest.anAdministratorsPendingInviteIsNotReplacedBySelfRegistration` covers only an ADMIN invite, so it
cannot catch this. Fix: record the origin (for example `invited_by`/`created_by`, or a column meaning "self
registered"), or make "pending" not replaceable when an admin created it. Add a USER-role invite case to the test.

**M2. A registration replace racing an activation can rename an account that just activated.**
`Registration.java:77-100` and `Activation.java:48-55`. Still applies at HEAD.

The two paths share no lock:
- `Registration.reserve` reads the holder with plain `findByEmail`. There is no `FOR UPDATE`, and `UserAccount` has
  no `@Version`.
- `Activation.activate` never takes the row lock either.

The race goes like this:
1. The victim's activation redeems the token and commits `activated_at` and the password.
2. At the same moment, a concurrent re-registration of the same address has already read the row as pending.
3. `replacePendingRegistration` then writes `username` and `created_at` over the now-activated account. This is a
   lost update: the victim signs in under a username the attacker chose.
4. It also mints an ACTIVATION token for an activated account. Redeeming that token reaches `UserAccount.activate`,
   which throws `IllegalStateException("already activated")`, so the caller gets a 500 (after `setPassword`, which
   rolls back).

The window is small. But ADR-032 already expects an attacker to loop registrations against a victim's pending
address, and that loop is exactly what makes a hit plausible. Fix: take `findForUpdateByEmail` (a pessimistic lock)
in `reserve`, and lock the row in `Activation` before stamping. Or add `@Version` to `UserAccount`.

### Low

**L1. Email-axis timing differs on both anonymous endpoints.** `Registration.java:77-100` and
`PasswordResetRequests.java` (`issue`). Still applies at HEAD.
- A new or pending address costs an INSERT/UPDATE on `users`, a DELETE and INSERT on `credential_tokens`, and an
  `EmailService.send`. An activated address costs one or two SELECTs.
- The reset request is the same: an activated account mints a token and sends mail, and every other state does
  one SELECT.

There is no BCrypt asymmetry, which is what T-AUTH-014 and ADR-032 target, and the spec says "verified by call
count, not by stopwatch" (REJ-056). So this is an accepted class, but ASVS 6.3.8 names response times. Worth one
line in the deferral register. Moving `EmailService.send` onto an async executor would take away the largest
future cost, once a real mail transport exists.

**L2. Reset redemption never re-checks the account's state.** `PasswordResetRedemption.java:66-76`. Still applies
at HEAD.

Redemption sets a password and clears the lock and cap on whatever account the token names, including a disabled
account or a never-activated one. The request side filters (`canReset`), but an admin-minted token (ADR-006) or a
token issued before a disable is honoured. Today the only guard is ADR-007's "disable cancels pending tokens", and
that is not built yet (ticket 20). Consider refusing with `RESET_TOKEN_INVALID` when the account is disabled.
Otherwise, make sure ticket 20's disable path deletes pending tokens, with a T-CRED-019 case.

**L3. The token stays in the address bar and in browser history.** `frontend/src/components/LinkPasswordForm.tsx:54`.
Still applies at HEAD.

The fragment keeps the token out of server logs and `Referer`, which is good. But the page never runs
`history.replaceState` to strip `#token=...`, so the token stays in history and in any screenshot or shared URL
for its lifetime (24 h for activation, 30 min for reset). Fix: read it once, then replace the URL.

**L4. The ArchUnit rule for T-CRED-009 is narrower than the row it proves.**
`backend/src/test/java/sg/securedhello/architecture/ArchitectureRules.java` (`RESET_PATH_PACKAGES`). Still applies at
HEAD.

The row says "no class on the reset-request or redemption path depends on `AuthenticationManager`". The rule checks
only *direct* dependencies of classes in `passwordreset`, `credential`, `password` and `email`. The path also runs
through `session`, `user`, `audit` and `security.ratelimit` (`AuthRateLimiter`), and a transitive dependency would
pass. It holds today, but it is weaker than the claim. Consider `transitivelyDependOnClassesThat` scoped to the two
entry classes.

**L5. A self-registrant can permanently block the bootstrap seed.** `AdminBootstrap.java:83-96`. Still applies at
HEAD.

The runner fails startup when the configured username or `<username>@admin.invalid` is held by any live account,
pending ones included. Two things make that reachable:
- Seeding runs after the port opens (ADR-047), and registration is open.
- `Identifiers.canonicalEmail` accepts the `admin.invalid` domain.

So a stranger's pending registration of the operator's chosen admin username turns every later boot into a
startup failure, until someone edits the database. That defeats "no database edits". It is narrow: it needs a
fresh deploy that is exposed, and a guessed username. Consider:
- refusing `*.invalid` (the RFC 2606 names) in `canonicalEmail`;
- letting the seed take over a *self-registered pending* row that holds its username;
- or making the refusal message tell the operator exactly which row to remove.

**L6. The test plan and the code disagree on the audit reason name.** T-ADM-015 (and ADR-046 and the register)
specify the audit reason `grace-expired`. The code emits and the test asserts `CREDENTIAL_EXPIRED`
(`ForcedChangeExpiryTest.pastThirtyDays...`). The behaviour is right, but a reader following the test-plan row will
not find the literal. Fix the row, or rename the reason.

**L7. Pending registrations never expire and keep holding their usernames.** `Registration.java`, together with
CONTEXT.md's "reserved username".

An expired activation token leaves a permanent pending row that blocks the username for every other address. With
throwaway addresses, an attacker can squat any number of names, limited only by the per-source budget. This follows
the glossary and ADR-032, so it is a design note rather than a defect, but no reaper or TTL exists or is ticketed.

**L8. Two issuance paths don't truncate to microseconds.** `PasswordService.java:86` (`issueForcedChangeCredential`)
and `AdminBootstrap.java:93` (`created_at`/`activated_at`) store `clock.instant()` without
`.truncatedTo(MICROS)`. Every other writer in this diff truncates so that comparisons match what was stored. The
effect is under 1 µs of early expiry, but it is inconsistent with the stated convention and can cause flaky
exact-boundary tests.

### Spec coverage: what checks out

- **Tokens (ADR-007):** 32 bytes of `SecureRandom` encoded as Base64url (43 chars), stored as
  `SHA-256(TYPE:token)` hex, with a shape pre-check and a single conditional `UPDATE ... used_at IS NULL AND
  expires_at > now`. The consume happens before `setPassword` in one transaction, and a rejected password rolls it
  back. T-CRED-011, T-CRED-012, T-CRED-013, T-CRED-014, T-CRED-015 and T-CRED-022 are all genuinely exercised.
- **Canonicalisation (ADR-045):** NFC, strip, lowercase; a username is rejected if canonicalisation would change it;
  the email is converted; the reserved-name set is shared with the bootstrap.
- **Reset request:** it is uniform, spends the identifier budget before any lookup, sends nothing for pending or
  disabled accounts, writes the audit row without `user.id`, takes the link origin from `app.origins.spa` only
  (tested on the wire with a forged `Host` and `X-Forwarded-Host`), and the dev logger name matches
  `ResetLinkLoggerGuard.LOGGER_NAME`.
- **Redemption:** takes the row lock, clears the lockout and cap, clears the forced flag and `credential_issued_at`
  through `replacePasswordHash`, leaves TOTP untouched, ends sessions after commit, and writes the audit rows after
  commit.
- **Lockout carry-over:** `endAll` runs only on a lock or cap *transition*, is registered inside the row-lock
  transaction, and so runs after commit (ADR-039). T-SES-017 and T-SES-021 are proved over a real port.
- **Forced change:**
  - The filter runs just before `AuthorizationFilter` with exactly five allowlisted matchers and fails closed for
    anything else, including unknown routes.
  - The flag is read at login and cleared on the session principal after a completed change.
  - Expiry is enforced in the pre-authentication checks (T-ADM-030), with the same audit reason, 401, counters and
    `matches()` count whether the password was correct or wrong (T-ADM-015).
- **Bootstrap:**
  - Validation runs in the constructor, during refresh, before the port binds; T-ADM-022 and T-ADM-023 assert
    `portOpened() == false`.
  - Seeding runs in the runner, is skipped for any existing ADMIN row (disabled included), fails fast on a
    tombstone, and issues a forced-change credential through `PasswordService`.
- **Scope creep:** none of note. `AuthorizationMatrix.rolesByRoute` (`hasAnyRole` for a route two roles share) is a
  necessary fix, and `ForcedChangeTest` exercises it.

### Tests versus their `@Proves` claims

Apart from L4 and L6 above, the claimed T-IDs are proved as the rows describe. Specific checks:
- T-CRED-013 boundaries: 29:59 passes, 30:01 fails.
- The T-CRED-014 race uses 6 threads released by one latch.
- T-CRED-022 checks the id is assigned before any flush and that registration rolls back atomically, using an
  injected CHECK constraint.
- T-SES-014 replays a raw cookie.
- T-RL-004 is tested from distinct sources and verifies no `findByEmail` call.
- T-AUD-030 is a restart-level scan attributed by logger.

The gap is on the negative side: nothing covers H1's two-request probe or M1's USER-role invite, and those are
where the defects are.

---

## Standards

The repo documents no coding standards, so this axis uses only the baseline. Every item is a judgement call.

- **Duplicated Code:** `PasswordResetPortTest`, `ActivationSessionsPortTest` and `LockoutSessionsPortTest` each
  re-declare the same `send(...)`, `cookieValue(...)`, anonymous-bootstrap and sign-in helpers (about 30 lines each).
  → Extract a `RawSessionClient` into `testsupport`, next to `SessionRows`.
- **Duplicated Code:** `clock.instant().truncatedTo(ChronoUnit.MICROS)` is repeated in `CredentialTokens`,
  `Registration`, `Activation` and `LockoutRecorder`, and missing in two places (L8). → One `StoredClock.now()`
  helper, or a `Clock` bean that already truncates.
- **Primitive Obsession:** roles are string literals (`"USER".equals(account.getRole())`, `existsByRole("ADMIN")`,
  `UserAccount.pendingRegistration` setting `"USER"`). M1 comes from a string role standing in for "how was this
  pending row created". → A small `Role` type, plus an explicit `origin` concept on pending rows.
- **Flag argument (Speculative Generality / Mysterious Name):** `PasswordService.set(accountId, raw, @Nullable
  Instant issuedAt)` uses null-versus-non-null to choose between "user-chosen" and "forced-change issued". → Two
  small private methods sharing a `validateAndEncode` step, or an explicit enum.
- **Data Clumps:** `SignedInUser`'s private constructor now takes 9 positional arguments, 5 of them adjacent
  booleans (`enabled, accountNonLocked, passwordDisabled, forcedChangeExpired, passwordChangeRequired`), and
  `withPasswordChanged` has to repeat them in order. → Group the login-time checks into one small record, such as
  `SignInChecks(locked, capped, forcedChangeExpired)`.
- **Middle Man (minor):** `CredentialTokenConsumption.redeemed(int)` exists only to turn `> 1` into an exception.
  It is justified as PIT-scoped pure logic, so it is acceptable.

---

**Summary.** Spec axis: 11 findings (0 Critical, 1 High, 2 Medium, 8 Low). The worst is H1, where the
username-reservation side-effect turns registration into an oracle for activated email addresses. Standards axis:
6 smell notes (judgement calls). The worst is Primitive Obsession on roles, which is the root of M1.
