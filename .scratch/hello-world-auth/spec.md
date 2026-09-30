Status: ready-for-agent

# Spec: Username/password sign-in with admin-managed Accounts

## Problem Statement

The demo app has no way for a person to sign in with a username and password. The template only knows SSO-style login, and its local stand-in accepts `{noop}password` for any username and auto-creates the Account. Nobody can be given access deliberately, admins cannot manage who has access, and brute-force guessing is not slowed down. The demo needs a production-grade baseline for password sign-in that builds on the template's existing security and does not weaken it.

## Solution

Accounts are created and managed only by admins. An admin creates an Account (or resets a forgotten password) and receives a one-time **Temporary Password** to hand to the person. The person signs in with it, is forced to choose their own password (typed twice), and then reaches the protected greeting. Repeated failed sign-ins slow down progressively and are throttled per IP, without ever hard-locking a victim out. Sessions are server-side, stored in the database, and are ended everywhere when a password changes, is reset, or the Account is disabled or deleted. There is no self-registration, no email, and no CORS: the app stays same-origin.

## User Stories

1. As an Account holder, I want to sign in with my username and password, so that I can reach the protected app.
2. As an Account holder, I want a wrong username and a wrong password to look identical, so that nobody can discover which usernames exist.
3. As an Account holder with a disabled Account, I want the same generic failure as any bad sign-in, so that my Account status is not disclosed.
4. As an Account holder, I want my session to be a secure HttpOnly cookie, so that scripts in the page cannot steal it.
5. As an Account holder, I want a fresh session id issued at sign-in, so that a pre-planted session id cannot be reused against me.
6. As an Account holder, I want sign-in to be CSRF-protected, so that another site cannot sign me in as someone else.
7. As an Account holder, I want to log out and have my session end on the server, so that a captured cookie is useless afterwards.
8. As an Account holder, I want a replayed pre-logout cookie to be rejected as unauthenticated, so that logout is real.
9. As a signed-in Account holder, I want to see "Hello, <username>", so that I can confirm sign-in worked.
10. As an unauthenticated caller, I want the greeting endpoint to return 401, so that protected content stays protected.
11. As an Account holder given a Temporary Password, I want to be forced to set my own password before doing anything else, so that only I know it.
12. As an Account holder in the forced-change state, I want every other endpoint except change-password and logout to be refused with a clear "password change required" reason, so that I know what to do.
13. As an Account holder, I want to type my new password twice and have a mismatch rejected, so that a typo does not lock me out.
14. As an Account holder, I want the mismatch check enforced by the server, so that it does not depend on the form.
15. As an Account holder, I want a password strength policy (at least 12 characters), so that weak passwords are refused.
16. As an Account holder, I want my other sessions ended when I change my password, so that a stolen session does not survive the change.
17. As an Account holder, I want an unused Temporary Password to stop working after a set time, so that a leaked hand-over slip expires.
18. As an Account holder, I want the plaintext of my password never logged or stored, so that logs and the database do not expose it.
19. As a security-conscious operator, I want repeated failures on one Account to trigger a growing delay, so that guessing gets impractical.
20. As a security-conscious operator, I want the delay to double from a base up to a cap, all tunable by property, so that I can adjust without a code change.
21. As a security-conscious operator, I want no permanent lockout, so that an attacker cannot lock a victim out on purpose.
22. As an Account holder, I want a correct password submitted after the delay to succeed and reset the counter, so that one bad day does not haunt me.
23. As an Account holder, I want a correct password submitted during the delay to still be rejected, so that the delay cannot be bypassed by luck.
24. As a security-conscious operator, I want repeated failures from one IP across many usernames to be throttled independently, so that spraying is blunted.
25. As an operator, I want the IP counter in memory and the forwarded-for header trusted only behind a configured proxy, so that the throttle cannot be spoofed.
26. As an admin, I want to create an Account with a unique username and a Role, so that I can give someone access.
27. As an admin, I want the new Account to come with a generated Temporary Password shown to me once, so that I can hand it over without ever choosing it.
28. As an admin, I want a duplicate username refused with a conflict, so that identities stay unique.
29. As an admin, I want to reset another Account's password, so that a forgetful person can get back in without email.
30. As an admin, I want a reset to end that Account's sessions and clear its delay state, so that recovery is immediate and clean.
31. As an admin, I want to be refused when I try to reset my own password this way, so that I use the normal change-password flow.
32. As an admin, I want to list all Accounts with username, Role, enabled status and created date, so that I can review who has access.
33. As an admin, I want password hashes, Temporary Passwords and delay fields never shown in any listing, so that secrets stay secret.
34. As an admin, I want to enable or disable another Account, so that I can suspend access without deleting data.
35. As an admin, I want a disabled Account's sessions ended and further sign-ins refused, so that suspension takes effect at once.
36. As an admin, I want to change another Account's Role, so that I can grant or revoke privileges.
37. As an admin, I want to delete another Account, so that removed people cannot come back.
38. As an admin, I want to be refused when I disable, demote or delete my own Account, so that I cannot lock everyone out by accident.
39. As a non-admin, I want every admin endpoint to answer 403, so that privileges cannot be borrowed.
40. As an operator deploying for the first time, I want an initial admin seeded from configuration, so that I can get into the admin area without database edits.
41. As an operator, I want cloud startup to fail if no admin password is configured and no admin exists, so that a default password can never ship.
42. As an operator, I want a restart to never create a duplicate admin, so that seeding is safe to repeat.
43. As an operator, I want admin credentials in cloud to come from the secrets manager and local ones from a local-only property, so that no credentials are committed.
44. As an operator, I want structured audit log lines (actor and target) for sign-in success and failure, delay triggered, Account created, password reset, password changed, and Role change, enable, disable and delete, so that security events are traceable.
45. As an operator, I want passwords and Temporary Passwords never in any log line, so that logs are safe to ship.
46. As a developer, I want the local `{noop}password` sign-in and the auto-creation of unknown usernames removed, so that the demo cannot be entered with a guessable password.
47. As a developer, I want the same login and session behavior in every profile, so that local testing reflects production.
48. As a developer, I want schema changes delivered as changelogs that run on H2 and MSSQL, so that cloud profiles get a schema.
49. As a frontend user, I want a sign-in page that shows the generic failure, and a delay message when the IP throttle answers 429, so that I know why I was refused. (Account backoff deliberately returns the same generic 401 for enumeration resistance, so only the IP throttle produces a delay message.)
50. As a frontend user, I want a change-password page with two password fields, so that I can complete the forced change.
51. As an admin using the SPA, I want pages to create Accounts, reset passwords and manage Accounts, so that I do not need raw API calls.

