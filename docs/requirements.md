# PRD acceptance checklist

Backend integration tests make real HTTP requests to a random-port Spring server
with the real security filter chain, H2, Flyway, BCrypt and JDBC Spring Session.
Only the outbound EmailService is replaced when reset tests need to capture links.
No security test substitutes an authenticated principal or bypasses CSRF.

| PRD item | Implementation | Verification |
| --- | --- | --- |
| 1 Registration | Register form, AuthService, normalized unique users, BCrypt, USER/enabled defaults | `registrationValidatesPolicyUniquenessAndStoresOnlyBcrypt` |
| 2 Login | JSON login, generic failure, context saved, failures cleared | `registersLogsInAndRejectsReplayedSessionAfterLogout`, `wrongUnknownDisabledAndLockedCredentialsHaveIdenticalErrors` |
| 3 Lockout/throttle | Account row lock; five account failures; independent three-failure IP limit | `distributedFailuresLockAccountAndCooldownResetsCounter`, `ipThrottleIsIndependentAndStopsOneSourceLockingAnAccount`, `LoginThrottleTest` |
| 4 Logout | Spring Security logout invalidates JDBC session and clears cookie | Captured pre-logout cookie is replayed and rejected in `registersLogsInAndRejectsReplayedSessionAfterLogout` |
| 5 Greeting | Protected `/api/hello` returns JSON string `Hello, <username>` | Exact greeting and anonymous 401 in authentication integration test; rendered UI flow test |
| 6 Reset request | Generic response, hashed random token, 20-minute expiry, EmailService stub | `resetRequestIsGenericAndOnlyTokenHashPersists` |
| 7 Reset confirmation | Locks, policy, consume tokens, replace hash, revoke all sessions | `resetIsSingleUseRevokesAllSessionsAndChangesPassword`, `expiredInvalidAndWeakPasswordTokensDoNotChangePassword`, `concurrentRedemptionSucceedsExactlyOnce` |
| 8 Admin list | Safe DTOs, ADMIN matcher, admin table | `adminListsOnlySafeFieldsAndCannotModifySelf`, `userCannotAccessAnyAdminOperation` |
| 9 Enable/disable | Status endpoint, immediate session revocation, self guard | `adminMutationsRevokeSessionsAndRejectInvalidRoles`, admin self-guard test, UI admin status test |
| 10 Roles | USER/ADMIN validation, immediate revocation, self guard | Same admin tests, including stale demoted-admin session rejection |
| 11 Delete | Account removal, cascading tokens, session revocation, self guard | Same admin tests, database deletion assertion |
| 12 Bootstrap | Configured initial ADMIN, no defaults, skip if any ADMIN exists | `loginRotatesSessionAndBootstrapAdminIsAvailable`, `bootstrapIsIdempotentAndExpiredSessionsAreRejected` |
| CSRF/CORS | Every mutation protected; exact credentialed origin allow-list | `csrfIsRequiredForEveryMutationAndCorsIsAnExactAllowList`, missing-CSRF admin mutation test |
| Cookie/session | HttpOnly, Secure default, SameSite, fixation/expiry protection | `ProductionCookieIntegrationTest`, login rotation, expiry and replay tests |
| Audit/password safety | Structured sanitized actor/target events; safe DTOs and credential redaction | Source review, supplementary-Unicode login regression, hash-only database assertion |
| JWT alternative | Documented only | `docs/security.md` and original PRD appendix |

The frontend suite exercises login/logout, generic failures, reset request and
confirmation, incomplete links, registration confirmation mismatch, admin status
changes and self-action visibility. API transport tests cover credentials, fresh
CSRF, structured errors, server-error redaction and empty logout responses.

Run commands and environment prerequisites are in the root README. The final
verification record, including any environmental limits, is in `docs/handoff.md`.
