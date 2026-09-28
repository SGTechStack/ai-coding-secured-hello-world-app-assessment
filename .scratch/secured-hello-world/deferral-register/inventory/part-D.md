# Inventory part D — tickets 13, 14, 15, 16, 18, 19

Line numbers are from the files as read in this session. "(uncounted)" in a gist means the ticket's own
count statement does not include the item; it is a claim made elsewhere in the file.

## 1. Items

### Ticket 13 — audit event catalogue

| id | kind | owner | source | title / gist (≤25 words) | status | shape |
|---|---|---|---|---|---|---|
| 13-A-1 | adr-new | 13 | 13:322–379, 13:670 | `user.id` on resolved login failures; deviates from four quoted recipe points incl. inverted assertion at typed-module line 305 | live | adr |
| 13-A-2 | adr-new | 13 | 13:117–136, 13:670–671 | Lockout level WARN not ERROR, reversing ticket 03 C3 under the §0 precedence rule | live | adr |
| 13-A-3 | adr-new | 13 | 13:135–136, 13:296–320, 13:672 | `source.ip_hash` only, never cleartext IP; narrower ADR replacing 03's C1; `source.ip.hash` prohibited | live (input pinned by 13:906–913, ticket 31) | adr |
| 13-A-4 | adr-new | 13 | 13:269–294, 13:672 | Three-field schema-amendment package (`unlock_reason`, `count`, `ip_hash`); no declaration process, precedent `error.category` | live | borderline |
| 13-A-5 | adr-new | 13 | 13:476–509, 13:672–673 | Data-driven `AuditEvent` enum + single `emit`, deviating from typed-module "one method per event" | live | adr |
| 13-A-6 | adr-new | 13 | 13:601–605, 13:673 | No `total-size-cap` on the audit rolling appender, asserted by test | live (second reason added 13:739–744, ticket 21) | adr |
| 13-A-7 | adr-new | 13 | 13:606–608, 13:673–674 | Console (stdout) duplication against org separate-destination constraint; ASVS 16.2.3 satisfied by documentation | live | adr |
| 13-A-8 | adr-new | 13 | 13:624–645, 13:674 | Dev-only non-audit reset-link logger, with three enforcement controls (validator, ApplicationReady check, actuator loggers) | live | adr |
| 13-A-9 | adr-new | 13 | 13:465–474, 13:674–675 | Idle expiry observed lazily as row 11, indistinguishable from forged id; reaper declined on scope | live | adr |
| 13-A-10 | adr-new | 13 | 13:117–126 | (uncounted) Precedence rule: governing user standard > logging standard > recipes; says consequences "recorded as ADRs" | live | borderline |
| 13-A-11 | adr-new | 13 | 13:138–147 | (uncounted) Amendment rule: reopen only if amendment weakens a compensating control for a declined SHALL; "ticket 17 inherits a principle" | live | borderline |
| 03-X-1 | adr-reversed | 03 | 13:128–136, 13:653 | Ticket 03 C1 (cleartext `source.ip` on security events) withdrawn; replaced by 13-A-3 | withdrawn by 13:135 | adr |
| 03-X-2 | adr-reversed | 03 | 13:128–136, 13:653 | Ticket 03 C3 (lockout at ERROR) withdrawn; replaced by 13-A-2 | withdrawn by 13:135 | adr |
| 13-M-1 | adr-amend | 13 | 13:739–744 | (ticket 21 amendment) total-size-cap decline gains second reason: `totalSizeCap` arithmetic unreliable and silent; target 13-A-6 | live | |
| 13-M-2 | adr-amend | 13 | 13:904–913 | (ticket 31 amendment) `source.ip_hash` input pinned to HMAC over source key; prefix-length change breaks correlation; target 13-A-3 | live | |
| 13-G-1 | glossary | 13 | 13:677–678 | audit row | live | |
| 13-G-2 | glossary | 13 | 13:677–678 | discriminator | live | |
| 13-G-3 | glossary | 13 | 13:677–678 | reason family | live | |
| 13-G-4 | glossary | 13 | 13:677–678 | identity-absent failure ratio | live | |
| 13-G-5 | glossary | 13 | 13:677–678 | pre-handler row | live | |
| 13-R-1 | register | 13 | 13:614–620, 13:680 | ASVS 16.4.2 (L2) F — app-owned file not tamper-proof; deployer obligation | live | |
| 13-R-2 | register | 13 | 13:614–620, 13:680 | ASVS 16.4.3 (L2) F — no separate log system; deployer obligation | live | |
| 13-R-3 | register | 13 | 13:609–613, 13:681 | Platform 90-day TTL is an integrator obligation (ticket 25); unlock-reason record expires externally | live | |
| 13-R-4 | register | 13 | 13:604–605, 13:681 | Disk-full monitoring obligation for uncapped audit file | live | |
| 13-R-5 | register | 13 | 13:290–292, 13:681 | Three-field `Log_Schema.md` amendment request as integrator obligation | live | |
| 13-R-6 | register | 13 | 13:292–294, 13:681–682 | `user.target.*` encoder write stays an inference; verify empirically | superseded by 13:294 (ticket 32, T-AUD-010) | |
| 13-R-7 | register | 13 | 13:537–544, 13:682 | Jackson `INCLUDE_SOURCE_IN_LOCATION` pin that cannot join ticket 24's validator; Jackson 3 default unconfirmed | live | |
| 13-R-8 | register | 13 | 13:546–551, 13:683 | `spring.jackson.use-jackson2-defaults` checked and excluded from prohibited config | live | |
| 13-R-9 | register | 13 | 13:391–405, 13:683 | NIST §3.2.2 corrected framing (consecutive, disable+rebind SHALL); handed as amendment to ticket 09's register entry | live | |
| 13-R-10 | register | 13 | 13:406–410, 13:684 | Progressive delay is an unadopted NIST addition, not alternative; ticket 09 parenthetical wrong; not reopened | live | |
| 13-R-11 | register | 13 | 13:586–588, 13:684 | Unclosable crash window between commit and audit write (no audit table) | live | |
| 13-R-12 | register | 13 | 13:350–353 | (uncounted) Residual: presence/absence of `user.id` is a log-reader existence oracle | live | |
| 13-R-13 | register | 13 | 13:305–308 | (uncounted) Residual: a future ECS field named `source.ip_hash`; `labels.source_ip_hash` considered | live | |
| 13-R-14 | register | 13 | 13:254–267 | (uncounted) N/A: §3.3 header-config, critical-config, bulk-export events; successful authz not logged (L3 only). Negative assertions | live | |
| 13-R-15 | register | 13 | 13:812–849 | (uncounted; ticket 25 amendment) ASVS 16.3.3 (L2) second limb unassessed: input-validation and business-logic rejection families owed; verdict recorded S | live | |
| 13-R-16 | register | 13 | 13:886–889 | (uncounted; ticket 28 amendment) Runner stdout joins 16.2.3 documented destinations; 16.4.3 stays F | live | |
| 13-R-17 | register | 13 | 13:912 | (uncounted; ticket 31 amendment) Residual: no log can distinguish hosts inside one IPv6 /64 | live | |
| 13-R-18 | register | 13 | 13:720–727 | (uncounted; ticket 21 amendment) Per-IP audit-volume axis has no app-side ceiling under transition-keying alone | superseded by 13:729–735 (row 46 cap) | |
| 13-T-1 | trigger | 13 | 13:686–687 | Log-management platform enters scope: 16.4.2/16.4.3 close, retention moves | live | |
| 13-T-2 | trigger | 13 | 13:687–688 | Scheduled job enters scope: idle-expiry rows become producible | live | |
| 13-T-3 | trigger | 13 | 13:688 | `api.base-path` fixed rather than externalised: pattern-vs-path argument collapses | live | |
| 13-T-4 | trigger | 13 | 13:689 | Jackson re-enables source inclusion by default: pin becomes load-bearing | live | |
| 13-T-5 | trigger | 13 | 13:448–450 | (uncounted; ticket 27 inline amendment) If inbound trace continuation re-enabled, session join needs a new field | live | |
| 13-T-6 | trigger | 13 | 13:826–830 | (uncounted; ticket 25 amendment) CAPTCHA/proof-of-work/bot-scoring entering scope reopens 16.3.3 anti-automation limb | live | |
| 11-T-1 | trigger | 11 | 13:763–773 | (ticket 09 amendment) `grace-expired` reason leaks password correctness; reopen trigger filed on ticket 11 | live | |