## Implementation Decisions

- **Model**: extend the existing Account record with a password hash, enabled flag, failed-attempt counter, delay expiry, must-change-password flag and Temporary Password expiry. Email is unused. Roles stay `USER`, `USER_MANAGER`, `ADMIN`.
- **Authentication**: replace the local stand-in and the auto-provisioning listener with real credential checks against stored hashes. Keep the template's delegating password encoder (BCrypt by default) so hashes stay upgradeable. Accounts with the SSO fields are left untouched; SSO cleanup is out of scope.
- **Sign-in API**: a JSON sign-in endpoint replaces the form-login redirect flow. It returns JSON errors, one generic failure for unknown username, wrong password, disabled Account and expired Temporary Password. The template's CSRF exemption for sign-in and sign-out is removed.
- **Backoff**: after a configurable threshold (default 3) of consecutive failures the delay expiry is set to now plus a delay that doubles per failure from a base (default 1 s) to a cap (default 15 min). Correct password after expiry succeeds and resets. Reset by an admin clears it. Threshold, base and cap are properties (they need tuning without a code change).
- **IP throttle**: in-memory per-IP counter independent of Account state. `X-Forwarded-For` honored only when the forward-headers strategy is configured for a known proxy.
- **Sessions**: Spring Session JDBC in every profile, replacing the template's Redis sessions and plain in-memory Tomcat sessions (see ADR-DEMO-BE-0001). A principal-indexed store is what makes "end all sessions of an Account" possible. Session fixation protection stays on.
- **Same-origin**: no CORS, no `SameSite` relaxation, no absolute API URLs in the SPA (see ADR-DEMO-0002). Vite proxy also covers the admin API prefix.
- **Forced password change**: while must-change-password is set, a filter refuses everything except change-password and logout with a "password change required" problem detail. Change-password takes `newPassword` and `confirmPassword`, refuses mismatches and weak passwords (length ≥ 12), clears the flags, and ends the holder's other sessions.
- **Temporary Password**: server-generated with a secure random source, returned once in the admin response, stored only as a hash, expiry default 24 h (a property). Same lockout and throttle rules as any password.
- **Admin API**: under the template's separate admin chain and prefix (`/admin/api/**`, requires `ADMIN`), with its own OpenAPI spec and generated client: create, list, reset password, enable/disable, change Role, delete. Self-targeting reset, disable, demote and delete are refused. Ending sessions accompanies reset, disable, delete.
- **Greeting**: `GET /api/hello` on the authenticated API chain returns the greeting.
- **Admin seed**: on startup, if no `ADMIN` exists, seed from configuration. Cloud values from the secrets manager, local from a `local`-only property. Cloud startup fails without a password. No default password.
- **Migrations**: no migration tool in the repo (Liquibase removed). `local`/`test` build the schema from entities and Spring's embedded session-table setup; the deployed MSSQL schema, including the new columns and session tables, is managed outside this repo. The persistence target is MSSQL, not Postgres or MySQL.
- **Audit**: structured log lines with actor and target for the events listed in the stories; passwords never logged.
- **Frontend**: sign-in page, forced change-password page, and admin pages, using the project's form standard and the query-wrapper conventions. Generated client is regenerated per the documented steps.
- **Docs**: the PRD, CONTEXT.md glossary (Account, Role, Temporary Password) and the two ADRs already reflect these decisions.

