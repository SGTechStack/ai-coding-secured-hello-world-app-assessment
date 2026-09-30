# Acceptance checklist (Phase 6)

Walk the stories in readiness order; each row names the automated test that covers the
criterion and the manual step to eyeball it in the browser. Tick the boxes as you verify.
"Defect" = criterion not met; anything extra you want is a new story.

Automated tests live in `backend/src/test/java/...` (run `.\mvnw.cmd test`) and
`frontend/src/**/*.test.ts(x)` (run `npm test`).

## Story 1 — Register

- [ ] Unique username/email + 12-char password → account with role USER, enabled, BCrypt hash — `RegistrationIntegrationTest.registersUser` — manual: Register page, then check the admin list.
- [ ] Duplicate username/email → 409 with field error, no account — `rejectsDuplicateUsername`, `rejectsDuplicateEmail` — manual: register the same name twice.
- [ ] Weak password → 400, no account — `rejectsWeakPassword`, `PasswordPolicyTest` — manual: 8-char password.
- [ ] Plaintext password never logged/stored — `RegistrationServiceTest.requestToStringNeverRevealsPassword`, hash assertion in `registersUser`; `AuditLogger` only receives usernames.

## Story 2 — Login

- [ ] Correct credentials → session cookie, `failed_login_attempts` = 0 — `LoginIntegrationTest.loginSucceeds`.
- [ ] Wrong credentials → generic 401, counter increments; unknown user gets the same body — `genericErrorForWrongPasswordAndUnknownUser`.
- [ ] Locked account + correct password → still 401 — `lockedAccountRejected`.

## Story 3 — Lockout and throttling

- [ ] N failures lock the account (`locked_until` set) — `LockoutIntegrationTest.locksAfterThreshold`, `AccountLockoutServiceTest`.
- [ ] After cooldown, correct password logs in and resets the counter — `unlocksAfterCooldown`.
- [ ] Failures from one IP across usernames → 429 independent of account lockout — `throttlesByIp`, `IpLoginThrottleTest`.

## Story 4 — Logout

- [ ] Logout invalidates the session and clears the cookie — `LogoutIntegrationTest.logoutEndsSession`.
- [ ] Replayed pre-logout cookie → 401 — same test.

## Story 5 — Greeting

- [ ] Authenticated `GET /api/hello` → `Hello, <username>` — `HelloIntegrationTest.greetsAuthenticatedUser` — manual: home page after login.
- [ ] No/invalid session → 401 — `anonymousIsUnauthorized`, `bogusCookieIsUnauthorized`.

## Story 6 — Request reset

- [ ] Generic response whether or not the email exists — `PasswordResetIntegrationTest.requestIsEnumerationSafe`.
- [ ] Known email → hashed single-use token with short expiry, `EmailService.sendPasswordResetEmail` called — same test + `PasswordResetServiceTest.knownEmailStoresHashNotTokenAndEmailsTheLink` — manual: link appears in backend console.

## Story 7 — Confirm reset

- [ ] Valid token → password updated, token marked used, all sessions invalidated — `confirmResetsPasswordAndInvalidatesSessions`.
- [ ] Expired token rejected — `expiredTokenRejected`, `PasswordResetServiceTest.expiredTokenIsRejectedWithoutChange`.
- [ ] Reused token rejected — `confirmResetsPasswordAndInvalidatesSessions` (second confirm), `usedTokenIsRejected`.

## Story 8 — Admin list

- [ ] Admin sees username, email, role, enabled, created-at; no hashes — `AdminUserIntegrationTest.adminListsUsers`.
- [ ] USER → 403 on every `/api/admin/**` endpoint — `userIsForbidden`; frontend `guards.test.tsx` covers the route guard.

## Story 9 — Enable/disable

- [ ] Other user toggled; disabled user cannot log in — `disableUser`.
- [ ] Self toggle rejected — `disableUser`, `AdminUserServiceTest.adminCannotDisableThemselves`; UI disables the buttons (`UserTable.test.tsx`).

## Story 10 — Role change

- [ ] Other user's role updated — `changeRole`.
- [ ] Self demotion rejected — `changeRole`, `adminCannotDemoteThemselves`.

## Story 11 — Delete

- [ ] Other user removed — `deleteUser`.
- [ ] Self delete rejected — `deleteUser`, `adminCannotDeleteThemselves`.

## Story 12 — Admin bootstrap

- [ ] No ADMIN → seeded from `app.admin.*` with BCrypt — `AdminBootstrapIntegrationTest.seededAdminOnStartup`, `AdminBootstrapTest.seedsAdminWhenNoneExists`.
- [ ] ADMIN exists → no duplicate — `rerunIsIdempotent`, `skipsWhenAdminExists`.

## Non-functional

- [ ] CSRF enforced on register/login/logout/reset/admin — `RegistrationIntegrationTest.requiresCsrf`, `LogoutIntegrationTest.logoutRequiresCsrf`, `AdminUserIntegrationTest.mutationsRequireCsrf`; SPA retry logic `client.test.ts`.
- [ ] Cookie HttpOnly + SameSite, session id rotates on login — `LoginIntegrationTest.loginSucceeds`.
- [ ] Security headers and correlation id on API responses — `HelloIntegrationTest.greetsAuthenticatedUser`.
- [ ] Layering — `ArchitectureTest`.