### Ticket 14 — frontend architecture

| id | kind | owner | source | title / gist (≤25 words) | status | shape |
|---|---|---|---|---|---|---|
| 14-A-1 | adr-new | 14 | 14:314–367, 14:611 | `423 FACTOR_DISABLED` on three surfaces (`rebindRequired`, entry-point third branch, verification endpoint); tier-2 loop as justification | live | adr |
| 14-A-2 | adr-new | 14 | 14:291–312, 14:612 | Tier 1 as `429` + `reason: LOCKED`, two-member discriminator, ticket 06 §11 enumeration carve-out | live | adr |
| 14-A-3 | adr-new | 14 | 14:369–381, 14:613 | `409` for two-admin invariant, `403 ACCESS_DENIED` for self-action; three-condition copy | live | borderline |
| 14-A-4 | adr-new | 14 | 14:231–257, 14:613–614 | Sign-out terminal; mint-a-session path is idle/eviction only | live | adr |
| 14-A-5 | adr-new | 14 | 14:205–215, 14:614 | Lazy CSRF token bootstrap against anonymous session-row cost; proactive refresh | live | adr |
| 14-A-6 | adr-new | 14 | 14:510–529, 14:614–615 | Retry predicate (retry only with no status) and audited-but-unbudgeted self-read | live | borderline |
| 14-A-7 | adr-new | 14 | 14:483–508, 14:615–616 | Keep ticket 23 server PNG; symmetric create/revoke effect as remedy | live | borderline |
| 14-A-8 | adr-new | 14 | 14:472–482, 14:616 | Document-wide `Referrer-Policy` meta, first in head; two-origin split vs API `SAME_ORIGIN` | live | borderline |
| 14-A-9 | adr-new | 14 | 14:467–470, 14:616–617 | `style-src` left unsplit on the IM8 `as-9` grep reason | live | register |
| 23-M-1 | adr-amend | 23 | 14:360–363, 14:641 | Ticket 23 §12 off-label statuses become three recorded deviations (adds 423) | live | |
| 14-G-1 | glossary | 14 | 14:619 | belief versus authority | live | |
| 14-G-2 | glossary | 14 | 14:619 | step-up queue | live | |
| 14-G-3 | glossary | 14 | 14:619 | terminal factor state | live | |
| 14-G-4 | glossary | 14 | 14:619 | rebinding | live | |
| 14-G-5 | glossary | 14 | 14:620 | one-time secret display | live | |
| 14-G-6 | glossary | 14 | 14:620 | visual-integrity degradation | live | |
| 14-R-1 | register | 14 | 14:558–560, 14:622 | Accessibility conformance target and verification method remain fog | live | |
| 14-R-2 | register | 14 | 14:453–457, 14:622–623 | No-inline-`<script>` from mounted components is an assumption until asserted | live | |
| 14-R-3 | register | 14 | 14:458–460, 14:623–624 | `build.chunkImportMap` rationale strongly evidenced, not confirmed | live | |
| 14-R-4 | register | 14 | 14:624–625 | `Clear-Site-Data` on `http://localhost` untested rather than observed | withdrawn by 14:655 (ticket 16) | |

