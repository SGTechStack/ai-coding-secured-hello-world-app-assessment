# Ticket 17 sizing gate — inventory part B (tickets 07, 08, 09)

Files read in full: `07-password-policy-and-hashing.md` (630 lines), `08-session-and-csrf-contract.md` (768 lines),
`09-lockout-and-dual-rate-limiting.md` (1632 lines). Line numbers are for the files as they stand now.
Abbrevs: A = adr-new, M = adr-amend, X = adr-reversed, J = adr-rejected, R = register, G = glossary, T = trigger,
H = handover. Where a ticket has no numbering of its own, `n` is assigned in source order.

## 1. Items

### Ticket 07

| id | kind | owner | source | title / gist (≤25 words) | status | shape |
|---|---|---|---|---|---|---|
| 07-A-1 | adr-new | 07 | 07:477 (also 07:28–30, 07:409–415) | BCrypt over Argon2id: PRD mandate, not a security argument; memory-hardness and 72-byte costs stated | live | adr |
| 07-A-2 | adr-new | 07 | 07:478 (also 07:155–162) | 72-byte ceiling and the ≥64-character SHOULD deviation, naming non-ASCII users as affected | live | adr |
| 07-A-3 | adr-new | 07 | 07:479–482 (also 07:188–191, 07:216–222) | Composition rules replaced by strength gate; lead with Standard never imposing complexity; `Password123!@#$` demo; SHALL NOT is not a floor | live | adr |
| 07-A-4 | adr-new | 07 | 07:483–484 (also 07:236–239) | Blocklist sourcing/sizing: breach corpus sliced ≥15 chars, citing App. A.3 and §3.1.1.2 excessive-size caution | live | borderline |
| 07-A-5 | adr-new | 07 | 07:485–486 | zxcvbn score-3 gate as deliberate stricter-than-NIST control; three misread clauses named (premises 07:424–433) | live | adr |
| 07-A-6 | adr-new | 07 | 07:487–488 (also 07:265–271) | `CompromisedPasswordChecker` implemented but not registered as bean; registering breaks ticket 06's uniform 401 | live | adr |
| 07-A-7 | adr-new | 07 | 07:489–490 (also 07:19) | `app.security.password.*` over `spring.password.sso.*` key, plus 15-char floor overriding PRD's 12 (carried from ticket 19) | live | borderline |
| 07-A-8 | adr-new | 07 | 07:63–64 | Prose only, in question text: password history 3 kept against NIST's drift — "the ADR should record the tension". Not in the numbered seven | live | borderline |
| 07-A-9 | adr-new | 07 | 07:129–131 | Prose only: cost-12 measurement of the five-BCrypt change request and the machine "the ADR must record". Could fold into 07-A-1 | live | borderline |
| 07-A-10 | adr-new | 07 | 07:389–403, 07:522–523, 07:575 | Prose only: pepper / keyed pre-hash declined on non-rotatability; amendments say "the ADR" must carry it. No numbered ADR exists | live | adr |
| 07-M-1 | adr-amend | 07 | 07:504–507 (Amendment from 11) | Target 07-A-7: floor ADR must say we were below a single-factor SHALL (final text), not adopting a recommendation | live | adr |
| 07-M-2 | adr-amend | 07 | 07:518–519 (Amendment from 11) | Target 07-A-3: composition rules are a verbatim SHALL NOT, so rejection is compelled, not preferred | live | adr |
| 07-M-3 | adr-amend | 07 | 07:520–523 (Amendment from 11) | Target 07-A-10: pepper is a declined SHOULD (key separately stored, HSM/TPM); ADR must name it so, not rotation alone | live | adr |
| 07-M-4 | adr-amend | 07 | 07:544–550 (Amendment from 10) | Target 07-A-2: ASVS 6.2.9 (L2) knowingly failed; cause restated as declined pre-hash (07:159–161 said PRD BCrypt mandate) | live | adr |
| 07-M-5 | adr-amend | 07 | 07:538–540 (Amendment from 10) | Target 07-A-4: ASVS 6.2.4 (L1) vindicates ≥15-char breach-corpus slice | live | borderline |
| 07-M-6 | adr-amend | 07 | 07:575–579 (Amendment from 12) | Target 07-A-10: pepper (credential, non-rotatable = disqualifying) vs tombstone HMAC (one-bit fact, tolerable) asymmetry belongs in this ADR | live | adr |
| 07-M-7 | adr-amend | 07 | 07:594–620 (Amendment from 25) | Target 07-A-7: 15 floor barred two ways (users single-factor; admin recovery companion); 15 is a raisable floor, not a composition rule | live | borderline |
| 07-R-1 | register | 07 | 07:320–322, 07:469–470 | Periodic credential expiry: deferral worded "would not" implement (SHALL NOT), not "have not" | live | |
| 07-R-2 | register | 07 | 07:544–550 | ASVS 6.2.9 (L2) knowingly failed: 72-byte ceiling rejects 64-char non-Latin passphrases; affected population non-Latin-script users | live | |
| 07-R-3 | register | 07 | 07:389–403, 07:520–523 | Pepper / keyed pre-hash (NIST SHOULD) not built; declined on rotation; residual is the 72-byte ceiling. Also ADR 07-A-10 | live | |
| 07-R-4 | register | 07 | 07:333–337, 07:541–543 | Mistyping allowances (trim, case-fold, space-collapse; all MAY) declined on record; 6.2.8 documented | live | |
| 07-R-5 | register | 07 | 07:404–408 | Live HIBP k-anonymity API not built; availability dependency; not prohibited by IM8/ARC | live | |
| 07-R-6 | register | 07 | 07:534–537, 07:626–630 | Documented context-specific word list (ASVS 6.1.2/6.2.11 L2) owed; refresh obligation now with ticket 25. Possibly a handover item instead | live | |

