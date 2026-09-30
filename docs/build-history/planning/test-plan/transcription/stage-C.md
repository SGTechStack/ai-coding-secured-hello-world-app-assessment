## Lists

| list | file | heading or first words | first line | last line | n tests | keys |
| --- | --- | --- | --- | --- | --- | --- |
| L08-a | issues/08-session-and-csrf-contract.md | "assert `Strict` with a comment pointing at the ADR" | 78 | 78 | 1 | S08-01 |
| L08-b | issues/08-session-and-csrf-contract.md | "that profile is asserted by a test and never run" | 82 | 82 | 1 | S08-02 |
| L08-c | issues/08-session-and-csrf-contract.md | "Silent-failure warning inherited from ticket 05 … Requires an integration test" | 240 | 242 | 1 | S08-03 |
| L08-d | issues/08-session-and-csrf-contract.md | "Decided: send the header exactly as prescribed … Ticket 16 gets a real browser assertion" | 378 | 380 | 1 | S08-04 |
| L08-e | issues/08-session-and-csrf-contract.md | "§5:446 — Previously redeemed CSRF tokens are rejected" | 394 | 400 | 1 | S08-05 |
| L08-f | issues/08-session-and-csrf-contract.md | "§5:499 and §3.5:346-350 … negative assertion: no CSRF cookie" | 402 | 410 | 1 | S08-06 |
| L08-g | issues/08-session-and-csrf-contract.md | "16 (test plan): the reinterpreted §5:446 …" (handover bullet) | 449 | 451 | 2 | S08-07, S08-08 (also refs S08-04, S08-05, S08-06) |
| L08-h | issues/08-session-and-csrf-contract.md | "One test you own that ticket 09 cannot write" | 694 | 695 | 1 | S08-09 |
| L08-i | issues/08-session-and-csrf-contract.md | "Two conditions worth carrying into ticket 16's test" | 722 | 731 | 0 | facets of S08-04 |
| L08-j | issues/08-session-and-csrf-contract.md | "## Amendment from ticket 16 (test plan)" | 758 | 769 | 14 | S08-10 … S08-23 (also refs S08-04, S08-09) |
| L10-a | issues/10-credential-flows.md | "Rule, generalised and testable … Three negative assertions, one test each" | 355 | 359 | 3 | S10-01, S10-02, S10-03 |
| L10-b | issues/10-credential-flows.md | "Which lockout, precisely … Stated as a positive assertion" | 398 | 403 | 1 | S10-04 |
| L10-c | issues/10-credential-flows.md | "One negative assertion owed as a test" | 594 | 597 | 1 | S10-05 |
| L10-d | issues/10-credential-flows.md | "ADR 8. Reset and activation links are never built from request-controlled input" | 617 | 619 | 0 | refs S10-01 … S10-03 |
| L10-e | issues/10-credential-flows.md | "16 (test plan): cross-type token redemption fails …" (handoff bullet) | 636 | 639 | 5 | S10-06 … S10-10 (also refs S10-01 … S10-04) |
| L10-f | issues/10-credential-flows.md | "Assert that the reset-request and redemption paths never route through AuthenticationManager" | 760 | 764 | 1 | S10-11 |

## Rows

