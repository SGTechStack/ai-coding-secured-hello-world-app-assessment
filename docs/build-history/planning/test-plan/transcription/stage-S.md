## Map
| std row | file:line | disposition | target | note |
|---|---|---|---|---|
| Role definitions loaded at startup | Std §5:423 | test | S32-04, S11-09, S11-01 | Loaded by the refresh-phase validator and checked against the roles table (11:155); matrix bound once at refresh. |
| Duplicate role definitions fail fast at startup | Std §5:424 | test | S11-09 | 11:957; 16:561. |
| Mutating role definitions via API returns 403 | Std §5:425 | test | S11-11, S11-10 | Explicit denyAll on /api/admin/roles/** (11:958); anonymous gets 401, USER and ADMIN 403 ACCESS_DENIED. |
| Role mapping change takes effect on next load | Std §5:426 | test | S11-12 | Restart is the only reload path (11:965). |
| Each HTTP method on a path independently authorized | Std §5:427 | test | S11-01, S11-08 | Matrix keyed on (role, method, path); factor matrix separates GET from mutations. |
| Role-protected endpoints: 200/201 authorized, 403 without role | Std §5:428 | new | STD-16, S11-08, S06-05 | S11-08 gives ADMIN success; STD-16 adds the USER 403 matrix and USER 200 on its own endpoints. |
| User management endpoints reject non-admins, including own account | Std §5:429 | new | STD-16, S11-14 | No canonical row enumerated every admin route for a USER, nor the own-uuid case. |
| Login gives new session id, kills prior session, differs per login | Std §5:433 | test | S05-05, S08-07 | Rotation on login (S05-05); maximumSessions(1) new-login-wins (S08-07). |
| Session persisted to DB for concurrent limits across restarts | Std §5:434 | new | STD-03, S05-10, S08-03 | S05-10 proves session survival across restart; STD-03 adds the cap across restart. |
| Idle timeout forces re-auth after 15 minutes | Std §5:435 | test | S08-22 | |
| Absolute timeout forces re-auth after 8 hours | Std §5:436 | test | S08-08 | Lifetime from AUTH_INSTANT. |
| Logout invalidates server session and clears cookies | Std §5:437 | new | STD-01, S14-24 | No canonical row asserted server-side logout invalidation or cookie expiry. |
| Logout on expired session handled gracefully by client | Std §5:438 | new | S14-24, STD-02 | Client half is S14-24; server half (403, logout not CSRF-exempt, 08:34) is STD-02. |
| Dedicated CSRF endpoint returns token in body | Std §5:442 | new | STD-04, S05-10 | |
| CSRF token endpoint not cacheable | Std §5:443 | test | S05-02 | 08:255. |
| CSRF required on POST/PUT/PATCH/DELETE, not GET/HEAD | Std §5:444 | new | STD-04 | Existing rows cover login POST and one unsafe request only. |
| Invalid or missing CSRF token rejected with 403 | Std §5:445 | test | S05-09, S06-05, S29-03 | |
| Previously redeemed CSRF tokens rejected | Std §5:446 | test | S08-05 | Reinterpreted as superseded-session rejection (08:394, 08 ADR 5); session-bound repository has no redemption concept. |
| Failed logins increment counter, lock at 5, success resets | Std §5:450 | new | S09-18, S09-06, S09-07, STD-05 | Reset-on-success had no canonical row. |
| Lockout persists restarts, auto-expires 20 min, admin unlock | Std §5:451 | new | STD-06, S16-55, S09-13, STD-07 | Persistence (09:488) and admin unlock (11:216) had no row; duration is ladder rung 1 (S09-13). |
| Login endpoint per-account rate limit, 429 with Retry-After | Std §5:452 | test | S09-02, S09-03, S16-04, S09-25 | Login half. Per-account is the submitted-username axis. |
| Password-reset request per-account rate limit | Std §5:452 | new | STD-08, S09-25 | Reset-request half: submitted-identifier axis in the handler (09:314); S09-25 is binding only. |
| Password-reset redemption per-account rate limit | Std §5:452 | register | owed-17 | Redemption is keyed per-IP only because the account is unknowable before the token lookup (09:737; 10:147, Standard's tenth defect). Needed row: Std §5:452 per-account limiting on /api/password-reset/confirm not met; per-IP budget instead; residual and rationale. |
| Rate limiter and sessions consistent under distributed deployment | Std §5:453 | register | 17:416 | 16:575 §6(a); single-instance by decision (09:486); clustering properties refused (S24-10). |
| Separate endpoints for admin issuance and user redemption | Std §5:457 | test | S08-13, S08-12, STD-09 | Admin issuance POST /api/admin/users/{uuid}/password-reset, redemption /api/password-reset/confirm (10:255). |
| Admin reset token random, single-use, plaintext returned once | Std §5:458 | new | STD-09, S10-07 | Single-use by S10-07; random and returned-once had no row. |
| Tokens expire 30 min, stored hashed, new token invalidates prior | Std §5:459 | new | STD-11, STD-10, STD-12 | None of the three facets had a canonical row. |
| Expired or already redeemed token fails with 400 | Std §5:460 | new | STD-11, S10-07, S10-06 | Redeemed half by S10-07; expired half STD-11. Code RESET_TOKEN_INVALID. |
| Successful reset invalidates all prior sessions | Std §5:461 | test | S08-12 | |
| Self-service change requires current password, rejects mismatch | Std §5:465 | new | STD-13 | |
| Self-service change succeeds and invalidates existing sessions | Std §5:466 | test | S08-10 | Deviation: the acting session survives with id rotated (08 ADR 2); all other sessions die. |
| New passwords meet strength and history in reset and change | Std §5:467 | test | S07-01, S07-03, S07-05, S10-08 | Single PasswordService seam serves both flows (07 §8, S07-02). |
| Create fails 400 when username exists (incl tombstones) or email used | Std §5:471 | test | S04-01, S12-13 | USER_EXISTS, admin create only (06:140). |
| Created user flagged for mandatory change, 30-day grace | Std §5:472 | register | owed-17 | Admin create issues an invite token; the user sets their own password at redemption, which clears forcePasswordChange, so creation sets no flag (10:446; 11:625). Needed row: Std §5:472 deviated, invite-token creation replaces a flagged generated password. |
| Generic update rejects username change or lock | Std §5:473 | register | owed-17 | N/A by construction: no generic update endpoint and no lock route (11:966; 16:563); USERNAME_CHANGE_NOT_ALLOWED removed (06:481). Needed row: Std §5:473 N/A with those citations. |
| User cannot delete own account, 403 | Std §5:474 | test | S04-07 | AdminActionGuard actor ≠ subject; the row asserts refusal and no change. |
| Deleted users retained as soft-delete tombstones | Std §5:475 | test | S04-10, S12-13, S12-11 | Tombstone row plus hard delete of users (11 §Soft delete). |
| Read-only self endpoint returns only caller's record | Std §5:479 | test | S11-14 | |
| Admins get full records; users cannot read others | Std §5:480 | test | S11-08, S11-14, S11-04 | Deviation: full record excludes password hash, TOTP secret and email HMAC (11 wire contract). |
| First-login users disabled if change not done in 30 days | Std §5:484 | new | STD-14 | Lazy refusal at login instead of a disablement job (11:337); S06-01 covers only wire uniformity of that state. |
| Re-enabled account flagged for mandatory password change | Std §5:485 | test | S11-13 | 11:967. |
| Accounts disabled after 90 days inactivity | Std §5:486 | register | 17:409 | §6(b) not built (16 Answer); also 17:23. |
| Roles revoked from long-inactive accounts after 180 days | Std §5:487 | register | 17:409 | §6(b); 17:23. |
| Batch job disablement invalidates active sessions | Std §5:488 | register | 17:409 | §6(b); no batch job built. |
| Scheduler runs serialized per job name | Std §5:489 | register | 17:409 | §6(b); 17:23 (ShedLock). |
| Over-length or invalid-format input rejected with 400 | Std §5:493 | new | STD-15 | |
| Request bodies over maximum size rejected with 400 | Std §5:494 | test | S16-61, S09-26, S09-27, S09-28, S09-29, S09-30 | 16 KiB cap, VALIDATION_FAILED (16:564; 06:483). |
| Batch reset requests over entry limits rejected with 400 | Std §5:494 | register | owed-17 | Batch half N/A: batch password reset declined (11:232), BATCH_TOO_LARGE removed (06:481), 16:564. Needed row: Std §5:494 batch clause N/A with those citations. |
| HSTS, nosniff, X-Frame-Options, CSP on all responses | Std §5:498 | test | S16-63, S16-64, S20-05 | HSTS on secure requests only (16:565; 05:754); real-TLS HSTS behaviour is register 17:415. |
| Session and CSRF cookies Secure, HttpOnly, SameSite=Lax | Std §5:499 | test | S20-03, S20-04, S08-06 | Deviation: SameSite=Strict (20 ADR 1); no CSRF cookie exists (08 ADR 5, 08:402); __Host- over real HTTPS is 17:415. |
| CORS preflight from allowed origin succeeds | Std §5:500 | test | S20-01, S16-79 | |
| Non-allowlisted origins rejected; no wildcard | Std §5:501 | test | S20-02 | |
| Logout returns Clear-Site-Data | Std §5:502 | new | STD-01, S08-04 | Header value asserted by STD-01; browser reach by S08-04. |
| Auth failures identical in status, body and timing | Std §5:506 | test | S06-01, S09-10, S16-04 | Timing verified by mechanism (matches() count); wall-clock uniformity is register 17:418 and 17:420. |
| Auth failures use machine-readable codes without specific reason | Std §5:507 | test | S06-01, S06-04, S06-03 | Single code AUTHENTICATION_FAILED; account locked is a log reason only (17:94). |
| Validation errors name the violated rule | Std §5:508 | test | S07-01, S07-03, S07-05, S06-08 | errors[{field, rule}] (06:209). |
| Error bodies conform to documented schema in all environments | Std §5:509 | test | S06-08, S06-07, S06-02 | RFC 9457 envelope deviation, 17:85 ADR 1. |
| INFO events for login, logout, password and admin operations | Std §5:513 | test | S13-02, S13-03, S13-04 | Levels fixed in the generated inventory (S13-04). |
| WARN events for failures, lockouts, rate limits, denials | Std §5:514 | test | S13-02, S13-03, S13-04 | |
| Logs never contain passwords, CSRF tokens, session IDs, reset tokens | Std §5:515 | test | S16-58, S13-05, S13-06, S13-07 | |
| Audit trail retained minimum 90 days | Std §5:516 | register | 17:410 | §6(b); S13-17 only asserts max-history 90 on the appender. Also 17:30. |
| Owners notified on password change, lock, reset completion | Std §5:517 | register | 17:411 | §6(b) not built, although 10:492 specifies the events. |
| Dependency-Check as CI gate blocking release | Std §5:521 | register | 17:412 | Bound to verify instead; 16 handover 16:634 area. |
| Use deterministic synthetic usernames and emails | Std §5:525 | new | STD-17 | Decided at 16:608. |
| Fixed or controllable clocks for inactivity and grace tests | Std §5:526 | test | S16-51, S16-52, S16-53 | Forward-only shared clock (16 §4). |
| Never log plaintext reset tokens | Std §5:527 | test | S13-06, S16-68, S16-58 | |
| Test tokens with production entropy and format | Std §5:528 | new | STD-18 | Decided at 16:608. |
| Login success | PRD:155 | test | S05-05 | |
| Login wrong password gives generic error | PRD:155 | test | S06-01, S06-04 | |
| Unknown username gives identical generic error | PRD:155 | test | S06-01, S09-10 | |
| Login on locked account refused | PRD:155 | new | PRD-01 | S06-01 does not pin that the locked case submits the correct password. |
| N failed attempts trigger lockout | PRD:156 | test | S09-18, S09-06, S16-74 | Threshold 5; duration 20 min not 15 (PRD deviation, 17:41). |
| Successful login after cooldown resets counter | PRD:156 | new | S16-55, STD-05 | |
| IP throttling independent of account lockout | PRD:156 | test | S09-01, S09-14 | S09-01 asserts the per-IP 429 leaves failed_login_attempts unchanged. |
| Reused session cookie rejected after logout | PRD:157 | new | STD-01 | |
| Reset token single-use | PRD:158 | test | S10-07 | |
| Reset token expiry | PRD:158 | new | STD-11 | |
| Reset invalidates existing sessions | PRD:158 | test | S08-12 | |
| Admin cannot disable, delete or demote own account | PRD:159 | test | S04-07 | |
| USER calling any /api/admin/** gets 403 | PRD:160 | new | STD-16, S06-05 | |
| PIN verify success | MFA §5:388 | register | owed-17 | PIN factor not built: TOTP-only (22:97), sanctioned by MFA §4.1 (22:633). Needed row: MFA_Core §5.1 PIN tests (388–392) N/A, PIN factor not built. |
| PIN verify failure | MFA §5:389 | register | owed-17 | Same row as MFA §5:388. |
| PIN verify missing header | MFA §5:390 | register | owed-17 | Same row as MFA §5:388. |
| PIN format validation | MFA §5:391 | register | owed-17 | Same row as MFA §5:388. |
| PIN setup guard | MFA §5:392 | register | owed-17 | Same row as MFA §5:388. |
| TOTP computation with fixed secret and timestamp | MFA §5:393 | test | S22-11, S22-12 | |
| TOTP ±1 windows accepted, ±2 rejected | MFA §5:394 | new | MFA-01, S22-13 | After a first success the effective window is +1 only (22:395), asserted by S22-13. |
| TOTP period boundary accepts previous window | MFA §5:395 | new | MFA-02 | |
| TOTP replay rejected, lastUsedCounter updated | MFA §5:396 | test | S22-13 | |
| TOTP provisioning: encryption, QR bytes, failure error | MFA §5:397 | new | MFA-04, MFA-07, S12-07 | |
| Full request flow with principal and X-PIN, X-TOTP headers | MFA §5:401 | test | S16-81, S11-08 | Deviation: code posted to /api/mfa/totp/verification and held as a session factor, not per-request headers (22:646, ADR owed). |
| Encrypt/decrypt via deterministic passthrough doubles | MFA §5:402 | new | MFA-04 | Real AES-GCM under a fixed test key replaces passthrough doubles. |
| Deterministic fixed-byte TOTP secrets | MFA §5:406 | test | S22-11, S22-12 | |
| Fixed timestamp for counter and TTL boundaries | MFA §5:407 | test | S22-13, S16-51, MFA-02 | |
| No real PII in fixtures | MFA §5:408 | new | STD-17 | |
| Stub or fixed-key encryption in CI | MFA §5:409 | new | MFA-04 | Fixed test key supplied outside committed config (S24-01). |
| Domain exceptions mapped to correct HTTP status | MFA §5:415 | new | MFA-05, S14-05, S22-13 | |
| Audit events with required fields on success and failure | MFA §5:416 | test | S13-02, S13-08, S19-01 | Event set per 23:872 table; catalogue test covers every member. |
| Brute-force lockout and backoff enforced | MFA §5:417 | new | MFA-03, MFA-06, S09-25 | Escalating backoff declined (23:547). |
| Log output structured and parsable | LOG §5:346 | test | S13-01 | |
| Required fields present in all log events | LOG §5:347 | new | LOG-01 | S13-02 covers audit rows only. |
| Timestamps consistent with system time and synchronisation | LOG §5:348 | register | 17:184 | Time sync is a deployer handover item (25:577), in the register rendering; LOG-01 asserts the @timestamp format. |
| Startup log has service metadata, profiles, host, no secrets | LOG §5:349 | new | LOG-02 | |
| Trace and span IDs propagate across threads and async | LOG §5:352 | register | owed-17 | N/A by construction: no async executor in the design (03:280 is conditional and nothing on the map is @Async). Needed row: LOG §5:352–353 N/A, with the reopening trigger of adding an executor. |
| MDC fields propagate across @Async | LOG §5:353 | register | owed-17 | Same row as LOG §5:352. |
| MDC set for request lifecycle and cleared after | LOG §5:354 | new | LOG-03 | |
| Correlation ID injected into outbound requests | LOG §5:355 | register | owed-17 | N/A: no outbound calls; correlation.id dropped (27:221); outbound call is a reopening trigger (27:308). Ticket 27 says it owes no register lines, so the row is needed. |
| Request logs include method, path, status, duration, outcome | LOG §5:358 | register | owed-17 | No general request log is built: access log disabled (27:252), audit rows carry only url.path and http.request.method. Needed row: LOG §5:358 not built. |
| Request logs exclude IP, query params, auth headers, bodies | LOG §5:359 | new | LOG-07, S13-05 | source.ip hashed everywhere (03:577). |
| Error paths logged once with required error fields | LOG §5:362 | new | LOG-04 | |
| Auth failure logs generic, not revealing account existence | LOG §5:365 | register | 17:261 | Deviation: failure reason and resolved user.id go to the audit log (06:155; 13:322); log-reader oracle priced there; 11:783 moves the password-confirming reason. |
| All audit-contract events logged | LOG §5:366 | test | S13-03, S13-02 | |
| No sensitive data in logs | LOG §5:367 | test | S16-58, S13-05, S13-06, S13-07, S13-08, S13-10 | |
| User identifiers only as user.id UUID | LOG §5:368 | test | S13-09, S13-14, S13-16 | |
| Audit events in dedicated audit destination | LOG §5:369 | new | LOG-05 | |
| Log injection does not corrupt output | LOG §5:370 | test | S13-12, S26-04 | |
| Access controls protect log data | LOG §5:371 | register | 17:184 | Unmitigated deployer item (25:321); 13 declined an in-app permission check. |
| Logging performs under load without dropping events | LOG §5:374 | register | owed-17 | No load test in this harness; appenders write synchronously to stdout and a local file (03:333), so drop-by-design is absent but throughput is unmeasured. Needed row: LOG §5:374 not exercised. |
| Logging failures do not crash the application | LOG §5:375 | new | LOG-06 | |
| Destination outages do not break application flow | LOG §5:376 | new | LOG-06 | No network shipping (03:333); forwarding agent is the deployer's. |
| Deterministic synthetic IDs in test data | LOG §5:379 | new | STD-17 | |
| Assert sanitized injection payload, not raw | LOG §5:380 | test | S13-12 | |

## Rows
| key | pillar | control | assertion | level | context | isolation | clause | source | polarity | dup | note |
|---|---|---|---|---|---|---|---|---|---|---|---|
| STD-01 | SES | Logout ends the session and clears the cookie | With a valid CSRF token, a logout POST returns 204, deletes the SPRING_SESSION row, carries Clear-Site-Data "cache","cookies","storage" and a raw Set-Cookie expiring the session cookie under its profile name and attributes; replaying the captured pre-logout cookie on GET /api/hello returns 401 AUTHENTICATION_FAILED. | P | ctx-port | keyed | Std §5:437; Std §5:502; PRD Story 4; PRD §Testing | 05:169; 08:378; 08:382 | neg | ≡? 08:39 | |
| STD-02 | CSRF | Logout stays CSRF-protected on a dead session | A logout POST carrying an expired or unknown session cookie with a stale token returns 403 CSRF_TOKEN_INVALID in the shared envelope; logout is not exempt from CSRF. | C | ctx-default | keyed | Std §5:438 | 08:34; 08:389; 06:252 | neg | | Server half of §5:438; client half is S14-24. |
| STD-03 | SES | Concurrent-session cap survives restart | Restart harness: U logs in on session A in run 1; after a full close, run 2 on the same file path logs U in on session B; A's cookie then returns 401 AUTHENTICATION_FAILED and B stays authenticated. | C | restart | own-DB | Std §5:434; Std §3.5:343; 08 §3 | 05:341; 08:150 | neg | ≡? 08:449 | Cross-restart companion to S08-07. |
| STD-04 | CSRF | Token endpoint and unsafe-method enforcement | GET /api/csrf returns the token in the JSON body; parameterised over POST, PUT, PATCH and DELETE on authenticated routes, a request without X-CSRF-TOKEN gets 403 CSRF_TOKEN_INVALID and the same request with the fetched token proceeds; GET and HEAD without a token are not refused for CSRF. | C | ctx-default | keyed | Std §5:442; Std §5:444 | 08:95; 08:244 | neg | | |
| STD-05 | LCK | Successful login resets the failure counter | After threshold minus one wrong-password logins (threshold read from app.security.lockout.threshold), a correct-password login sets failed_login_attempts to 0 so as many further failures do not lock; after a tier-1 lock auto-lifts, the first successful login likewise leaves the counter at 0. | C | ctx-default | keyed | Std §5:450; PRD Story 2; PRD Story 3; PRD §Testing | 09:377 | pos | | |
| STD-06 | LCK | Lockout persists across restart | Restart harness: lock U in run 1; after a full close, run 2 on the same file path refuses U's correct password with the uniform 401 AUTHENTICATION_FAILED until the shared clock passes locked_until, then admits it. | C | restart | own-DB | Std §5:451 | 09:488 | neg | | |
| STD-07 | LCK | Admin unlock clears both lockout axes | An ADMIN with a TOTP factor under 10 minutes old calling POST /api/admin/users/{uuid}/unlock on a tier-1-locked target clears failed_login_attempts, last_failed_at, locked_until and the TOTP tier-1 lock; the target's correct password then authenticates before locked_until; per-IP buckets and the TOTP tier-2 disable are untouched. | C | ctx-default | keyed | Std §5:451; 11 endpoint set; 23 §6 | 09:361; 11:216; 11:667; 23:615 | pos | | |
| STD-08 | RL | Reset-request per-identifier budget | With burst and refill read from app.security.rate-limit.password-reset-request.identifier.burst and .refill-period, reset requests for one identifier beyond the burst (from distinct source keys) produce no further captured email, identically for registered and unregistered identifiers; the budget refills after the bound period on the shared clock. | C | ctx-default | keyed | Std §5:452; ASVS 6.3.8 (L3) | 09:314; 09:334; 09:1615 | neg | | Refusal response shape not restated; asserted uniform across account existence. |
| STD-09 | CRED | Admin-issued reset token returned once | POST /api/admin/users/{uuid}/password-reset by an ADMIN returns a 43-character Base64url PASSWORD_RESET token in the body with no-store caching; repeated issuances return distinct tokens, no endpoint returns a plaintext again, and the token redeems at /api/password-reset/confirm. | C | ctx-default | keyed | Std §5:457; Std §5:458 | 10:255; 10:308 | pos | | |
| STD-10 | CRED | Reset token stored only as a domain-separated hash | After issuance the credential_tokens row's token_hash equals lowercase hex SHA-256 of "PASSWORD_RESET:" followed by the plaintext, and no column of any table holds the plaintext. | C | ctx-default | keyed | Std §5:459 | 10:303; 10:309 | neg | ≡? 13:558 | |
| STD-11 | CRED | Reset token expires after 30 minutes | A PASSWORD_RESET token redeemed with the shared clock just under 30 minutes after issuance succeeds; one redeemed just past 30 minutes returns 400 RESET_TOKEN_INVALID and the password is unchanged. | C | ctx-default | keyed | Std §5:459; Std §5:460; PRD Story 7; PRD §Testing | 10:312; 10:316 | neg | | |
| STD-12 | CRED | Pending-token invalidation triggers | Parameterised over the five triggers (credential set through PasswordService, new issuance of the same type, admin disable, admin soft-delete, re-registration against an unactivated record): a previously pending token of the affected type then returns 400 RESET_TOKEN_INVALID. | C | ctx-default | keyed | Std §5:459; 10 §9 | 10:453; 10:460 | neg | | |
| STD-13 | CRED | Self-service change requires the current password | PATCH /api/profile/password with a wrong current password is rejected: the stored hash and history are unchanged, no session is invalidated, and failed_login_attempts does not move. | C | ctx-default | keyed | Std §5:465; ASVS 6.2.3 (L1) | 10:421; 09:732 | neg | | |
| STD-14 | ADM | Lazy 30-day forced-change expiry | For an account with forcePasswordChange set, a login with credentialIssuedAt under 30 days old on the shared clock authenticates and the next non-allowlisted request gets 403 PASSWORD_CHANGE_REQUIRED; past 30 days the login returns the uniform 401 AUTHENTICATION_FAILED and no session is authenticated. | C | ctx-default | keyed | Std §5:484 | 11:337; 11:785 | neg | ≡? 06:175 | Lazy refusal replaces the standard's disablement job (11:337). |
| STD-15 | CRED | Input format and length validation | Parameterised over registration and admin create: an over-length username, an over-length email, a malformed email and a username outside the permitted pattern each return 400 VALIDATION_FAILED whose errors[] names the field and rule, and nothing is created. | C | ctx-default | keyed | Std §5:493 | 06:137; 06:209 | neg | | |
| STD-16 | ADM | USER refused on every admin route | Enumerated from the bound authorization matrix: for every /api/admin/** route and method, a USER (forcePasswordChange clear, valid CSRF token) gets 403 ACCESS_DENIED, including when {uuid} is the caller's own; the same USER gets 200 on GET /api/hello and GET /api/profile. | C | ctx-default | keyed | Std §5:428; Std §5:429; PRD Story 8; PRD §Testing; IM8 ac-1 | 11:175; 11:220 | neg | ≡? 01:306 | |
| STD-17 | ARCH | Synthetic test identities only | The fixture allocator mints usernames and emails only under example.test and never reuses one within a run. | U | none | none | Std §5:525; MFA §5:408; LOG §5:379 | 16:608 | neg | | |
| STD-18 | ARCH | Test tokens come from the production generator | ArchUnit over test classes: no test code fabricates credential-token strings with SecureRandom or a Base64 encoder; tokens reach tests only from the production generator, captured responses or captured emails. | A | archunit | none | Std §5:528 | 16:608 | neg | | |
| PRD-01 | LCK | Locked account refuses the correct password | While locked_until is in the future, the account's correct password returns the uniform 401 AUTHENTICATION_FAILED and no session is authenticated (acceptance after the lift is Story 3 (i), 16:545). | C | ctx-default | keyed | PRD Story 2; PRD §Testing | 06:155; 09:377 | neg | ≡? 16:545 | |
| MFA-01 | MFA | ±1 skew window on a fresh principal | Unit, fixed secret, clock at counter c and lastUsedCounter -1 (fresh principal per case): codes for c-1, c and c+1 verify, and codes for c-2 and c+2 are rejected as invalid. | U | none | none | MFA §5:394; RFC 6238 | 22:387 | neg | | Effective +1-only tolerance after a success is S22-13. |
| MFA-02 | MFA | Period boundary accepts previous window | Unit: with the clock exactly on a 30-second period boundary, the code for the previous counter verifies on a fresh principal. | U | none | none | MFA §5:395 | 22:383; 22:387 | pos | | |
| MFA-03 | MFA | Factor tier-1 lock and auto-lift | Ten consecutive wrong codes inside the 20-minute observation window lock the factor: the correct code then gets 429 with the factor member and integer Retry-After; once the shared clock passes the 20-minute lock the code verifies, and that success resets the tier-1 counter. | C | ctx-default | keyed | MFA §5:417; ASVS 6.1.1 (L1) | 23:484 | neg | | Escalating backoff declined (23:547). |
| MFA-04 | MFA | Provisioning encrypts under the configured key | POST /api/mfa/totp/enrolment returns 200 with a non-empty qrPng that decodes as a PNG, and the PENDING_TOTP totp_key is 69 bytes that decrypt under the configured test key to userUuid, keyVersion and the secret matching secretBase32. | C | ctx-default | keyed | MFA §5:397; MFA §5:402; MFA §5:409; 23 §9 | 23:144; 23:706 | pos | | Real AES-GCM under a fixed test key, supplied outside committed config, replaces passthrough doubles. |
| MFA-05 | MFA | Factor failure HTTP mapping | Parameterised: an admin route with no TOTP factor gives 412 MISSING_FACTOR (factor TOTP, reason MISSING); a mutation with a factor older than 10 minutes gives 412 reason EXPIRED; an unenrolled admin gives 422 FACTOR_ENROLMENT_REQUIRED; a wrong code at verification gives 412 INVALID_FACTOR; provisioning while enrolled gives 409 FACTOR_ALREADY_ENROLLED; each in the shared envelope. | C | ctx-default | keyed | MFA §5:415 | 23:401; 23:406; 23:866 | neg | | |
| MFA-06 | MFA | Factor tier-2 cumulative disable | 100 cumulative wrong codes, interleaved with successes that do not reset the cumulative count and with tier-1 lifts on the shared clock, disable the factor: the correct code then gets 423 FACTOR_DISABLED; admin unlock does not clear it and admin factor reset does. | C | ctx-default | keyed | MFA §5:417 | 23:494 | neg | ≡? 23:1142 | |
| MFA-07 | MFA | Encryption failure is an internal error | Unit: with the BytesEncryptor throwing, provisioning raises the internal-system exception that maps to 500 INTERNAL_ERROR and persists no PENDING_TOTP row. | U | none | none | MFA §5:397 | 23:706 | neg | | |
| LOG-01 | AUD | Tier A fields on every log line | Across a login, an admin mutation and an error flow, every NDJSON line on stdout and in the audit file carries @timestamp (ISO-8601 with offset), message, log.level, log.logger, ecs.version, process.thread.name, service.name, service.version, service.environment, trace.id and span.id. | C | ctx-default | keyed | LOG §5:347; LOG §5:348 | 03:81 | pos | ≡? 13:498 | |
| LOG-02 | AUD | Startup event carries metadata and no secrets | The application-startup line carries event.action application-startup, service.name, service.version, the active profiles, host.name and host.ip, and no configured secret value. | C | restart | own-DB | LOG §5:349 | 03:132; 03:245 | pos | ≡? 16:384 | |
| LOG-03 | AUD | MDC cleared after each request | After an authenticated request completes, the MDC on the executing thread holds no user.id or session.hash, and the lines of a following anonymous request carry neither. | C | ctx-default | keyed | LOG §5:354 | 03:277; 03:281 | neg | ≡? 13:567 | |
| LOG-04 | AUD | Error path logged once with Tier C fields | An exception escaping to the /error dispatch produces exactly one ERROR line, carrying error.code 500, error.category, error.follow_up_action, error.type and error.stack_trace. | C | ctx-default | keyed | LOG §5:362 | 03:117 | pos | ≡? 06:301 | |
| LOG-05 | AUD | Audit rows reach the dedicated destination | A login-success row emitted on the audit logger is written to the dedicated audit rolling file, not only to stdout. | C | ctx-default | keyed | LOG §5:369 | 03:332 | pos | | |
| LOG-06 | AUD | Logging failure never breaks the request | With the audit file appender's target unwritable, a login still succeeds and the Logback StatusListener writes to stderr and increments its dedicated counter. | C | restart | own-DB | LOG §5:375; LOG §5:376 | 21:447; 21:451 | pos | | |
| LOG-07 | AUD | Client IP, query string and auth header never logged | For a request from a fixture source address carrying a query-string canary and an Authorization-header canary, neither the raw address in any textual form nor either canary appears in any appender; rows carry only source.ip_hash. | P | ctx-port | keyed | LOG §5:359; ASVS 16.2.5 (L2) | 03:364; 03:577 | neg | ≡? 16:384 | |
