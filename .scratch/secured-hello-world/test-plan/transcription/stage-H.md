## Lists
| list | file | heading or first words | first line | last line | n tests | keys |
|---|---|---|---|---|---|---|
| L22-a | 22 | §11 "Tests: this ticket's premise was wrong." (6 ported MFADialog cases from FE-STD §8.1 + the MFATotpForm surface 16 owns) | 546 | 556 | 10 | S22-01..S22-10 |
| L22-b | 22 | §8 "Recipe 9's only conformance vector is RFC 6238 Appendix B" | 389 | 393 | 2 | S22-11, S22-12 |
| L22-c | 22 | §16 "16 (test plan):" handover, "Add: …" | 730 | 735 | 3 | S22-13..S22-15 |
| L22-d | 22 | "## Amendment from ticket 10 (credential flows)" | 757 | 766 | 1 | S22-16 |
| L23-a | 23 | §2 "plus a one-line assertion that no `RoleHierarchy` bean exists" | 291 | 293 | 1 | S23-01 |
| L23-b | 23 | §2 "**Negative assertion:** no rule for any surface is ever routed through a factor-bearing …" | 295 | 298 | 1 | S23-02 |
| L23-c | 23 | §2 "**Three startup assertions**, all fail-fast during context refresh" | 300 | 307 | 3 | S23-03..S23-05 |
| L23-d | 23 | §3 "**Serialisation is asserted, not assumed**" | 445 | 450 | 1 | S23-06 |
| L23-e | 23 | Amendment from ticket 12, item 2 "The negative tests drive the checks with 48-byte and 81-byte values" | 962 | 970 | 1 | S23-07 |
| L23-f | 23 | Amendment from ticket 12, item 4 "`VARBINARY`, never `BINARY`" | 977 | 980 | 1 | S23-08 |
| L23-g | 23 | Amendment from ticket 15 §1 "**Amendment: the trip acquires the `users` row first**" | 1142 | 1146 | 1 | S23-09 |
| L23-h | 23 | Amendment from ticket 15 §2 "**Amendment: state the asymmetry**, and give ticket 16 an assertion enumerated from ticket 11's matrix" | 1167 | 1171 | 1 | S23-10 |

