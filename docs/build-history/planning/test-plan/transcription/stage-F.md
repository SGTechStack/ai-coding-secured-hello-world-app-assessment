## Lists
| list | file | heading or first words | first line | last line | n tests | keys |
|---|---|---|---|---|---|---|
| L13-a | 13 | §3: "`addKeyValue("user.target.id", …)` writes cleanly … becomes a named first-implementation test" | 292 | 294 | 1 | S13-01 |
| L13-b | 13 | §9 constraints 4–5: "the parameterised test …" / "Two tests, not one" | 498 | 506 | 2 | S13-02, S13-03 |
| L13-c | 13 | §9: "The ASVS 16.1.1 inventory table is generated from the enum as a snapshot test" | 508 | 509 | 1 | S13-04 |
| L13-d | 13 | §10: "pin it, and assert the outcome rather than the flag" (malformed credential-bearing request) | 537 | 542 | 1 | S13-05 |
| L13-e | 13 | §10: "The negative-assertion set, every item a test" (table) | 553 | 567 | 12 | S13-05 (ref), S13-06 to S13-16 |
| L13-f | 13 | §12: "no `total-size-cap` — the omission is deliberate and asserted by a test" | 601 | 604 | 1 | S13-17 |
| L13-g | 13 | §13: "the containment becomes enforceable" items 1–3 | 630 | 642 | 3 | S13-18 to S13-20 |
| L13-h | 13 | Amendment from 09 §3: "An assertion your negative list must carry" (runner `System.out`) | 794 | 800 | 0 | (superseded; see Not transcribed) |
| L13-i | 13 | Amendment from 09 §4: "The converter must set `WebAuthenticationDetails`, asserted" | 807 | 808 | 1 | S13-21 |
| L13-j | 13 | Amendment from 28 item 2: "the negative list's runner entry becomes a content assertion" | 856 | 859 | 1 | S13-22 |
| L13-k | 13 | Amendment from 28 item 4: "There is an owed build check on which uid" | 877 | 878 | 1 | S13-23 |
| L14-a | 14 | Inherited from 20: "Confirm the built `index.html` contains no inline script" | 90 | 92 | 1 | S14-01 |
| L14-b | 14 | Inherited from 20: "the CSP assertion proper runs against `vite preview`" | 97 | 98 | 1 | S14-02 |
| L14-c | 14 | §2: "the client owns the header, and one test binds them" | 226 | 229 | 1 | S14-03 |
| L14-d | 14 | §3: "No service worker, as a negative assertion with a test" | 255 | 257 | 1 | S14-04 |
| L14-e | 14 | §5: "ticket 09's 429 must never carry that member … it needs the test named" | 308 | 312 | 1 | S14-05 |
| L14-f | 14 | §5: "the new test reproduces the loop with the fix removed" | 332 | 333 | 1 | S14-06 |
| L14-g | 14 | §7: "a test deletes the guard and asserts the admin data still cannot render" | 394 | 396 | 1 | S14-07 |
| L14-h | 14 | §10: "Owed: a negative assertion that nothing mounted emits an inline script" | 457 | 457 | 1 | S14-08 |
| L14-i | 14 | §10: "`build.chunkImportMap` … "keep it false" is an assertion" / "the real control is the check" | 458 | 462 | 2 | S14-09, S14-01 (ref) |
| L14-j | 14 | §11: "Ticket 16's assertion is revoke-count equals create-count" | 504 | 506 | 1 | S14-10 |
| L14-k | 14 | §13 Accessibility (behaviours; accessibility assertions per 16 §8) | 537 | 556 | 13 | S14-11 to S14-23 |
| L14-l | 14 | §15 item 2: "The assertion is positive: the injected sheet exists and carries rules" | 590 | 594 | 1 | S14-02 (ref) |
| L14-m | 14 | §16: "Test delta (eleven)" | 627 | 635 | 12 | S14-10, S14-02, S14-01, S14-08, S14-03, S14-05, S14-06, S14-07, S14-04, S14-24, S14-25, S14-26 |
| L14-n | 14 | Amendment from ticket 16 (test plan) | 653 | 659 | 3 | S14-27, S14-02 (ref), S14-28 |
| L15-a | 15 | §3: "Build-phase controls — eight assertions, handed to ticket 16" | 231 | 234 | 8 | S15-01 to S15-08 |
| L15-b | 15 | §2 TM-04: "ticket 16 owes an assertion enumerated from the matrix" | 184 | 186 | 1 | S15-01 (ref) |
| L15-c | 15 | §2 TM-02 amended by 27: "this is asserted by ticket 16 row 4" | 178 | 179 | 1 | S15-04 (ref) |
| L15-d | 15 | Handover item (TM-07): "status: asserted-by-test … both meters are non-zero" | 295 | 297 | 1 | S15-08 (ref) |
| L15-e | 15 | Handover item (TM-06): "a non-allowlisted class fails to deserialise" | 307 | 307 | 1 | S15-07 (ref) |
| L15-f | 15 | Amendment from 28: "The argument-gating assertion went to ticket 16" | 375 | 375 | 1 | S15-05 (ref) |
| L15-g | 15 | Amendment from 27: "The mitigation is asserted by ticket 16 row 4, which is restated" | 385 | 386 | 1 | S15-04 (ref) |

