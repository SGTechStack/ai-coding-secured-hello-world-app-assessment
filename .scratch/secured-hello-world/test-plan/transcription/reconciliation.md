# Ticket 32 reconciliation: every source test to its T-ID

This is the audit trail for [the test-plan table](../../../../docs/test-plan/test-plan.md). There are 460 staged tests (`stage-A.md` to `stage-J.md`, keyed `Snn-mm` by source ticket). They were deduplicated in `dedup.md` into 316 rows. Eight of those were minted by ticket 32 for controls that had no test. The standards pass (`stage-S.md`) added 33 more, giving 349 IDs. Retirements are listed with the decision that retired them.

- **Per source ticket**: a staged row that merged into several canonical rows contributes several IDs, so the IDs can outnumber the tests.
- **Lists**: every source list, with the IDs its pointer names.
- **Line numbers**: they refer to the tickets as they stood when transcribed. Pointers were appended to existing lines, so no line moved.

## Per source ticket

| source ticket | n tests transcribed | n IDs | IDs |
|---|---|---|---|
| ticket 01 | 7 (retired 0) | 7 | T-HDR-002, T-HDR-001, T-SES-001, T-RL-001, T-RL-002, T-AUTH-001, T-AUTH-002 |
| ticket 02 | 3 (retired 0) | 3 | T-LCK-001, T-CRED-001, T-SES-002 |
| ticket 03 | 6 (retired 0) | 6 | T-AUD-007, T-AUD-009, T-AUD-008, T-AUD-010, T-AUD-011, T-AUD-012 |
| ticket 04 | 27 (retired 4) | 23 | T-ADM-001, T-ADM-002, T-ADM-003, T-ADM-004, T-SES-003, T-SES-004, T-ADM-005, T-HDR-001, T-HDR-002, T-CSRF-001, T-SES-005, T-LCK-003, T-LCK-002, T-SES-006, T-ADM-006, T-ADM-007, T-ADM-009, T-ADM-008, T-AUD-013, T-CRED-002, T-AUTH-003, T-HDR-004, T-HDR-003 |
| ticket 05 | 14 (retired 0) | 15 | T-MFA-001, T-CSRF-002, T-SES-010, T-SES-009, T-SES-006, T-SES-005, T-AUTH-004, T-SES-011, T-CSRF-001, T-CSRF-003, T-CSRF-004, T-RL-002, T-RL-003, T-AUTH-005, T-ARCH-001 |
| ticket 06 | 8 (retired 0) | 8 | T-AUTH-006, T-AUTH-008, T-AUTH-009, T-AUTH-010, T-AUTH-002, T-AUTH-001, T-AUTH-011, T-AUTH-012 |
| ticket 07 | 6 (retired 0) | 6 | T-CRED-004, T-CRED-005, T-CRED-001, T-CRED-006, T-CRED-002, T-CRED-007 |
| ticket 08 | 23 (retired 0) | 23 | T-SES-002, T-SES-011, T-SES-009, T-HDR-005, T-CSRF-007, T-CSRF-001, T-SES-010, T-SES-006, T-SES-022, T-SES-012, T-SES-013, T-SES-014, T-SES-015, T-SES-003, T-SES-004, T-SES-016, T-SES-017, T-SES-018, T-SES-021, T-SES-019, T-SES-020, T-SES-001, T-SES-023 |
| ticket 09 | 30 (retired 0) | 29 | T-RL-001, T-RL-003, T-RL-002, T-AUTH-005, T-RL-005, T-LCK-008, T-LCK-001, T-CFG-001, T-OBS-001, T-AUTH-003, T-LCK-009, T-LCK-010, T-RL-006, T-RL-007, T-CRED-009, T-SES-022, T-LCK-012, T-LCK-013, T-LCK-014, T-LCK-015, T-LCK-016, T-RL-008, T-RL-009, T-RL-010, T-RL-011, T-RL-012, T-RL-013, T-RL-014, T-RL-015 |
| ticket 10 | 11 (retired 0) | 11 | T-CRED-016, T-CRED-017, T-CRED-018, T-LCK-017, T-CRED-007, T-CRED-011, T-CRED-014, T-CRED-015, T-AUTH-014, T-ADM-010, T-CRED-009 |
| ticket 11 | 14 (retired 0) | 14 | T-ADM-012, T-ADM-014, T-ADM-016, T-ADM-008, T-AUTH-005, T-CRED-007, T-ADM-017, T-MFA-002, T-ADM-018, T-ADM-019, T-ADM-020, T-ADM-006, T-ADM-005, T-ADM-009 |
| ticket 12 | 18 (retired 0) | 18 | T-CFG-002, T-CFG-003, T-CFG-004, T-CFG-005, T-CFG-006, T-CFG-007, T-MFA-003, T-MFA-004, T-CRED-020, T-CFG-008, T-ADM-021, T-CRED-021, T-ADM-002, T-CRED-022, T-BLD-001, T-MFA-005, T-LCK-018, T-SES-024 |
| ticket 13 | 23 (retired 0) | 24 | T-AUD-010, T-AUD-007, T-AUD-014, T-AUD-017, T-AUD-018, T-AUD-019, T-AUD-020, T-AUD-021, T-AUD-008, T-AUD-022, T-AUD-016, T-AUD-023, T-AUD-024, T-AUD-009, T-AUD-025, T-AUD-026, T-AUD-011, T-CFG-009, T-CFG-010, T-CFG-011, T-RL-005, T-AUD-027, T-RUN-002, T-RUN-001 |
| ticket 14 | 28 (retired 0) | 28 | T-BLD-002, T-HDR-003, T-AUTH-015, T-E2E-001, T-MFA-006, T-FE-001, T-FE-002, T-HDR-006, T-BLD-003, T-FE-003, T-FE-004, T-FE-005, T-FE-006, T-FE-007, T-FE-008, T-FE-009, T-FE-010, T-FE-011, T-FE-012, T-FE-013, T-FE-014, T-FE-015, T-FE-016, T-FE-017, T-FE-018, T-FE-019, T-HDR-005, T-AUTH-012 |
| ticket 15 | 8 (retired 0) | 11 | T-MFA-002, T-MFA-007, T-RL-016, T-OBS-002, T-OBS-003, T-OBS-004, T-AUD-028, T-RUN-003, T-CRED-023, T-SES-025, T-OBS-005 |
| ticket 16 | 81 (retired 1) | 98 | T-AUTH-006, T-AUTH-009, T-AUTH-010, T-AUTH-002, T-AUTH-001, T-AUTH-011, T-RL-017, T-HDR-004, T-SES-002, T-SES-011, T-HDR-007, T-HDR-008, T-AUTH-016, T-BLD-004, T-BLD-005, T-BLD-006, T-AUD-027, T-ADM-022, T-MFA-002, T-MFA-007, T-RL-016, T-OBS-002, T-OBS-003, T-OBS-004, T-AUD-028, T-RUN-003, T-CRED-023, T-SES-025, T-OBS-005, T-RUN-004, T-RUN-005, T-RUN-006, T-RUN-007, T-RUN-008, T-RUN-009, T-RUN-010, T-RUN-011, T-AUD-029, T-RUN-001, T-CFG-012, T-CFG-013, T-CFG-014, T-SES-026, T-SES-027, T-SES-024, T-OBS-006, T-LCK-011, T-ADM-023, T-ADM-024, T-ADM-025, T-ADM-026, T-ADM-027, T-ADM-028, T-ADM-029, T-OBS-007, T-CRED-024, T-RL-018, T-MFA-008, T-ARCH-001, T-LCK-019, T-LCK-002, T-LCK-020, T-SES-012, T-SES-013, T-SES-014, T-SES-015, T-SES-003, T-SES-004, T-SES-016, T-SES-017, T-SES-018, T-SES-021, T-SES-019, T-SES-020, T-AUD-013, T-HDR-003, T-ADM-018, T-RL-019, T-ADM-020, T-HDR-001, T-HDR-002, T-SES-023, T-ARCH-002, T-AUTH-012, T-AUD-030, T-SES-028, T-RL-020, T-BLD-007, T-BLD-008, T-CRED-025, T-LCK-003, T-ADM-005, T-ADM-009, T-HDR-005, T-E2E-002, T-E2E-003, T-E2E-004, T-E2E-005 |
| ticket 19 | 2 (retired 0) | 3 | T-MFA-009, T-ADM-027, T-ADM-028 |
| ticket 20 | 6 (retired 0) | 6 | T-HDR-007, T-HDR-008, T-SES-002, T-SES-011, T-HDR-004, T-BLD-002 |
| ticket 21 | 15 (retired 1) | 14 | T-OBS-008, T-AUTH-017, T-OBS-009, T-OBS-010, T-OBS-011, T-CFG-015, T-CFG-016, T-OBS-012, T-OBS-001, T-OBS-013, T-LCK-015, T-OBS-014, T-AUTH-003, T-BLD-004 |
| ticket 22 | 16 (retired 0) | 16 | T-FE-020, T-FE-021, T-FE-013, T-FE-022, T-FE-023, T-FE-024, T-FE-025, T-FE-003, T-FE-026, T-FE-027, T-MFA-012, T-MFA-013, T-MFA-014, T-MFA-015, T-MFA-016, T-LCK-017 |
| ticket 23 | 10 (retired 0) | 10 | T-ADM-017, T-MFA-018, T-CFG-017, T-CFG-018, T-CFG-019, T-MFA-005, T-MFA-003, T-CFG-003, T-MFA-007, T-MFA-002 |
| ticket 24 | 27 (retired 0) | 26 | T-CFG-020, T-CFG-021, T-SES-011, T-CFG-024, T-CFG-025, T-CFG-026, T-CFG-027, T-CFG-028, T-CFG-029, T-CFG-030, T-CFG-031, T-CFG-032, T-CFG-033, T-CFG-034, T-CFG-035, T-BLD-009, T-AUD-018, T-AUD-022, T-OBS-012, T-CFG-012, T-CFG-013, T-CFG-014, T-RUN-012, T-AUD-029, T-RUN-001, T-RUN-013 |
| ticket 25 | 6 (retired 0) | 6 | T-BLD-004, T-BLD-005, T-ADM-022, T-BLD-006, T-CRED-023, T-AUD-027 |
| ticket 26 | 15 (retired 0) | 15 | T-AUD-033, T-AUD-034, T-AUD-035, T-AUD-024, T-RL-016, T-AUD-032, T-SES-029, T-SES-030, T-SES-031, T-AUD-036, T-AUD-037, T-RL-021, T-RL-023, T-ARCH-005, T-AUD-038 |
| ticket 27 | 6 (retired 0) | 6 | T-OBS-015, T-AUD-012, T-OBS-002, T-OBS-003, T-OBS-004, T-AUD-028 |
| ticket 28 | 14 (retired 0) | 16 | T-RUN-004, T-RUN-005, T-RUN-006, T-RUN-007, T-RUN-008, T-RUN-009, T-RUN-010, T-RUN-011, T-RUN-003, T-AUD-027, T-AUD-029, T-RUN-001, T-CFG-012, T-CFG-013, T-CFG-014, T-RUN-002 |
| ticket 29 | 15 (retired 0) | 15 | T-SES-026, T-SES-027, T-CSRF-008, T-SES-032, T-SES-024, T-SES-033, T-SES-034, T-SES-035, T-RL-024, T-OBS-006, T-AUD-039, T-OBS-016, T-OBS-017, T-SES-028, T-SES-023 |
| ticket 30 | 8 (retired 0) | 8 | T-ADM-023, T-ADM-024, T-ADM-025, T-ADM-026, T-ADM-027, T-ADM-028, T-ADM-029, T-OBS-007 |
| ticket 31 | 12 (retired 0) | 12 | T-RL-026, T-RL-027, T-AUD-041, T-RL-025, T-RL-028, T-AUD-040, T-RL-029, T-RL-020, T-RL-030, T-RL-031, T-LCK-011, T-CFG-037 |
| ticket 32 | 1 (retired 0) | 1 | T-ADM-019 |
| 32 (minted for untested controls) | 8 | 8 | T-CFG-022, T-CFG-023, T-ADM-011, T-RL-022, T-CFG-036, T-AUTH-013, T-AUTH-007, T-AUD-015 |
| standards §5 (new rows) | 33 | 33 | T-SES-007, T-CSRF-005, T-SES-008, T-CSRF-006, T-LCK-006, T-LCK-007, T-LCK-005, T-RL-004, T-CRED-010, T-CRED-012, T-CRED-013, T-CRED-019, T-CRED-008, T-ADM-015, T-CRED-003, T-ADM-013, T-ARCH-003, T-ARCH-004, T-LCK-004, T-MFA-011, T-MFA-010, T-MFA-020, T-MFA-017, T-MFA-019, T-MFA-021, T-MFA-022, T-AUD-001, T-AUD-003, T-AUD-004, T-AUD-002, T-AUD-005, T-AUD-031, T-AUD-006 |