## Rows
| key | pillar | control | assertion | level | context | isolation | clause | source | polarity | dup | note |
|---|---|---|---|---|---|---|---|---|---|---|---|
| S22-01 | FE | MFADialog title | MFADialog renders its title as "<mfaType> Verification" (i.e. "TOTP Verification"). | F | vitest | none | FE-STD §8.1:439 | 22:547 | pos |  | Ported case 1 of 6 (16 §8). Case text taken from FE-STD §8.1 L439; ticket 22 names only the count. |
| S22-02 | FE | MFADialog submits code | Entering six digits and clicking Verify calls `callback` with the entered code. | F | vitest | none | FE-STD §8.1:441 | 22:547 | pos |  | Ported case 2 of 6. FE-STD L441. |
| S22-03 | FE | MFADialog clears code | After a Verify click the digit-slot input value is cleared from dialog local state. | F | vitest | none | FE-STD §8.1:442; FE-STD §7.3 | 22:547; 22:537 | pos |  | Ported case 3 of 6. FE-STD L442. 22:537 confirms MFADialog clears on Verify and Cancel. |
| S22-04 | FE | MFADialog cancel | Clicking Cancel calls `onCancel()`. | F | vitest | none | FE-STD §8.1:443 | 22:547 | pos |  | Ported case 4 of 6. FE-STD L443. |
| S22-05 | FE | MFADialog in-flight spinner | With `showSpinner` true the Verify button shows the spinner (and is not actionable). | F | vitest | none | FE-STD §8.1:444 | 22:547 | pos |  | Ported case 5 of 6. FE-STD L444. |
| S22-06 | FE | MFADialog description | When a `description` prop is provided its text is rendered in the dialog. | F | vitest | none | FE-STD §8.1:445 | 22:547 | pos |  | Ported case 6 of 6. FE-STD L445. |
| S22-07 | FE | MFATotpForm QR generation | Clicking Generate calls `POST /api/mfa/totp/enrolment` and renders the returned `qrPng` as an `<img>` whose src is a `blob:` object URL (base64 → Uint8Array → Blob image/png), never a `data:` URI. | F | vitest | none | 22 §11; 23 §1 | 22:552 | pos |  | New test (16 §8). Restated on 23 §1's JSON envelope (23:164-168) in place of the corpus's PNG-bytes GET. |
| S22-08 | FE | MFATotpForm object-URL lifecycle | The QR object URL is revoked when replaced, on unmount, on the error path and once `isVerified` flips, and a StrictMode double-mount does not leave the `<img>` pointing at a revoked URL. | F | vitest | none | 22 §11 | 22:553 | pos |  | New test. Facets taken from 22 §11 defects (22:512-524): StrictMode revoke, no revoke on error, none on verify. |
| S22-09 | FE | MFATotpForm keyExists disable | When `keyExists` is true the Generate QR button is disabled and no "Regenerate QR Code" label or old-TOTP warning is rendered. | F | vitest | none | 22 §11 (FE-STD §5 over §3.2); 23 §1 | 22:553 | neg |  | New test. The label/warning absence is from 22:506-509 and 23:209-211. |
| S22-10 | FE | MFATotpForm verify outcome | Submitting a 6-digit code: on 204 the form shows the enrolled state; on 412 `INVALID_FACTOR` it shows the in-page error and the code is not accepted. | F | vitest | none | 22 §11; 23 §1 | 22:553 | pos |  | New test; source says "verify success/failure" as one item, kept as one row. Statuses restated per 23 §1 (204 / 412) replacing the corpus boolean. |
| S22-11 | MFA | RFC 6238 conformance vector | `generateTotpFromCounter("12345678901234567890" US-ASCII, 1L, 8)` returns `"94287082"` (RFC 6238 Appendix B). | U | none | none | RFC 6238 App. B; Recipe 9 | 22:389 | pos |  | Recipe 9's only vector; its `props::checkMinimumDigits` half is not transcribed (see Not transcribed). |
| S22-12 | MFA | 6-digit TOTP vector | A 6-digit vector derived from the RFC 6238 Appendix B secret and counter asserts the production 6-digit truncation (e.g. counter 1 yields "287082"). | U | none | none | RFC 6238 App. B; STD §3.4 | 22:391; 22:733 | pos |  | "we write one"; exact expected value to be computed at implementation. |
| S22-13 | MFA | +1-window-only effective skew | After a successful verification at counter c, codes for c-1 and c are rejected as replay (412 `INVALID_FACTOR`) while the code for c+1 is accepted, driven on the shared clock. | C | ctx-default | keyed | STD §2.5; STD §3.4; ASVS 6.5.1 (L2) | 22:734; 22:395 | neg |  | Documents the ±1 → +1 behaviour 23 §10 records for ticket 25. |
| S22-14 | MFA | Empty code not counted | A verification request with an empty-string code is rejected without incrementing the tier-1 or cumulative factor failure counter. | C | ctx-default | keyed | 22 §9; 23 §10 | 22:734; 22:420; 23:773 | neg |  | Restated: 22 handed "the empty-string lockout lever"; 23:773-776 fixes the corpus bug, so the test asserts the fix (not counted), not the corpus behaviour. |
| S22-15 | MFA | PENDING_TOTP kept on failed confirmation | A wrong code on `POST /api/mfa/totp/enrolment/confirmation` returns 412 and leaves the unexpired `PENDING_TOTP` row intact so a retry with the correct code succeeds. | C | ctx-default | keyed | Recipe 10 rule; 23 §1 | 22:735 | pos |  |  |
| S22-16 | CRED | Reset redemption leaves TOTP state | Redeeming a password-reset token clears `failed_login_attempts`, `last_failed_at` and `locked_until` only, and leaves the TOTP enrolment row, its counters and any lock unchanged (before/after equal). | C | ctx-default | keyed | ASVS 6.4.3 (L2) | 22:764; 22:759 | pos | ≡? ticket 10 (reset-redemption clears only password columns) | Source insists it is "a positive assertion rather than an absence", hence pos. |
| S23-01 | ADM | No RoleHierarchy bean | The application context contains no `RoleHierarchy` bean, so a future hierarchy fails loudly rather than being ignored by the hand-built admin rules. | C | ctx-default | none | 23 §2; 11 (hierarchy out of scope) | 23:291 | neg |  |  |
| S23-02 | MFA | No factor-bearing authorization factory | No authorization rule (web or method, including `anonymous()`) is produced by a `DefaultAuthorizationManagerFactory` carrying additional authorization, and no such factory bean exists. | C | ctx-default | none | 23 §2 | 23:295 | neg |  |  |
| S23-03 | CFG | Startup: TOTP provider registered | Context refresh fails when the TOTP `AuthenticationProvider` bean is absent. | C | restart | own-DB | STD §4.1; 23 §2 | 23:302; 22:285 | neg |  | Fail-fast startup assertion; transcribed as a failing-startup test on the 24/28 validator precedent. Context: dedicated SpringApplicationBuilder run outside the cache. |
| S23-04 | CFG | Startup: rule sets non-empty | Context refresh fails when either admin factor rule set (read or mutation) is empty. | C | restart | own-DB | STD §4.1; 23 §2 | 23:304; 22:285 | neg |  | As S23-03. |
| S23-05 | CFG | Startup: read duration covers session lifetime | Context refresh fails when the read rule's `validDuration` is less than the configured absolute session lifetime. | C | restart | own-DB | 23 §2; 23 §4 | 23:305 | neg | ≡? 05 (absolute session lifetime) | As S23-03. Binds against the configured lifetime, not a literal 8h. |
| S23-06 | MFA | Factor authority survives JDBC session | After a factor grant, the `SPRING_SESSION_ATTRIBUTES` round trip yields a `FactorGrantedAuthority` (deserialised type asserted) with identical `getIssuedAt()`; and a session holding a plain `SimpleGrantedAuthority("FACTOR_TOTP")` is denied on the admin read and mutation rules. | C | ctx-default | keyed | 23 §3; 23 §4 | 23:445 | pos | ≡? 08 (session serialisation) | Source frames as one test with a negative case, kept as one row. |
| S23-07 | MFA | 69-byte envelope check | Writing a `totp_key` of 48 or 81 bytes to `totp_user_details` or `pending_totp` is rejected by `ck_totp_user_details_totp_key_len` / `ck_pending_totp_totp_key_len`; 69 bytes is accepted. | C | ctx-default | keyed | 23 §9; 12 (V5__totp.sql) | 23:966 | neg | ≡? 12 (totp_key length check) | Parameterised over two tables × two wrong widths: one row. |
| S23-08 | MFA | VARBINARY not BINARY | A schema declaring `totp_key` as `BINARY(69)` fails Hibernate `ddl-auto: validate` at startup. | C | restart | own-DB | 23 amendment from 12 §4 | 23:977 | neg | ≡? 12 (column type) | Source holds this "as an assertion rather than as a fact" (H2 2.x metadata unverified); see problems. |
| S23-09 | LCK | Tier-2 trip lock order | On the 100th cumulative factor failure the tier-2 trip locks the `users` row before the `totp_user_details` row, so under concurrent `AdminActionGuard` contention on the same admin the verification path takes no `LOCK_TIMEOUT` and no deadlock occurs. | C | ctx-locktimeout | keyed | TM-03 | 23:1142 | pos | ≡ 15 #2 (16 §9, confirmed) | 16 §9 cites this as 23:1144; the "Ticket 16 owes the assertion" sentence is 23:1145. Counted once across 23 and 15. |
| S23-10 | MFA | Factor matrix, enumerated from 11 | For every admin route and method enumerated from ticket 11's authorization matrix, a session with `ROLE_ADMIN` + `FACTOR_PASSWORD` but no `FACTOR_TOTP` is refused; for every mutation a `FACTOR_TOTP` older than 10 minutes (on the shared clock) is refused. | C | ctx-default | keyed | TM-04 | 23:1167 | neg | ≡ 11:888; 15 #1 (16 §9, confirmed) | Matrix-generated: one ID (16 §2). Counted once across 11, 15 and 23. |