## Rows
| key | pillar | control | assertion | level | context | isolation | clause | source | polarity | dup | note |
|---|---|---|---|---|---|---|---|---|---|---|---|
| S13-01 | AUD | `user.target.*` keys write cleanly through the structured encoder | Emitting an admin row with `user.target.id` (and `user.target.roles`) via `addKeyValue` produces a well-formed NDJSON line with those keys as siblings of `user.id`, not a mapping conflict or dropped key. | U | none | none | 13 §3; ASVS 16.2.1 (L2) | 13:292 | pos | | First-implementation test turning ticket 03's inference into a fact. |
| S13-02 | AUD | Audit catalogue consistency | Parameterised over every `AuditEvent` enum member: the emitted row carries the full Tier A/B field set plus `url.path` and `http.request.method` on request-scoped rows, and its reason belongs to the event's reason family; the degraded-row path is never reached. | U | none | none | Std §3.3; Std §3.4; ASVS 16.2.1 (L2) | 13:500; 13:498 | pos | | One parameterised row (13 §9 item 5 "consistency test"); 13:498's "parameterised test" is the same test. |
| S13-03 | AUD | Audit catalogue completeness | Every Std §3.3 required event maps to at least one `AuditEvent` member; the three N/A events (security-header config change, critical config change, bulk export) are explicit negative entries asserting no mutating endpoint or property exists. | U | none | none | Std §3.3; ASVS 16.3.3 (L2) | 13:501; 13:258 | pos | | 13 §9 item 5 "completeness test"; 13:258–266's three N/A negative assertions folded in as the source says. |
| S13-04 | AUD | ASVS 16.1.1 inventory generated from the enum | Snapshot test: the log-inventory table (events, fields, levels, destination, retention, who-can-read) generated from `AuditEvent` matches the committed catalogue document. | U | none | none | ASVS 16.1.1 (L2) | 13:508 | pos | | Destination/retention/access columns required by 13 §12 (13:592–594). |
| S13-05 | AUD | No password in logs via exception message (Jackson source redaction) | A real malformed credential-bearing request to the login endpoint yields a logged parse message containing `REDACTED` and not the submitted password, on any field of any appender. | C | ctx-default | keyed | Std §3.4; 13 §10 | 13:540; 13:557 | neg | | Asserts outcome, not the `INCLUDE_SOURCE_IN_LOCATION` flag; 13:557 table row is the same test. |
| S13-06 | AUD | No credential-token plaintext or hash logged | Across the reset flow, neither the token plaintext nor its stored hash appears in any appender (all loggers, stdout/stderr, audit file). | C | ctx-default | keyed | Std §3.4; 13 §10 | 13:558 | neg | | |
| S13-07 | AUD | No CSRF token or raw session id logged | Across an authenticated flow, the CSRF token and raw session id appear in no appender while `session.hash` is present on the rows. | C | ctx-default | keyed | Std §3.4; ASVS 16.2.5 (L2) | 13:559 | neg | | |
| S13-08 | AUD | No TOTP material logged | On rows 36–42 (enrol, confirm, verify, fail, tier-1, tier-2, decrypt mismatch) no TOTP secret, `otpauthUri`, ciphertext or key material appears in any appender. | C | ctx-default | keyed | Std §3.4; 13 §10 | 13:560 | neg | | |
| S13-09 | AUD | No cleartext identity keys on audit rows | Key-absence across every emitted audit row: no raw username, email, `user.name` or `user.hash`. | C | ctx-default | keyed | Std §3.4; 13 §5 | 13:561; 13:880 | neg | | 13:880 (amendment from 28) confirms the assertion is untouched by `labels.operator_claimed_id` / `process.real_user.name`. |
| S13-10 | AUD | No key material; rejected config values never logged | No key material appears at any level, and a rejected configuration value is never written to any log. | C | ctx-default | none | 13 §10; 24 | 13:562 | neg | ≡? 24 (key-material / rejected-value rule) | Rejected-value half needs a failing-startup context; merge to confirm context. |
| S13-11 | AUD | No throwable attachable to an audit row | Structural: the audit emitter's `emit` exposes no `Throwable` parameter and no main-code call on the `audit` logger uses `setCause`. | A | archunit | none | 13 §9; 13 §10 | 13:563; 13:494 | neg | | Level A chosen for "structural"; 13:494 is the constraint it proves. |
| S13-12 | AUD | Log injection neutralised | A CRLF payload sent through a real request against an unmatched path appears in the audit row only in its sanitised form (CR, LF and pipe stripped at the single emitter). | P | ctx-port | keyed | ASVS 16.4.1 (L2) | 13:564 | pos | | P: raw client bytes on a pre-handler row need Tomcat. Asserts sanitised form, not raw absence. |
| S13-13 | AUD | Raw-URI field length cap | A request with an over-long raw URI on a pre-handler row produces an audit row whose raw-URI field is truncated to the cap. | P | ctx-port | keyed | ASVS 16.4.1 (L2); 13 §10 | 13:565 | neg | | |
| S13-14 | AUD | `user.id` absent on unresolved login failure | A failed login for an unknown username emits row 2 with no `user.id` key (and no `user.hash`). | C | ctx-default | keyed | Std §3.4; 13 §5 | 13:566 | neg | | Split: 13:566 says "two paired tests". |
| S13-15 | AUD | `user.id` present on resolved login failure | A failed login for an existing account emits row 2 carrying that account's UUID `user.id`, set at the listener. | C | ctx-default | keyed | Std §3.4; 13 §5 | 13:566 | pos | | Split partner of S13-14; inverts recipe line 305. |
| S13-16 | AUD | `user.id` never leaks from MDC onto pre-auth rows | Key-absence of `user.id` on rows 2 (unresolved), 5, 6, 11, 16 (reason `EXISTING_ADDRESS`), 17 and 20. | C | ctx-default | keyed | Std §3.4; 13 §5 | 13:567 | neg | | |
| S13-17 | AUD | Audit file appender has no `total-size-cap` | The effective audit rolling-file appender has daily rollover, `max-history` 90 and no `total-size-cap`, read from the production logging configuration. | C | ctx-nondev | none | Std §3.4; 13 §12 | 13:603 | neg | | Asserts off configuration. |
| S13-18 | CFG | Reset-link logger property refused outside dev | Setting the dev-only reset-link logger's property in a non-dev profile fails the refresh-phase validator (startup refused). | C | ctx-nondev | none | 13 §13; 24 §validator | 13:632 | neg | ≡? 24 (prohibited-configuration entry) | Context built with the property set; startup must fail. |
| S13-19 | CFG | Effective reset-link logger level checked at ApplicationReadyEvent | With `LOGGING_LEVEL_<reset-link logger>=DEBUG` in a non-dev profile, the `ApplicationReadyEvent` check reading `LoggingSystem.getLoggerConfiguration` refuses to run. | C | ctx-nondev | none | 13 §13 | 13:634 | neg | | Catches the path S13-18's validator cannot see. |
| S13-20 | CFG | `/actuator/loggers` not writable outside dev | Outside dev, `/actuator/loggers` is absent or read-only: a POST changing a logger level is refused and the level is unchanged. | C | ctx-nondev | none | 13 §13 | 13:640 | neg | ≡? 21 (actuator exposure) | |
| S13-21 | AUD | Converter sets `WebAuthenticationDetails` | Failed-login and breach rows from two distinct source keys carry non-null, distinct `source.ip_hash` values (the Route C converter sets `WebAuthenticationDetails`). | C | ctx-default | keyed | 13 §4; 09 §R | 13:807 | pos | ≡? 09 §R (WebAuthenticationDetails assertion) | Unset, every row still carries one hash, so the assertion is distinctness. |
| S13-22 | AUD | Rebinding runner emits no secret | Run the rebinding runner with a known password on stdin; that string appears in none of stdout, stderr or the audit file. | R | runner | own-DB | 13 §10; 28 §11 | 13:856 | neg | ≡? 28:377 (28 test 9) | Replaces the retired `System.out` assertion 13:794–800 (superseded by 28 test 9). |
| S13-23 | RUN | Runner OS-reported identity | Build check recording which uid `ProcessHandle.current().info().user()` reports for the runner, and that the row records absence when the Optional is empty. | R | runner | own-DB | 28 §14 | 13:877 | pos | ≡? 28:334 | Owed build check named in 13's amendment from 28. |
| S14-01 | BLD | Built `index.html` has no inline script | Grep the production `index.html` from `vite build`: no inline `<script>` (including `type="importmap"` or modulepreload polyfill). | B | build | none | 20 constraints; 14 §10 | 14:92; 14:462; 14:628 | neg | ≡? 20 | Split from test-delta item 3 (14:628): its two halves are separately owed (14:92/462 vs 14:457) and need levels B and F. |
| S14-02 | HDR | Document CSP enforced without violations | Under `vite preview` with the production policy, the app (including the OTP field) raises zero `securitypolicyviolation` events and `input-otp`'s injected stylesheet exists and carries rules. | E | playwright | none | 20 CSP; 14 §10; 14 §15 | 14:98; 14:594; 14:627; 14:656 | neg | ≡? 16:598 | Restated by 14's amendment from 16 (count violations = 0); "no warning" form rejected (14:592). |
| S14-03 | AUTH | SPA `Accept` header bound to server entry-point matcher | The SPA's exported request headers are `Accept: application/json, application/problem+json` with no `X-Requested-With`, and a request carrying exactly those headers to a protected endpoint gets the problem+json 401, not a redirect or 406. | C | ctx-default | keyed | 06 §406; 14 §2 | 14:228; 14:629 | pos | ≡? 23 (browser-request matcher) | "One test binds them"; level C with the header set taken from the client's shared constant is a judgement. |
| S14-04 | E2E | No service worker | After loading and signing in, `navigator.serviceWorker.getRegistrations()` is empty. | E | playwright | none | 14 §3 | 14:255; 14:633 | neg | | E chosen: jsdom has no service-worker registry. |
| S14-05 | MFA | Per-IP 429 carries no `factor` member | A 429 from ticket 09's early per-IP filter on `POST /api/mfa/totp/verification` has no `factor` member, while the factor tier-1 429 does. | C | ctx-default | keyed | 14 §5; 06 producers | 14:309; 14:631 | neg | ≡? 06 / 09 (producer relationship) | Discriminator across two envelope producers. |
| S14-06 | FE | Tier-2 factor state is terminal | With `factors.rebindRequired` true (and 423 `FACTOR_DISABLED`), the app reaches the terminal screen and the TOTP prompt is never offered; the test runs before both factor tests and fails with the fix removed. | F | vitest | none | 14 §5 | 14:332; 14:631 | neg | | |
| S14-07 | FE | Client route guard is not the control | With the admin route guard deleted, admin data still does not render because the (MSW) server refuses it. | F | vitest | none | 14 §7 | 14:394; 14:632 | neg | | |
| S14-08 | HDR | No mounted component emits inline script | Mounting every component (including Base UI with `CSPProvider disableStyleElements`) inserts no inline `<script>` element into the document. | F | vitest | none | 14 §10 | 14:457; 14:628 | neg | | Split partner of S14-01; deferral-register entry until asserted (14:622). |
| S14-09 | BLD | `build.chunkImportMap` stays false | The effective Vite build config has `build.chunkImportMap` false and `build.assetsInlineLimit` 0. | B | build | none | 20 constraints; 14 §10 | 14:459 | neg | ≡? 20 | assetsInlineLimit included as the sibling constraint of 14:458; merge may drop it. |
| S14-10 | FE | QR object-URL effect is symmetric | Across a simulated remount (StrictMode / Fast Refresh), `URL.revokeObjectURL` call count equals `URL.createObjectURL` call count. | F | vitest | none | 14 §11 | 14:504; 14:627 | pos | ≡? 22 | Image-renders assertion explicitly insufficient. |
| S14-11 | FE | Invalid-code message is an alert | The invalid-code message in the TOTP dialog has `role="alert"`. | F | vitest | none | 14 §13 | 14:540; 14:549 | pos | ≡? 16 §8 (MFATotpForm tests) | Accessibility behaviour (16 §8). |
| S14-12 | FE | Labels and error association | Every form field has an accessible label and every field error is linked by `aria-describedby`. | F | vitest | none | 14 §13 | 14:541 | pos | | Accessibility behaviour (16 §8). |
| S14-13 | FE | Focus on first error | On submit failure focus moves to the first invalid field. | F | vitest | none | 14 §13 | 14:541 | pos | | Accessibility behaviour. |
| S14-14 | FE | Focus return on dialog close | Closing a dialog returns focus to its trigger. | F | vitest | none | 14 §13 | 14:542 | pos | | Accessibility behaviour. |
| S14-15 | FE | Live region for async failures | An async request failure is announced through a live region. | F | vitest | none | 14 §13 | 14:542 | pos | | Accessibility behaviour. |
| S14-16 | FE | Keyboard paths through admin table | The admin user table and its enable/disable, role-change and delete confirm dialogs are fully operable by keyboard. | F | vitest | none | 14 §13 | 14:543 | pos | | Accessibility behaviour. |
| S14-17 | FE | OTP autofocus | When the code prompt opens, focus is on slot 0. | F | vitest | none | 14 §13 | 14:546 | pos | ≡? 16 §8 (MFATotpForm tests) | |
| S14-18 | FE | OTP paste fills slots | Pasting a six-digit string fills all six slots. | F | vitest | none | 14 §13 | 14:547 | pos | ≡? 16 §8 (MFATotpForm tests) | |
| S14-19 | FE | OTP no auto-submit | Entering a complete code sends no verification request until Verify is clicked. | F | vitest | none | 14 §13 | 14:547 | neg | ≡? 16 §8 (MFATotpForm tests) | |
| S14-20 | FE | OTP cleared on Verify | The code field clears on every Verify click. | F | vitest | none | 14 §13 | 14:548 | pos | ≡? 16 §8 (MFATotpForm tests) | |
| S14-21 | FE | Dialog dismissal drops step-up queue | Esc or overlay dismissal closes the code dialog and drops the queued step-up requests (none replayed). | F | vitest | none | 14 §4; 14 §13 | 14:549 | neg | | |
| S14-22 | FE | Failed status probe fails closed | When the TOTP status probe fails, enrolment state is treated as unknown and the control is disabled, never "not enrolled". | F | vitest | none | 14 §13 | 14:552 | neg | ≡? 16 §8 (SetupMFA tests) | Decided behaviour inverting 22's fail-open mount check; no explicit "test" wording. |
| S14-23 | FE | 409 `FACTOR_ALREADY_ENROLLED` surfaced distinctly | A 409 `FACTOR_ALREADY_ENROLLED` on provisioning renders its distinct copy, not the generic generation failure. | F | vitest | none | 14 §13; 23 §7 | 14:553 | pos | ≡? 16 §8 (SetupMFA tests) | Decided behaviour; no explicit "test" wording. |
| S14-24 | FE | Sign-out is terminal | Sign-out clears all local state and routes to sign-in on 204, 401 and 403 alike, with no retry or re-bootstrap. | F | vitest | none | 14 §3 | 14:633 | pos | | Parameterised over the three statuses. |
| S14-25 | FE | `PASSWORD_REJECTED` rules have distinct copy | Parameterised over the six rules (`MIN_LENGTH`, `MAX_BYTES`, `BLOCKLISTED`, `CONTEXT_TERM`, `TOO_WEAK`, `HISTORY_REUSE`): each renders distinct SPA copy. | F | vitest | none | 14 §9; IM8 as-5 | 14:633 | pos | | NIST 800-63B §3.1.1.2 guidance discharged by this copy. |
| S14-26 | FE | Client counts bytes, not characters | A 30-character multibyte password over 72 UTF-8 bytes (after NFC) is rejected client-side with the `MAX_BYTES` copy. | F | vitest | none | 14 §9; IM8 as-5 | 14:634 | neg | | |
| S14-27 | SES | `Clear-Site-Data` scoping observed | In Chromium and Firefox against `http://localhost`, sign-out's `Clear-Site-Data` is observed on the API origin and does not clear the SPA origin's storage (SPA clears its own). | E | playwright | none | 08 §Clear-Site-Data; 14 §3 | 14:655 | pos | ≡? 16:599; ≡? 08:372 | Reverses deferral-register entry 4 (14:624). |
| S14-28 | FE | MSW fixtures match backend enum schema | Every MSW fixture's `code` and rule values validate against the backend's exported enum schema. | F | vitest | none | 06 §enum; 16 §8 | 14:657 | pos | ≡? 06 (amendment from 16) | Own T-ID per 16 §8. |
| S15-01 | MFA | Factor gate enumerated from the authorization matrix | Generated from ticket 11's matrix: for every admin route and method, a session with `ROLE_ADMIN` + `FACTOR_PASSWORD` and no `FACTOR_TOTP` is refused; for every mutation, a factor older than 10 minutes is refused; the test asserts it is generated from the matrix. | C | ctx-default | keyed | TM-04 | 15:231; 15:184 | neg | ≡ 11:888; ≡ 23:1167; ≡? 16:234 | 16 §9: 11:888, 23:1167 and 15 #1 are one test. One matrix-generated ID. |
| S15-02 | MFA | Tier-2 trip lock order | Under contention with `AdminActionGuard`, the tier-2 TOTP trip acquires the `users` row before the `totp_user_details` row, so a correct code is not refused with a lock timeout. | C | ctx-locktimeout | keyed | TM-03 | 15:231; 15:180 | pos | ≡ 23:1144; ≡? 16:235 | 16 §9: 23:1144 and 15 #2 are one test. Context choice is a judgement. |
| S15-03 | RL | Unbudgeted routes are still budgeted | A request (including a CSRF-less POST to an unmatched path) to a route with no budget row is rate-limited by the default ticket 26 decides. | C | ctx-default | keyed | TM-01 | 15:231; 15:133 | pos | ≡? 16:236; ≡? 26 | |
| S15-04 | OBS | No inbound trace header ever sets `trace.id` | Cases: (i) per format (`traceparent` with and without `tracestate`, single `b3`, multi `X-B3-*`) logged `trace.id` differs from inbound; (ii) two requests pinning one `traceparent` get different `trace.id`s; (iii) with sampling 0.0 a `-01` request is not sampled; (iv) raw header value in no application log line, access log disabled. | P | ctx-port | keyed | TM-02; W3C Trace Context §3.4 | 15:231; 15:174; 15:178; 15:385 | neg | ≡? 16:237; ≡? 27:256 | Restated by 27 §7 (was length/content validation). One row: 27 frames (i)–(iv) as cases of one assertion. Companion guard (27:195) and baggage (27:208) tests are 27's, not emitted here. |
| S15-05 | RUN | Runner invocation is argument-gated | A bound property carrying the runner's name (env var or config) does not trigger the rebinding runner; only the command-line argument does. | R | runner | own-DB | TM-12 | 15:231; 15:148; 15:375 | neg | ≡? 16:238; ≡? 28 | |
| S15-06 | MFA | Recovery routes structurally absent | In `ctx-default`, no handler in `getHandlerMethods()` issues or redeems the issued-recovery-code token type, and ticket 26's three-registry disposition test contains no recovery entry. | C | ctx-default | none | TM-13 | 15:231; 15:207 | neg | ≡? 16:239; ≡? 16:589 | Restated by 16 Answer §7 (structural absence; no HTTP status pinned, so 16:446's denyAll 403 is not asserted). |
| S15-07 | SES | Session deserialisation filter | First-implementation check records the effective `ObjectInputFilter` on Spring Session's deserialisation path; then a non-allowlisted class fails to deserialise while `SecurityContext`, `FactorGrantedAuthority`, `AUTH_INSTANT` and the CSRF token round-trip. | C | ctx-default | none | TM-06; ASVS 13.2.2 (L2) | 15:231; 15:190; 15:307 | neg | ≡? 16:240 | |
| S15-08 | OBS | Thread pin and saturation meters | `server.tomcat.threads.max` binds the configured production value, and the Tomcat and Hikari saturation meters exist and are non-zero after a password path is exercised. | P | ctx-port | delta | TM-07; ASVS 6.1.1 (L1) | 15:231; 15:193; 15:295 | pos | ≡? 16:241; ≡? 21 | Binding half reads configuration (ctx-nondev) and meter half needs P; merge may split. |

