Status: ready-for-agent

# Spec: Secured Hello World

This is the build specification for the Secured Hello World auth app: a React single-page app and a Spring Boot API
on separate origins, with username-and-password sign-in, a second factor for administrators, lockout and throttling,
credential recovery and admin user management. It is the input to `/to-tickets` and `/do-work`.

How to read it:

- **The spec states what the system does. It does not argue why.** Each decision cites the record that carries its
  reasoning, so that there is only one copy to keep current.
  - `ADR-nnn` and `REJ-nnn` are in [`adr/README.md`](adr/README.md).
  - `R-<PILLAR>-nnn` rows are in [`register/register.md`](register/register.md).
  - `T-<PILLAR>-nnn` rows are in [`test-plan/test-plan.md`](test-plan/test-plan.md).
- **Where the spec and a cited record disagree, the record wins.** Amend the spec to match.
- **Vocabulary is [`../CONTEXT.md`](../CONTEXT.md)'s.** A term such as *pending registration*, *authenticator
  disable*, *source key* or *keyed row* means exactly what the glossary says.
- **The product source is [`../prd/assessment-prd.md`](../prd/assessment-prd.md).** Where the PRD and the
  governing standard disagree, the standard decides how a control behaves and the PRD decides which features exist.
  Every deviation from the PRD is a register row keyed on the PRD story and acceptance criterion.
  [`prd-coverage.md`](prd-coverage.md) maps each PRD criterion to its tests and rows.
- **Other design inputs:** the threat model is [`threat-model/report.md`](threat-model/report.md) (ADR-064), and
  its threats are cited as `TM-nn`.

## Declarations

These two declarations are the spec's own. Several register verdicts rest on nothing else.

### IM8 risk classification: Low Risk

The system's IM8 risk classification is **Low Risk**. Every IM8 severity, in the register and in `im8-review` output,
uses the LR column (R-OPS-001). If the system later holds classified data or serves internal officers, the
classification is re-declared and every severity is regraded.

### User population

This is **a standalone reference application serving neither internal public officers nor production public users.
Administrators are external, application-local accounts.**

This declaration is the single basis for the N/A verdict on six IM8 controls:

| IM8 control | Register row |
|---|---|
| ac-7 | R-ADM-005 |
| ac-8 | R-ADM-004 |
| ac-12 | R-ADM-003 |
| dp-8 | R-FE-002 |
| lm-18 | R-FE-003 |
| st-3 | R-FE-004 |

If administrators are ever internal officers, the declaration no longer holds and those verdicts reopen
(R-ADM-006). IM8 ac-12 would then bind the admin login, and dp-8 would bind every input field.

## Problem Statement

The team needs a reference application that shows a secure username-and-password sign-in flow built to a production
security baseline, not a demo that cuts corners. It has three audiences:

- **Visitors and users** need to register, sign in, stay signed in safely, sign out for real, and recover a
  forgotten password without an account-existence leak.
- **Administrators** need to see and manage accounts without being able to lock themselves or the system out.
- **Operators** need to deploy the application for the first time with no database edits, and to recover it when
  every administrator is locked out.

It is assessed formally against IM8, the Standalone User Access Control Application Standard and OWASP ASVS 5.0.
Every deviation, residual and deployer obligation must therefore be recorded and traceable to a test.

## Solution

- **Two origins:**
  - a React SPA on `localhost:5173`;
  - a Spring Boot REST API on `localhost:8080`.

  They are cross-origin but same-site. Authentication uses server-side sessions in Spring Session JDBC, carried by an
  `HttpOnly`, `SameSite=Strict` cookie. CSRF uses a session-bound synchronizer token, sent in a header only.
- **Registration is two-step and enumeration-resistant.** The password is set when the activation token is redeemed.
- **Password sign-in is uniform.** Every failure returns the same 401, whatever the cause.
- **Brute force is blunted by independent controls:**
  - an escalating per-account lockout;
  - a NIST failure cap that disables the password authenticator;
  - per-source and per-username throttles;
  - a per-source cap on how many accounts a source can drive into lockout.
- **Administrators must hold a TOTP factor for the whole admin surface.** Admin mutations re-verify it every ten
  minutes.
- **Admins issue single-use tokens, never passwords.** A guard stops an admin acting on themselves, and a two-admin
  invariant stops the last recovery route being removed.
- **Recovery with no authenticable admin** is an offline runner, run inside a planned outage.
- **Every security event goes to a dedicated audit stream** in ECS form, with keyed hashes instead of addresses.
- **Each control is proved by a test with a stable ID.** The build fails when the test plan and the tests disagree,
  or when the register's two renderings drift.

## User Stories

### Visitors

1. As a visitor, I want to register with a username and an email address, so that I can create an account without
   choosing a password on an unauthenticated form. (PRD Story 1; R-CRED-009; ADR-032)
2. As a visitor, I want registration to answer the same way whether or not my email address is already in use, so
   that nobody can learn which addresses hold accounts. (R-CRED-018; T-AUTH-014; T-AUTH-018)
3. As a visitor, I want to be told at once when my chosen username is taken, so that I can pick another.
   (`USERNAME_UNAVAILABLE`; R-AUTH-001)
4. As a visitor, I want a username that differs only in case, spacing or Unicode form to be rejected rather than
   silently changed, so that my account's name is exactly what I typed. (ADR-045)
5. As a visitor, I want to activate my account from the activation link and set my password there, so that only
   whoever controls the mailbox can take the account. (ADR-032; ADR-007)
6. As a visitor, I want activation links to expire after 24 hours and to work once, so that an old link in my
   mailbox is not a standing risk. (ADR-007)
7. As a visitor, I want a registration that is repeated before activation to replace the earlier pending one, so
   that a mistyped attempt does not block me. (ADR-032; R-CRED-010)
8. As a visitor, I want a password policy that accepts long passphrases and rejects short, breached, guessable or
   context-derived passwords, so that my password is strong without composition rules. (ADR-002; ADR-005)
9. As a visitor, I want each password rejection to name its rule, so that I know what to change. (ADR-005)
10. As a visitor, I want a strength meter while I type, so that I can see the likely outcome before I submit.
    (ADR-005)
11. As a visitor, I want paste and password managers to work on every password field, so that I can use a long
    generated password. (R-FE-001)
12. As a visitor, I want sign-in to fail identically whether the username is unknown, the password is wrong or the
    account is locked, disabled, capped, not yet activated or expired, so that failures reveal nothing about the
    account. (PRD Story 2; ADR-033)
13. As a visitor who forgot their password, I want to request a reset by email address and always get the same
    answer, so that the request does not confirm my account exists. (PRD Story 6)
14. As a visitor, I want a reset link that expires in 30 minutes, works once, and was built from configured origins
    only, so that a leaked or forged link is of little use. (PRD Story 7; ADR-007; REJ-022)
15. As a visitor redeeming a reset link, I want every existing session on my account ended, so that anyone holding
    one of them is signed out. (PRD Story 7; ADR-035; ADR-037)
16. As a visitor redeeming a reset link, I want my password lockout cleared, so that recovery also recovers access.
    (ADR-009)

### Signed-in users

17. As a user, I want to sign in with my username and password and get a server-side session, so that I can use
    the app. (PRD Story 2)
18. As a user, I want to see "Hello, <username>" once signed in, so that I know the sign-in worked. (PRD Story 5)
19. As a user, I want sign-out to end my session on the server and clear the app's local state, so that nothing
    from my session can be reused. (PRD Story 4; REJ-051; R-HDR-008)
20. As a user, I want a newer sign-in to replace my older session, so that one account holds one live session.
    (R-AUTH-002)