## Not transcribed
| file:line | item | reason |
|---|---|---|
| 22:547 | MFADialog "does not show ResendOtpButton for any factor type" (FE-STD §8.1 L440) | Vacuous: component defined nowhere (MCC leak), 16 §8 |
| 22:547 | MFAPinForm "renders PIN and Confirm PIN fields and Set PIN button" (FE-STD L451) | Code not built (PIN out of scope), 16 §8 |
| 22:547 | MFAPinForm "disables all fields and button when isSetupAllowed is false" (L452) | Code not built, 16 §8 |
| 22:547 | MFAPinForm "shows 'already set' advisory …" (L453) | Code not built, 16 §8 |
| 22:547 | MFAPinForm "shows 'Pin set successfully.' …" (L454) | Code not built, 16 §8 |
| 22:547 | MFAPinForm "shows field error when PIN does not match /^\d{6}$/" (L455) | Code not built, 16 §8 |
| 22:547 | MFAPinForm "shows field error when PINs do not match" (L456) | Code not built, 16 §8 |
| 22:547 | MFAPinForm "calls handlePinFormSubmit with correct data on valid submit" (L457) | Code not built, 16 §8 |
| 22:547 | MFAPinForm "shows spinner when showSpinner is true" (L458) | Code not built, 16 §8 |
| 22:547 | useMFA "calls GET /mfa/requirePinAndTotpSetup on mount and caches result" (L464) | Code not built (CT orchestrator), 16 §8 |
| 22:547 | useMFA "does not open dialog and redirects to /settings/mfa when mfaSetupRequired is true" (L465) | Code not built, 16 §8 |
| 22:547 | useMFA "opens dialog and stores submitFn when handleMFA is called …" (L466) | Code not built, 16 §8 |
| 22:547 | useMFA "invokes submitFn with collected code on useEffect trigger" (L467) | Code not built, 16 §8 |
| 22:547 | useMFA "sets retry=true and reopens dialog on 412 error (invalid code)" (L468) | Code not built, 16 §8 |
| 22:547 | useMFA "sets retry=true and reopens dialog on 412 error (missing factor)" (L469) | Code not built, 16 §8 |
| 22:547 | useMFA "shows toast and closes dialog on 403 error" (L470) | Code not built, 16 §8 |
| 22:547 | useMFA "shows MFA required toast on 422" (L471) | Code not built, 16 §8 |
| 22:547 | useMFA "shows rate-limited toast and closes dialog on 429 error" (L472) | Code not built, 16 §8 |
| 22:547 | useMFA "closes dialog and clears state on handleMFASuccess" (L473) | Code not built, 16 §8 |
| 22:547 | useMFA "sets showSpinner=false on handleMFAFinally" (L474) | Code not built, 16 §8 |
| 22:547 | useMFAPinForm "calls GET /mfa/setupAllowed on mount" (L480) | Code not built, 16 §8 |
| 22:547 | useMFAPinForm "sets isSetupAllowed=false when endpoint returns false" (L481) | Code not built, 16 §8 |
| 22:547 | useMFAPinForm "calls POST /mfa/setPin with Content-Type: text/plain on submit" (L482) | Code not built, 16 §8 |
| 22:547 | useMFAPinForm "sets pinChangeSuccess=true and resets form on 200 response" (L483) | Code not built, 16 §8 |
| 22:547 | useMFAPinForm "shows error toast on 422 response" (L484) | Code not built, 16 §8 |
| 22:391 | Recipe 9 test's `props::checkMinimumDigits` call | Cannot implement: method does not exist and `@PostConstruct` does not fire on `new TOTPProperties()`; digits validation belongs to 23 §10's `@Validated` config (ticket 24 validators) |
| 22:554 | Prescribed error-path fixtures `mock412Error` / `mock422MfaError` / `mock429Error` | Observation that fixtures are mis-shaped, not a test; fixture validation against the enum schema is 16 §8's own T-ID |
| 23:370 | "The test must be `AuthenticationTrustResolver.isAnonymous(...)`" | Code predicate in the provider precondition, not an owed test |

## Counts
| source ticket/section | n tests | keys |
|---|---|---|
| 22 §11 frontend (22:546–556) | 10 | S22-01..S22-10 (6 ported + 4 new MFATotpForm) |
| 22 §8 Recipe 9 vectors (22:389–393) | 2 | S22-11, S22-12 (S22-12 also 22:733) |
| 22 §16 handover to 16 (22:730–735) | 3 | S22-13..S22-15 (S22-14 also 23:773) |
| 22 Amendment from ticket 10 (22:757–766) | 1 | S22-16 |
| 23 §2 RoleHierarchy assertion (23:291) | 1 | S23-01 |
| 23 §2 negative assertion (23:295) | 1 | S23-02 |
| 23 §2 startup assertions (23:300–307) | 3 | S23-03..S23-05 (S23-03/04 also 22:285) |
| 23 §3 serialisation (23:445) | 1 | S23-06 |
| 23 Amendment from 12 item 2 (23:962) | 1 | S23-07 |
| 23 Amendment from 12 item 4 (23:977) | 1 | S23-08 |
| 23 Amendment from 15 §1 (23:1142) | 1 | S23-09 |
| 23 Amendment from 15 §2 (23:1167) | 1 | S23-10 |
| **Total** | **26** | |