## Not transcribed
| file:line | item | reason |
|---|---|---|
| 13:794 | Runner prints its token to `System.out`, never through logging (13:794–800) | Superseded by 28 test 9 (16 §9); replacement transcribed as S13-22. |
| 13:345 | Recipe's `loginFailure_emitsWarnEvent_withNoUserIdentity` `doesNotContainKey("user.id")` assertion | Inverted by this ticket, not owed; replaced by S13-14/S13-15. |
| 13:621 | File-permission assertion on the audit file | Explicitly declined ("No file-permission assertion from the application"). |
| 13:643 | Row 43 records audit-relevant loggers in force | "Evidence, not a control"; no test stated. |
| 14:168 | Prototype's headless checks of the pure module (15 codes to one action, terminal sign-out, one retry, gate order, queue drains, forgetting total) | Throwaway prototype exercise, not stated as owed; see Problems. |
| 14:659 | vendored shadcn/Base UI internals, zxcvbn score parity, visual styling at level F | Explicitly not tested at level F (CSP/cookie items go to E). |
| 14:433 | zxcvbn client/server score equality | Explicitly not asserted ("never score equality"). |
| 14:558 | Accessibility conformance target and keyboard/screen-reader verification method | Stays fog; not owed. |
| 15:203 | TM-10: row 46's `N` "no named binding test" | Handed to ticket 26, not owed from 15. |
| 15:271 | Handover: rebinding token absent from collector's index (rehearsal) | Deployer acceptance check, not a test; TM-12 closed by 28. |
| 15:283 | Handover: disk sizing recorded, truncation-row alert rule exists | Deployer acceptance check, not a test. |
| 15:319 | Handover: alert rule on row 18 `ADMIN_RESET` | Deployer acceptance check, not a test. |
| 15:330 | Handover: platform retention value recorded | Deployer acceptance check, not a test. |
| 15:347 | Handover: inbound `traceparent` does not survive the proxy hop | Item retired by ticket 27 ("Do not extract"). |