No glossary, trigger or `### Handover items (ticket 25)` content in 07.

### Ticket 08

| id | kind | owner | source | title / gist (≤25 words) | status | shape |
|---|---|---|---|---|---|---|
| 08-A-1 | adr-new | 08 | 08:458–459 (also 08:183–188) | Failed login does not invalidate an existing session; deviation from Failure Path 1 on DoS grounds (Standard defect four) | live | adr |
| 08-A-2 | adr-new | 08 | 08:460–461 (also 08:190–203) | Self-actor triggers terminate all *other* sessions; deviation from §3.5/§5:466, citing ASVS and `revokeOtherSessions` name | live | adr |
| 08-A-3 | adr-new | 08 | 08:462–463 (also 08:143–148) | No cookie `Max-Age`; server-side filter is sole absolute-lifetime control; rejects persistent cookie as implicit remember-me | live | borderline |
| 08-A-4 | adr-new | 08 | 08:464–465 (also 08:259–264) | Header-only CSRF resolution, overriding `_csrf` parameter fallback because §3.4 forbids logging tokens | live | adr |
| 08-A-5 | adr-new | 08 | 08:466–467 (also 08:392–410) | Two prescribed tests reinterpreted: §5:446 as superseded-session rejection; §5:499 CSRF-cookie clause as negative assertion | live | register |
| 08-A-6 | adr-new | 08 | 08:468–469 (also 08:222–229) | Session invalidation extended to four omitted triggers: admin disable, role change, soft-delete, lockout | live | adr |
| 08-A-7 | adr-new | 08 | 08:470–471 (also 08:131–141, 08:412–420) | Session-id rotation on factor grant and credential change; single `AUTH_INSTANT` stamping point; filter-ordering invariant | live | adr |
| 08-A-8 | adr-new | 08 | 08:472–473 (also 08:361–380) | `Clear-Site-Data` retained for compliance; SPA-side state clearing is the actual control, given per-directive origin scoping | live | borderline |
| 08-A-9 | adr-new | 08 | 09:1398–1399 (listed in 09's "New, nine"); 08:666–686; 09:1328–1329 | After-commit session dispatch plus idempotent reconciliation sweep for all five triggers; atomicity unavailable (`REQUIRES_NEW`). Ownership moved 09→08 | live | adr |
| 08-M-1 | adr-amend | 08 | 08:501–506 (Amendment from 09) | Target 08-A-7: ordering invariant reworded "after `SecurityContextHolderFilter`, before `CsrfFilter`", per-IP filter now first in chain | live | adr |
| 08-M-2 | adr-amend | 08 | 08:649–695 (Amendment from 09 §R) | Target 08-A-6: fifth trigger (cap disable); rule "user, TOTP rows in txn; session rows after commit"; sweep hosted on cleanup job | live | adr |
| 08-M-3 | adr-amend | 08 | 08:522–524 (Amendment from 10) | Target 08-A-6 trigger table: activation and invite redemption rows invalidate nothing, recorded so absence is not an oversight | live | borderline |
| 08-M-4 | adr-amend | 08 | 08:607–612 (Amendment from 13) | Target 08-A-7: audit strategy must sit after `ConcurrentSessionControl`, before `ChangeSessionId` in the login composite | live | borderline |
| 08-M-5 | adr-amend | 08 | 08:744–749 (Amendment from 29) | Target 08-A-7: absolute filter gains anonymous `CREATION_TIME` branch (invalidate, never set negative); composite resets `maxInactiveInterval` | live | adr |
| 08-M-6 | adr-amend | 08 | 08:699–731 (Amendment from 25) | Target 08-A-8: cookies directive reaches SPA's registered domain; storage/cache do not; credentials flag and service-worker conditions | live | borderline |
| 08-J-1 | adr-rejected | 08 | 08:382–387 | Dropping `.deleteCookies("JSESSIONID","SESSION")`: explicitly "implementation note rather than an ADR" | live | register |
| 08-J-2 | adr-rejected | 08 | 08:113–114 | Session numbers (15 min / 8 h / 1): "no ADR is owed for the values", standard defaults | live | register |
| 08-R-1 | register | 08 | 08:175–188 | Standard defect four: Failure Path 1 invalidates session on failed login, an unauthenticated DoS. Also 08-A-1 | live | |
| 08-R-2 | register | 08 | 08:394–402 | Standard defect five: §5:446 "redeemed CSRF tokens rejected" untestable under session-bound repository; reinterpreted | live | |
| 08-R-3 | register | 08 | 08:404–410 | Standard defect six: §5:499 / §3.5:346–350 CSRF-cookie attributes are dead text under synchroniser pattern; negative assertion instead | live | |
| 08-R-4 | register | 08 | 08:168–171 | Residual: evicted user gets generic 401, no "signed in elsewhere" explanation; distinction lives in audit log only | live | |
| 08-R-5 | register | 08 | 08:490–494 (Amendment from 09) | Known oddity: unauthenticated `GET /csrf` persists a session, violating safe-method semantics; unfixable under §3.1:238 | live | |
| 13-R-1 | register | 13 | 08:622–634 (Amendment from 13) | Idle expiry audit row declined on scope (no JDBC events; reaper out of scope); lazy `UNKNOWN_OR_EXPIRED` ambiguous row | live | |
| 08-G-1 | glossary | 08 | 08:475–476 | auth instant: absolute-lifetime anchor, distinct from session creation time | live | |
| 08-G-2 | glossary | 08 | 08:476–477 | superseded session: displaced by newer login or credential change, distinct from expired | live | |
| 08-G-3 | glossary | 08 | 08:477 | factor freshness: the `getIssuedAt()`-based window `validDuration` reads | live | |

No trigger or `### Handover items (ticket 25)` content in 08 (08:454 is a handoff bullet under §13, not that heading).

### Ticket 09 — original resolution layer (09:139–715) and inherited amendments

| id | kind | owner | source | title / gist (≤25 words) | status | shape |
|---|---|---|---|---|---|---|
| 09-A-1 | adr-new | 09 | 09:633 | Lockout duration 20 min, not PRD's 15 (standard wins on control behaviour) | live (amended, see 09-M-1) | borderline |
| 09-A-2 | adr-new | 09 | 09:636–637 | 20-min observation window added where Standard has none; same mechanism 22 called a defect; paced-attack limitation stated | live (amended, see 09-M-2) | adr |
| 09-A-3 | adr-new | 09 | 09:639–640 (struck through) | NIST §3.2.2 cap not implemented; calibration and failure-mode legs; detection compensating control; lm-16 gap | superseded by 09:640–644 and 09:1393 | adr |
| 09-A-4 | adr-new | 09 | 09:645–646 (also 09:514–523) | PRD Story 3 lockout-prevention criterion not met; 240× headroom; state-independence reading adopted | live | borderline |
| 09-A-5 | adr-new | 09 | 09:647 (also 09:527–533) | ASVS 6.1.1 satisfied with documented residual; malicious account lockout accepted | live (re-argued, see 09-M-10) | register |
| 09-A-6 | adr-new | 09 | 09:648–649 (also 09:550–578) | Progressive backoff declined (thread-pool exhaustion); bot detection declined (scope); `RateLimit` headers declined (draft) | live (amended, see 09-M-3) | adr |
| 09-A-7 | adr-new | 09 | 09:654–655 (also 09:408–440) | `forward-headers-strategy: framework` prohibited; trusted proxies must be named or context refresh fails | live | adr |
| 09-A-8 | adr-new | 09 | 09:656–659 | Admin password reset does not clear lock, following standard L131 over recipe `ResetPasswordCommand`; friction to ticket 25 handover | live (confirmed 09:1112) | adr |
| 09-A-9 | adr-new | 09 | 09:660 (also 09:223–227) | `is_account_non_locked` derived, not stored, deviating from admin recipe's entity | live | borderline |
| 09-A-10 | adr-new | 09 | 09:661 (also 09:486–495) | §5:453 distributed-limiter test deferred; single-instance asserted by startup check | live | register |
| 09-M-4 | adr-amend | 09 | 09:862–873 (Amendment from 13) | Target 09-A-3: NIST framing corrected — SHALL is consecutive; "the deviation is the remedy, not the ceiling"; 100's rationale matches auto-lift | superseded by 09:1393 | adr |
| 09-M-5 | adr-amend | 09 | 09:874–879 (Amendment from 13) | Target 09-A-6: progressive delays are NIST additions, not alternatives; recorded as unadopted addition | superseded by 09:1193–1200 and 09:1392–1393 | adr |
| 09-M-11 | adr-amend | 09 | 09:719–725 (Amendment from 10) | Target 09-A-5 / §10: malicious-lockout residual "closed" — reset redemption clears lockout (WSTG tier 2) | superseded by 09:1075–1082 and 09:1373–1378 | borderline |
| 09-R-6 | register | 09 | 09:527–533 | ASVS 6.1.1 pass-with-note with 20-min auto-lifting residual | superseded by 09:1373–1381 | |
| 09-R-8 | register | 09 | 09:496–511 | NIST §3.2.2 cap deviation: no ceiling, detection (50/24h) as compensating control, itself an IM8 lm-16 gap | withdrawn by 09:478–481 | |
| 09-R-9 | register | 09 | 09:512 | Per-account 429 not reachable by an attacker; recorded rather than fixed | live (scoped to username axis by 09:482–485; 09:1632) | |
| 09-R-10 | register | 09 | 09:486–495 | Distributed-deployment consistency (§5:453) unimplementable; deferred; no H2/generic JDBC `ProxyManager`. Also 09-A-10 | live | |
| 09-R-11 | register | 09 | 09:349–355 | `PATCH /api/profile/password` keyed per IP: one user can consume an office's budget; accepted residual | live | |
| 09-R-12 | register | 09 | 09:544–548 | Self-service unlock (WSTG tier 2) not taken; added to map's "Not yet specified" | withdrawn by 09:719–725 | |
| 09-R-13 | register | 09 | 09:737–739 (Amendment from 10) | Standard's tenth defect: per-account limit mandated on redemption, where account is knowable only after token lookup | live | |
| 09-R-14 | register | 09 | 09:874–879 (Amendment from 13) | Progressive delay recorded as unadopted NIST addition | superseded by 09:1193–1200 | |
| 09-R-16 | register | 09 | 09:391–393 | Correct password on expired credential neither increments nor resets; stale counter persists; accepted | live | |
| 02-R-1 | register | 02 | 09:276–283 | Corrects ticket 02's "two counters cannot both bite" defect: per-account 10/min is reachable by mixed traffic; number unchanged | live | |
| 09-T-1 | trigger | 09 | 09:510–511 | Weakening password floor or strength gate, or arrival of a recovery flow, brings the NIST cap back | withdrawn by 09:478 (cap implemented) | |
| 09-G-1 | glossary | 09 | 09:665–666 | Lockout: temporary, account-bound, auto-lifting, DB-persisted, escalating 20/40/60. Amended in place by §R (09:1402); prior flat text not retained | live | |
| 09-G-2 | glossary | 09 | 09:667 | Throttle: per source or submitted key, in-memory, 429, no account state | live | |
| 09-G-3 | glossary | 09 | 09:668–672 | Disable: no auto-lift, exited only by rebinding; policy-driven and automatic, never administrative. Earlier clause withdrawn in place | live | |
| 09-G-5 | glossary | 09 | 09:676 | Observation window: period within which failures count as consecutive | live | |
| 09-G-6 | glossary | 09 | 09:677–678 | Client IP: resolved address limiter keys on. Ticket 31 replaces the key with "source key" (09:1531–1534) | live (key renamed by 09:1531) | |

### Ticket 09 — §R re-resolution layer (09:1034–1442) and later amendments

| id | kind | owner | source | title / gist (≤25 words) | status | shape |
|---|---|---|---|---|---|---|
| 09-M-1 | adr-amend | 09 | 09:1392 (also 09:633–635, 09:198–201) | Target 09-A-1: duration escalates 20/40/60; 60-min rung outside WSTG band, NIST §3.2.2 governs | live | adr |
| 09-M-2 | adr-amend | 09 | 09:1392 (also 09:637–638, 09:204–207) | Target 09-A-2: window and duration split into two values with `duration ≥ window` | live | borderline |
| 09-M-3 | adr-amend | 09 | 09:1392–1393 (also 09:649–653) | Target 09-A-6: decline narrowed to sleep-based form; escalating form taken as lockout duration; bot detection and headers stay declined | live | adr |
| 09-X-1 | adr-reversed | 09 | 09:1393–1394 (also 09:640–644, 09:1038–1040) | ADR 3 flipped: NIST cap implemented at 100 consecutive under "MAY impose lower limits"; 100 throughput-minimising; detection retained | live | adr |
| 09-A-11 | adr-new | 09 | 09:1394–1395 (§R.1, 09:1045–1071) | Consecutive (reset on password success) vs ticket 23's cumulative counting; NIST "authenticators that were used" as authority | live | adr |
| 09-A-12 | adr-new | 09 | 09:1395–1396 (§R.4, 09:1161–1185) | Cap refusal in pre-authentication checker slot; post-auth slot would be a password-correctness oracle in the audit stream | live | adr |
| 09-A-13 | adr-new | 09 | 09:1396 (§R.4, 09:1149–1160) | Own-state columns (`consecutive_failures_since_success`, `password_disabled_at`), not `enabled = false` | live | borderline |
| 09-A-14 | adr-new | 09 | 09:1396–1397 (§R.3, 09:1109–1145) | Operator rebinding entrypoint with four constraints; rejected "is recovery working?" enforcement gate | live (amended, see 09-M-6) | adr |
| 09-A-15 | adr-new | 09 | 09:1397 (§R.5, 09:1193–1233) | Escalating lockout ladder 20/40/60 as NIST technique 2, expressed as duration not `Retry-After`; three costs | live (amended, see 09-M-8) | adr |
| 09-A-16 | adr-new | 09 | 09:1397–1398 (§R.6, 09:1247–1277) | Per-source cardinality axis (distinct accounts driven into lockout, k=5/h); three rejected formulations; IP-rotation ceiling | live (amended, see 09-M-7, 09-M-9) | adr |
| 09-A-17 | adr-new | 09 | 09:1399–1400 (§R.9, 09:1333–1352; 09:964–971) | Narrow multi-authenticator reading of §3.2.2; middle path: tier-2 factor disable forces password rebinding via ticket 11's flag | live | adr |
| 09-A-18 | adr-new | 09 | 09:1400 (§R.10, 09:1383–1390) | Reserved-name username denylist, one set two readers, justified on availability not anti-enumeration or 6.3.2 | live | borderline |
| 09-M-6 | adr-amend | 09 | 09:1486–1510 (Amendment from 28) | Target 09-A-14: runner is offline same-jar run (planned outage); 6.4.1 citation inverted; constraints 1, 3, 4 superseded; 2 stands | live | adr |
| 09-M-7 | adr-amend | 09 | 09:1539–1551 (Amendment from 31) | Target 09-A-16: "7×" was a units error, ≈100× per bucket; binds below ~20 source keys; reading (B) refusal with first-insertion expiry | live (supersedes 09:1272–1277 ceiling) | adr |
| 09-M-8 | adr-amend | 09 | 09:1535–1538, 09:1552–1555 (Amendment from 31) | Target 09-A-15: fencepost 840 min to cap, alert 260 min, ~9.7 h warning; startup floor fails refresh below 840/580 | live (supersedes 09:1211–1214 figures) | borderline |
| 09-M-9 | adr-amend | 09 | 09:1581–1588 (Amendment from 16) | Target 09-A-16: k = 5 exactly; "rolling hour" is a fixed hour from each member's first lock | live | borderline |
| 09-M-10 | adr-amend | 09 | 09:1373–1381, 09:1486–1492 | Target 09-A-5: 6.1.1 verdict now conditional on runner and ladder; then "pending first rehearsal", reverts to F on failure. Not in §R's amended list | live | adr |
| 11-M-1 | adr-amend | 11 | 09:1235–1245, 09:1411 | Target ticket 11 ADR 13: auto-expiry closes lockout path; rebinding entrypoint closes cap path; two-admin invariant monitorable-only | superseded by 09:1568–1572 | adr |
| 11-M-2 | adr-amend | 11 | 09:1568–1572 (Amendment from 30) | Target ticket 11 ADR 13: three routes keyed on `authenticable` (auto-expiry; in-app reset; runner outage); seeding one conditional on second admin | live | adr |
| 09-R-1 | register | 09 | 09:1405, 09:1373–1381 | ASVS 6.1.1 conditional verdict: final clause ("prevent malicious account lockout") passes only because runner and ladder land | live (qualified by 09:1489–1492) | |
| 09-R-2 | register | 09 | 09:1405, 09:1272–1275 | IP rotation defeats the per-source cardinality axis | live | |
| 09-R-3 | register | 09 | 09:1405–1406, 09:1276–1277 | Shared-NAT false positive on the cardinality axis | live | |
| 09-R-4 | register | 09 | 09:1406, 09:1143–1145 | Recovery requires deploy-level access; exists on paper if sole admin lacks shell | live (reaffirmed 09:1556–1557) | |
| 09-R-5 | register | 09 | 09:1406–1407 | Mass primitive's 36/hour residual after the 7× reduction | superseded by 09:1544–1546 | |
| 09-R-7 | register | 09 | 09:1544–1546 (Amendment from 31) | Mass primitive: 36/h from one unlimited source; ≈0.36/h per limited bucket; never faster than ≈14 h per account | live | |
| 09-R-15 | register | 09 | 09:1468–1474 (Amendment from 25) | Runner's console print fails NIST §4.2.2 options and arity; accepted failure, one cause: no mail transport outside `dev` | live | |
| 09-T-2 | trigger | 09 | 09:1491–1492 (Amendment from 28) | ASVS 6.1.1 reverts from pass-with-note to F if the runner's first rehearsal fails | live | |
| 11-T-1 | trigger | 11 | 09:1187–1191, 09:1411–1412 | Reopen trigger for ticket 11: 30-day `credentialIssuedAt` expiry on `isCredentialsNonExpired()` is a password-correctness oracle | live | |
| 09-G-4 | glossary | 09 | 09:673–675 (added per 09:1402) | Authenticator disable: NIST §3.2.2 state on one authenticator, distinct from ticket 11's admin account disable | live | |
| 09-G-7 | glossary | 09 | 09:1403 | cap counter | live | |
| 09-G-8 | glossary | 09 | 09:1403 | rebinding | live | |
| 09-G-9 | glossary | 09 | 09:1403 | cardinality axis | live | |
| 09-G-10 | glossary | 09 | 09:1403 | authenticable: one predicate over five terms across two tables (defined 09:1242–1245) | live | |
| 09-G-11 | glossary | 09 | 09:1403 | reconciliation sweep. Mechanism now owned by 08 (08:683–686, 09:1328–1329); term may belong with 08-A-9 | live | |

No `### Handover items (ticket 25)` heading in 09 (09:680 is `### Handoffs`; 25-bound bullets at 09:708–710, 09:1423–1424).

## 2. Per-ticket tally (stated vs enumerated)

| ticket | kind | stated | enumerated | note |
|---|---|---|---|---|
| 07 | adr-new | 7 (07:469, 07:475–490) | 10 | MISMATCH: three prose-only ADR claims outside the list — history tension (07:64), cost-12 measurement (07:130), pepper decline (07:522, 07:575) |
| 07 | adr-amend | — | 7 | No stated count; five amendments from 11/10/12/25; two target the unlisted pepper ADR |
| 07 | register | 1 (07:469–470, "would not") | 6 | MISMATCH: only the expiry deferral is addressed to 17; the other five are declines/fails recorded in-body |
| 07 | glossary / trigger / handover | 0 / 0 / 0 | 0 / 0 / 0 | — |
| 08 | adr-new | 8 (08:456) | 9 | MISMATCH: 08-A-9 (after-commit dispatch + sweep) is listed in 09's "New, nine" but ownership moved to 08 (09:1328–1329, 08:666) |
| 08 | adr-amend | — | 6 | No stated count |
| 08 | adr-rejected | — | 2 | Both are explicit "no ADR" statements |
| 08 | register | 3 defects ("total to six", 08:100) | 6 (incl. 13-R-1) | MISMATCH: three defects match; plus eviction residual, safe-method oddity, and 13's idle-expiry decline |
| 08 | glossary | 3 (08:456) | 3 | match |
| 08 | trigger / handover | 0 / 0 | 0 / 0 | — |
| 09 | adr-new (original) | 10 numbered (09:631–661) | 10 | match; 09-A-3 superseded |
| 09 | adr-amend (§R) | 3 (1, 2, 6; 09:1392–1393) | 3 in §R + 7 more = 10 owned by 09, plus 2 on 11's ADR 13 | MISMATCH: 09-M-4/5/11 are pre-§R amendments that §R superseded; 09-M-6..9 come after §R (28, 31, 16); 09-M-10 (ADR 5 re-argued) is missing from §R's list |
| 09 | adr-reversed | 1 (ADR 3, 09:1393) | 1 | match |
| 09 | adr-new (§R) | 9 (09:1394) | 9 | match in count, but one (after-commit dispatch) is reassigned to 08 as 08-A-9, so 09 owns 8 |
| 09 | adr-rejected | — | 0 | Rejected formulations sit inside 09-A-14 and 09-A-16, not as ADR candidates |
| 09 | glossary | none stated in original; §R: 2 amended + 1 added + 5 (09:1402–1403) | 11 | Consistent: 6 in original list (one of them added by §R) + 5 new. Lockout and Disable are amended in place, so their prior text can't be recovered |
| 09 | register | 5 (09:1405–1407) | 15 owned by 09 (8 live) + 02-R-1 | MISMATCH: §R's five (one superseded by 31's rewrite 09-R-7); the rest are pre-§R residuals/deferrals (several superseded or withdrawn) and 25's runner fail |
| 09 | trigger | — | 2 owned by 09 (1 live) + 11-T-1 | No stated count |
| 09 | handover | — | 0 | No `### Handover items (ticket 25)` heading |