## Deduplications

| staged | ≡ | → ID | reason |
|---|---|---|---|
| S01-01 (01:144) | S16-64 | T-HDR-002 | 01:144 CSP example ≡ 16:401 §5:498 other-headers row (as-9 no-unsafe facet pulled in via Edits) |
| S01-02 (01:144) | S16-63 | T-HDR-001 | 01:144 HSTS max-age ≡ 16:401 HSTS secure-only |
| S01-03 (01:144) | S08-22 | T-SES-001 | 01:144 session timeout value ≡ 08:769 idle timeout (≤ 15 min bound pulled in via Edits) |
| S01-04 (01:144) | S09-01 | T-RL-001 | 01:144 per-IP 429 at N+1 (amended by 16) ≡ 09:178 |
| S01-05 (01:144) | S09-03 | T-RL-002 | 01:144 per-account 429 on mixed traffic ≡ 09:183 |
| S01-06 (01:144) | S06-06 | T-AUTH-001 | 01:144 generic error body ≡ 06:301 /error producer |
| S01-07 (01:306) | S06-05 | T-AUTH-002 | 01:306 403 on role violation ≡ 06:300 AccessDeniedHandler ACCESS_DENIED |
| S02-01 (02:198) | S09-07 | T-LCK-001 | 02:198 observation window ≡ 09:702 staleness reset |
| S02-02 (02:342) | S07-03 | T-CRED-001 | 02:342 ≡ 07:465 MAX_BYTES rule |
| S02-03 (02:395) | S20-03 | T-SES-002 | 02:395 cookie attributes ≡ 20:159 raw Set-Cookie |
| S03-01 (03:394) | S13-02 | T-AUD-007 | 03:394 event schema ≡ 13:500 catalogue consistency |
| S03-02 (03:394) | S13-14 | T-AUD-009 | 03:394 C2 ≡ 13:566 unresolved half; resolved half inverted by 13 |
| S03-03 (03:394) | S13-09 | T-AUD-008 | 03:394 C5 ≡ 13:561 key absence |
| S03-04 (03:467) | S13-01 | T-AUD-010 | 03:467 ≡ 13:292 first-implementation test |
| S03-05 (03:635) | S13-17 | T-AUD-011 | 03:635 ≡ 13:603 retention |
| S03-06 (03:691) | S27-02 | T-AUD-012 | 03:691 ≡ 27:208 baggage-off test |
| S04-02 (04:225) | S12-13 | T-ADM-002 | 04:225 tombstone username ≡ 12:290 tombstone blocks both identifiers |
| S04-03 (04:225) | S04-01 | T-ADM-001 | 04:225 duplicate email ≡ 04:224 duplicate username (one USER_EXISTS test) |
| S04-08 (04:227) | S04-07 | T-ADM-003 | 04:227 self-delete ≡ actor ≠ subject guard test |
| S04-09 (04:227) | S04-07 | T-ADM-003 | 04:227 self-unlock ≡ actor ≠ subject guard test |
| S04-11 (04:228) | S04-10 | T-ADM-004 | 04:228 deletedById ≡ tombstone-write test |
| S04-12 (04:230) | S08-14, S08-15 | T-SES-003, T-SES-004 | 04:230 ≡ 08:211 disable and 08:212 role-change replay rows |
| S04-13 (04:231) | S11-13 | T-ADM-005 | 04:231 forced-change allowlist ≡ 11:967 (parameterised via Edits) |
| S04-14 (04:299) | S16-63, S16-64 | T-HDR-001, T-HDR-002 | 04:299 headers ≡ 16:401 §5:498 rows |
| S04-15 (04:299) | S08-06 | T-CSRF-001 | 04:299 no XSRF-TOKEN cookie ≡ 08:402 |
| S04-16 (04:300) | S05-05 | T-SES-005 | 04:300 session id rotation ≡ 05:606 Route C rotation |
| S04-17 (04:300) | S16-74, S16-55 | T-LCK-003, T-LCK-002 | 04:300 lock at threshold and auto-lift ≡ 16:547 Story 3 (iii) and 16:545 Story 3 (i) |
| S04-18 (04:301) | S08-08 | T-SES-006 | 04:301 absolute timeout ≡ 08:449 |
| S04-20 (04:301) | S11-12 | T-ADM-006 | 04:301 YAML-only role change ≡ 11:965 matrix change on next load |
| S04-22 (04:302) | S11-14 | T-ADM-009 | 04:302 IDOR bounded to self ≡ 11:968 / Std §5:479 |
| S04-23 (04:302) | S11-04 | T-ADM-008 | 04:302 no credential fields ≡ 11:418 API-wide absence |
| S04-24 (04:303) | S16-58 | T-AUD-013 | 04:303 recipe plaintext password replaced by reset token (10); ≡ 16:384 canary scan |
| S04-25 (04:304) | S07-05 | T-CRED-002 | 04:304 history rotation ≡ 07:465 history-length boundary |
| S04-26 (04:305) | S09-10 | T-AUTH-003 | ruling 2: 04:305 timing ≡ 09§R-5 matches() count |
| S04-27 (04:524) | S20-05, S14-02 | T-HDR-004, T-HDR-003 | 04:524 SPA CSP ≡ 20:227 header/meta and 14:98 zero violations |
| S05-03 (05:413) | S08-07, S08-03 | T-SES-010, T-SES-009 | 05:413 ≡ 08:449 max-one-session and 08:240 PRINCIPAL_NAME |
| S05-04 (05:517) | S08-08 | T-SES-006 | 05 risk 3 (restated on the clock, 16 §9) ≡ 08:449 absolute lifetime |
| S05-07 (05:716) | S20-04 | T-SES-011 | 05:716 ≡ 20:173 profile-scoped cookie test (rule 12) |
| S05-08 (05:722) | S08-06 | T-CSRF-001 | 05:722 ≡ 08:402 no CSRF cookie |
| S05-11 (05:1180) | S09-03 | T-RL-002 | 05:1180 ≡ 09:183 429-not-401 |
| S05-12 (05:1186) | S09-02 | T-RL-003 | 05:1186 ≡ 09:178 per-account throw leaves counter |
| S05-13 (05:1192) | S09-04 | T-AUTH-005 | 05:1192 ≡ 09:397 |
| S05-14 (05:1205) | S16-53 | T-ARCH-001 | 05:1205 ≡ 16:533 clock ban |
| S07-06 (07:551) | S10-05 | T-CRED-007 | 07:551 ≡ 10:594 canonicaliser never on passwords |
| S08-01 (08:78) | S20-03 | T-SES-002 | 08:78 ≡ 20:159 SameSite=Strict raw Set-Cookie |
| S08-02 (08:82) | S20-04 | T-SES-011 | 08:82 ≡ 20:173 profile-scoped cookie test (rule 12) |
| S08-23 (08:769) | S29-15 | T-SES-023 | ruling 8: 08:769 ≡ 29:458 dedicated cron-enabled test |
| S10-11 (10:760) | S09-16 | T-CRED-009 | 10:760 ≡ 09§R-6 ArchUnit |
| S09-11 (09:1177) | S09-10 | T-AUTH-003 | ruling 2: 16 §5 the matches() count catches a UserCache hit |
| S09-17 (09:1358) | S08-09 | T-SES-022 | 09§R-7 ≡ 08:694 reconciliation sweep |
| S16-01 (16:30) | S06-01 | T-AUTH-006 | ruling 3: 16:30 ≡ 06:175 |
| S16-02 (16:36) | S06-03, S06-04, S06-05, S06-06 | T-AUTH-009, T-AUTH-010, T-AUTH-002, T-AUTH-001 | ruling 3: 16:36 ≡ 06:299–301 producers |
| S16-03 (16:36) | S06-07 | T-AUTH-011 | ruling 3: 16:36 ≡ 06:303 no sendError |
| S16-05 (16:52) | S20-05 | T-HDR-004 | 16:52 ≡ 20:227 document CSP under vite preview |
| S16-06 (16:57) | S20-03 | T-SES-002 | 16:57 ≡ 20:159 SameSite=Strict raw Set-Cookie |
| S16-07 (16:60) | S20-04 | T-SES-011 | 16:60 ≡ 20:173 profile-scoped cookie name |
| S16-08 (16:63) | S20-01 | T-HDR-007 | 16:63 ≡ 20:141 CORS allow-listed preflight |
| S16-09 (16:63) | S20-02 | T-HDR-008 | 16:63 ≡ 20:143 CORS non-allow-listed |
| S16-11 (16:173) | S25-01, S25-02 | T-BLD-004, T-BLD-005 | 16:173 ≡ 25:664 jar and bundle assertions |
| S16-12 (16:188) | S25-04 | T-BLD-006 | 16:188 ≡ 25:491 drift gate |
| S16-14 (16:207) | S28-10 | T-AUD-027 | ruling 1: 16:207 ≡ 28 test 9 runner emits no secret |
| S16-15 (16:209) | S25-03 | T-ADM-022 | ruling 7: 16:209 ≡ 25:415 reserved-name refusal |
| S16-16 (16:234) | S11-08 | T-MFA-002 | ruling 5: 15 #1 ≡ 11:888 factor matrix |
| S16-17 (16:235) | S23-09 | T-MFA-007 | ruling 5: 15 #2 ≡ 23:1144 tier-2 lock order |
| S16-18 (16:236) | S26-05 | T-RL-016 | 16:236 (15 #3) ≡ 26:510 miss budget on every route |
| S16-19 (16:237) | S27-03, S27-04, S27-05, S27-06 | T-OBS-002, T-OBS-003, T-OBS-004, T-AUD-028 | ruling 4: 15 row 4 restated by 27:256 |
| S16-20 (16:238) | S28-09 | T-RUN-003 | ruling 6: 16 row 5 argument gating ≡ 28 test 8 |
| S16-22 (16:240) | S15-07 | T-SES-025 | 16:240 ≡ 15:190 TM-06 |
| S16-23 (16:241) | S15-08 | T-OBS-005 | 16:241 ≡ 15:193 TM-07 |
| S16-24 (16:261) | S28-01 | T-RUN-004 | ruling 6: 16:261 ≡ 28 test 1 |
| S16-25 (16:262) | S28-02 | T-RUN-005 | ruling 6: 16:262 ≡ 28 test 2 |
| S16-26 (16:263) | S28-03 | T-RUN-006 | ruling 6: 16:263 ≡ 28 test 3 |
| S16-27 (16:264) | S28-04 | T-RUN-007 | ruling 6: 16:264 ≡ 28 test 4 |
| S16-28 (16:265) | S28-05 | T-RUN-008 | ruling 6: 16:265 ≡ 28 test 5 (refusals) |
| S16-29 (16:265) | S28-06 | T-RUN-009 | ruling 6: 16:265 ≡ 28:245 lost-authenticator admission |
| S16-30 (16:268) | S28-07 | T-RUN-010 | ruling 6: 16:268 ≡ 28 test 6 |
| S16-31 (16:270) | S28-08 | T-RUN-011 | ruling 6: 16:270 ≡ 28 test 7 |
| S16-32 (16:273) | S28-09 | T-RUN-003 | ruling 6: 16:273 ≡ 28 test 8 |
| S16-33 (16:275) | S28-10 | T-AUD-027 | ruling 1: 16:275 ≡ 28 test 9 |
| S16-34 (16:277) | S28-11 | T-AUD-029 | ruling 6: 16:277 ≡ 28 test 10 |
| S16-35 (16:278) | S28-12 | T-RUN-001 | ruling 6: 16:278 ≡ 28 test 11 |
| S16-36 (16:280) | S24-21, S24-22, S24-23 | T-CFG-012, T-CFG-013, T-CFG-014 | ruling 6: 16 §9 28 test 12 duplicates 24's validator entries |
| S16-37 (16:293) | S29-01 | T-SES-026 | ruling 8: 16:293 ≡ 29 §9 test 1 |
| S16-38 (16:295) | S29-02 | T-SES-027 | ruling 8: 16:295 ≡ 29 §9 test 2 |
| S16-39 (16:297) | S29-05 | T-SES-024 | ruling 8: 16:297 ≡ 29 §9 test 5 |
| S16-40 (16:298) | S29-10 | T-OBS-006 | ruling 8: 16:298 ≡ 29 §9 test 10 |
| S16-41 (16:310) | S31-11 | T-LCK-011 | 16:310 ≡ 31:345 ladder startup floor |
| S16-42 (16:321) | S30-01 | T-ADM-023 | ruling 7: 16:321 ≡ 30:195 |
| S16-43 (16:322) | S30-02 | T-ADM-024 | ruling 7: 16:322 ≡ 30:196 |
| S16-44 (16:323) | S30-03 | T-ADM-025 | ruling 7: 16:323 ≡ 30:197 |
| S16-45 (16:324) | S30-04 | T-ADM-026 | ruling 7: 16:324 ≡ 30:198 |
| S16-46 (16:328) | S30-05 | T-ADM-027 | ruling 7: 16:328 ≡ 30:201 |
| S16-47 (16:329) | S30-06 | T-ADM-028 | ruling 7: 16:329 ≡ 30:202 |
| S16-48 (16:331) | S30-07 | T-ADM-029 | ruling 7: 16:331 ≡ 30:203 |
| S16-49 (16:334) | S30-08 | T-OBS-007 | ruling 7: 16:334 ≡ 30:206 |
| S16-57 (16:383) | S08-10, S08-11, S08-12, S08-13, S08-14, S08-15, S08-16, S08-17, S08-18, S08-19, S08-20, S08-21 | T-SES-012, T-SES-013, T-SES-014, T-SES-015, T-SES-003, T-SES-004, T-SES-016, T-SES-017, T-SES-018, T-SES-021, T-SES-019, T-SES-020 | 16 §5 session replay ≡ 08:761 per-trigger rows |
| S16-59 (16:392) | S14-02 | T-HDR-003 | 16:597 ≡ 14:656 zero securitypolicyviolation (rule 12) |
| S16-60 (16:397) | S11-09 | T-ADM-018 | 16:561 §5:424 ≡ 11:957 |
| S16-62 (16:399) | S11-11 | T-ADM-020 | 16:562 / 32:59 ≡ 11:961 §5:425 denyAll 403 |
| S16-65 (16:416) | S29-15 | T-SES-023 | ruling 8: 16:522 ≡ 29:458 cron-enabled test |
| S16-67 (16:427) | S06-08 | T-AUTH-012 | 16:605 ≡ 06:484 enum-schema contract test |
| S16-69 (16:450) | S29-14 | T-SES-028 | ruling 8: 16:450 ≡ 29:457 cron binding |
| S16-70 (16:454) | S31-08 | T-RL-020 | 16:558 merged unparseable test ≡ 31:341 |
| S16-75 (16:566) | S11-13 | T-ADM-005 | 16:566 §5:485 ≡ 11:967 |
| S16-76 (16:566) | S11-14 | T-ADM-009 | 16:566 §5:479 ≡ 11:968 |
| S16-77 (16:598) | S08-04 | T-HDR-005 | 16:598 ≡ 08:760 Clear-Site-Data browser assertion |
| S32-01 (32:58) | S11-10 | T-ADM-019 | 32:58 anonymous 401 ≡ 11:958 |
| S11-05 (11:587) | S09-04 | T-AUTH-005 | 11:587 keep-the-assertion ≡ 09:397 |
| S11-06 (11:658) | S10-05 | T-CRED-007 | 11:658 ≡ 10:594 |
| S11-07 (11:682) | S23-01 | T-ADM-017 | 11:682 ≡ 23:291 (ticket 23 adds it) |
| S12-16 (12:797) | S23-06 | T-MFA-005 | 12:797 JDBC round trip of FactorGrantedAuthority ≡ 23:445 serialisation test |
| S12-18 (12:1112) | S29-05 | T-SES-024 | ruling 8: 12:1112 ≡ 29 §9 test 5 |
| S13-13 (13:565) | S26-04 | T-AUD-024 | 13:565 raw-URI cap ≡ 26:540 (26 owns truncation) |
| S13-21 (13:807) | S09-05 | T-RL-005 | 13:807 ≡ 09:586 converter sets WebAuthenticationDetails |
| S13-22 (13:856) | S28-10 | T-AUD-027 | ruling 1: 13:856 ≡ 28 test 9 |
| S13-23 (13:877) | S28-14, S28-12 | T-RUN-002, T-RUN-001 | 13:877 ≡ 28:334 uid build check and 28:309 absence fallback |
| S14-27 (14:655) | S08-04 | T-HDR-005 | 14:655 ≡ 08:760 Clear-Site-Data browser assertion |
| S14-28 (14:657) | S06-08 | T-AUTH-012 | 14:657 ≡ 06:484 enum-schema contract test |
| S15-01 (15:231) | S11-08 | T-MFA-002 | ruling 5: 15 #1 ≡ 11:888 |
| S15-02 (15:231) | S23-09 | T-MFA-007 | ruling 5: 15 #2 ≡ 23:1144 |
| S15-03 (15:231) | S26-05 | T-RL-016 | 15 #3 (TM-01) ≡ 26:510 miss budget |
| S15-04 (15:231) | S27-03, S27-04, S27-05, S27-06 | T-OBS-002, T-OBS-003, T-OBS-004, T-AUD-028 | ruling 4: 15 row 4 restated by 27:256 |
| S15-05 (15:231) | S28-09 | T-RUN-003 | ruling 6: 15:375 argument gating ≡ 28 test 8 |
| S15-06 (15:231) | S16-21 | T-CRED-023 | 15 #6 (TM-13) ≡ 16:588 §7 structural test |
| S19-02 (19:310) | S30-05, S30-06 | T-ADM-027, T-ADM-028 | 19:310 two-admin invariant ≡ 30:201 exemption and 30:202 refusals |
| S20-06 (20:305) | S14-01 | T-BLD-002 | 20:305 ≡ 14:92 built index.html check |
| S21-09 (21:396) | S09-09 | T-OBS-001 | 21:396 ≡ 09:1014 recordStats (third structure pulled in via Edits) |
| S21-11 (21:575) | S09-21 | T-LCK-015 | 21:575 ≡ 09:1596 alert-threshold binding |
| S21-14 (21:627) | S09-10 | T-AUTH-003 | 16 §9: 21:627 ≡ 09§R-5 matches() count |
| S21-15 (21:722) | S25-01 | T-BLD-004 | 21:722 filenames ≡ 25:283 jar-content test |
| S22-03 (22:547) | S14-20 | T-FE-013 | 22:547 ported case 3 ≡ 14:548 clear on every Verify |
| S22-08 (22:553) | S14-10 | T-FE-003 | 22:553 object-URL lifecycle ≡ 14:504 revoke-count test |
| S22-16 (22:764) | S10-04 | T-LCK-017 | 22:764 ≡ 10:400 redemption clears password lockout, never TOTP |
| S23-07 (23:966) | S12-07 | T-MFA-003 | 23:966 ≡ 12:287 totp_key width checks |
| S23-08 (23:977) | S12-02 | T-CFG-003 | 23:977 ≡ 12:276 BINARY retype caught |
| S23-10 (23:1167) | S11-08 | T-MFA-002 | ruling 5: 23:1167 ≡ 11:888 |
| S24-03 (24:504) | S20-04 | T-SES-011 | 24:504 ≡ 20:173 profile-scoped cookie test (rule 10: ctx-nondev over 24:573) |
| S24-04 (24:505) | S20-04 | T-SES-011 | 24:505 ≡ 20:173 profile-scoped cookie test |
| S24-18 (24:884) | S13-05 | T-AUD-018 | 24:884 ≡ 13:540 Jackson source redaction outcome |
| S24-19 (24:919) | S13-10 | T-AUD-022 | 24:919 adopted verbatim into 13's negative list ≡ 13:562 |
| S24-20 (24:931) | S21-08 | T-OBS-012 | 24:931 ≡ 21:352 OTLP dormant (absent beans) |
| S24-25 (24:1078) | S28-11 | T-AUD-029 | 24:1078 ≡ 16:277 / 28:307 test |
| S24-26 (24:1079) | S28-12 | T-RUN-001 | 24:1079 ≡ 16:278 / 28:309 test |
| S25-05 (25:870) | S16-21 | T-CRED-023 | 25:870 TM-13 ≡ 16:588 §7 structural test |
| S25-06 (25:897) | S28-10 | T-AUD-027 | ruling 1: 25:897 ≡ 28 test 9 |
| S28-13 (28:314) | S24-21, S24-22, S24-23 | T-CFG-012, T-CFG-013, T-CFG-014 | ruling 6: 16 §9 28 test 12 duplicates 24's validator entries |

## Retired

| staged | retired by | reason |
|---|---|---|
| S04-04 (04:225) | 06:481 | No generic user-update endpoint exists (11:209–218), so Standard §5:473's immutability case is N/A by construction. |
| S04-05 (04:226) | 06:481 | No generic user-update endpoint exists (11:209–218), so there is no path to change a password through it. |
| S04-06 (04:226) | 06:481 | 06:481 records §5:473's "lock via generic update" half as N/A by construction. |
| S04-19 (04:301) | 23:291 | Role hierarchy is out of scope; ticket 23 asserts no RoleHierarchy bean exists instead. |
| S16-13 (16:205) | 25:956 | No runner token exists (28 reads the password from stdin); ASVS 6.4.1 (L1) single-use limb has no subject. |
| S21-13 (21:615) | 21:615 | Harness configuration, not a test: an inert registry makes every meter assertion fail, so the trap self-detects. |

## Lists

| list | file | first line | last line | IDs |
|---|---|---|---|---|
| L01-a | 01 | 144 | 144 | T-HDR-002, T-HDR-001, T-SES-001, T-RL-001, T-RL-002, T-AUTH-001 |
| L01-b | 01 | 306 | 306 | T-HDR-002, T-HDR-001, T-SES-001, T-RL-001, T-RL-002, T-AUTH-001, T-AUTH-002 |
| L01-c | 01 | 318 | 324 | T-RL-001, T-RL-002 |
| L02-a | 02 | 197 | 198 | T-LCK-001, T-RL-002 |
| L02-b | 02 | 341 | 343 | T-CRED-001 |
| L02-c | 02 | 394 | 396 | T-SES-002 |
| L03-a | 03 | 394 | 394 | T-AUD-007, T-AUD-009, T-AUD-008 |
| L03-b | 03 | 467 | 467 | T-AUD-010 |
| L03-c | 03 | 618 | 619 | T-AUD-010 |
| L03-d | 03 | 635 | 638 | T-AUD-011 |
| L03-e | 03 | 691 | 696 | T-AUD-012 |
| L04-a | 04 | 223 | 232 | T-ADM-001, T-ADM-002, T-ADM-003, T-ADM-004, T-SES-003, T-SES-004, T-ADM-005 |
| L04-b | 04 | 298 | 306 | T-HDR-001, T-HDR-002, T-CSRF-001, T-SES-005, T-LCK-003, T-LCK-002, T-SES-006, T-ADM-006, T-ADM-007, T-ADM-009, T-ADM-008, T-AUD-013, T-CRED-002, T-AUTH-003, T-RL-001 |
| L04-c | 04 | 523 | 525 | T-HDR-004, T-HDR-003 |
| L05-a | 05 | 128 | 134 | T-MFA-001 |
| L05-b | 05 | 240 | 241 | T-CSRF-002 |
| L05-c | 05 | 413 | 417 | T-SES-010, T-SES-009 |
| L05-d | 05 | 516 | 519 | T-SES-006 |
| L05-e | 05 | 605 | 607 | T-SES-010, T-SES-009, T-SES-005, T-AUTH-004 |
| L05-f | 05 | 716 | 717 | T-SES-011 |
| L05-g | 05 | 719 | 722 | T-CSRF-001 |
| L05-h | 05 | 1031 | 1066 | T-MFA-001, T-SES-010, T-SES-009, T-SES-006, T-SES-005, T-AUTH-004, T-SES-011, T-CSRF-003, T-CSRF-004 |
| L05-i | 05 | 1180 | 1190 | T-RL-002, T-RL-003 |
| L05-j | 05 | 1192 | 1198 | T-AUTH-005 |
| L05-k | 05 | 1202 | 1208 | T-SES-006, T-CSRF-004, T-ARCH-001 |
| L06-a | 06 | 175 | 176 | T-AUTH-006 |
| L06-b | 06 | 296 | 307 | T-AUTH-008, T-AUTH-009, T-AUTH-010, T-AUTH-002, T-AUTH-001, T-AUTH-011 |
| L06-c | 06 | 484 | 484 | T-AUTH-012 |
| L07-a | 07 | 260 | 261 | T-CRED-004 |
| L07-b | 07 | 355 | 356 | T-CRED-005 |
| L07-c | 07 | 465 | 468 | T-CRED-004, T-CRED-005, T-CRED-001, T-CRED-006, T-CRED-002 |
| L07-d | 07 | 551 | 553 | T-CRED-007 |
| L08-a | issues/08-session-and-csrf-contract.md | 78 | 78 | T-SES-002 |
| L08-b | issues/08-session-and-csrf-contract.md | 82 | 82 | T-SES-011 |
| L08-c | issues/08-session-and-csrf-contract.md | 240 | 242 | T-SES-009 |
| L08-d | issues/08-session-and-csrf-contract.md | 378 | 380 | T-HDR-005 |
| L08-e | issues/08-session-and-csrf-contract.md | 394 | 400 | T-CSRF-007 |
| L08-f | issues/08-session-and-csrf-contract.md | 402 | 410 | T-CSRF-001 |
| L08-g | issues/08-session-and-csrf-contract.md | 449 | 451 | T-SES-010, T-SES-006, T-HDR-005, T-CSRF-007, T-CSRF-001 |
| L08-h | issues/08-session-and-csrf-contract.md | 694 | 695 | T-SES-022 |
| L08-i | issues/08-session-and-csrf-contract.md | 722 | 731 | T-HDR-005 |
| L08-j | issues/08-session-and-csrf-contract.md | 758 | 769 | T-SES-012, T-SES-013, T-SES-014, T-SES-015, T-SES-003, T-SES-004, T-SES-016, T-SES-017, T-SES-018, T-SES-021, T-SES-019, T-SES-020, T-SES-001, T-SES-023, T-HDR-005, T-SES-022 |
| L10-a | issues/10-credential-flows.md | 355 | 359 | T-CRED-016, T-CRED-017, T-CRED-018 |
| L10-b | issues/10-credential-flows.md | 398 | 403 | T-LCK-017 |
| L10-c | issues/10-credential-flows.md | 594 | 597 | T-CRED-007 |
| L10-d | issues/10-credential-flows.md | 617 | 619 | T-CRED-016, T-CRED-017, T-CRED-018 |
| L10-e | issues/10-credential-flows.md | 636 | 639 | T-CRED-011, T-CRED-014, T-CRED-015, T-AUTH-014, T-ADM-010, T-CRED-016, T-CRED-017, T-CRED-018, T-LCK-017 |
| L10-f | issues/10-credential-flows.md | 760 | 764 | T-CRED-009 |
| L09-a | 09 | 178 | 188 | T-RL-001, T-RL-003, T-RL-002 |
| L09-b | 09 | 397 | 402 | T-AUTH-005 |
| L09-c | 09 | 586 | 594 | T-RL-005 |
| L09-d | 09 | 687 | 693 | T-AUTH-005 |
| L09-e | 09 | 702 | 706 | T-RL-002, T-RL-003, T-AUTH-005, T-LCK-008, T-LCK-001, T-CFG-001 |
| L09-f | 09 | 1014 | 1023 | T-OBS-001 |
| L09-g | 09 | 1131 | 1134 | — |
| L09-h | 09 | 1171 | 1184 | T-AUTH-003, T-LCK-009 |
| L09-i | 09 | 1218 | 1219 | T-LCK-010 |
| L09-j | 09 | 1281 | 1281 | T-RL-005 |
| L09-k | 09 | 1358 | 1369 | T-RL-006, T-RL-007, T-RL-005, T-LCK-009, T-AUTH-003, T-CRED-009, T-SES-022, T-LCK-010 |
| L09-l | 09 | 1590 | 1630 | T-LCK-012, T-LCK-013, T-LCK-010, T-LCK-014, T-LCK-015, T-LCK-016, T-RL-008, T-RL-009, T-RL-010, T-RL-011 |
| L09-m | 09 | 1624 | 1628 | T-RL-012, T-RL-013, T-RL-014, T-RL-015 |
| L16-a | 16 | 24 | 45 | T-AUTH-006, T-AUTH-009, T-AUTH-010, T-AUTH-002, T-AUTH-001, T-AUTH-011, T-AUTH-003, T-RL-017 |
| L16-b | 16 | 47 | 65 | T-HDR-004, T-SES-002, T-SES-011, T-HDR-007, T-HDR-008 |
| L16-c | 16 | 107 | 140 | T-RL-002, T-RL-003, T-AUTH-005, T-LCK-008, T-AUTH-016, T-LCK-001, T-CFG-001 |
| L16-d | 16 | 144 | 164 | T-RL-006, T-RL-007, T-RL-005, T-LCK-009, T-AUTH-003, T-CRED-009, T-SES-022, T-LCK-010 |
| L16-e | 16 | 168 | 216 | T-BLD-004, T-BLD-005, T-BLD-006, T-AUD-027, T-ADM-022 |
| L16-f | 16 | 220 | 252 | T-MFA-002, T-MFA-007, T-RL-016, T-OBS-002, T-OBS-003, T-OBS-004, T-AUD-028, T-RUN-003, T-CRED-023, T-SES-025, T-OBS-005 |
| L16-g | 16 | 256 | 284 | T-RUN-004, T-RUN-005, T-RUN-006, T-RUN-007, T-RUN-008, T-RUN-009, T-RUN-010, T-RUN-011, T-RUN-003, T-AUD-027, T-AUD-029, T-RUN-001, T-CFG-012, T-CFG-013, T-CFG-014 |
| L16-h | 16 | 288 | 298 | T-SES-026, T-SES-027, T-SES-024, T-OBS-006 |
| L16-i | 16 | 302 | 312 | T-RL-005, T-LCK-011 |
| L16-j | 16 | 316 | 334 | T-ADM-023, T-ADM-024, T-ADM-025, T-ADM-026, T-ADM-027, T-ADM-028, T-ADM-029, T-OBS-007 |
| L16-k | 16 | 338 | 460 | T-CRED-024, T-RL-018, T-MFA-008, T-ARCH-001, T-LCK-019, T-LCK-002, T-LCK-020, T-SES-012, T-SES-013, T-SES-014, T-SES-015, T-SES-003, T-SES-004, T-SES-016, T-SES-017, T-SES-018, T-SES-021, T-SES-019, T-SES-020, T-AUD-013, T-HDR-003, T-ADM-018, T-RL-019, T-ADM-020, T-HDR-001, T-HDR-002, T-SES-023, T-ARCH-002, T-AUTH-012, T-AUD-030, T-SES-028, T-RL-020, T-RL-007, T-AUTH-003, T-SES-022, T-LCK-008, T-AUTH-016, T-BLD-006, T-RL-002, T-RL-003 |
| L16-l | 16 | 484 | 497 | T-BLD-007, T-BLD-008 |
| L16-m | 16 | 499 | 523 | T-CRED-025, T-SES-023, T-SES-028 |
| L16-n | 16 | 525 | 534 | T-RL-018, T-MFA-008, T-ARCH-001, T-LCK-019, T-AUTH-016 |
| L16-o | 16 | 536 | 566 | T-AUTH-003, T-LCK-002, T-LCK-020, T-LCK-003, T-LCK-008, T-SES-012, T-SES-013, T-SES-014, T-SES-015, T-SES-003, T-SES-004, T-SES-016, T-SES-017, T-SES-018, T-SES-021, T-SES-019, T-SES-020, T-SES-022, T-AUD-013, T-AUD-030, T-ARCH-002, T-RL-020, T-RL-007, T-CRED-024, T-ADM-018, T-ADM-020, T-RL-019, T-HDR-001, T-HDR-002, T-ADM-005, T-ADM-009 |
| L16-p | 16 | 586 | 592 | T-CRED-023 |
| L16-q | 16 | 594 | 608 | T-HDR-003, T-HDR-005, T-E2E-002, T-E2E-003, T-E2E-004, T-E2E-005, T-AUTH-012, T-BLD-006 |
| L32-a | 32 | 52 | 56 | T-RL-012, T-RL-013, T-RL-014, T-RL-015 |
| L32-b | 32 | 57 | 62 | T-ADM-019, T-ADM-020 |
| L32-c | 32 | 63 | 63 | T-BLD-007, T-BLD-008 |
| L11-a | 11 | 175 | 175 | T-ADM-012 |
| L11-b | 11 | 247 | 247 | T-ADM-014 |
| L11-c | 11 | 417 | 419 | T-ADM-016, T-ADM-008 |
| L11-d | 11 | 587 | 590 | T-AUTH-005 |
| L11-e | 11 | 658 | 658 | T-CRED-007 |
| L11-f | 11 | 682 | 683 | T-ADM-017 |
| L11-g | 11 | 722 | 722 | T-ADM-008 |
| L11-h | 11 | 887 | 888 | T-MFA-002 |
| L11-i | 11 | 957 | 968 | T-ADM-018, T-ADM-019, T-ADM-020, T-ADM-006, T-ADM-005, T-ADM-009 |
| L12-a | 12 | 270 | 285 | T-CFG-002, T-CFG-003, T-CFG-004, T-CFG-005, T-CFG-006 |
| L12-b | 12 | 287 | 291 | T-CFG-007, T-MFA-003, T-MFA-004, T-CRED-020, T-CFG-008, T-ADM-021, T-CRED-021, T-ADM-002 |
| L12-c | 12 | 323 | 327 | T-CRED-022 |
| L12-d | 12 | 778 | 783 | T-BLD-001 |
| L12-e | 12 | 797 | 799 | T-MFA-005 |
| L12-f | 12 | 991 | 993 | T-CFG-002, T-MFA-003, T-BLD-001 |
| L12-g | 12 | 1087 | 1088 | T-LCK-018 |
| L12-h | 12 | 1112 | 1112 | T-SES-024 |
| L12-i | 12 | 315 | 317 | T-ADM-008 |
| L13-a | 13 | 292 | 294 | T-AUD-010 |
| L13-b | 13 | 498 | 506 | T-AUD-007, T-AUD-014 |
| L13-c | 13 | 508 | 509 | T-AUD-017 |
| L13-d | 13 | 537 | 542 | T-AUD-018 |
| L13-e | 13 | 553 | 567 | T-AUD-019, T-AUD-020, T-AUD-021, T-AUD-008, T-AUD-022, T-AUD-016, T-AUD-023, T-AUD-024, T-AUD-009, T-AUD-025, T-AUD-026, T-AUD-018 |
| L13-f | 13 | 601 | 604 | T-AUD-011 |
| L13-g | 13 | 630 | 642 | T-CFG-009, T-CFG-010, T-CFG-011 |
| L13-h | 13 | 794 | 800 | — |
| L13-i | 13 | 807 | 808 | T-RL-005 |
| L13-j | 13 | 856 | 859 | T-AUD-027 |
| L13-k | 13 | 877 | 878 | T-RUN-002, T-RUN-001 |
| L14-a | 14 | 90 | 92 | T-BLD-002 |
| L14-b | 14 | 97 | 98 | T-HDR-003 |
| L14-c | 14 | 226 | 229 | T-AUTH-015 |
| L14-d | 14 | 255 | 257 | T-E2E-001 |
| L14-e | 14 | 308 | 312 | T-MFA-006 |
| L14-f | 14 | 332 | 333 | T-FE-001 |
| L14-g | 14 | 394 | 396 | T-FE-002 |
| L14-h | 14 | 457 | 457 | T-HDR-006 |
| L14-i | 14 | 458 | 462 | T-BLD-003, T-BLD-002 |
| L14-j | 14 | 504 | 506 | T-FE-003 |
| L14-k | 14 | 537 | 556 | T-FE-004, T-FE-005, T-FE-006, T-FE-007, T-FE-008, T-FE-009, T-FE-010, T-FE-011, T-FE-012, T-FE-013, T-FE-014, T-FE-015, T-FE-016 |
| L14-l | 14 | 590 | 594 | T-HDR-003 |
| L14-m | 14 | 627 | 635 | T-FE-003, T-HDR-003, T-BLD-002, T-HDR-006, T-AUTH-015, T-MFA-006, T-FE-001, T-FE-002, T-E2E-001, T-FE-017, T-FE-018, T-FE-019 |
| L14-n | 14 | 653 | 659 | T-HDR-005, T-HDR-003, T-AUTH-012 |
| L15-a | 15 | 231 | 234 | T-MFA-002, T-MFA-007, T-RL-016, T-OBS-002, T-OBS-003, T-OBS-004, T-AUD-028, T-RUN-003, T-CRED-023, T-SES-025, T-OBS-005 |
| L15-b | 15 | 184 | 186 | T-MFA-002 |
| L15-c | 15 | 178 | 179 | T-OBS-002, T-OBS-003, T-OBS-004, T-AUD-028 |
| L15-d | 15 | 295 | 297 | T-OBS-005 |
| L15-e | 15 | 307 | 307 | T-SES-025 |
| L15-f | 15 | 375 | 375 | T-RUN-003 |
| L15-g | 15 | 385 | 386 | T-OBS-002, T-OBS-003, T-OBS-004, T-AUD-028 |
| L19-a | 19 | 310 | 311 | T-MFA-009, T-ADM-027, T-ADM-028 |
| L20-a | 20 | 141 | 144 | T-HDR-007, T-HDR-008 |
| L20-b | 20 | 159 | 163 | T-SES-002 |
| L20-c | 20 | 173 | 174 | T-SES-011 |
| L20-d | 20 | 227 | 230 | T-HDR-004 |
| L20-e | 20 | 305 | 306 | T-BLD-002 |
| L20-f | 20 | 310 | 313 | T-HDR-007, T-HDR-008, T-SES-002, T-SES-011, T-HDR-004 |
| L21-a | 21 | 251 | 261 | T-OBS-008 |
| L21-b | 21 | 267 | 271 | T-AUTH-017 |
| L21-c | 21 | 287 | 294 | T-OBS-009, T-OBS-010, T-OBS-011 |
| L21-d | 21 | 299 | 308 | T-CFG-015, T-CFG-016 |
| L21-e | 21 | 352 | 354 | T-OBS-012 |
| L21-f | 21 | 390 | 396 | T-OBS-001 |
| L21-g | 21 | 482 | 486 | T-OBS-013 |
| L21-h | 21 | 574 | 578 | T-LCK-015 |
| L21-i | 21 | 608 | 627 | T-OBS-014, T-AUTH-003, T-OBS-008, T-OBS-011, T-CFG-015, T-CFG-016, T-OBS-012, T-OBS-001, T-OBS-013 |
| L21-j | 21 | 719 | 724 | T-BLD-004 |
| L21-k | 21 | 837 | 837 | T-OBS-013 |
| L22-a | 22 | 546 | 556 | T-FE-020, T-FE-021, T-FE-013, T-FE-022, T-FE-023, T-FE-024, T-FE-025, T-FE-003, T-FE-026, T-FE-027 |
| L22-b | 22 | 389 | 393 | T-MFA-012, T-MFA-013 |
| L22-c | 22 | 730 | 735 | T-MFA-014, T-MFA-015, T-MFA-016 |
| L22-d | 22 | 757 | 766 | T-LCK-017 |
| L23-a | 23 | 291 | 293 | T-ADM-017 |
| L23-b | 23 | 295 | 298 | T-MFA-018 |
| L23-c | 23 | 300 | 307 | T-CFG-017, T-CFG-018, T-CFG-019 |
| L23-d | 23 | 445 | 450 | T-MFA-005 |
| L23-e | 23 | 962 | 970 | T-MFA-003 |
| L23-f | 23 | 977 | 980 | T-CFG-003 |
| L23-g | 23 | 1142 | 1146 | T-MFA-007 |
| L23-h | 23 | 1167 | 1171 | T-MFA-002 |
| L24-a | 24 | 214 | 219 | T-CFG-020 |
| L24-b | 24 | 325 | 327 | T-CFG-020 |
| L24-c | 24 | 295 | 296 | T-CFG-021 |
| L24-d | 24 | 504 | 505 | T-SES-011 |
| L24-e | 24 | 534 | 554 | T-CFG-024, T-CFG-025, T-CFG-026, T-CFG-027, T-CFG-028, T-CFG-029, T-CFG-030, T-CFG-031, T-CFG-032, T-CFG-033 |
| L24-f | 24 | 644 | 648 | T-CFG-034, T-CFG-035 |
| L24-g | 24 | 793 | 797 | T-BLD-009 |
| L24-h | 24 | 884 | 899 | T-AUD-018 |
| L24-i | 24 | 919 | 921 | T-AUD-022 |
| L24-j | 24 | 931 | 938 | T-OBS-012 |
| L24-k | 24 | 974 | 977 | T-OBS-012 |
| L24-l | 24 | 990 | 1000 | — |
| L24-m | 24 | 1035 | 1041 | T-BLD-004 |
| L24-n | 24 | 1058 | 1061 | — |
| L24-o | 24 | 1063 | 1070 | T-CFG-012, T-CFG-013, T-CFG-014 |
| L24-p | 24 | 1075 | 1080 | T-RUN-012, T-AUD-029, T-RUN-001, T-RUN-013 |
| L25-a | 25 | 140 | 145 | T-CFG-029 |
| L25-b | 25 | 264 | 272 | T-CFG-031 |
| L25-c | 25 | 279 | 281 | T-BLD-009 |
| L25-d | 25 | 283 | 288 | T-BLD-004, T-BLD-005 |
| L25-e | 25 | 415 | 416 | T-ADM-022 |
| L25-f | 25 | 438 | 442 | T-ADM-022, T-AUD-027 |
| L25-g | 25 | 491 | 494 | T-BLD-006 |
| L25-h | 25 | 664 | 670 | T-BLD-004, T-BLD-005 |
| L25-i | 25 | 698 | 704 | T-BLD-004 |
| L25-j | 25 | 870 | 874 | T-CRED-023 |
| L25-k | 25 | 897 | 905 | T-AUD-027 |
| L25-l | 25 | 964 | 971 | T-BLD-006, T-CRED-023 |
| L26-a | 26 | 504 | 513 | T-AUD-033, T-AUD-034, T-AUD-035, T-AUD-024, T-RL-016, T-AUD-032 |
| L26-b | 26 | 519 | 542 | T-SES-029, T-SES-030, T-SES-031, T-AUD-036, T-AUD-037, T-RL-021, T-RL-023, T-ARCH-005, T-AUD-038, T-AUD-034, T-AUD-024, T-AUD-032 |
| L26-c | 26 | 202 | 203 | T-RL-021 |
| L26-d | 26 | 427 | 430 | T-AUD-032 |
| L27-a | 27 | 195 | 199 | T-OBS-015 |
| L27-b | 27 | 208 | 215 | T-AUD-012 |
| L27-c | 27 | 256 | 267 | T-OBS-002, T-OBS-003, T-OBS-004, T-AUD-028 |
| L28-a | 28 | 245 | 246 | T-RUN-009 |
| L28-b | 28 | 280 | 290 | T-RUN-004, T-RUN-005, T-RUN-006, T-RUN-007, T-RUN-008, T-RUN-010, T-RUN-011, T-RUN-003 |
| L28-c | 28 | 307 | 310 | T-AUD-029, T-RUN-001 |
| L28-d | 28 | 314 | 319 | T-CFG-012, T-CFG-013, T-CFG-014 |
| L28-e | 28 | 333 | 335 | T-RUN-002 |
| L28-f | 28 | 377 | 378 | T-AUD-027 |
| L29-a | 29 | 243 | 244 | T-OBS-016 |
| L29-b | 29 | 292 | 293 | T-OBS-017 |
| L29-c | 29 | 327 | 354 | T-SES-026, T-SES-027, T-CSRF-008, T-SES-032, T-SES-024, T-SES-033, T-SES-034, T-SES-035, T-RL-024, T-OBS-006, T-AUD-039, T-OBS-016, T-OBS-017 |
| L29-d | 29 | 452 | 458 | T-SES-028, T-SES-023 |
| L30-a | 30 | 192 | 206 | T-ADM-023, T-ADM-024, T-ADM-025, T-ADM-026, T-ADM-027, T-ADM-028, T-ADM-029, T-OBS-007 |
| L31-a | 31 | 180 | 184 | T-LCK-011 |
| L31-b | 31 | 205 | 207 | T-AUD-040 |
| L31-c | 31 | 222 | 225 | T-RL-025 |
| L31-d | 31 | 246 | 247 | T-AUD-041 |
| L31-e | 31 | 324 | 347 | T-RL-026, T-RL-027, T-AUD-041, T-RL-025, T-RL-028, T-AUD-040, T-RL-029, T-RL-020, T-RL-030, T-RL-031, T-LCK-011, T-CFG-037 |

## Standards and PRD test sections

| prescribed test | at | disposition | T-IDs or register row |
|---|---|---|---|
| Role definitions loaded at startup | Std §5:423 | test | T-ADM-011, T-ADM-018, T-ADM-012 |
| Duplicate role definitions fail fast at startup | Std §5:424 | test | T-ADM-018 |
| Mutating role definitions via API returns 403 | Std §5:425 | test | T-ADM-020, T-ADM-019 |
| Role mapping change takes effect on next load | Std §5:426 | test | T-ADM-006 |
| Each HTTP method on a path independently authorized | Std §5:427 | test | T-ADM-012, T-MFA-002 |
| Role-protected endpoints: 200/201 authorized, 403 without role | Std §5:428 | new | T-ADM-013, T-MFA-002, T-AUTH-002 |
| User management endpoints reject non-admins, including own account | Std §5:429 | new | T-ADM-013, T-ADM-009 |
| Login gives new session id, kills prior session, differs per login | Std §5:433 | test | T-SES-005, T-SES-010 |
| Session persisted to DB for concurrent limits across restarts | Std §5:434 | new | T-SES-008, T-CSRF-004, T-SES-009 |
| Idle timeout forces re-auth after 15 minutes | Std §5:435 | test | T-SES-001 |
| Absolute timeout forces re-auth after 8 hours | Std §5:436 | test | T-SES-006 |
| Logout invalidates server session and clears cookies | Std §5:437 | new | T-SES-007, T-FE-017 |
| Logout on expired session handled gracefully by client | Std §5:438 | new | T-FE-017, T-CSRF-005 |
| Dedicated CSRF endpoint returns token in body | Std §5:442 | new | T-CSRF-006, T-CSRF-004 |
| CSRF token endpoint not cacheable | Std §5:443 | test | T-CSRF-002 |
| CSRF required on POST/PUT/PATCH/DELETE, not GET/HEAD | Std §5:444 | new | T-CSRF-006 |
| Invalid or missing CSRF token rejected with 403 | Std §5:445 | test | T-CSRF-003, T-AUTH-002, T-CSRF-008 |
| Previously redeemed CSRF tokens rejected | Std §5:446 | test | T-CSRF-007 |
| Failed logins increment counter, lock at 5, success resets | Std §5:450 | new | T-LCK-012, T-LCK-008, T-LCK-001, T-LCK-006 |
| Lockout persists restarts, auto-expires 20 min, admin unlock | Std §5:451 | new | T-LCK-007, T-LCK-002, T-LCK-010, T-LCK-005 |
| Login endpoint per-account rate limit, 429 with Retry-After | Std §5:452 | test | T-RL-003, T-RL-002, T-RL-017, T-RL-010 |
| Password-reset request per-account rate limit | Std §5:452 | new | T-RL-004, T-RL-010 |
| Password-reset redemption per-account rate limit | Std §5:452 | register | owed-17 |
| Rate limiter and sessions consistent under distributed deployment | Std §5:453 | register | 17:416 |
| Separate endpoints for admin issuance and user redemption | Std §5:457 | test | T-SES-015, T-SES-014, T-CRED-010 |
| Admin reset token random, single-use, plaintext returned once | Std §5:458 | new | T-CRED-010, T-CRED-014 |
| Tokens expire 30 min, stored hashed, new token invalidates prior | Std §5:459 | new | T-CRED-013, T-CRED-012, T-CRED-019 |
| Expired or already redeemed token fails with 400 | Std §5:460 | new | T-CRED-013, T-CRED-014, T-CRED-011 |
| Successful reset invalidates all prior sessions | Std §5:461 | test | T-SES-014 |
| Self-service change requires current password, rejects mismatch | Std §5:465 | new | T-CRED-008 |
| Self-service change succeeds and invalidates existing sessions | Std §5:466 | test | T-SES-012 |
| New passwords meet strength and history in reset and change | Std §5:467 | test | T-CRED-004, T-CRED-001, T-CRED-002, T-CRED-015 |
| Create fails 400 when username exists (incl tombstones) or email used | Std §5:471 | test | T-ADM-001, T-ADM-002 |
| Created user flagged for mandatory change, 30-day grace | Std §5:472 | register | owed-17 |
| Generic update rejects username change or lock | Std §5:473 | register | owed-17 |
| User cannot delete own account, 403 | Std §5:474 | test | T-ADM-003 |
| Deleted users retained as soft-delete tombstones | Std §5:475 | test | T-ADM-004, T-ADM-002, T-ADM-021 |
| Read-only self endpoint returns only caller's record | Std §5:479 | test | T-ADM-009 |
| Admins get full records; users cannot read others | Std §5:480 | test | T-MFA-002, T-ADM-009, T-ADM-008 |
| First-login users disabled if change not done in 30 days | Std §5:484 | new | T-ADM-015 |
| Re-enabled account flagged for mandatory password change | Std §5:485 | test | T-ADM-005 |
| Accounts disabled after 90 days inactivity | Std §5:486 | register | 17:409 |
| Roles revoked from long-inactive accounts after 180 days | Std §5:487 | register | 17:409 |
| Batch job disablement invalidates active sessions | Std §5:488 | register | 17:409 |
| Scheduler runs serialized per job name | Std §5:489 | register | 17:409 |
| Over-length or invalid-format input rejected with 400 | Std §5:493 | new | T-CRED-003 |
| Request bodies over maximum size rejected with 400 | Std §5:494 | test | T-RL-019, T-RL-011, T-RL-012, T-RL-013, T-RL-014, T-RL-015 |
| Batch reset requests over entry limits rejected with 400 | Std §5:494 | register | owed-17 |
| HSTS, nosniff, X-Frame-Options, CSP on all responses | Std §5:498 | test | T-HDR-001, T-HDR-002, T-HDR-004 |
| Session and CSRF cookies Secure, HttpOnly, SameSite=Lax | Std §5:499 | test | T-SES-002, T-SES-011, T-CSRF-001 |
| CORS preflight from allowed origin succeeds | Std §5:500 | test | T-HDR-007, T-E2E-003 |
| Non-allowlisted origins rejected; no wildcard | Std §5:501 | test | T-HDR-008 |
| Logout returns Clear-Site-Data | Std §5:502 | new | T-SES-007, T-HDR-005 |
| Auth failures identical in status, body and timing | Std §5:506 | test | T-AUTH-006, T-AUTH-003, T-RL-017 |
| Auth failures use machine-readable codes without specific reason | Std §5:507 | test | T-AUTH-006, T-AUTH-010, T-AUTH-009 |
| Validation errors name the violated rule | Std §5:508 | test | T-CRED-004, T-CRED-001, T-CRED-002, T-AUTH-012 |
| Error bodies conform to documented schema in all environments | Std §5:509 | test | T-AUTH-012, T-AUTH-011, T-AUTH-008 |
| INFO events for login, logout, password and admin operations | Std §5:513 | test | T-AUD-007, T-AUD-014, T-AUD-017 |
| WARN events for failures, lockouts, rate limits, denials | Std §5:514 | test | T-AUD-007, T-AUD-014, T-AUD-017 |
| Logs never contain passwords, CSRF tokens, session IDs, reset tokens | Std §5:515 | test | T-AUD-013, T-AUD-018, T-AUD-019, T-AUD-020 |
| Audit trail retained minimum 90 days | Std §5:516 | register | 17:410 |
| Owners notified on password change, lock, reset completion | Std §5:517 | register | 17:411 |
| Dependency-Check as CI gate blocking release | Std §5:521 | register | 17:412 |
| Use deterministic synthetic usernames and emails | Std §5:525 | new | T-ARCH-003 |
| Fixed or controllable clocks for inactivity and grace tests | Std §5:526 | test | T-RL-018, T-MFA-008, T-ARCH-001 |
| Never log plaintext reset tokens | Std §5:527 | test | T-AUD-019, T-AUD-030, T-AUD-013 |
| Test tokens with production entropy and format | Std §5:528 | new | T-ARCH-004 |
| Login success | PRD:155 | test | T-SES-005 |
| Login wrong password gives generic error | PRD:155 | test | T-AUTH-006, T-AUTH-010 |
| Unknown username gives identical generic error | PRD:155 | test | T-AUTH-006, T-AUTH-003 |
| Login on locked account refused | PRD:155 | new | T-LCK-004 |
| N failed attempts trigger lockout | PRD:156 | test | T-LCK-012, T-LCK-008, T-LCK-003 |
| Successful login after cooldown resets counter | PRD:156 | new | T-LCK-002, T-LCK-006 |
| IP throttling independent of account lockout | PRD:156 | test | T-RL-001, T-RL-006 |
| Reused session cookie rejected after logout | PRD:157 | new | T-SES-007 |
| Reset token single-use | PRD:158 | test | T-CRED-014 |
| Reset token expiry | PRD:158 | new | T-CRED-013 |
| Reset invalidates existing sessions | PRD:158 | test | T-SES-014 |
| Admin cannot disable, delete or demote own account | PRD:159 | test | T-ADM-003 |
| USER calling any /api/admin/** gets 403 | PRD:160 | new | T-ADM-013, T-AUTH-002 |
| PIN verify success | MFA §5:388 | register | owed-17 |
| PIN verify failure | MFA §5:389 | register | owed-17 |
| PIN verify missing header | MFA §5:390 | register | owed-17 |
| PIN format validation | MFA §5:391 | register | owed-17 |
| PIN setup guard | MFA §5:392 | register | owed-17 |
| TOTP computation with fixed secret and timestamp | MFA §5:393 | test | T-MFA-012, T-MFA-013 |
| TOTP ±1 windows accepted, ±2 rejected | MFA §5:394 | new | T-MFA-011, T-MFA-014 |
| TOTP period boundary accepts previous window | MFA §5:395 | new | T-MFA-010 |
| TOTP replay rejected, lastUsedCounter updated | MFA §5:396 | test | T-MFA-014 |
| TOTP provisioning: encryption, QR bytes, failure error | MFA §5:397 | new | T-MFA-017, T-MFA-022, T-MFA-003 |
| Full request flow with principal and X-PIN, X-TOTP headers | MFA §5:401 | test | T-E2E-005, T-MFA-002 |
| Encrypt/decrypt via deterministic passthrough doubles | MFA §5:402 | new | T-MFA-017 |
| Deterministic fixed-byte TOTP secrets | MFA §5:406 | test | T-MFA-012, T-MFA-013 |
| Fixed timestamp for counter and TTL boundaries | MFA §5:407 | test | T-MFA-014, T-RL-018, T-MFA-010 |
| No real PII in fixtures | MFA §5:408 | new | T-ARCH-003 |
| Stub or fixed-key encryption in CI | MFA §5:409 | new | T-MFA-017 |
| Domain exceptions mapped to correct HTTP status | MFA §5:415 | new | T-MFA-019, T-MFA-006, T-MFA-014 |
| Audit events with required fields on success and failure | MFA §5:416 | test | T-AUD-007, T-AUD-021, T-MFA-009 |
| Brute-force lockout and backoff enforced | MFA §5:417 | new | T-MFA-020, T-MFA-021, T-RL-010 |
| Log output structured and parsable | LOG §5:346 | test | T-AUD-010 |
| Required fields present in all log events | LOG §5:347 | new | T-AUD-001 |
| Timestamps consistent with system time and synchronisation | LOG §5:348 | register | 17:184 |
| Startup log has service metadata, profiles, host, no secrets | LOG §5:349 | new | T-AUD-003 |
| Trace and span IDs propagate across threads and async | LOG §5:352 | register | owed-17 |
| MDC fields propagate across @Async | LOG §5:353 | register | owed-17 |
| MDC set for request lifecycle and cleared after | LOG §5:354 | new | T-AUD-004 |
| Correlation ID injected into outbound requests | LOG §5:355 | register | owed-17 |
| Request logs include method, path, status, duration, outcome | LOG §5:358 | register | owed-17 |
| Request logs exclude IP, query params, auth headers, bodies | LOG §5:359 | new | T-AUD-006, T-AUD-018 |
| Error paths logged once with required error fields | LOG §5:362 | new | T-AUD-002 |
| Auth failure logs generic, not revealing account existence | LOG §5:365 | register | 17:261 |
| All audit-contract events logged | LOG §5:366 | test | T-AUD-014, T-AUD-007 |
| No sensitive data in logs | LOG §5:367 | test | T-AUD-013, T-AUD-018, T-AUD-019, T-AUD-020, T-AUD-021, T-AUD-022 |
| User identifiers only as user.id UUID | LOG §5:368 | test | T-AUD-008, T-AUD-009, T-AUD-026 |
| Audit events in dedicated audit destination | LOG §5:369 | new | T-AUD-005 |
| Log injection does not corrupt output | LOG §5:370 | test | T-AUD-023, T-AUD-024 |
| Access controls protect log data | LOG §5:371 | register | 17:184 |
| Logging performs under load without dropping events | LOG §5:374 | register | owed-17 |
| Logging failures do not crash the application | LOG §5:375 | new | T-AUD-031 |
| Destination outages do not break application flow | LOG §5:376 | new | T-AUD-031 |
| Deterministic synthetic IDs in test data | LOG §5:379 | new | T-ARCH-003 |
| Assert sanitized injection payload, not raw | LOG §5:380 | test | T-AUD-023 |