## Counts
| source ticket/section | n tests | keys |
|---|---|---|
| 13 §3 (292) | 1 | S13-01 |
| 13 §9 (498–509) | 3 | S13-02 to S13-04 |
| 13 §10 Jackson + negative-assertion set (537–567) | 12 | S13-05 to S13-16 (S13-05 also 13:557; S13-14/15 split from 13:566) |
| 13 §12 (603) | 1 | S13-17 |
| 13 §13 (630–642) | 3 | S13-18 to S13-20 |
| 13 amendment from 09 §4 (807) | 1 | S13-21 |
| 13 amendment from 28 (856–878) | 2 | S13-22, S13-23 |
| 14 inherited from 20 (92–98) | 2 | S14-01, S14-02 (other refs 14:462, 628, 594, 627, 656) |
| 14 §2 (228) | 1 | S14-03 (also 14:629) |
| 14 §3 (255) | 1 | S14-04 (also 14:633) |
| 14 §5 (309–333) | 2 | S14-05, S14-06 (also 14:631) |
| 14 §7 (394) | 1 | S14-07 (also 14:632) |
| 14 §10 (457–459) | 2 | S14-08, S14-09 (S14-08 also 14:628) |
| 14 §11 (504) | 1 | S14-10 (also 14:627) |
| 14 §13 accessibility (540–553) | 13 | S14-11 to S14-23 |
| 14 §16 test delta (627–635), first-sourced | 3 | S14-24 to S14-26 (other 8 delta items counted under their earlier first source) |
| 14 amendment from 16 (653–659) | 2 | S14-27, S14-28 |
| 15 §3 build-phase assertions (231) | 8 | S15-01 to S15-08 |

Total rows: 59 (13: 23, 14: 28, 15: 8).