### Ticket 15 — threat model

| id | kind | owner | source | title / gist (≤25 words) | status | shape |
|---|---|---|---|---|---|---|
| 15-R-1 | register | 15 | 15:45–47, 15:236–240 | Accepted risk going in: local HTTP | live | |
| 15-R-2 | register | 15 | 15:45–47, 15:236–240 | Accepted risk going in: stubbed email | live | |
| 15-R-3 | register | 15 | 15:45–47, 15:236–240 | Accepted risk going in: no durable audit store | live | |
| 15-R-4 | register | 15 | 15:45–47, 15:236–240 | Accepted risk going in: single-instance in-memory rate limiting | live | |
| 15-R-5 | register | 15 | 15:45–47, 15:236–240 | Accepted risk going in: enumeration via any surviving path | live | |
| 15-R-6 | register | 15 | 15:45–47, 15:239–240 | Accepted risk going in: no MFA — withdrawn because ticket 19 put TOTP in scope | withdrawn by 15:239 | |
| 15-R-7 | register | 15 | 15:196–198 | TM-08 accepted risk: any admin can take over any other admin (flat role) | live (widened at two admins by 15:415–429, ticket 30) | |
| 15-R-8 | register | 15 | 15:199–201 | TM-09 accepted risk: tombstone records no role; privileged-deletion audit expires on external TTL | live | |
| 15-H-1 | handover | 15 | 15:262–271 | Assume stdout collected; prove rebinding token absent from collector | superseded by 15:363–366 (ticket 28: runner emits no secret) | |
| 15-H-2 | handover | 15 | 15:273–284 | Recompute audit disk sizing/diskspace threshold on bounded `daily`; alert on row 46 truncation | live | |
| 15-H-3 | handover | 15 | 15:286–297 | Pin aggregate request capacity (`threads.max`), keep Tomcat/Hikari saturation meters | live | |
| 15-H-4 | handover | 15 | 15:299–307 | Off dev H2: session store JDK deserialisation needs an `ObjectInputFilter` | live | |
| 15-H-5 | handover | 15 | 15:309–320 | Admin-on-admin takeover: no preventive control; alert on row 18 `ADMIN_RESET` with longer retention | live (6.4.6 withdrawn on runner path 15:376–377; bound changed 15:415–429) | |
| 15-H-6 | handover | 15 | 15:322–331 | Role-at-deletion preserved only by platform retention; record retention value | live | |
| 15-H-7 | handover | 15 | 15:333–348 | Upstream proxy must strip/regenerate `traceparent` | withdrawn by 15:333 and 15:391 (ticket 27) | |
| 11-T-2 | trigger | 11 | 15:87–89 | (inherited from 09) `postAuthenticationChecks` audit-reason password oracle; reopen trigger filed on ticket 11 (same as 11-T-1) | live | |
| 25-T-1 | trigger | 25 | 15:207–210, 15:229 | TM-13: amend ticket 25's mail-transport trigger to carry recovery-code containment inversion | live | |