21. As a user, I want my session to end after 15 minutes idle and 8 hours in total, so that an unattended session
    does not last. (REJ-012)
22. As a user, I want to change my password by giving my current one, so that someone at my unlocked screen cannot
    change it. (ADR-008)
23. As a user who changes their password, I want my other sessions ended and my current one kept, so that I stay
    signed in here while other devices are signed out. (ADR-035)
24. As a user, I want my last three passwords refused on change, so that I do not rotate back to an old one.
    (REJ-007; T-CRED-002)
25. As a user who fails to sign in repeatedly, I want the lockout to lift on its own, so that I do not need an admin.
    (PRD Story 3; ADR-011)
26. As a user, I want a failed sign-in attempt from anyone to leave my live session alone, so that a stranger cannot
    sign me out by guessing. (ADR-034)
27. As a user whose credential must be changed, I want to be allowed only sign-in, the token fetch, my profile,
    sign-out and the password change, so that I reach the change without detours.
28. As a user, I want the app to show me the right screen from my own session state, and to treat the server's error
    code as final, so that a stale view never overrides the server. (*belief versus authority*)

### Administrators

29. As an admin, I want to list all users with username, email, role, enabled status and creation date, and never a
    password hash, so that I can review access. (PRD Story 8; T-ADM-008)
30. As an admin, I want to view one user's detail, so that I can check an account before acting on it.
31. As an admin, I want to create an account by invite, and to receive the invite token once to pass on, so that the
    user sets their own password. (ADR-006; R-ADM-012)
32. As an admin, I want to enable or disable another account, and a disable to end its sessions, so that a
    suspension takes effect at once. (PRD Story 9; ADR-037)
33. As an admin, I want to change another account's role between USER and ADMIN, and the change to end its sessions,
    so that no session keeps old authorities. (PRD Story 10; ADR-037)
34. As an admin, I want to delete another account, leaving a tombstone that blocks reuse of its username and email,
    so that a deleted identity cannot be re-registered. (PRD Story 11; ADR-044)
35. As an admin, I want to unlock another account's password lockout and tier-1 factor lock, with a recorded reason,
    so that a user can get back in before the lock lifts. (R-AUTH-002; REJ-028; REJ-072)
36. As an admin, I want to issue a password-reset token for any account, including my own, returned to me once, so
    that I can help a user who cannot use email. (ADR-006)
37. As an admin, I want to reset another admin's second factor, which ends their sessions and makes them re-enrol,
    so that a lost phone is recoverable in the app. (ADR-024; ADR-049)
38. As an admin, I want every action on my own account to disable, demote, delete, unlock or reset my factor to be
    refused, so that I cannot lock myself out. (PRD Stories 9–11; ADR-048)
39. As an admin, I want disabling, demoting or deleting either of exactly two enrolled admins refused, so that
    another admin always remains to recover me. (R-ADM-008; ADR-048)
40. As an admin, I want a refusal under the two-admin rule to tell me the next step: invite, redeem, promote, enrol.
    Then an invariant working correctly does not read as a bug. (REJ-050)
41. As an admin, I want to enrol a TOTP authenticator by QR code or by typing the secret, so that I can enrol with or
    without a camera or screen reader. (ADR-025; R-FE-005)
42. As an admin, I want to be sent straight to enrolment or to the challenge after password sign-in, so that the admin
    surface opens without a detour. (ADR-023)
43. As an admin, I want admin reads to accept my factor for the whole session and admin changes to ask again after
    ten minutes, so that the check fits how much harm each action can do. (ADR-021)
44. As an admin who is asked to step up mid-task, I want my refused requests queued and replayed once after one
    successful code, so that I do not redo my work. (*step-up queue*)
45. As an admin whose factor is under tier-2 disable, I want to see that state rather than a challenge that cannot
    succeed, so that I know I need another admin to reset my factor. (*terminal factor state*; REJ-049)
46. As a USER, I want every admin route to refuse me with 403, and to never ask me for a second factor, so that the
    admin surface is invisible to me. (PRD Story 8; ADR-026)

### Operators and deployers

47. As an operator, I want the first admin seeded from configuration on first start, and never again, so that there
    is a way in without database edits. (PRD Story 12; ADR-047)
48. As an operator, I want startup to fail before the port opens when the seed credential, a key, a role definition
    or any prohibited configuration is wrong, so that a misconfigured deployment never serves traffic. (ADR-047;
    ADR-062)
49. As an operator, I want the seeded admin forced to change password and enrol a factor, and the seeded credential
    to expire after 30 days unused, so that a configured password is not a standing credential. (ADR-046; ADR-047)
50. As an operator, I want a second enrolled admin before go-live, so that in-app recovery exists from day one.
    (R-ADM-017)
51. As an operator, I want an offline recovery runner that sets a password I type, or clears a factor, with a
    dry-run digest I must confirm, so that I can recover when no admin can sign in. (ADR-072; ADR-073; ADR-074)
52. As an operator, I want one operational handover document, in deployment order, with a proof for each obligation,
    so that I know what to do and can show it was done. (ADR-069; R-OPS-005)
53. As an operator, I want every key supplied from a mounted secret or the environment, never from a committed file,
    and its fingerprint logged at startup, so that I can confirm which key is live. (ADR-022; ADR-052; ADR-062)
54. As an operator, I want `/actuator/health` to be the only exposed endpoint, and it to reveal no details, so that
    monitoring reveals nothing to an anonymous caller. (ADR-061)
55. As an operator, I want an authenticable-admins gauge I can alert on below two, so that I know before recovery
    needs an outage. (ADR-048; R-OBS-007)
56. As an operator, I want new anonymous sessions shed when session rows or free disk cross their limits, so that a
    flood of token fetches cannot fill the database. (ADR-041)

### Security reviewers and maintainers

57. As an auditor, I want every login success and failure, lockout, lock clearance, reset request and completion,
    and admin action to be a structured audit row naming the actor and the subject, never a password or token, so
    that I can reconstruct events. (PRD §Non-functional; ADR-055)
58. As an auditor, I want audit rows to carry keyed hashes of the source key and session instead of addresses, so
    that correlation works without storing personal data. (ADR-054)
59. As an auditor, I want every deviation, residual and obligation in one register with its verdict and proof, so
    that the compliance position is readable in one place. (ADR-069)
60. As a maintainer, I want each security control tied to a test ID, and the build to fail when a row loses its
    test, so that no control silently loses its test. (ADR-068)
61. As a maintainer, I want error bodies to come from one writer with a closed code enum, so that clients branch on
    one field and no second error shape appears. (ADR-031)
62. As a maintainer, I want time to come from one injectable clock, so that time-dependent controls are testable
    without sleeping. (ADR-066)

## Implementation Decisions

### Stack

- **Backend:**
  - Spring Boot 4.1.x, Spring Security 7.1.x and Spring Session 4.1.x, on Java 21, built with Maven 3.9.
  - Spring Data JPA over H2 in file mode, with Flyway migrations and `ddl-auto: validate` (REJ-003; ADR-051).
- **Frontend:**
  - React 19 with Vite and strict TypeScript;
  - shadcn/ui on Base UI;
  - React Hook Form with Zod;
  - TanStack Query.
- **Session store:** Spring Session JDBC (ADR-029; ADR-030).
- **Password hashing:** BCrypt, as the PRD requires (ADR-001).
- **OWASP ASVS target:** 5.0 Level 1, with named L2 and L3 controls adopted where they are cheap (R-STD-027;
  R-STD-057). An L2 or L3 citation is a per-control claim, never a level claim.
- **Standards precedence:** governing user standard over the logging standard over the recipes (REJ-047).

### Modules

Each module below owns one decision behind a small interface. The spec names them so that tickets can be cut along
them.