| key | pillar | control | assertion | level | context | isolation | clause | source | polarity | dup | note |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| S08-01 | SES | Session cookie SameSite=Strict | The raw Set-Cookie for the session cookie carries SameSite=Strict (not Lax), HttpOnly and Path=/; the test carries a comment pointing at the ticket 20 ADR explaining the deliberate failure of the Standard's Lax test. | P | ctx-port | keyed | Std §5:499; Std §3.5:346-350; 20 origin topology | 08:78; 08:405 | pos | | Deviation by design from Std §5:499's Lax; ticket 20 may list the same assertion. Distinct from ticket 16's E-level SameSite delivery test (16:600). |
| S08-02 | SES | Non-dev session cookie profile split | In the non-dev profile the effective cookie serializer binds name `__Host-SESSION`, Secure=true, HttpOnly, Path=/ and SameSite=Strict, read off configuration; the profile is asserted, never run as a deployment. | C | ctx-nondev | none | Std §5:499; 08 per-profile cookie config | 08:82 | pos | | Production-value binding test. |
| S08-03 | SES | PRINCIPAL_NAME index populated | After a login, the SPRING_SESSION row's PRINCIPAL_NAME equals the username and `findByPrincipalName` returns that session; guards every invalidation trigger against silent no-op. | C | ctx-default | keyed | 08 §5 | 08:240 | pos | | Integration test required, not inspection (08:241). |
| S08-04 | HDR | Clear-Site-Data origin scoping on logout | In Chromium and Firefox, logout from the API origin clears the SPA's cookies (registered-domain reach) but not the SPA origin's localStorage or cache; fetch sent with credentials, response not service-worker served. | E | playwright | none | Std §3.5:391; 08 §10 | 08:378; 08:449; 08:722; 08:760 | pos | ≡? 16:598 | Owner and level set by 08:760 (ticket 16). Facets from 08:722: credentials flag set, no service worker, prefer loopback literal 127.0.0.1 if UA-independence is needed. |
| S08-05 | CSRF | Superseded-session CSRF token rejected | A CSRF token fetched pre-login is rejected with 403 CSRF_TOKEN_INVALID after login, and a token fetched pre-logout is rejected after logout (then re-login); proves CsrfAuthenticationStrategy is in the Route C composite and CsrfLogoutHandler still arrives. | C | ctx-default | keyed | Std §5:446; 08 §11; 08 ADR 5 | 08:394; 08:449 | neg | | Reinterpretation of Std §5:446 (no redemption concept in HttpSessionCsrfTokenRepository). Two facets of one reinterpreted test, not split. |
| S08-06 | CSRF | No CSRF cookie is ever set | Across the bootstrap, login, logout, authenticated mutation and error responses, no Set-Cookie header other than the session cookie is emitted (in particular no XSRF-TOKEN or other CSRF cookie). | P | ctx-port | keyed | Std §5:499; Std §3.5:346-350; 08 ADR 5 | 08:402; 08:449 | neg | | Dead-text CSRF-cookie clause converted to a negative assertion. P because raw Set-Cookie. |
| S08-07 | SES | Max one concurrent session, new login wins | Log in as user U on session A, log in again as U on session B; the next request on A returns 401 AUTHENTICATION_FAILED in the ProblemDetail envelope (not plain text), and B stays authenticated. | C | ctx-default | keyed | Std §3.5:343; 08 §3 | 08:449 | neg | | Envelope facet from 08 §3 (SessionInformationExpiredStrategy wired to ProblemDetailWriter). |
| S08-08 | SES | Absolute session lifetime from AUTH_INSTANT | Advance the shared forward-only clock past 8 h after AUTH_INSTANT (with activity inside the idle window); the next request returns 401 AUTHENTICATION_FAILED and the session is invalidated. | C | ctx-default | keyed | Std §3.5:345; 08 §2; 08 §12 | 08:449 | neg | | "Short-lifetime integration test" restated on the clock per 16 §4 (no Thread.sleep, no shortened property). Optional facet: a POST on the expired session gets 401 not 403 CSRF_TOKEN_INVALID (ordering invariant 08:412), not separately owed. |
| S08-09 | SES | Session reconciliation sweep | For a user with password_disabled_at set whose session kill was never dispatched, the sweep deletes that user's sessions; running it twice is idempotent (no error, no further change). | C | ctx-default | keyed | 09 §R; ASVS 7.4.2 (L1) | 08:694; 08:768 | neg | ≡? 16:158 | Ticket 16 owns this test outright (08:768); same test as 09 §R test 7 (16:158). |
| S08-10 | SES | Replay: self-service password change | Capture the raw cookies of two sessions of U, change the password via PATCH /api/profile/password on one; replaying the other against the real JDBC store returns 401 and its row is gone, while the acting session survives (id rotated). | P | ctx-port | keyed | Std §5:466; 08 §5; 08 ADR 2 | 08:761; 08:207 | neg | | Enumerated from trigger table row 1; asserts "all others". |
| S08-11 | SES | Replay: forced-change completion | For U with forcePasswordChange set, two sessions; complete the forced change on one; the other's replayed cookie returns 401 and its row is gone, while the acting session survives (id rotated). | P | ctx-port | keyed | Std §5:466; 08 §5; 08 ADR 2 | 08:761; 08:208 | neg | | Trigger table row 2; asserts "all others". |
| S08-12 | SES | Replay: password reset redemption | Capture U's live session cookie, redeem a PASSWORD_RESET token for U; replaying the cookie returns 401 and the row is gone, and redemption returns no session. | P | ctx-port | keyed | Std §5:466; 08 §5 | 08:761; 08:209 | neg | | Trigger table row 3 ("all"). |
| S08-13 | SES | Replay: admin reset token issuance | Capture target T's session cookie, admin issues POST /api/admin/users/{uuid}/password-reset for T; replaying T's cookie returns 401 and the row is gone. | P | ctx-port | keyed | 08 §5 | 08:761; 08:210 | neg | | Trigger table row 4. |
| S08-14 | SES | Replay: admin disable | Capture target T's session cookie, admin disables T; replaying T's cookie returns 401 and the row is gone. | P | ctx-port | keyed | 08 §5; 08 ADR 6; ASVS 7.4.2 (L1) | 08:761; 08:211 | neg | | Trigger table row 5 (trigger added beyond the Standard). |
| S08-15 | SES | Replay: admin role change | Capture target T's session cookie, admin changes T's role; replaying T's cookie returns 401 and the row is gone (no stale privileged authorities survive). | P | ctx-port | keyed | 08 §5; 08 ADR 6 | 08:761; 08:212 | neg | | Trigger table row 6 (added trigger). |
| S08-16 | SES | Replay: admin soft-delete | Capture target T's session cookie, admin deletes T; replaying T's cookie returns 401 and the row is gone. | P | ctx-port | keyed | 08 §5; 08 ADR 6 | 08:761; 08:213 | neg | | Trigger table row 7 (added trigger). |
| S08-17 | SES | Replay: account lockout | Capture U's live session cookie, drive U into lockout with failed logins; replaying the cookie returns 401 and the row is gone. | P | ctx-port | keyed | 08 §5; 08 ADR 6 | 08:761; 08:214 | neg | | Trigger table row 8 (added trigger). Failure source keys distinct under ticket 31. |
| S08-18 | SES | Replay: admin TOTP factor reset | Capture target T's session cookie, admin resets T's TOTP factor (DELETE /api/admin/users/{uuid}/totp); replaying T's cookie returns 401 and the row is gone. | P | ctx-port | keyed | 08 §5 | 08:761; 08:215 | neg | | Trigger table row 9. |
| S08-19 | SES | Replay: NIST cap authenticator disable | Capture U's live session cookie, drive U to the ticket 09 §R consecutive-failure cap so password_disabled_at is set; after commit dispatch, replaying the cookie returns 401 and the row is gone. | P | ctx-port | keyed | 09 §R; ASVS 7.4.2 (L1) | 08:761; 08:763; 08:651 | neg | | The "fifth" trigger (08:649–651). Cap value read off configuration, not hard-coded. |
| S08-20 | SES | Activation redemption invalidates nothing | An existing unrelated live session survives an activation-token redemption (the activated account has no sessions); the test carries its reason inline citing 08:522. | P | ctx-port | keyed | 08 §5 amendment from 10 | 08:761; 08:765; 08:522 | neg | | Vacuous row; negative test so a tidy-up reads failure as an overturned decision. |
| S08-21 | SES | Invite redemption invalidates nothing | An existing unrelated live session (e.g. the inviting admin's) survives an invite-token redemption; the test carries its reason inline citing 08:522. | P | ctx-port | keyed | 08 §5 amendment from 10 | 08:761; 08:765; 08:522 | neg | | Vacuous row, as S08-20. |
| S08-22 | SES | Idle timeout 15 min | Age the SPRING_SESSION row's LAST_ACCESS_TIME beyond the configured idle timeout (cron disabled with `-`); the next request returns 401 AUTHENTICATION_FAILED, never a redirect. | C | ctx-default | keyed | Std §3.5:344; 08 §1; 06 expiry 401 | 08:769 | neg | | Aging rows, no Thread.sleep (16 §4). Idle timeout read off spring.session.timeout. |
| S08-23 | SES | Spring Session cleanup cron deletes expired rows | With the cleanup cron enabled, an aged (expired) SPRING_SESSION row is deleted by the job, while a live row is untouched. | C | ctx-default | own-DB | 08 §1; 16 §3 | 08:769 | pos | | The one dedicated cron-enabled test. Needs a cron-enabled override; no fixed context has the cron enabled (see Problems). Distinct from the production cron binding test (16:450). |
| S10-01 | CRED | Link origin ignores Host header | A reset request (and a registration) sent with `Host: attacker.example` produces a captured email link whose origin equals the configured link-origin property. | P | ctx-port | keyed | 10 §6; 10 ADR 8 | 10:355; 10:617; 10:636 | neg | | One of three negative assertions, one test each (source). P so Tomcat processes the Host header. |
| S10-02 | CRED | Link origin ignores X-Forwarded-Host | A reset request (and a registration) sent with `X-Forwarded-Host: attacker.example` produces a captured email link whose origin equals the configured link-origin property. | P | ctx-port | keyed | 10 §6; 10 ADR 8 | 10:355; 10:617; 10:636 | neg | | Second of three. P because RemoteIpValve/forwarded processing is bypassed by MockMvc. |
| S10-03 | CRED | Link origin ignores body field | A reset request (and a registration) carrying an extra reset-URL or origin body field is either rejected or ignored, and the captured link's origin equals the configured link-origin property. | C | ctx-default | keyed | 10 §6; 10 ADR 8 | 10:355; 10:617; 10:636 | neg | | Third of three. Source rule also names "query parameter" but counts only three assertions. |
| S10-04 | LCK | Redemption clears password lockout, never TOTP | After a successful PASSWORD_RESET redemption, failed_login_attempts, last_failed_at, locked_until, consecutive_failures_since_success and password_disabled_at are cleared, while TOTP failure counters and enrolment state are unchanged. | C | ctx-default | keyed | ASVS 6.4.3 (L2); 10 §7; 09 §R | 10:400; 10:636; 10:719 | pos | | Extended by ticket 09 §R amendment (10:719) to also clear the cap counter and password_disabled_at. |
| S10-05 | CRED | Identifier canonicalisation never applied to passwords | A password with leading/trailing spaces and mixed case set at activation authenticates only with the exact submitted value; the trimmed/lowercased variant fails. | C | ctx-default | keyed | 10 §14; ASVS 6.2.8 (L1) | 10:594 | neg | | |
| S10-06 | CRED | Cross-type token redemption fails | An ACTIVATION token submitted to /api/password-reset/confirm, and a PASSWORD_RESET token submitted to /api/register/activate, each return 400 RESET_TOKEN_INVALID and consume nothing. | C | ctx-default | keyed | 10 §5; 10 ADR 4 | 10:636; 10:303 | neg | | Both directions in one test. |
| S10-07 | CRED | Conditional-consume single-use race | Concurrent redemptions of one token released from one CountDownLatch: exactly one succeeds and every other returns RESET_TOKEN_INVALID; used_at is set once. | C | ctx-default | keyed | ASVS 6.5.1 (L2); 10 §5 | 10:636; 10:315 | neg | | Only 09 §R row 4 is mandated at cost 12 (16 §5); ctx-nondev not required here. |
| S10-08 | CRED | Rejected password does not burn token | Redeeming a valid token with a policy-rejected password returns 400 PASSWORD_REJECTED and the same token then redeems successfully with a valid password. | C | ctx-default | keyed | 10 §5; 06 ordering rule | 10:636; 10:325 | pos | | |
| S10-09 | AUTH | Registration uniform across email-axis states | POST /api/register returns an identical 202 status and body for a new address, a pending unactivated address and an activated address, and in every state performs zero PasswordEncoder encode or matches calls. | C | ctx-default | keyed | ASVS 6.3.8 (L3); PRD Story 1; 10 §2 | 10:636; 10:184 | neg | | "Comparable timings" restated as a mechanism count per 16 §5 (no wall-clock assertion anywhere). See Problems. |
| S10-10 | ADM | Invited-unredeemed admin not counted for two-admin invariant | With one activated TOTP-enrolled admin plus one invited-but-unredeemed admin, the sole real admin's self-demotion or disable is refused. | C | ctx-default | delta | 10 §4; 11 two-admin invariant | 10:636; 10:288 | neg | | Predicate is activated_at IS NOT NULL and TOTP-enrolled. Global admin count, so delta. Ticket 11 may list a twin. |
| S10-11 | CRED | Reset paths never reach AuthenticationManager | No class on the reset-request or redemption path depends on or calls AuthenticationManager. | A | archunit | none | 09 §R | 10:760 | neg | ≡? 16:157 | Ticket 16 carries the test (10:764); 09 §R row 6. 16 §5 adds a behavioural companion (reset works while the password authenticator is disabled), owned in ticket 16. |

## Not transcribed

| file:line | item | reason |
| --- | --- | --- |
| 08:254 | `Cache-Control: no-store` on /csrf "§5:443 tests it" | Standard §5:443's prescribed test, cited but not owed by an 08 list; belongs to the Standard rows (STD-). Flag for merge. |
| 08:311 | "Separately verify that CsrfLogoutHandler still arrives" | Verification, not a separately owed test; discharged by S08-05's pre-logout facet. |
| 08:412 | Filter-ordering invariant (absolute filter before CsrfFilter) | Recorded invariant, not stated as a test; noted as an optional facet of S08-08. |
| 08:633 | Audit strategy ordering inside the composite | Ticket 13's concern; no test owed by 08. |
| 08:738 | Anonymous-session CREATION_TIME branch (ticket 29 amendment) | No test stated in 08; ticket 29 owns its tests. |
| 10:239 | "the axes separate cleanly and testably" (username specific, email unobservable) | Described as testable, not stated as owed; email half covered by S10-09. Flag for merge. |
| 10:578 | 6.2.7 (L1) "Manager compatibility is the actual test" | Password-manager compatibility is a manual/browser check handed to ticket 14; not an owed automated test here. |
| 10:760 (companion) | Behavioural companion "reset works while the password authenticator is disabled" | Stated in 16 §5, not in 10; owned by ticket 16's rows. |

## Counts

| source ticket/section | n tests | keys |
| --- | --- | --- |
| 08:78 (L08-a) | 1 | S08-01 (also 08:405) |
| 08:82 (L08-b) | 1 | S08-02 |
| 08:240 (L08-c) | 1 | S08-03 |
| 08:378 (L08-d) | 1 | S08-04 (also 08:449, 08:722, 08:760) |
| 08:394 (L08-e) | 1 | S08-05 (also 08:449) |
| 08:402 (L08-f) | 1 | S08-06 (also 08:449) |
| 08:449 (L08-g) | 2 | S08-07, S08-08 |
| 08:694 (L08-h) | 1 | S08-09 (also 08:768) |
| 08:758–769 (L08-j) | 14 | S08-10 … S08-23 (replay rows also cite 08:207–215, 08:651, 08:522) |
| 10:355 (L10-a) | 3 | S10-01, S10-02, S10-03 (also 10:617, 10:636) |
| 10:398 (L10-b) | 1 | S10-04 (also 10:636, 10:719) |
| 10:594 (L10-c) | 1 | S10-05 |
| 10:636 (L10-e) | 5 | S10-06 … S10-10 |
| 10:760 (L10-f) | 1 | S10-11 |