### Ticket 16 — test plan

| id | kind | owner | source | title / gist (≤25 words) | status | shape |
|---|---|---|---|---|---|---|
| 16-A-1 | adr-new | 16 | 16:644, 16:514 | `@WebMvcTest` and all slices banned for security-control tests | live | adr |
| 16-A-2 | adr-new | 16 | 16:645, 16:525–535 | Forward-only real-time mutable clock and Caffeine/Bucket4j adapters as production design | live | adr |
| 16-A-3 | adr-new | 16 | 16:646, 16:538–546 | Timing verified by mechanism (`matches()` count), no wall-clock assertion | live | adr |
| 16-A-4 | adr-new | 16 | 16:647, 16:595–603 | Playwright in, narrow (Chromium+Firefox, ~8 tests, `vite preview`) | live | borderline |
| 16-A-5 | adr-new | 16 | 16:648, 16:519 | No `test` profile; capture-bean override in `ctx-default` instead | live | adr |
| 16-A-6 | adr-new | 16 | 16:649, 16:482–499 | Traceability gate (`@Proves`) with transcribed canonical table | live | adr |
| 16-J-1 | adr-rejected | 16 | 16:651 | "Sequential suite" ADR rejected: easy to reverse | live | register |
| 16-R-1 | register | 16 | 16:571, 16:652 | Fidelity: Postgres/MySQL locking, types, gap/phantom locking not exercised | live | |
| 16-R-2 | register | 16 | 16:572, 16:652 | Fidelity: MySQL accent-insensitive collation not exercised | live | |
| 16-R-3 | register | 16 | 16:573, 16:652 | Fidelity: DDL portability beyond ticket 12's seam register | live | |
| 16-R-4 | register | 16 | 16:574, 16:652 | Fidelity: real TLS, HSTS, `__Host-` over HTTPS | live | |
| 16-R-5 | register | 16 | 16:575, 16:135, 16:652 | Fidelity: multi-instance limiter/session consistency (§5:453) | live | |
| 16-R-6 | register | 16 | 16:576, 16:652 | Fidelity: deployer's launch command (rehearsal only) | live | |
| 16-R-7 | register | 16 | 16:577, 16:652 | Fidelity: wall-clock timing uniformity | live | |
| 16-R-8 | register | 16 | 16:578, 16:652 | Fidelity: WebKit/Safari unexercised | live | |
| 16-R-9 | register | 16 | 16:581, 16:652 | Not built: §5 hygiene rows (90/180-day, scheduler serialisation, batch session kill) | live | |
| 16-R-10 | register | 16 | 16:582, 16:652 | Not built: 90-day audit retention | live | |
| 16-R-11 | register | 16 | 16:583, 16:212–215, 16:652 | Not built: owner notifications incl. NIST §4.2.3 (failed, no transport) | live | |
| 16-R-12 | register | 16 | 16:584, 16:652 | Not built: Dependency-Check as a CI gate (bound to `verify`) | live | |
| 16-R-13 | register | 16 | 16:411, 16:607, 16:652 | Failsafe residual: `-DskipITs`/`-Dmaven.test.skip` accepted bypasses; below 3.6.0 `-DskipTests` bypasses | live | |
| 16-R-14 | register | 16 | 16:538–546, 16:652 | ASVS 6.3.8 (L3) "verified by mechanism" | live | |
| 16-R-15 | register | 16 | 16:135–138 | (uncounted) Per-account 429 not reachable by pure failure traffic; deferral, narrowed to submitted-username axis | live | |
| 16-R-16 | register | 16 | 16:249–252 | (uncounted) "Narrowing for the register": unauthenticated-BCrypt lever gone from registration only | live | |
| 16-R-17 | register | 16 | 16:364, 16:531 | (uncounted) NTP-step monotonicity loss "accepted and recorded" | live | |
| 16-R-18 | register | 16 | 16:400, 16:563 | (uncounted) N/A: §5:473 username change; `USERNAME_CHANGE_NOT_ALLOWED` removed (amends 06) | live | |
| 16-R-19 | register | 16 | 16:398, 16:564 | (uncounted) N/A: §5:494 batch half (ticket 11) | live | |
| 16-G-1 | glossary | 16 | 16:653 | canary secret | live | |
| 16-G-2 | glossary | 16 | 16:653 | isolation class | live | |
| 16-G-3 | glossary | 16 | 16:653 | context configuration | live | |
| 16-T-1 | trigger | 16 | 16:655, 16:442, 16:607 | Any Surefire or Failsafe version bump | live | |
| 16-T-2 | trigger | 16 | 16:656, 16:212–215 | Mail transport enters scope (TM-13 status pin; three notification SHALLs testable) | live | |
| 16-T-3 | trigger | 16 | 16:657, 16:451 | Any change to k or the cleanup cron (via tickets 09, 29) | live | |
| 25-T-2 | trigger | 25 | 16:446, 16:603–604 | (uncounted) Constraint on 25's mail-transport trigger: route gate keys on transport property, never `EmailService` bean type | live | |
| 16-H-1 | handover | 16 | 16:629–632 | Build with Surefire/Failsafe 3.6.0; never `-DskipITs`/`-Dmaven.test.skip` for release | live | |
| 16-H-2 | handover | 16 | 16:633–636 | Failed `verify` is a release blocker (no CI) | live | |
| 16-H-3 | handover | 16 | 16:637–639 | Run E-level suite in Chromium and Firefox; WebKit/Safari accepted or by hand | live | |