| Module | Owns | Record |
|---|---|---|
| `PasswordService` | the single path that sets any password: normalise, run the policy, encode, write history. No call site is exempt. | ADR-005 |
| Credential tokens | mint, hash, consume and invalidate `ACTIVATION` and `PASSWORD_RESET` tokens | ADR-007 |
| `AuthRateLimiter` + `SourceKeyResolver` | every per-source and per-submitted-value budget, the cardinality axis and the miss budget, over one source key | ADR-010; ADR-015; ADR-017; ADR-020 |
| Lockout and cap | windowed counter, ladder, NIST cap, pre-authentication checks | ADR-011; ADR-012; ADR-013; ADR-046 |
| `SessionTerminationService` | every call that ends sessions, after commit, plus the startup reconciliation sweep | ADR-037; ADR-039 |
| `ProblemDetailWriter` | every error body | ADR-031 |
| `AdminActionGuard` | self-action refusal and the two-admin invariant, from one service call site | ADR-048; ADR-049 |
| TOTP factor | provisioning, confirmation, verification, two-tier lockout, encryption with the context prefix | ADR-021 to ADR-028 |
| Audit emitter | the `AuditEvent` enum and its single `emit` | ADR-055 |
| Configuration validator | refresh-phase refusal of invalid or prohibited configuration and missing secrets | ADR-047; ADR-062 |
| Recovery runner | offline password and factor rebinding | ADR-072; ADR-073; ADR-074 |
| SPA | routing, the auth interceptor, the step-up queue and the forms | see Frontend below |

Pure decision logic is kept out of Spring, so it can be unit-tested and mutation-tested. That covers the guard's
decision, token consumption and the TOTP window arithmetic (ADR-065).

### API surface

All paths sit under the externalised base path `/api`. Every endpoint needs an authorization-matrix row, or it is
denied (ADR-043). Each unsafe method requires the CSRF header (ADR-036).

| Method and path | Caller | Behaviour | Record |
|---|---|---|---|
| `GET /api/csrf` | anyone | Returns `{headerName, token}`. It is the only route that creates an anonymous session. It is shed under load. | ADR-036; ADR-040; ADR-041 |
| `POST /api/register` | anonymous | Takes `{username, email}` and always returns the same 202. | ADR-032 |
| `POST /api/register/activate` | anonymous | Takes the token and a password, then activates the account. | ADR-032 |
| `POST /api/login` | anonymous | JSON credentials. Returns the uniform 401 on any failure. | ADR-033 |
| `POST /api/logout` | session | Ends the session. Still CSRF-protected on a dead session (T-CSRF-005). | ADR-036 |
| `POST /api/password-reset/request` | anonymous | Takes an email address and always returns the same response. | PRD Story 6 |
| `POST /api/password-reset/confirm` | anonymous | Takes the token and a password. Also redeems admin-issued reset tokens. | ADR-006; ADR-009 |
| `GET /api/profile` | any signed-in user, password only | The self-read, including the `factors` object (see below). | ADR-043 |
| `PATCH /api/profile/password` | any signed-in user | Self-service change and forced-change completion. | ADR-008 |
| `GET /api/hello` | `ROLE_USER` | Returns `"Hello, <username>"`. Not on the forced-change allowlist. | PRD Story 5; REJ-055 |
| `POST /api/mfa/totp/enrolment` | `ROLE_ADMIN`, no factor | Provisioning. Writes the pending enrolment and returns the JSON envelope. | ADR-025; REJ-069 |
| `POST /api/mfa/totp/enrolment/confirmation` | `ROLE_ADMIN`, no factor | *Enrolment binding*. Grants the factor. | REJ-071 |
| `POST /api/mfa/totp/verification` | `ROLE_ADMIN`, password held | Grants or renews the factor. The code travels in the JSON body. | ADR-021; REJ-070 |
| `GET /api/admin/users` | admin, factor of any age | The user list, with no hash. Carries `user.target.count` in its audit row. | PRD Story 8 |
| `GET /api/admin/users/{uuid}` | admin, factor of any age | One user. | |
| `POST /api/admin/users` | admin, factor within 10 min | Invite: creates a pending account and returns the activation token once. The same identifiers of a pending invite re-issue it. | ADR-006; ADR-007 |
| `PUT /api/admin/users/{uuid}/enabled` | admin, factor within 10 min | Enable or disable. Guarded, and ends sessions on disable. Re-enable issues a forced-change credential. | PRD Story 9; ADR-046 |
| `PUT /api/admin/users/{uuid}/role` | admin, factor within 10 min | Role change. Guarded, and ends sessions. | PRD Story 10 |
| `DELETE /api/admin/users/{uuid}` | admin, factor within 10 min | Delete plus a tombstone, in one transaction. Guarded, and ends sessions. | PRD Story 11; ADR-044 |
| `POST /api/admin/users/{uuid}/unlock` | admin, factor within 10 min | Clears the password lockout and the tier-1 factor lock, never tier 2, with a reason. Actor ≠ subject. | REJ-072; REJ-028 |
| `POST /api/admin/users/{uuid}/password-reset` | admin, factor within 10 min | Returns a reset token once. No self-action rule applies. Ends the subject's sessions. | ADR-006; ADR-037 |
| `DELETE /api/admin/users/{uuid}/totp` | admin, factor within 10 min | Factor reset: deletes the confirmed and pending rows and ends sessions. Exempt from the two-admin count. | ADR-024; ADR-049 |
| `/api/admin/roles/**`, all methods | nobody | Explicit `denyAll()`, placed before the admin guards. | ADR-043 |
| `GET /actuator/health` | anyone | Health only, with no details. | ADR-061 |

Further rules for the API surface:

- **One-time secrets.** Every response carrying one (an invite token, a reset token, a new factor secret) is sent with
  `Cache-Control: no-store`, and the secret is never logged (R-FE-007).
- **Forced-change allowlist.** It holds exactly five paths: `POST /api/login`, `GET /api/csrf`, `GET /api/profile`,
  `POST /api/logout` and `PATCH /api/profile/password`. `/api/mfa/**` is deliberately off it (ADR-023; R-STD-029).
- **`factors` on the self-read.** It has four members: `held`, `required`, `enrolled` and `rebindRequired`. A tier-2
  disable also surfaces there as `FACTOR_DISABLED` (REJ-049).
- **Path mapping.** Admin paths are mapped in controllers, and `spring.mvc.servlet.path` is prohibited (REJ-024).

### Error contract

- **Envelope.** Every error is an RFC 9457 `application/problem+json` body with a `code` extension, written only by
  `ProblemDetailWriter`. `sendError` is prohibited (ADR-031).
  - Clients branch on `code` and on nothing else (REJ-092).
  - `detail` is a constant per code.
  - On authentication failures, `instance` is a constant (ADR-033).
  - `/actuator/**` is exempt from the envelope (REJ-066).
- **The closed code enum.** Each code has exactly one status:

| `code` | Status | Used for |
|---|---|---|
| `AUTHENTICATION_FAILED` | 401 | every password-axis sign-in failure, an unauthenticated request, and absolute expiry |
| `PASSWORD_CHANGE_REQUIRED` | 403 | a forced-change credential used outside the allowlist |
| `CSRF_TOKEN_INVALID` | 403 | a missing, wrong or superseded CSRF token, including logout on a dead session |
| `ACCESS_DENIED` | 403 | insufficient role, an unmatched route for a signed-in caller, and a self-action refusal (REJ-050) |
| `VALIDATION_FAILED` | 400 | format and length rejections, the 16 KiB body cap, and rule `USERNAME_UNAVAILABLE` |
| `PASSWORD_REJECTED` | 400 | password policy, with a `rule` from `MIN_LENGTH`, `MAX_BYTES`, `BLOCKLISTED`, `CONTEXT_TERM`, `TOO_WEAK` or `HISTORY_REUSE` |
| `RESET_TOKEN_INVALID` | 400 | any failed redemption of an activation or reset token |
| `USER_EXISTS` | 400 | admin-initiated creation only, including tombstone hits |
| `TOO_MANY_REQUESTS` | 429 | every throttle and a tier-1 factor lock (with the factor member and `reason: LOCKED`). Always carries an integer `Retry-After`. |
| `MISSING_FACTOR` / `INVALID_FACTOR` | 412 | factor absent, expired (reason `MISSING` or `EXPIRED`) or wrong code (R-MFA-001) |
| `FACTOR_ENROLMENT_REQUIRED` | 422 | an unenrolled admin on the admin surface (R-MFA-002) |
| `FACTOR_ALREADY_ENROLLED` | 409 | provisioning when a confirmed factor exists |
| `FACTOR_DISABLED` | 423 | tier-2 disable, on the self-read, the admin entry point and verification (R-MFA-006) |
| `TWO_ADMIN_INVARIANT` | 409 | the two-admin invariant's refusal of a disable, demote or delete (REJ-050; R-ADM-008) |
| `INTERNAL_ERROR` | 500 | anything unhandled |

- **The two-admin invariant's refusal is a 409** (REJ-050), `TWO_ADMIN_INVARIANT`: see Further Notes.
- **Off-label statuses.** 412, 422 and 423 are recorded deviations, kept for envelope consistency (REJ-073).
- **Build deliverable.** The error contract's prose form, `docs/api/error-contract.md`, is generated from the closed
  enum by the build and committed with it. The spec does not write it (R-AUTH-003). A JSON Schema generated from the
  same enum validates every backend body (T-AUTH-011) and every frontend fixture (T-AUTH-012).
- **401s carry no `WWW-Authenticate` challenge.** This is a recorded deviation (R-AUTH-005).
- **The main entry point must match `fetch` requests.** The SPA's `Accept` header is therefore part of the server's
  configuration (T-AUTH-015).

### Authentication and enumeration

- **Uniform password-axis failure.** Wrong password, unknown user, locked, disabled, capped, not activated and
  expired forced-change credentials all return one identical `401 AUTHENTICATION_FAILED`. The internal reason goes to
  the audit stream at WARN (ADR-033).
- **Timing uniformity.** It rests on the framework's dummy `matches()`, which runs at the same cost as live hashes.
  Two further rules protect it:
  - no lockout branch may run before authentication on account existence;
  - no encoder wrapper may skip `matches()`.

  Uniformity is verified by call count, not by stopwatch (ADR-001; R-AUTH-004; REJ-056).
- **Pre-authentication check order.** Spring's locked and disabled checks run first, then the NIST cap, then the
  forced-change expiry. `isCredentialsNonExpired()` is hard-wired `true` (ADR-013; ADR-046; REJ-019).
- **`alwaysPerformAdditionalChecksOnUser` stays `true`.** `CompromisedPasswordChecker` is implemented but not
  registered as a bean (REJ-005).
- **The per-username 429 is not an existence oracle.** It is thrown before any repository lookup (T-RL-017).
- **Accounts.** Users sign in by username only (R-CRED-015). A failed login never touches existing sessions
  (ADR-034).

### Password policy

- **Checks.** `PasswordService` runs `MIN_LENGTH` (15 code points after NFC), `MAX_BYTES` (72 UTF-8 bytes),
  `BLOCKLISTED`, `CONTEXT_TERM`, `TOO_WEAK` (zxcvbn4j 1.9.0 score below 3) and `HISTORY_REUSE` (the last three
  hashes), in that order, on every path that sets a password (ADR-002; ADR-003; ADR-005).
- **Where length is checked.** Length is checked when a password is set, never on the login path (ADR-003).
- **Blocklist.** It is a version-pinned breach-corpus slice of entries of 15 characters or more, plus a documented
  context word list. Refreshing both is an operator duty (REJ-004; R-CRED-006).
- **Encoding.** BCrypt at cost 12 behind `DelegatingPasswordEncoder`, with no pepper (ADR-001; ADR-004). The client
  meter uses zxcvbn-ts and is indicative only.
- **Declined:** periodic expiry (R-CRED-002) and mistyping allowances (R-CRED-003).

### Credential flows

- **Tokens.** One `credential_tokens` table holds both types. A token is 256 bits of Base64url, stored as
  domain-separated SHA-256, and consumed by a single conditional update (ADR-007).
- **Lifetimes.** Reset tokens last 30 minutes and activation tokens 24 hours.
- **Redemption order.** Consume first, then set the password, in one transaction.
- **Reset request.** It is uniform whether or not the address is registered. A reset against a never-activated
  account does nothing (R-CRED-012).
  - **In `dev` only**, the stubbed `EmailService` logs the link, on a dedicated non-audit logger held by three
    controls (ADR-057; R-CRED-020).
  - **Outside `dev`**, a reset request produces nothing deliverable. Recovery is an admin-issued token or the runner
    (R-CRED-021).
- **Link origin.** It comes only from configuration, never from `Host`, `X-Forwarded-*` or the request body (REJ-022).
- **What redemption clears.** Redemption clears the password lockout, the cap, the forced-change flag and
  `credential_issued_at`. It never touches TOTP state (ADR-009).
- **What issuance clears.** Issuing a token clears nothing. Admin issuance deliberately does not unlock (REJ-016;
  R-LCK-010; R-STD-024).
- **Self-service change.** `PATCH /api/profile/password` always requires the current password. It follows ADR-008's
  six steps: verify, set, invalidate reset tokens, end other sessions, rotate the id, notify. Owner notification is
  not built (see Out of Scope).
- **Admin create and admin reset** issue tokens and never generated passwords (ADR-006).

### Identifiers

- **Canonicalisation.** Both usernames and email addresses are canonicalised: NFC, trim, lowercase, with no dot or
  `+tag` folding. The canonical value is the stored value (ADR-045).
  - A username that canonicalisation would change is rejected. An email address is converted.
  - The whole email address is lowercased (R-CRED-014).
- **Username restrictions.**
  - Usernames may not contain `@` (REJ-027).
  - A reserved-name denylist applies at both bootstrap and registration (REJ-021; T-ADM-022).
- **Scope.** Canonicalisation is never applied to passwords.
- **Identifiers on the wire.** Every identifier on the wire is a UUIDv4 primary key, generated by the application
  (ADR-050).

### Sessions and CSRF

- **Cookie.** `HttpOnly`, `SameSite=Strict`, `Path=/`, with no `Domain` and no `Max-Age`. It is named `__Host-SESSION`
  outside `dev` and `SESSION` in `dev` (ADR-058; REJ-008; REJ-061).
- **Cookie resolution.** Only the first session cookie is honoured, with an explicit `CookieSerializer` bean
  (R-SES-007; REJ-083).
- **Session attributes.** They are JDK-deserialised, so an allowlist filter is required (R-SES-003).
- **Limits.** 15 minutes idle, 8 hours absolute measured from the *auth instant*, and one concurrent session, where
  the new login wins (REJ-012). Idle expiry is observed lazily, with no reaper (REJ-046).
- **Session-id rotation.** The id rotates at login, logout, factor grant and credential change. `AUTH_INSTANT` is
  stamped at exactly one place, the login composite (ADR-038).
- **Filter order.** The source rate limiter, then `SecurityContextHolderFilter`, the header writer and `CorsFilter`,
  then the absolute-lifetime filter, then `CsrfFilter` (ADR-038; T-SES-037).