## Testing Decisions

- **What makes a good test**: assert external behavior only, through the HTTP boundary (status, body, cookies, headers, persisted state visible through the API). Never assert on internal classes, method calls or private state.
- **Seam**: one seam, the backend HTTP API exercised by a full-context integration test with real security filter chains, CSRF, the H2 database and JDBC sessions. Time-dependent behavior (delay expiry, Temporary Password expiry) is driven through a controllable clock bean rather than sleeping. No unit-level seams are added.
- **Covered at the seam**: sign-in success, wrong password, unknown username (identical generic error), disabled Account, CSRF required; delay grows and caps, recovery after delay, IP throttle independent of Account, admin reset clears delay; logout replay rejected; forced change (403 elsewhere, mismatch refused, expired Temporary Password refused, other sessions ended); every admin endpoint 403 for non-admins, self-action refusals, Temporary Password returned once and never listed; seed runs once and cloud startup fails without a password.
- **Frontend**: page-level tests of the sign-in and change-password forms with the API mocked, in the existing test setup.
- **Prior art**: the existing admin-chain security integration test and the current-user profile controller integration test.

## Out of Scope

- Self-registration.
- Email of any kind, including email-based reset.
- JWT (design stays in the PRD appendix only).
- Multi-factor authentication.
- CORS and separate-origin deployment.
- Containerization, CI/CD, hosting infrastructure, local HTTPS.
- Per-resource authorization beyond Role checks on admin endpoints.
- Removing the template's SSO/OIDC leftovers and stale references; tracked separately.

## Further Notes

- The amended PRD at `prd/assessment-prd.md` is the source of truth for behavior; this spec restates it for implementation.
- The template has stale references (missing changelogs, missing OIDC classes, profile groups naming absent property files, a stale profiles doc). Some existing tests or startup paths may fail for reasons unrelated to this work; check before assuming a regression.
- Backend ADR numbering follows the ADR naming convention: next backend ADR is the highest existing plus one.