### Ticket 18 — compliance review gate

No items owed to ticket 17 or ticket 25's table. The file (73 lines, status open, blocked by 17 and 32) lists
review steps and build-phase gates for the spec; the ticket 16 amendment (18:55–73) adds gates, not register rows.

### Ticket 19 — MFA scope conflict

| id | kind | owner | source | title / gist (≤25 words) | status | shape |
|---|---|---|---|---|---|---|
| 19-A-1 | adr-new | 19 | 19:273–277 | Enforcement layer replaced: `MFA_Critical_Transaction` AOP/`X-TOTP` rejected for Spring Security 7 factor authorities | live (mechanics amended 19:385–425) | adr |
| 19-A-2 | adr-new | 19 | 19:278–279 | TOTP key not stored in DB (standard defective); AES-GCM named; key-version column | live (amended 19:327–338, 19:401–415) | adr |
| 19-A-3 | adr-new | 19 | 19:280 | Password minimum 15 overriding PRD's 12, per NIST 800-63B-4 §3.1.1.2 | live | adr |
| 19-A-4 | adr-new | 19 | 19:281–282 | `MFAPrompt` and all-user `/settings/mfa` dropped; enrolment admin-only | live | adr |
| 19-A-5 | adr-new | 19 | 19:283–284 | Response contract deviates from corpus's three contradictory enrolment statuses; English-string dependency dropped | live | adr |
| 19-A-6 | adr-new | 19 | 19:285–286 | Recovery codes deferred; two-enrolled-admins invariant + break-glass runbook compensate | live (amended 19:512–514, 19:518–524) | adr |
| 19-M-1 | adr-amend | 19 | 19:325–338 | (ticket 11) `Encryptors.stronger()` prohibition over-broad; rule becomes use `AesGcmBytesEncryptor`, never two-arg `AesBytesEncryptor`; target 19-A-2 | live | |
| 19-M-2 | adr-amend | 19 | 19:385–397 | (ticket 23) `requireFactors`/`@EnableMultiFactorAuthentication` wrong; role-first hand-composed two-arg `allOf`; target 19-A-1 | live | |
| 19-M-3 | adr-amend | 19 | 19:401–411 | (ticket 23) Envelope 69 bytes, 16-byte IV; AAD unreachable, plaintext context prefix instead; target 19-A-2 | live | |
| 19-M-4 | adr-amend | 19 | 19:417–425 | (ticket 23) Admin-read rule gets `validDuration` = session lifetime as fail-closed type guard; "the ADR must say so"; target 19-A-1 | live | |
| 23-M-2 | adr-amend | 23 | 19:439–444 | (ticket 23) `MFA_Core` lockout deviated further: two-tier 10/20-min + 100-failure cap cleared only by reset | live | |
| 19-M-5 | adr-amend | 19 | 19:512–514 | (ticket 25) Two-enrolled-admins invariant now load-bearing for a third decision; target 19-A-6 | live (qualified by 19:518–524) | |
| 19-M-6 | adr-amend | 19 | 19:518–524 | (ticket 30) Factor-reset path exempt from invariant count; compensating control works at two admins; target 19-A-6 | live | |
| 19-R-1 | register | 19 | 19:198–202 | Privileged-action interpretation: admin surface + MFA settings; current-password re-entry for own change. "Recorded … in the deferral register" | live | |
| 19-R-2 | register | 19 | 19:377–380 | (ticket 10 amendment) ASVS L2 not claimable app-wide (6.3.3); target L1 with named L2/L3 controls | live | |
| 19-R-3 | register | 19 | 19:411–415 | (ticket 23 amendment) NIST SP 800-38D 128-bit IV satisfied, not deviated; "removes a register entry" | withdrawn by 19:415 | |
| 23-R-1 | register | 23 | 19:426–431 | (ticket 23 amendment) NIST §4.1.2.1 authenticator-added notification SHALL: named failure, compensated by alert-worthy audit row | live | |
| 12-R-1 | register | 12 | 19:450–456 | (ticket 12 amendment) `im8-review` MFA-flag grep a documented false negative; enrolment derived from row existence | live | |
| 12-R-2 | register | 12 | 19:457–462 | (ticket 12 amendment) Two-admin guard decrement-safe not phantom-safe (no H2 gap locking); cascade changes count | live | |
| 10-T-1 | trigger | 10 | 19:371–376 | (ticket 10 amendment) Self-service TOTP enrolment ever added reopens ticket 10 (CVE-2026-56081 pre-hijack) | live | |
| 19-T-1 | trigger | 19 | 19:471–510 | (ticket 25 amendment) Reversing recovery-codes deferral: take saved+issued, mint ≥112 bits, fourth throttled counter | live | |