- **CSRF.** A session-bound synchronizer token, resolved from the `X-CSRF-TOKEN` header only, and never from a
  parameter. No CSRF cookie exists (ADR-036; R-SES-004). Every unsafe method is covered, logout included.
- **Anonymous sessions.** Only `GET /api/csrf` creates them. Their expiry is pinned at creation plus the idle interval
  (ADR-040; REJ-091). `NullRequestCache` is set.
- **Invalidation triggers.**
  - The triggers and their scope are ADR-037's table.
  - Invalidation is dispatched after commit, with a startup reconciliation sweep as the repair (ADR-039; R-SES-012).
  - Lock order everywhere: `users` rows, then TOTP rows, then session rows after commit.
- **Logout.** Logout sends `Clear-Site-Data` for compliance. The SPA clears its own state regardless (REJ-010).
  Dropping `.deleteCookies(...)` from logout is harmless (REJ-011).

### Lockout, throttling and rate limits

- **Password lockout.**
  - It triggers at 5 consecutive failures inside a 20-minute observation window (ADR-012), and at the 10th failure
    since the last success whatever the window, then every 5th after it (ADR-011 amendment of 2026-09-29).
  - The duration escalates 20 → 40 → 60 minutes, 5 cycles per rung, read from the cap counter (ADR-011;
    R-LCK-007).
  - A startup floor refuses a ladder that lets an attack of any pacing reach the cap in under 840 minutes or leaves
    under 580 minutes of warning.
- **NIST cap.** 100 consecutive failures disables the password authenticator, with an alert at 50. Only rebinding
  clears it (ADR-013; R-LCK-004).
- **Budgets.** Per-source and per-submitted-value limits live in one component. Each row binds
  `app.security.rate-limit.<route>.<axis>.burst` and `.refill-period` (T-RL-010). The values are:

| Route | Axis | Burst | Refill |
|---|---|---|---|
| `POST /api/login` | source | 60 | 1 per second |
| `POST /api/login` | username | 10 | 1 per 6 s |
| `GET /api/csrf` | source | 30 | 1 per 2 s |
| `POST /api/register` | source | 5 | 1 per 12 s |
| `POST /api/password-reset/request` | source | 5 | 1 per 12 s |
| `POST /api/password-reset/request` | identifier | 3 | 1 per 20 min |
| `POST /api/password-reset/confirm` | source | 10 | 1 per 6 s |
| `POST /api/register/activate` | source | 10 | 1 per 6 s |
| `PATCH /api/profile/password` | source | 10 | 1 per 6 s |
| `POST /api/mfa/totp/verification` | source | 20 | 1 per 3 s |
| `POST /api/mfa/totp/enrolment/confirmation` | source | 20 | 1 per 3 s |
| `POST /api/mfa/totp/enrolment` | source | 10 | 1 per 6 s |

- **The table is a request-rate allowlist.** An unlisted route is bounded at the edge (R-OPS-006).
- **Session-miss budget.** Every request is metered on session-store misses: 300 per 15 minutes per source
  (ADR-017).
- **Cardinality axis.** One source may drive at most `k` = 5 distinct accounts into lockout per hour, with expiry
  from first insertion (ADR-015).
- **Source key.** Derived by one resolver: IPv4 /32, or IPv6 masked to
  `app.security.client-ip.ipv6-prefix-length` (default 64, allowed 48–128). No DNS is used. The raw-address path is
  banned (ADR-020).
- **Trusted proxies.** They are named explicitly, and `framework` forwarding is prohibited (REJ-015; R-RL-007).
- **Refusals.** Each is a 429 with an integer `Retry-After`. No refusal moves an account counter (ADR-010).
- **No server-side delay** on any authentication path (ADR-014).
- **Request body cap.** 16384 bytes, counted in bytes, before the login converter reads the body (T-RL-011;
  T-RL-019).
- **Single instance, asserted.** Distributed limiting is not supported (REJ-018; R-RL-005; R-RL-006).

### Second factor

- **Who.** TOTP is for `ADMIN` only (ADR-023; R-MFA-008).
  - The whole `/api/admin/**` surface requires it.
  - Reads accept a factor of any age within the session. Mutations need one issued within 10 minutes (ADR-021).
- **How the rules are built.** They are hand-composed, role first, with no `@EnableMultiFactorAuthentication` and no
  `RoleHierarchy` (ADR-026). A missing `FACTOR_TOTP` has its own entry point.
- **Secret storage.** Secrets are encrypted with `AesGcmBytesEncryptor` under an environment-supplied 32-byte key
  (ADR-022).
  - A 37-byte *context prefix* sits inside the plaintext, so the stored envelope is 69 bytes (ADR-028; R-MFA-016).
  - A prefix mismatch is a security event (R-MFA-020).
- **Codes.** Six digits, a 30-second step, ±1 step of skew, and replay rejected under the row lock (REJ-074;
  R-MFA-013; R-MFA-017).
- **Two-tier lockout** (ADR-027):
  - tier 1 is 10 failures in 20 minutes, then a 20-minute auto-lifting lock;
  - tier 2 is 100 cumulative failures, which disables the factor, forces a password change and ends sessions
    (ADR-016).

  Every guess costs a password: the username comes from the security context, never from the request body.
- **Provisioning.** It writes a *pending enrolment* only and resets no counter. It returns `otpauthUri`,
  `secretBase32` and `qrPng` once (ADR-025). Confirmation copies the pending blob verbatim.
- **Enrolled means a `totp_user_details` row exists** (ADR-053).
- **Not built:** recovery codes (ADR-024; R-MFA-021), PIN and email OTP (R-MFA-007), and TOTP for users (R-MFA-015).

### Admin module and bootstrap

- **Roles.** Two roles, one per user. Configuration is the source of truth, and a seeded read-only `roles` table
  enforces the foreign key (ADR-042).
- **Authorization matrix.** Whitelist first, then role guards, then `denyAll()`. `@PreAuthorize` repeats the role
  check only (ADR-043). Authorities are checked with `hasRole` (REJ-023).
- **One guard.** Every admin mutation goes through one service method that calls `AdminActionGuard` (ADR-048).
  - Check 1, actor ≠ subject, applies to disable, demote, delete, unlock and factor reset.
  - Check 2, the two-admin invariant, applies to disable, demote and delete, under a uniform pessimistic lock set.
- ***Authenticable* is defined once.** The guard and the authenticable-admins gauge read the same definition
  (ADR-048).
- **Recovery routes**, keyed on *authenticable*:
  1. auto-expiry;
  2. in-app reset, when another authenticable admin exists;
  3. the offline runner.

  Route 2 waits on route 1 if the other admin is only locked (R-ADM-019).
- **Bootstrap** (ADR-047):
  - validation runs at refresh, and seeding runs in a runner;
  - the seed happens only when no `ADMIN` row exists;
  - it never seeds around a disabled admin;
  - it fails fast on a tombstoned username;
  - credentials come from the environment only;
  - the seed is a forced-change credential.
- **Forced-change expiry.** It is lazy, at login, after 30 days (ADR-046). Setting a user-chosen password clears
  `credential_issued_at`, including at token redemption.
- **Last-write-wins.** Non-security admin edits have no optimistic locking (REJ-038; R-DATA-013).
- **Not built:**
  - batch password reset (R-ADM-014);
  - any generic user-update route (R-ADM-013);
  - scheduled access review (R-ADM-001).

### Data model

Flyway owns eight versioned migrations, in this order:

1. `roles`
2. `users`
3. `credential_tokens`
4. `password_history`
5. the two TOTP tables, `totp_user_details` and `pending_totp`
6. `deleted_users`
7. the Spring Session tables
8. `username_holds` (ADR-032 amendment of 2026-09-29)