## 3. Cross-file effects (for the aggregator)

- **19 ↔ 07:** 07-A-7 carries ticket 19's "15 overrides PRD's 12" ADR (07:19, 07:489–490). Dedupe against 19's list.
- **02:** 07:389–391 says ticket 02's pepper deferral reason ("blocked on secrets handling") has expired; the replacement reason is non-rotatability. 09:276–283 (02-R-1) corrects 02's "counters cannot both bite" defect.
- **11:** 07:531–533 (from 10) deletes the 20-char admin generator, which supersedes 07:366–385 and ticket 11's 20-char setting quoted at 07:525–527. Ticket 11's ADR 13 is amended at 09:1235–1245 (11-M-1), and that amendment is superseded by ticket 30 at 09:1568–1572 (11-M-2). There is a reopen trigger on 11's 30-day expiry at 09:1187–1191 (11-T-1). 09:1563–1567 supersedes argument two of the auto-lift triple; 11's route citations depend on it.
- **24:** 07:471–473: 24's key-rotation policy must scope to rotatable keys and state that no pepper exists. 07:565–568: 24's frozen tombstone-HMAC key is paired with 07-A-10. 09:1422: a prohibited-config entry is owed for the runner's output channel.
- **25:** 07:626–630: blocklist refresh content must name two lists (breach slice + context word list). 08:699–731: the handover item on `Clear-Site-Data` must be written from the body (cookies reach the SPA; storage/cache do not), not from bullet 08:454. 09:1423–1424: the runner ownership statement, batch procedure, and non-dev unrecoverability are owed to 25. 09:656–659: admin-reset lock friction goes to 25.
- **20:** 08:73–84: the `SameSite=Strict` ADR is ticket 20's and is only referenced here.
- **13:** 13-R-1 (08:622–634) is the idle-expiry decline. 09:1187–1191 corrects 13's `grace-expired` reasoning. 09:1415–1416 owes 13 cap/disable/cardinality rows.
- **29:** 08:735–756 deviates from 08:118–121 (anonymous sessions anchored on `CREATION_TIME`). 480/510 per source supersede ~150/~450 (08:127, 08:485, 09:345–347).
- **23:** 09:782 supersedes 09's naming of the multi-authenticator SHALL as declined/owned by 23 (09:626–629). 09-A-17 then makes it a 09 ADR, so it may overlap with 23's own ADRs.
- **28:** 09:1497 records a scoped withdrawal of the 6.4.6 pass on the runner path. That belongs to 28's register row.
- **19:** 09:1460–1461: a fourth throttled counter arrives if 19 reverses its recovery-codes deferral. Affects 19's deferral row.
- **31:** 09:1531–1534 renames "source IP" keys to "source key", which affects 09-G-6. 09:1583 says 31:190 and 31:448 hold reopening triggers on `k`; these belong to 31.
- **32:** 09:1624–1630 owes T-IDs (these are test-plan items, not 17's).
- **Within group B:** 08:679–681 replaces 09's ordering sentence at 09:793 ("user rows → TOTP rows → session rows"). 09:1328–1329 and 08:666 move ownership of 09's after-commit ADR to 08, as 08-A-9. 08:483–488 (from 09) revises 08's `/csrf` 10/min to 30/min.