## 2. Per-ticket tally (stated vs enumerated)

| ticket | kind | stated | enumerated | note |
|---|---|---|---|---|
| 13 | adr-new | 9 (13:670) | 9 + 2 uncounted | Mismatch: §0 precedence rule and §1 amendment rule (13:117–147) are framed as principles for 17 but not counted. |
| 13 | adr-reversed | "C1 and C3 withdrawn" (13:135) | 2 (owner 03) | Match. |
| 13 | adr-amend | none stated | 2 | Mismatch: amendments from tickets 21 and 31 modify 13-A-6 and 13-A-3 after the count was written. |
| 13 | glossary | 5 (13:677) | 5 | Match. |
| 13 | register | 11 (13:680) | 11 + 7 uncounted | Mismatch: two residuals, the N/A set, and four items added by amendments (21, 25, 28, 31) postdate or sit outside the count. |
| 13 | trigger | 4 (13:686) | 4 + 2 uncounted (+1 owned by 11) | Mismatch: triggers from ticket 27 inline and ticket 25 amendment not counted; 11-T-1 is owned by 11. |
| 14 | adr-new | 9 (14:611) | 9 | Match. |
| 14 | adr-amend | "§12 becomes three recorded deviations" (14:641) | 1 (owner 23) | Match. |
| 14 | glossary | 6 (14:619) | 6 | Match. |
| 14 | register | 4 (14:622) | 4 (1 withdrawn) | Count matches; R-4 withdrawn by ticket 16 (14:655), live total 3. |
| 15 | adr-new | 4 (15:354) | 0 | Mismatch: "four new ADRs" never enumerated in 15 or in threat-model/report.md (no "ADR" hits). Aggregator must source them from 17 or reconstruct. |
| 15 | adr-amend | 2 (15:354–355) | 0 | Mismatch: "two amended" never enumerated; targets unnamed. |
| 15 | register | 6 accepted risks, one withdrawn (15:355) | 6 going-in (1 withdrawn) + 2 | Mismatch: TM-08 and TM-09 marked "→ accepted risk" (15:198, 15:201) sit outside the six. 15:236 also calls handover rows "six accepted risks": ambiguous. |
| 15 | handover | 7 (15:259, 15:356) | 7 (1 withdrawn, 1 superseded) | Count matches; 15:394 confirms seven includes the retired one. Live total 5 if H-1 is treated as superseded. |
| 15 | trigger | none stated | 2 (owners 11, 25) | Mismatch: both are claims about other tickets' triggers. |
| 16 | adr-new | 6 (16:643–649) | 6 | Match. |
| 16 | adr-rejected | 1 (16:651) | 1 | Match. |
| 16 | register | "§6(a), §6(b), Failsafe residual, 6.3.8" (16:652), no number | 14 + 5 uncounted | Stated has no number; split gives 8+4+1+1=14. Five more register-shaped claims (16:135, 249, 364, 563, 564) are uncounted. |
| 16 | glossary | 3 (16:653) | 3 | Match. |
| 16 | trigger | 3 (16:654–657) | 3 + 1 (owner 25) | Mismatch: constraint on 25's trigger (16:446) not listed. |
| 16 | handover | 3 (16:627–639) | 3 | Match. |
| 18 | all | none | 0 | Nothing owed to 17 or 25. |
| 19 | adr-new | 6 (19:271–286) | 6 | Match. |
| 19 | adr-amend | none stated | 6 on own ADRs + 1 owned by 23 | Amendments from 11, 23, 25, 30 postdate the list. |
| 19 | register | none stated | 3 owned by 19 (1 withdrawn) + 1 owned by 23 + 2 owned by 12 | Mismatch: 19:201 explicitly sends an interpretation to the register but there is no register count. |
| 19 | trigger | none stated | 1 owned by 19 + 1 owned by 10 | Both come from amendments. |