Migrations are split by concern and are append-only. Flyway `clean` is disabled (REJ-041).

- **The session DDL.** It is copied verbatim, checked by blob hash, and Spring Session's own initialisation is off
  (REJ-040; ADR-030).
- **`users` beyond the PRD's columns:**
  - `activated_at`;
  - `last_failed_at` (ADR-012);
  - `consecutive_failures_since_success` and `password_disabled_at` (ADR-013; REJ-020);
  - `force_password_change` and `credential_issued_at` (ADR-046).

  `role` is a foreign key to `roles.name` (R-DATA-003). Being locked is derived from `locked_until`, never stored
  (REJ-017). The credential column is `VARCHAR(255)`.
- **`credential_tokens`** replaces the PRD's `password_reset_tokens` (R-DATA-002).
- **`deleted_users`** holds `user_id`, `username`, `email_hmac`, `deleted_at` and `deleted_by_id`, with no foreign
  keys (ADR-044; REJ-034). The HMAC key is versioned forward only (ADR-052).
- **`username_holds`** holds `id`, `username` (unique), the canonical `email` it was taken for and `expires_at`, with no
  foreign key. Every registration that passes the username check takes or renews one for 24 hours, whatever
  the email's state; expired holds are purged by the next registration (ADR-032).
- **Deletion.** `ON DELETE CASCADE` is the single deletion mechanism. Password history purges with the account
  (REJ-035).
- **Column types:**
  - timestamps are `TIMESTAMP(6) WITH TIME ZONE` (REJ-031);
  - digests are lowercase hex `VARCHAR(64)` (REJ-032);
  - `totp_key` is `VARBINARY(69)` with named width checks (REJ-033).
- **Schema gate.** `validate` with `NAMED` index validation and `ux_` / `ix_` naming. The real gate is ADR-051's
  negative-test set.
- **Portability.** It is a closed seam register, asserted by review and never executed (REJ-030; R-DATA-004 to
  R-DATA-009). Admin list sort columns are deliberately unindexed (REJ-037).
- **H2 settings.** File mode only. `LOCK_TIMEOUT=1000` is pinned on the URL, and `FILE_LOCK=NO` is prohibited.

### Audit and logging

- **Emitter.**
  - One `AuditEvent` enum and one `emit`, with typed context records (ADR-055).
  - Unknown keys are rejected, and `emit` has no throwable parameter.
  - Reasons belong to sealed families and serialise by `code()`.
  - `emit` fails soft at runtime and hard in tests.
- **The catalogue is generated, not written by hand.** The ASVS 16.1.1 log inventory is generated from the enum as a
  snapshot, with each row's destination, retention and readers (R-AUD-027).
- **Required rows.** The catalogue covers every PRD event: login success and failure, lockout triggered and
  cleared, reset requested and completed, and role change, enable, disable and delete with actor and subject. It
  also covers the standard's CSRF rejection, failed administrative attempt, session start and startup rows.
- **Row-shape decisions:**
  - `user.id` on failed logins when the account resolves (REJ-042; R-AUD-007), omitted on reset-requested rows
    (REJ-002);
  - lockout is logged at WARN (REJ-043);
  - the unlock reason is a closed enum: `USER_REQUEST`, `FALSE_POSITIVE`, `PASSWORD_RESET_COMPLETED` or `OTHER`
    (REJ-028);
  - `url.path` is the matched route pattern. The raw URI appears only on pre-handler rows, capped at 256 characters
    (REJ-081);
  - a password-rejected row never precedes a successful token check.
- **Fields.**
  - `source.ip_hash` and `session.hash` are keyed HMACs, and no address is ever logged (ADR-054).
  - Three custom fields are requested from the schema owner: `user.target.unlock_reason`, `user.target.count` and
    `source.ip_hash` (REJ-044; R-AUD-002). The runner uses `labels.operator_claimed_id`.
- **Keying.** Tier-1 and tier-2 *keyed rows* are held to 20 sources and 500 users per 15-minute *keying window*
  (ADR-019; REJ-080). Each keyed row carries its count and first-seen time (REJ-078). A *truncation row* carries
  exact counts (REJ-079). One audit row is keyed rather than deleted (REJ-082).
- **Destinations.** A dedicated rolling file, rolled daily with 90 archives and no total-size cap, plus a stdout copy
  in every profile (ADR-056; REJ-045; R-AUD-037).
- **Encoder.** The custom structured encoder redacts at source. There is no masking decorator (REJ-001).
- **Tracing.** Inbound trace context is restarted at the boundary (ADR-063). Baggage is off (REJ-087), and
  `correlation.id` is dropped (REJ-088).

### Observability

- **Actuator.** Only `health` is exposed, read-only, with `show-details: never`. Every other endpoint is denied
  explicitly (ADR-061).
- **Health.** `ping`, `diskspace` and a shared-count `h2Data` indicator. `db` health and probes are off (REJ-063;
  ADR-041).
- **Metrics.** Pushed over OTLP, off by default. A profile that enables export must make the URL required
  (ADR-061).
- **Monitoring beyond the boundary.** The alert taxonomy (per-event, rate-above, rate-below) and every threshold live
  in the deployer's collector (REJ-064; R-OBS-004). Daily audit volume is a formula with one deployer input (REJ-084;
  R-OBS-012).

### Configuration and secrets

- **Namespace.** Every application property sits under `app.*` (R-CFG-002; REJ-006).
- **Secrets.** Each secret binds through `@Validated @ConfigurationProperties`, never `@Value`, and has no default in
  any profile (ADR-062). The secrets are:
  - the TOTP key and its version;
  - the tombstone HMAC key and its version;
  - the log HMAC key;
  - the admin seed username and password.

  Each key is distinct material (ADR-052).
- **Refresh-phase refusals.** Startup refuses:
  - an in-memory or unset datasource URL;
  - any prohibited configuration (REJ-008; REJ-015; REJ-024; ADR-057);
  - a malformed key.
- **Build-time origins.** Frontend origins are baked at build time, so the bundle is environment-specific
  (R-BLD-012). The frontend `.env.*` files stay committed (R-CFG-012).

### Frontend

- **Routes.**
  - Public: sign-in, register, activate, forgot and reset.
  - Authenticated: hello, profile and change password.
  - Admin: the user list and user detail.
  - `/settings/mfa`, for admins only.
- **Gate order.** Forced change, then terminal factor state, then enrolment, then the challenge, then the app.
- **Guards are UX only.** A test deletes a guard and asserts the admin data still does not render.
- ***Belief versus authority*.** The self-read drives navigation, and the envelope `code` overrides it.
- **Retries.** Only a request that got no status is retried (REJ-052). On `CSRF_TOKEN_INVALID`, one silent
  re-bootstrap and retry is the backstop. The token is re-fetched proactively after each rotation (ADR-040).
- **Sign-out is terminal** on 204, 401 and 403 (REJ-051).
- **Step-up queue.** Client-side, single-flight, with a re-bootstrap before replay.
- **QR code.** Rendered from the server PNG via a `blob:` URL. One effect creates and revokes the URL (ADR-025).
- **Password fields.** The password byte limit is counted client-side, in bytes.
- **Document headers.**
  - CSP in three layers, with a templated meta tag before every script (ADR-060; ADR-059). No inline script
    (R-HDR-001).
  - `style-src` stays unsplit (REJ-054).
  - A document-wide `Referrer-Policy` meta tag, first in `<head>` (REJ-053).
- **No service worker** (T-E2E-001).
- **Accessibility.** The target is WCAG 2.2 AA (R-FE-005). The built behaviours are:
  - labels and error association;
  - focus on error;
  - live regions;
  - keyboard paths through tables and dialogs;
  - a manual-entry TOTP secret.

### Recovery runner

- **Process.** The same jar, run with the application stopped and `web-application-type=none`, inside a planned
  outage (ADR-072).
- **Scope.** Explicitly `password`, `totp` or `both`. It runs through the full context refresh, with `IFEXISTS=TRUE`,
  and it never migrates or seeds (REJ-089). It bypasses `AdminActionGuard`, and the enrolled-admin count before and
  after is audited.
- **Single run.** The operator supplies the password on stdin or at a prompt, and it goes through `PasswordService`
  as a forced-change credential. Batch mode mints nothing, and no secret crosses any stream (ADR-073).
- **Dry run by default.** A destructive run needs `--confirm=<digest>` over the account state, plus a reason
  (ADR-074).
- **Audit rows.** A dry-run row, an intent row and an outcome row (REJ-090).
- **Pass status.** Pass-with-note, pending the first rehearsal (R-RUN-001; R-RUN-002).

### Register and handover schema

This section is the schema of the single source table behind the register and the handover document (REJ-075). The
table is [`register/register.md`](register/register.md). It is amended by ID, and nothing else holds a copy of its
rows (ADR-069).

**Columns.** There are sixteen, in this order:

| # | Column | Allowed values and meaning |
|---|---|---|
| 1 | `ID` | `R-<PILLAR>-nnn`. Never renumbered or reused. A deleted row retires its ID. |
| 2 | `pillar` | `AUTH`, `SES`, `CSRF`, `LCK`, `RL`, `CRED`, `ADM`, `MFA`, `AUD`, `HDR`, `CFG`, `RUN`, `OBS`, `FE`, `BLD`, `DATA`; `OPS` for platform obligations with no application control; `STD` for standards defects, numbered in one sequence across all standards. |
| 3 | `kind` | `deviation` from a standard or the PRD; `residual` risk accepted; `n/a`; `not-built`; `fidelity` (the harness or environment cannot exercise it); `defect` in a standard; `obligation` on a deployer or operator; `trigger` that reopens a decision; `note` the register must carry. |
| 4 | `requirement` | The clause graded against. A PRD deviation names the PRD story and acceptance criterion. |
| 5 | `level` | ASVS level for ASVS requirements. Otherwise the modality (SHALL, SHOULD, MAY, MUST, Enforced Constraint, PRD) or the IM8 severity (LR column, per the declaration above). One entry per requirement, in order. |
| 6 | `verdict` | `pass`, `pass-with-note`, `conditional-pass`, `partial`, `fail`, `n/a`, `satisfied-by-procedure`, or `—` where nothing is graded. |
| 7 | `subject` | The row's one-line title. |
| 8 | `decision` | What was decided. The compliance rendering shows it as the deviation, the operational rendering as what to do. |
| 9 | `rationale` | The row's own reasoning, citing primary sources: the standard and section, the ASVS requirement with its level, the NIST section, the RFC, the CVE, or vendor documentation with its version. |
| 10 | `residual` | The risk that remains. |
| 11 | `responsibility` | Who acts: `application`, `deployer`, `shared` or `none`. |
| 12 | `status` | The application's own enforcement, and nothing else: `enforced`, `enforced-elsewhere-cited`, `asserted-by-test` (with a `T-…` in `refs`), `procedural` or `unmitigated`. |
| 13 | `priority` | `blocking`, `required` or `recommended`. |
| 14 | `sequence` | The operational rendering's position (see below). |
| 15 | `acceptance` | How a reader proves the row holds, or the sentinel "none possible — attests a named role is filled and its holder reachable." |
| 16 | `refs` | `ADR-…`, `REJ-…` and `T-…` IDs. Where a decision has both a row and an ADR, each names the other. |

**Rules on the columns:**

- **`priority` is empty exactly when `responsibility` is `none`.** Under the deployment assumption (no real
  deployment of this build exists), an empty priority on a `none` row is the schema, not a gap (R-OPS-005).
- **Roles, not people.** A role nobody fills is a `deployer` row with `priority` `blocking` and an unmet acceptance
  check, never a fifth `responsibility` value (REJ-077). This is a *declared vacancy*.
- **`sequence` is required on every `deployer` or `shared` row.** Its values are the steps 1–7 and `L`:
  1. keys and generation commands;
  2. trusted-proxy configuration;
  3. time synchronisation;
  4. headers and origins;
  5. mail transport;
  6. log forwarding and retention;
  7. recovery rehearsal.

  `L` marks a limitation: what nobody can currently prove. On the first real deployment a limitation row's
  `responsibility` becomes `deployer`, it gains a `priority`, and the table is regraded. The order follows the
  application's own failure points, not severity (REJ-076).
- **One level per requirement.** `level` carries one entry per clause in `requirement`, in the same order.
- **ASVS citation hygiene.** No row translates an ASVS requirement into SHALL, and no row maps an ASVS ID onto an
  SP 800-63B-4 section (R-STD-035).

**Renderings.** Both are generated from the table and never written by hand:

- **Compliance rendering (the deferral register):** every row, as `ID`, `requirement`, `level`, `verdict`, `kind`,
  `decision` (the deviation) and `residual`, plus an anchor to the row's entry in the operational rendering when
  `sequence` is set.
- **Operational rendering (the handover document):** rows with a `sequence`, grouped by step 1–7 then `L`, as `ID`,
  `decision` (what to do), `responsibility`, `priority`, `status` and `acceptance`. Severity appears only as
  `priority`. The grouping is deployment order.
- **The header.** Both renderings open with the deployment-assumption header (R-OPS-005).
- **The drift gate.** A single Failsafe test regenerates both renderings in `verify` and fails when a committed
  rendering differs (T-BLD-006; R-BLD-006). Editing a rendering by hand fails the build.

## Testing Decisions

- **The canonical table is [`test-plan/test-plan.md`](test-plan/test-plan.md).** Each row names one control, the
  assertion that proves it, and its clause. This spec does not copy its rows. Amend the table by ID.
- **What a good test is.** It asserts external behaviour at the highest seam that can observe the control: an HTTP
  status and envelope, a persisted state, an audit row, or a captured side effect. Most controls here are negative
  assertions, counts and absences, so the table records each row's polarity. A test that only proves a method was
  called is not evidence.
- **The legend, in summary:**
  - **Levels:**

    | Level | Meaning |
    |---|---|
    | U | unit |
    | C | full context, MockMvc |
    | P | full context on a real port |
    | R | runner process |
    | B | build artefact |
    | A | ArchUnit |
    | F | Vitest |
    | E | Playwright, on Chromium and Firefox |

  - **Contexts:** the fixed Spring test contexts are `ctx-default`, `ctx-port`, `ctx-locktimeout`, `ctx-lockhold` and `ctx-nondev`.
    `restart` is the harness of one or two sequential boots on one H2 file, and `runner` is the runner process.
    Levels with no Spring context read `none`, `archunit`, `build`, `vitest` or `playwright`.
  - **Isolation:** `keyed`, `delta`, `own-DB`, `merged` or `none`.
  - **`@Proves("T-…")`:** every Java test cites its row this way. Vitest and Playwright tests put the ID in the test
    name. `verify` fails when a row has no test, when a test cites an unknown ID, or when the table is missing,
    unreadable, empty or has the wrong header (ADR-068; T-BLD-007; T-BLD-008).