## 3. Cross-file effects (outside group D)

- 03: C1 (cleartext `source.ip`) and C3 (lockout ERROR) ADRs withdrawn by 13:128–136, 13:653. 03's C4 is too narrow: path/method needed on every audit row (13:654–655).
- 09: register entry on NIST §3.2.2 corrected (13:391–405). The "backoff and lockout are alternatives" parenthetical is wrong (13:406–410). `source.ip.hash` becomes `source.ip_hash` (13:648–649). k = 5 exactly, as an amendment rather than a reopening (16:451–456). The 50-in-24h alert is now computable (13:381–390).
- 10: new reopening trigger, self-service TOTP enrolment (19:371–376). BCrypt-lever claim narrowed to registration only (15:193–195, 16:249–252).
- 11: reopen trigger for the `grace-expired` password oracle (13:763–773, 15:87–89). `unlockReason` renamed to `unlock_reason` (13:278–281). Line 324's generated-password text is superseded (14:640). `rebindRequired` added to `factors` (14:639–640).
- 06: `FACTOR_DISABLED` at 423 (enum to 15 rows / 16 identifiers), `LOCKED` factor reason, and the logout-403 code, all from 14:637–639. `USERNAME_CHANGE_NOT_ALLOWED` removed as §5:473 N/A (16:400, 16:563).
- 12: im8-review false negative and the phantom-safety residual are recorded in 19 (19:450–462), owner 12.
- 20: its four constraints are verified, with three corrections (14:449–470, 14:642). The `SameSite=Strict` vs Standard `Lax` ADR is referenced as existing (16:57–59). It is not new.
- 21: the rows 5/6 amendment adds row 46 and the `N` ceiling (13:697–759). TM-10 says `N` has no value or key (15:202–204) and goes to 26. The `GET /api/profile` unbudgeted input goes to 09 and 21 (14:522–529).
- 23: §12 now has three off-label deviations (14:360–363, 14:641). An entry-point third branch is added (14:348–352). The `MFA_Core` lockout deviation grows (19:439–444). The §4.1.2.1 notification failure is recorded (19:426–431).
- 24: `app.security.hmac.log.key` rotation is constrained to at least the investigation window (13:310–315). Ticket 24's eleventh prohibited-config entry is superseded by 28 test 9 (16:614).
- 25: 15-H-1 is superseded by ticket 28 (15:361–377). 15-H-7 is retired, and ticket 27 carries its replacement (15:333–348, 15:391–395). 25's mail-transport trigger gains two things: the TM-13 consequence (15:207–210) and the gate-key constraint (16:446). 25's §4 "enforced" grade and its step-7 check were called mis-graded or unpassable (15:160–162), which 28 later mooted. Two things were handed back to 25 but not under a Handover heading, so they are not extracted: the two-admin copy and the one-time-token rules (14:648–649).
- 27: trace continuation re-enable would need a new join field (13:448–450). TM-02 is mitigated (15:380–398).
- 30: TM-08 is widened at two admins (15:415–429). The invariant exempts factor reset (19:518–524).
- 32: the `superseded by T-…` pointers throughout supersede test lists, not register items. The one exception is 13-R-6 (13:294, T-AUD-010).