- **The seams:**
  - **Primary: the HTTP API over the full application context** (level C). No slices: `@WebMvcTest` and every other
    slice is banned for security-control tests, and T-ARCH-006 enforces the ban (ADR-065). A real port (level P) is
    used only where MockMvc bypasses Tomcat: `RemoteIpValve`, raw `Set-Cookie`, duplicate cookies, header budgets
    and Tomcat meters.
  - **Inside the context, as production design:**
    - one `Clock` bean, forward-only and mutable in tests, with a Caffeine `Ticker` and a Bucket4j `TimeMeter`
      built from it. Ambient time is banned in main code, and `Thread.sleep` is banned in tests (ADR-066;
      T-ARCH-001);
    - a capturing `EmailService` in place of the stub. There is no `test` profile (ADR-067);
    - audit and log output observed through appender capture and a suite-wide *canary secret* scan.
  - **Below the HTTP seam:** pure decision functions at level U, for the guard, token consume, the TOTP window and
    replay check, the lockout ladder, `SourceKeyResolver`, the `PasswordService` policy and emitter key validation.
  - **Process seams:** the `restart` harness (bootstrap, reconciliation sweep, persistence across boots) and the
    `runner` process.
  - **Structural:** ArchUnit (level A) and build-artefact checks (level B).
  - **Frontend:** Vitest (level F) at component and hook level, against MSW fixtures. Every error fixture validates
    against the JSON Schema generated from the backend enum (T-AUTH-012), which is the one contract seam between the
    SPA and the API. Playwright (level E) is narrow, about eight tests (REJ-057).
- **Harness settings.**
  - Every context runs on a temporary H2 file. Shared contexts use BCrypt cost 4 and the cleanup cron set to `-`.
    `ctx-nondev` asserts the production values without refreshing a production context (ADR-067).
  - The suite runs sequentially (REJ-058), with OTLP export disabled (REJ-068).
  - Failsafe and Surefire are pinned at exactly 3.6.0 (ADR-069; R-BLD-005).
- **Prior art.** There is none in this repository, which holds no code yet. The test plan's rows, with their clauses
  and rationale sentences, are the reference for each test's shape.

## Mandatory gates

These gates are mandatory. A gate that fails blocks the phase it belongs to.

### After `/to-spec`, before `/to-tickets`

1. `spec-compliance` on this spec, against IM8 and ARC.
2. `spec-standards-check` on this spec, against the applicable appfw standards, including the Standalone User
   Access Control Application Standard.
3. `pragmatic-reviewer` over both sets of findings.

### Build phase, on every item

1. `dependency-vuln-scan`, plus the Maven-bound OWASP Dependency-Check with its CVSS failure threshold, in `verify`
   (R-BLD-007; R-BLD-010).
2. `semgrep` on all changed code.
3. The five Spring review skills: `spring-security-review`, `spring-web-review`, `spring-data-review`,
   `spring-logging-review` and `spring-test-review`.
4. `react-review`.
5. `im8-review` against the implementation. It expects Boot 3.4 / Security 6.4 configuration spellings, so a Boot 4.1
   control that it reports absent is checked by hand before it counts as a FAIL (R-CFG-001; R-CFG-007; R-MFA-005).
6. `build-check` and `code-reviewer`.
7. `mutation-testing`, run in a `-Pmutation` profile with `--threshold=85`. The skill's default is 70. The scope is
   the security-decision classes:
   - the lockout counter and ladder;
   - `AdminActionGuard`;
   - `SourceKeyResolver`;
   - the `PasswordService` policy;
   - token hashing and consume;
   - the TOTP window and replay check;
   - emitter key validation.

   The guard, token consume and TOTP window logic are pure decision functions, so PIT boots no context per mutant.
   PIT is not evidence for the pessimistic lock, because no standard mutator swaps a locking query for a non-locking
   one.
8. The `@Proves` traceability gate (ADR-068) and the register drift gate (ADR-069; T-BLD-006). Both run under
   Failsafe 3.6.0. Releasing with `-DskipITs` or `-Dmaven.test.skip` bypasses both, which is forbidden (R-BLD-009).
9. A recorded manual keyboard and screen-reader pass against WCAG 2.2 AA on every route, before acceptance
   (R-FE-005).
10. `browser-test` against the PRD's acceptance criteria, as the final acceptance gate. The Playwright suite (level
    E) does not replace it.

## Out of Scope

- **Application code in this document.** The spec plans; `/do-work` builds.
- **Testcontainers, Docker-based tests, and any real PostgreSQL or MySQL runtime.** H2 serves both dev and test. H2
  in PostgreSQL-compatibility mode does not prove PostgreSQL portability (R-DATA-012; R-DATA-014).
- **Account-hygiene scheduled jobs:** 90-day inactivity disable, 180-day role revocation and ShedLock serialisation.
  This is recorded as an IM8 **ac-3 FAIL** and accepted as a residual, because no PRD story asks for it (R-ADM-011).
- **MFA recovery and backup codes.** Two enrolled admins and the runner compensate (ADR-024).
- **MFA for regular users**, including opt-in enrolment and the `MFAPrompt` (ADR-023).
- **PIN and OTP factors.** TOTP only.
- **Remember-me and persistent login**, including a cookie `Max-Age` (REJ-008).
- **Owner notification mechanics:** channels, retry and delivery failure for every account-security event. Nothing
  can be delivered without a mail transport. The SHALL failures this leaves are R-CRED-022, R-CRED-024 and R-MFA-019,
  and R-CRED-026 is the trigger that reopens them when a transport enters scope.
- **Real SMTP or email delivery.** Only the `dev` logging stub exists (PRD).
- **CI/CD, containerisation and hosting infrastructure** (PRD). The gates live in Maven `verify` instead.
- **A JWT implementation.** It exists only as the PRD's appendix (ADR-029).
- **Local HTTPS setup.** An accepted gap, per the PRD.
- **SSO, OAuth2 and OIDC.** A different standard (R-ADM-003).
- **Batch password reset** (R-ADM-014; REJ-026).
- **The RBAC privilege layer, role hierarchy and startup role synchroniser.** There are no dev seed accounts of any
  kind (ADR-042).
- **A scheduled account-access review pipeline**, the compare-and-revoke half of IM8 ac-4. The authorization matrix
  is the declared baseline (R-ADM-001).
- **Client error ingestion** (R-OBS-009) and **a general request log** (R-AUD-025).

## Further Notes

- **Ten register rows state one level for several requirements**, against the one-level-per-requirement rule:
  R-ADM-008, R-AUTH-002, R-CFG-005, R-CRED-020, R-CRED-021, R-CRED-024, R-CRED-025, R-MFA-016, R-SES-007 and
  R-STD-049. Each carries one shared modality, so nothing is misgraded. The first build work item that touches the
  register expands their `level` columns, one entry per requirement.
- **Closed item: the code for the two-admin invariant's 409 is `TWO_ADMIN_INVARIANT`.** REJ-050 fixes the status and
  R-ADM-008 the behaviour; the first admin-module work item (enable and disable) named the code. It is a new enum
  member, because each code pairs with exactly one status and `FACTOR_ALREADY_ENROLLED` means something else. The
  generated JSON Schema (T-AUTH-011; T-AUTH-012) and `docs/api/error-contract.md` pick it up from the enum.
- **Handover items the build must honour before go-live:**
  - a second enrolled admin (R-ADM-017);
  - a named runner operator (R-RUN-003) and a green recovery rehearsal (R-RUN-002);
  - a TLS terminator (R-OPS-003);
  - one registrable domain for the SPA and the API (R-OPS-002);
  - the static host's header set (R-HDR-005).

  The full list is the operational rendering.
- **Reopening triggers** are register rows of kind `trigger`, and the ADRs carry their own. The ones most likely to
  fire during the build:
  - any inline script in the production document (R-HDR-003);
  - an executor or `@Async` method (R-AUD-024);
  - a Surefire or Failsafe version change (R-BLD-005);
  - a `git.properties` or build-info plugin (R-BLD-015);
  - a real mail transport (R-CRED-026).
